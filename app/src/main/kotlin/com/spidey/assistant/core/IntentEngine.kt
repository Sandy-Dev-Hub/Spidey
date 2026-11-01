package com.spidey.assistant.core

import com.spidey.assistant.actions.AllowedApp
import com.spidey.assistant.actions.AssistantAction
import com.spidey.assistant.actions.CallContactAction
import com.spidey.assistant.actions.FindContactAction
import com.spidey.assistant.actions.GreetingAction
import com.spidey.assistant.actions.OpenAppAction
import com.spidey.assistant.actions.SearchYouTubeAction
import com.spidey.assistant.actions.StopAction
import com.spidey.assistant.calling.CallSessionManager
import com.spidey.assistant.safety.ConfirmationPolicy

/**
 * Local rule-based intent engine.
 * Converts natural speech text into a typed [AssistantAction] without external cloud or LLM dependencies.
 */
class IntentEngine {

    fun parse(text: String, candidatesContext: List<String> = emptyList()): AssistantAction? {
        val normalized = text.trim().lowercase()
        if (normalized.isBlank()) return null

        // 1. If clarifying between ambiguous options or confirming an action
        if (candidatesContext.isNotEmpty()) {
            val clarifiedAction = handleClarification(normalized, candidatesContext)
            if (clarifiedAction != null) {
                return clarifiedAction
            }
        }

        // 2. Check for explicit Stop / Sleep commands (e.g. "stop", "sleep", "bye", "cancel")
        if (isStopCommand(normalized)) {
            return StopAction()
        }

        // 3. Check for Greeting intent
        if (isGreeting(normalized)) {
            return GreetingAction(queryText = text)
        }

        // 3. Check for YouTube Search / Play intent (e.g. "search song on youtube", "play ...")
        val ytSearchAction = matchYouTubeSearch(normalized)
        if (ytSearchAction != null) {
            return ytSearchAction
        }

        // 4. Check for Open App intent (e.g. "open whatsapp", "open chrome", "open youtube")
        val openAppAction = matchOpenApp(normalized)
        if (openAppAction != null) {
            return openAppAction
        }

        // 5. Check for Calling intent (Phase 3)
        val callAction = matchCall(normalized)
        if (callAction != null) {
            return callAction
        }

        // 6. Check for Contact Search intent (Phase 2)
        val contactAction = matchContactSearch(normalized)
        if (contactAction != null) {
            return contactAction
        }

        return null
    }

    private fun handleClarification(reply: String, candidatesContext: List<String>): AssistantAction? {
        // A. Confirmation or Cancellation of a pending call
        if (candidatesContext.contains("confirm_call") || (candidatesContext.contains("yes") && candidatesContext.contains("no"))) {
            val session = CallSessionManager.getValidSession()
            val conf = ConfirmationPolicy.evaluate(reply)
            return when (conf) {
                ConfirmationPolicy.ConfirmationResult.CONFIRMED -> {
                    CallContactAction(
                        contactQuery = session?.contactQuery ?: "",
                        selectedNumber = session?.selectedNumber,
                        selectedSim = session?.selectedSim,
                        isConfirmed = true
                    )
                }
                ConfirmationPolicy.ConfirmationResult.CANCELLED -> {
                    CallContactAction(
                        contactQuery = session?.contactQuery ?: "",
                        isCancelled = true
                    )
                }
                ConfirmationPolicy.ConfirmationResult.UNKNOWN -> null
            }
        }

        // B. SIM account selection
        if (candidatesContext.contains("sim_choice")) {
            val session = CallSessionManager.getValidSession()
            val choices = candidatesContext.filter { it != "sim_choice" }
            val chosen = resolveClarification(reply, choices) ?: reply
            return CallContactAction(
                contactQuery = session?.contactQuery ?: "",
                selectedNumber = session?.selectedNumber,
                selectedSim = chosen
            )
        }

        // C. Phone number selection
        if (candidatesContext.contains("number_choice")) {
            val session = CallSessionManager.getValidSession()
            val choices = candidatesContext.filter { it != "number_choice" }
            val chosen = resolveClarification(reply, choices) ?: reply
            return CallContactAction(
                contactQuery = session?.contactQuery ?: "",
                selectedNumber = chosen,
                selectedSim = session?.selectedSim
            )
        }

        // D. Ambiguous contact resolution
        val resolvedCandidate = resolveClarification(reply, candidatesContext)
        if (resolvedCandidate != null) {
            val session = CallSessionManager.getValidSession()
            return if (session != null) {
                CallContactAction(contactQuery = resolvedCandidate)
            } else {
                FindContactAction(query = resolvedCandidate)
            }
        }

        return null
    }

    private fun isStopCommand(text: String): Boolean {
        val clean = text.replace(Regex("[^a-z0-9 ]"), "").trim()
        val stopPhrases = setOf(
            "stop", "cancel", "bye", "goodbye", "sleep", "shut down", "go to sleep",
            "exit", "close", "turn off", "quit", "stop listening", "stop assistant"
        )
        return clean in stopPhrases
    }

    private fun isGreeting(text: String): Boolean {
        val clean = text.replace(Regex("[^a-z0-9 ]"), "").trim()
        val exactMatches = setOf(
            "hello", "hi", "hey", "hey spidey", "hey spider", "spider", "spidey",
            "good morning", "good afternoon", "good evening",
            "what's up", "whats up", "how are you", "who are you"
        )
        if (clean in exactMatches) return true

        val words = clean.split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (words.size == 1 && words[0] in setOf("hello", "hi", "hey", "sup")) {
            return true
        }

        // Starting with hello/hi/hey but not a command
        if (words.size <= 2 && words[0] in setOf("hello", "hi", "hey")) {
            return true
        }

        return false
    }

    private fun matchYouTubeSearch(text: String): AssistantAction? {
        val clean = text.replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

        // 1. Explicit YouTube search patterns including compound "open youtube and search..."
        val explicitPrefixes = listOf(
            "open youtube and search for song",
            "open youtube and search for the song",
            "open youtube and search song",
            "open youtube and search for video",
            "open youtube and search for movie",
            "open youtube and search for",
            "open youtube and search",
            "open youtube and play song",
            "open youtube and play",
            "open youtube and find",
            "open youtube search for song",
            "open youtube search for",
            "open youtube search",
            "open youtube play",
            "go to search bar and search for song",
            "go to search bar and search for the song",
            "go to search bar and search for",
            "go to search bar and search song",
            "go to search bar and search",
            "search bar and search for",
            "search bar and search",
            "search for song",
            "search for the song",
            "search song",
            "search for video",
            "search video",
            "search for movie",
            "search movie",
            "search for music",
            "search music",
            "search for track",
            "search on youtube for",
            "search on youtube",
            "search in youtube for",
            "search in youtube",
            "search youtube for",
            "search youtube",
            "play on youtube",
            "play in youtube",
            "play song",
            "play the song",
            "play movie",
            "play video",
            "play music",
            "play track",
            "in youtube search for",
            "in youtube search"
        )

        for (prefix in explicitPrefixes) {
            if (clean.startsWith("$prefix ")) {
                var query = clean.removePrefix("$prefix ").trim()
                query = query.removeSuffix(" on youtube").removeSuffix(" in youtube").trim()
                if (query.isNotBlank()) {
                    return SearchYouTubeAction(query = query)
                }
            }
        }

        // 2. Trailing "on youtube" patterns: e.g. "search despacito on youtube", "play believer on youtube"
        if (clean.endsWith(" on youtube") || clean.endsWith(" in youtube")) {
            var trimmed = clean.removeSuffix(" on youtube").removeSuffix(" in youtube").trim()
            val leadingTriggers = listOf("search for", "search", "play", "find", "look for", "listen to")
            for (trigger in leadingTriggers) {
                if (trimmed.startsWith("$trigger ")) {
                    trimmed = trimmed.removePrefix("$trigger ").trim()
                    break
                }
            }
            if (trimmed.isNotBlank()) {
                return SearchYouTubeAction(query = trimmed)
            }
        }

        // 3. Media-specific keywords with search/find verbs (e.g. "search for song magale from baththa movie", "find song ...")
        val mediaKeywords = setOf("song", "songs", "movie", "movies", "video", "videos", "music", "track")
        val words = clean.split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (words.any { it in mediaKeywords }) {
            val searchTriggers = listOf("search for", "search", "find", "look for", "listen to")
            for (trigger in searchTriggers) {
                if (clean.startsWith("$trigger ")) {
                    var candidate = clean.removePrefix("$trigger ").trim()
                    // Strip optional leading descriptor word (e.g. "song magale..." -> "magale...", "the song...")
                    val stripLeading = listOf("the song ", "song ", "the movie ", "movie ", "the video ", "video ", "the music ", "music ", "the track ", "track ")
                    for (descriptor in stripLeading) {
                        if (candidate.startsWith(descriptor)) {
                            candidate = candidate.removePrefix(descriptor).trim()
                            break
                        }
                    }
                    if (candidate.isNotBlank()) {
                        return SearchYouTubeAction(query = candidate)
                    }
                }
            }
        }

        // 4. "play [song/artist]" intent (e.g. "play some song", "play believer")
        if (clean.startsWith("play ") && clean != "play youtube") {
            val query = clean.removePrefix("play ").trim()
            if (query.isNotBlank()) {
                return SearchYouTubeAction(query = query)
            }
        }

        return null
    }

    private fun matchOpenApp(text: String): AssistantAction? {
        val clean = text.replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
        val words = clean.split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (words.isEmpty()) return null

        val appTriggers = listOf("open up", "open", "launch", "start", "play", "go to", "show", "run")

        var appName: String? = null

        // Check if starts with a trigger
        for (trigger in appTriggers) {
            if (clean.startsWith("$trigger ")) {
                val candidate = clean.removePrefix("$trigger ").trim()
                if (candidate.isNotBlank()) {
                    appName = candidate
                    break
                }
            }
        }

        // Single word or known standalone app name (e.g. "whatsapp", "youtube", "camera", "spotify", "chrome")
        if (appName == null) {
            if (words.size in 1..2) {
                val known = AllowedApp.entries.firstOrNull {
                    it.displayName.equals(clean, ignoreCase = true) ||
                    it.aliases.any { alias -> alias.equals(clean, ignoreCase = true) }
                }
                if (known != null) {
                    appName = known.displayName
                }
            }
        }

        if (appName != null) {
            // Strip any trailing filler words
            appName = appName.removeSuffix(" app").removeSuffix(" application").trim()

            // Check if matches AllowedApp enum
            val matchedAllowed = AllowedApp.entries.firstOrNull {
                it.displayName.equals(appName, ignoreCase = true) ||
                it.aliases.any { alias -> alias.equals(appName, ignoreCase = true) }
            }

            return if (matchedAllowed != null) {
                OpenAppAction(appName = matchedAllowed.displayName, packageName = matchedAllowed.packageName)
            } else {
                OpenAppAction(appName = appName)
            }
        }

        return null
    }

    private fun matchCall(text: String): AssistantAction? {
        val clean = text.replace(Regex("[^a-z0-9 +]"), " ").replace(Regex("\\s+"), " ").trim()

        val callPrefixes = listOf(
            "make a call to",
            "place a call to",
            "give a call to",
            "call up",
            "call to",
            "call",
            "dial",
            "phone"
        )

        for (prefix in callPrefixes) {
            if (clean.startsWith("$prefix ")) {
                val remainder = clean.removePrefix("$prefix ").trim()
                if (remainder.isNotBlank()) {
                    return parseCallTarget(remainder)
                }
            }
        }

        return null
    }

    private fun parseCallTarget(target: String): CallContactAction {
        // Check for SIM trailer: "on airtel", "on jio", "on sim 1", "using jio", etc.
        val simPattern = Regex("^(.*?)\\s+(?:on|using|via|through)\\s+(sim\\s*\\d+|airtel.*|jio.*|vi.*|bsnl.*)$", RegexOption.IGNORE_CASE)
        val match = simPattern.find(target)

        return if (match != null) {
            val contact = match.groupValues[1].trim()
            val sim = match.groupValues[2].trim()
            CallContactAction(contactQuery = contact, selectedSim = sim)
        } else {
            CallContactAction(contactQuery = target)
        }
    }

    private fun matchContactSearch(text: String): AssistantAction? {
        val clean = text.replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

        // Guard: If text mentions songs, movies, videos, music, or youtube, it is NEVER a contact lookup!
        val mediaKeywords = setOf("song", "songs", "movie", "movies", "video", "videos", "music", "youtube", "track", "album")
        val words = clean.split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (words.any { it in mediaKeywords }) {
            return null
        }

        val prefixes = listOf(
            "search for contact",
            "search contact",
            "search for",
            "search",
            "find contact",
            "find",
            "lookup contact",
            "look up contact",
            "lookup",
            "look up",
            "who is",
            "get number for",
            "number for",
            "contact details for",
            "contact for",
            "contact"
        )

        for (prefix in prefixes) {
            if (clean.startsWith("$prefix ")) {
                val query = clean.removePrefix("$prefix ").trim()
                if (query.isNotBlank()) {
                    return FindContactAction(query = query)
                }
            }
        }

        return null
    }

    private fun resolveClarification(reply: String, candidates: List<String>): String? {
        val clean = reply.replace(Regex("[^a-z0-9 ]"), " ").trim()

        // Ordinal indicators
        if (clean in setOf("first", "the first", "first one", "the first one", "1", "one", "number one")) {
            return candidates.getOrNull(0)
        }
        if (clean in setOf("second", "the second", "second one", "the second one", "2", "two", "number two")) {
            return candidates.getOrNull(1)
        }
        if (clean in setOf("third", "the third", "third one", "the third one", "3", "three", "number three")) {
            return candidates.getOrNull(2)
        }
        if (clean in setOf("fourth", "the fourth", "fourth one", "the fourth one", "4", "four", "number four")) {
            return candidates.getOrNull(3)
        }

        // Substring / token matching against candidate names
        for (candidate in candidates) {
            val normCandidate = candidate.lowercase().replace(Regex("[^a-z0-9 ]"), " ").trim()
            if (normCandidate.contains(clean) || clean.contains(normCandidate)) {
                return candidate
            }
        }

        return null
    }
}
