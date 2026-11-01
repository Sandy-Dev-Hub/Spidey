package com.spidey.assistant.apps.matcher

import com.spidey.assistant.apps.model.AppRecord

sealed class AppMatchResult {
    data class Resolved(val app: AppRecord, val score: Double) : AppMatchResult()
    data class Ambiguous(val candidates: List<AppRecord>) : AppMatchResult()
    object NotFound : AppMatchResult()
}

/**
 * Matches a spoken app name against installed applications using normalization,
 * a comprehensive alias table, and Levenshtein fuzzy distance matching.
 * Ambiguous close matches return [AppMatchResult.Ambiguous].
 */
class AppMatcher(
    private val customAliases: Map<String, String> = emptyMap()
) {
    companion object {
        val DEFAULT_ALIASES = mapOf(
            "insta" to "Instagram",
            "ig" to "Instagram",
            "wa" to "WhatsApp",
            "watsapp" to "WhatsApp",
            "whats app" to "WhatsApp",
            "yt" to "YouTube",
            "you tube" to "YouTube",
            "fb" to "Facebook",
            "face book" to "Facebook",
            "calc" to "Calculator",
            "calculator" to "Calculator",
            "cam" to "Camera",
            "camera" to "Camera",
            "photos" to "Gallery",
            "pictures" to "Gallery",
            "gallery" to "Gallery",
            "sms" to "Messages",
            "text" to "Messages",
            "texts" to "Messages",
            "message" to "Messages",
            "messages" to "Messages",
            "mail" to "Gmail",
            "email" to "Gmail",
            "gmail" to "Gmail",
            "music" to "Spotify",
            "songs" to "Spotify",
            "spotify" to "Spotify",
            "browser" to "Chrome",
            "internet" to "Chrome",
            "google chrome" to "Chrome",
            "chrome" to "Chrome",
            "dialer" to "Phone",
            "call app" to "Phone",
            "phone app" to "Phone",
            "phone" to "Phone",
            "contacts" to "Contacts",
            "contact" to "Contacts",
            "phonebook" to "Contacts",
            "setting" to "Settings",
            "settings" to "Settings",
            "alarm" to "Clock",
            "timer" to "Clock",
            "clock" to "Clock",
            "notes" to "Keep Notes",
            "keep" to "Keep Notes",
            "tg" to "Telegram",
            "telegram" to "Telegram"
        )
    }

    fun match(query: String, installedApps: List<AppRecord>): AppMatchResult {
        val cleanQuery = query.trim().lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (cleanQuery.isBlank() || installedApps.isEmpty()) {
            return AppMatchResult.NotFound
        }

        // 1. Alias lookup
        val aliasTarget = customAliases[cleanQuery] ?: DEFAULT_ALIASES[cleanQuery]
        if (aliasTarget != null) {
            val targetLower = aliasTarget.lowercase()
            val directAlias = installedApps.firstOrNull {
                it.label.equals(aliasTarget, ignoreCase = true) ||
                it.normalizedLabel == targetLower ||
                it.normalizedLabel.split(" ").contains(targetLower) ||
                it.normalizedLabel.contains(targetLower) ||
                targetLower.contains(it.normalizedLabel)
            }
            if (directAlias != null) {
                return AppMatchResult.Resolved(directAlias, 1.0)
            }
            // If alias points to a target name, check fuzzy against alias target
            val aliasFuzzy = installedApps.map { app ->
                app to calculateSimilarity(targetLower, app.normalizedLabel)
            }.maxByOrNull { it.second }
            if (aliasFuzzy != null && aliasFuzzy.second >= 0.70) {
                return AppMatchResult.Resolved(aliasFuzzy.first, aliasFuzzy.second)
            }
        }

        // 2. Exact match on normalized label
        val exactMatch = installedApps.firstOrNull { it.normalizedLabel == cleanQuery }
        if (exactMatch != null) {
            return AppMatchResult.Resolved(exactMatch, 1.0)
        }

        // 3. Exact word boundary / whole token match
        val wordMatches = installedApps.filter { app ->
            val appWords = app.normalizedLabel.split(" ")
            val queryWords = cleanQuery.split(" ")
            app.normalizedLabel.startsWith("$cleanQuery ") ||
            app.normalizedLabel.endsWith(" $cleanQuery") ||
            app.normalizedLabel.contains(" $cleanQuery ") ||
            cleanQuery.startsWith("${app.normalizedLabel} ") ||
            cleanQuery.endsWith(" ${app.normalizedLabel}") ||
            cleanQuery.contains(" ${app.normalizedLabel} ") ||
            (queryWords.size == 1 && appWords.contains(cleanQuery))
        }
        if (wordMatches.size == 1) {
            return AppMatchResult.Resolved(wordMatches.first(), 0.95)
        }

        // 4. Fuzzy scoring with Levenshtein similarity
        val scored = installedApps.map { app ->
            val levSim = calculateSimilarity(cleanQuery, app.normalizedLabel)
            val subScore = if (app.normalizedLabel.contains(cleanQuery) || cleanQuery.contains(app.normalizedLabel)) 0.82 else 0.0
            val finalScore = maxOf(levSim, subScore)
            app to finalScore
        }.sortedByDescending { it.second }

        val topScore = scored.firstOrNull()?.second ?: 0.0
        if (topScore < 0.60) {
            return AppMatchResult.NotFound
        }

        // Disambiguation: Check for close matches (within 0.08 of top score)
        val closeCandidates = scored.filter { it.second >= 0.70 && (topScore - it.second) <= 0.08 }
        if (closeCandidates.size > 1 && topScore < 0.98) {
            return AppMatchResult.Ambiguous(closeCandidates.take(3).map { it.first })
        }

        return AppMatchResult.Resolved(scored.first().first, topScore)
    }

    fun calculateSimilarity(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0
        val dist = levenshteinDistance(s1, s2)
        val maxLen = maxOf(s1.length, s2.length)
        return 1.0 - (dist.toDouble() / maxLen)
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[s1.length][s2.length]
    }
}

