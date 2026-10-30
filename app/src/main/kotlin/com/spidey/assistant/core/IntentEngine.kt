package com.spidey.assistant.core

import com.spidey.assistant.actions.AllowedApp
import com.spidey.assistant.actions.AssistantAction
import com.spidey.assistant.actions.CallContactAction
import com.spidey.assistant.actions.FindContactAction
import com.spidey.assistant.actions.GreetingAction
import com.spidey.assistant.actions.OpenAppAction
import com.spidey.assistant.actions.SearchYouTubeAction
import com.spidey.assistant.actions.StopAction
import com.spidey.assistant.actions.TimeAction
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

        val isYouTubeContext = candidatesContext.contains("context:youtube")

        // 2. Check for explicit Stop / Sleep commands (e.g. "stop", "sleep", "bye", "cancel")
        if (isStopCommand(normalized)) {
            return StopAction()
        }

        // 3. Check for Time or Date request (e.g. "what is the time now")
        val timeAction = matchTimeOrDate(normalized)
        if (timeAction != null) {
            return timeAction
        }

        // 4. Check for user feedback / "I don't see anything"
        val feedbackAction = matchFeedback(normalized)
        if (feedbackAction != null) {
            return feedbackAction
        }

        // 5. Check for Greeting intent
        if (isGreeting(normalized)) {
            return GreetingAction(queryText = text)
        }

        // 6. Check for YouTube Search / Play intent
        val ytSearchAction = matchYouTubeSearch(normalized, isYouTubeContext)
        if (ytSearchAction != null) {
            return ytSearchAction
        }

        // 7. Check for Open App intent (e.g. "open whatsapp", "open chrome", "open telegram")
        val openAppAction = matchOpenApp(normalized)
        if (openAppAction != null) {
            return openAppAction
        }

        // 8. Check for Calling intent (Phase 3)
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

    private fun matchTimeOrDate(text: String): AssistantAction? {
        val clean = text.replace(Regex("[^a-z0-9 ]"), " ").trim()
        val timePhrases = setOf(
            "what is the time now", "what is the time", "what time is it", "tell me the time",
            "what is the current time", "whats the time", "time now", "the time", "time"
        )
        if (clean in timePhrases) {
            return TimeAction(isDateOnly = false)
        }
        val datePhrases = setOf(
            "what is the date", "what is today's date", "what is todays date", "what date is it",
            "today's date", "todays date", "tell me the date", "date today", "date"
        )
        if (clean in datePhrases) {
            return TimeAction(isDateOnly = true)
        }
        return null
    }

    private fun matchFeedback(text: String): AssistantAction? {
        val clean = text.replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
        val feedbackPhrases = setOf(
            "i dont see anything", "i do not see anything", "i m not see anything", "im not see anything",
            "i cannot see anything", "cant see anything", "nothing is showing", "not showing anything",
            "i m not seeing anything", "im not seeing anything", "i am not seeing anything",
            "it is still on home page", "still on home page", "it is on home page", "on home page",
            "not opening anything", "nothing is opening", "it is not opening", "it not opening",
            "why not opening", "why is it not opening"
        )
        if (clean in feedbackPhrases || feedbackPhrases.any { clean.contains(it) }) {
            return GreetingAction("I'm right here. Tell me what video, song, or app you would like me to open, and I will open it for you.")
        }
        return null
    }

    private fun matchYouTubeSearch(text: String, isYouTubeContext: Boolean = false): AssistantAction? {
        val clean = text.replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

        // 1. Explicit YouTube search patterns including compound "open youtube and search..."
        val explicitPrefixes = listOf(
            "open youtube and search for song",
            "open youtube and search for the song",
            "open youtube and search for trailer",
            "open youtube and search for the trailer",
            "open youtube and search trailer",
            "open youtube and search song",
            "open youtube and search for video",
            "open youtube and search for movie",
            "open youtube and search for",
            "open youtube and search",
            "open youtube and play song",
            "open youtube and play",
            "open youtube and find",
            "open youtube search for song",
            "open youtube search for trailer",
            "open youtube search for",
            "open youtube play",
            "go to youtube and search for song",
            "go to youtube and search for the song",
            "go to youtube and search for trailer",
            "go to youtube and search for the trailer",
            "go to youtube and search trailer",
            "go to youtube and search song",
            "go to youtube and search for video",
            "go to youtube and search for movie",
            "go to youtube and search for",
            "go to youtube and search",
            "go to youtube and play song",
            "go to youtube and play",
            "go to youtube search for song",
            "go to youtube search for trailer",
            "go to youtube search for",
            "go to youtube search",
            "go to youtube play",
            "go to search bar and search for song",
            "go to search bar and search for the song",
            "go to search bar and search for trailer",
            "go to search bar and search for",
            "go to search bar and search song",
            "go to search bar and search trailer",
            "go to search bar and search",
            "search bar and search for",
            "search bar and search",
            "search for song",
            "search for the song",
            "search song",
            "search for trailer",
            "search for the trailer",
            "search trailer",
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
            "play trailer",
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

        // 3. Media-specific keywords with search/find verbs (e.g. "search for infinity war trailer", "search for song ...")
        val mediaKeywords = setOf(
            "song", "songs", "movie", "movies", "video", "videos", "music", "track", "tracks",
            "trailer", "trailers", "teaser", "teasers", "film", "films", "clip", "clips",
            "scene", "scenes", "episode", "episodes", "show", "shows", "series", "cinema"
        )
        val words = clean.split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (words.any { it in mediaKeywords }) {
            val searchTriggers = listOf("search for", "search", "find", "look for", "listen to", "play")
            for (trigger in searchTriggers) {
                if (clean.startsWith("$trigger ")) {
                    var candidate = clean.removePrefix("$trigger ").trim()
                    // Strip optional leading descriptor word (e.g. "trailer infinity war..." -> "infinity war...", "the song...")
                    val stripLeading = listOf(
                        "the song ", "song ", "the movie ", "movie ", "the video ", "video ",
                        "the music ", "music ", "the track ", "track ", "the trailer ", "trailer ",
                        "the teaser ", "teaser ", "the clip ", "clip "
                    )
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

        // 4. In YouTube Context (user just opened YouTube): ANY search/find/play query routes to YouTube!
        if (isYouTubeContext) {
            val searchTriggers = listOf(
                "go to search bar and search for", "go to search bar and search",
                "search bar and search for", "search bar and search", "search bar search for", "search bar search",
                "search for", "search", "find", "look for", "play", "listen to"
            )
            for (trigger in searchTriggers) {
                if (clean.startsWith("$trigger ")) {
                    val candidate = clean.removePrefix("$trigger ").trim()
                    if (candidate.isNotBlank()) {
                        return SearchYouTubeAction(query = candidate)
                    }
                }
            }
            // If user simply speaks a media or video title (e.g. "infinity war trailer", "magale song", "avengers") while in YouTube
            val nonQueryWords = setOf("stop", "sleep", "bye", "cancel", "exit", "quit", "hello", "hi", "hey", "back", "home")
            if (clean !in nonQueryWords && clean.isNotBlank()) {
                return SearchYouTubeAction(query = clean)
            }
        }

        // 5. "play [song/artist]" intent (e.g. "play some song", "play believer")
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

        val appTriggers = listOf(
            "click open with", "click open", "open with", "open up", "open", "launch",
            "start", "play", "go to", "show", "run", "click on", "switch to"
        )

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

        // Check if any known app name is mentioned with an open/show verb anywhere in the command
        // e.g. "gallery open the recent", "open the recent photo in gallery"
        if (appName == null) {
            val openVerbs = setOf("open", "launch", "show", "start", "view", "go")
            if (words.any { it in openVerbs }) {
                for (allowed in AllowedApp.entries) {
                    val names = listOf(allowed.displayName.lowercase()) + allowed.aliases
                    if (names.any { name -> words.contains(name) }) {
                        appName = allowed.displayName
                        break
                    }
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
            // Strip any leading or trailing filler words
            appName = appName.removePrefix("with ").trim()
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
        val mediaKeywords = setOf(
            "song", "songs", "movie", "movies", "video", "videos", "music", "track", "tracks",
            "trailer", "trailers", "teaser", "teasers", "film", "films", "clip", "clips",
            "scene", "scenes", "episode", "episodes", "show", "shows", "series", "cinema",
            "youtube", "album"
        )
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
