package com.spidey.assistant.contacts.matcher

import com.spidey.assistant.contacts.model.ContactRecord
import com.spidey.assistant.contacts.model.ContactResolution
import com.spidey.assistant.data.ContactAlias
import kotlin.math.max
import kotlin.math.min

/**
 * Multi-stage contact matching engine designed for Indian names, honorifics (Anna, Bhai, Ji),
 * and speech recognition variations.
 *
 * Pipeline:
 * 1. Normalization (case, punctuation, spacing)
 * 2. Exact name match
 * 3. User alias lookup
 * 4. Token & prefix/suffix overlap
 * 5. Levenshtein fuzzy string distance
 * 6. Ambiguity thresholding & tie-breaking
 */
class ContactMatcher(
    private val ambiguityDeltaThreshold: Double = 0.08,
    private val minimumResolvedScore: Double = 0.72
) {

    fun match(
        query: String,
        contacts: List<ContactRecord>,
        aliases: List<ContactAlias> = emptyList()
    ): ContactResolution {
        val normQuery = normalize(query)
        if (normQuery.isBlank() || contacts.isEmpty()) {
            return ContactResolution.NotFound(query)
        }

        // 1. Check user alias mapping first
        val aliasMatch = aliases.firstOrNull { normalize(it.alias) == normQuery }
        if (aliasMatch != null) {
            val contact = contacts.firstOrNull { it.id == aliasMatch.contactId }
            if (contact != null) {
                return ContactResolution.Resolved(contact, 1.0)
            }
        }

        // 2. Score each contact
        val queryTokens = normQuery.split("\\s+".toRegex()).filter { it.isNotBlank() }

        val scoredCandidates = contacts.mapNotNull { contact ->
            val normName = normalize(contact.displayName)
            if (normName.isBlank()) return@mapNotNull null

            val score = computeScore(normQuery, queryTokens, normName)
            if (score >= 0.50) {
                ScoredContact(contact, score)
            } else null
        }.sortedByDescending { it.score }

        if (scoredCandidates.isEmpty()) {
            return ContactResolution.NotFound(query)
        }

        val top = scoredCandidates[0]

        // 3. Check for ambiguity between top candidates
        if (scoredCandidates.size > 1) {
            val second = scoredCandidates[1]
            val scoreDiff = top.score - second.score

            // If the top 2 are very close in score, or top score is borderline
            if (scoreDiff < ambiguityDeltaThreshold || top.score < minimumResolvedScore) {
                val ambiguousCandidates = scoredCandidates
                    .filter { (top.score - it.score) < 0.15 }
                    .take(4)
                    .map { it.contact }
                return ContactResolution.Ambiguous(query, ambiguousCandidates)
            }
        }

        // 4. Check confidence threshold for single match
        return if (top.score >= minimumResolvedScore) {
            ContactResolution.Resolved(top.contact, top.score)
        } else {
            ContactResolution.NotFound(query)
        }
    }

    private fun computeScore(
        normQuery: String,
        queryTokens: List<String>,
        normName: String
    ): Double {
        // Exact match
        if (normQuery == normName) return 1.0

        val nameTokens = normName.split("\\s+".toRegex()).filter { it.isNotBlank() }

        // Token containment: all query tokens present in name tokens
        val matchedTokens = queryTokens.count { qToken ->
            nameTokens.any { nToken -> nToken == qToken || (qToken.length > 3 && nToken.startsWith(qToken)) }
        }

        var tokenScore = 0.0
        if (matchedTokens > 0) {
            val coverage = matchedTokens.toDouble() / queryTokens.size
            val nameRatio = matchedTokens.toDouble() / nameTokens.size
            if (coverage >= 1.0) {
                // Query fully covered by contact name tokens
                tokenScore = 0.85 + (nameRatio * 0.10)
            } else {
                tokenScore = 0.60 * coverage + 0.20 * nameRatio
            }
        }

        // Fuzzy similarity (Levenshtein)
        val fullFuzzy = similarity(normQuery, normName)

        // Token-level best fuzzy similarity
        val tokenFuzzyAvg = queryTokens.map { q ->
            nameTokens.maxOfOrNull { n -> similarity(q, n) } ?: 0.0
        }.average()

        return max(tokenScore, max(fullFuzzy, tokenFuzzyAvg * 0.85))
    }

    fun normalize(text: String): String {
        return text
            .lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun similarity(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        val len1 = s1.length
        val len2 = s2.length
        if (len1 == 0 || len2 == 0) return 0.0

        val distance = levenshteinDistance(s1, s2)
        val maxLen = max(len1, len2)
        return 1.0 - (distance.toDouble() / maxLen)
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }

        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = min(
                    dp[i - 1][j] + 1, // deletion
                    min(
                        dp[i][j - 1] + 1, // insertion
                        dp[i - 1][j - 1] + cost // substitution
                    )
                )
            }
        }
        return dp[s1.length][s2.length]
    }

    private data class ScoredContact(
        val contact: ContactRecord,
        val score: Double
    )
}
