package com.spidey.assistant.core

import com.spidey.assistant.actions.AllowedApp
import com.spidey.assistant.actions.AssistantAction
import com.spidey.assistant.actions.CallContactAction
import com.spidey.assistant.actions.FindContactAction
import com.spidey.assistant.actions.GreetingAction
import com.spidey.assistant.actions.OpenAppAction
import com.spidey.assistant.actions.PlayFirstResultAction
import com.spidey.assistant.actions.SearchYouTubeAction
import com.spidey.assistant.actions.StopAction
import com.spidey.assistant.actions.TimeAction
import com.spidey.assistant.actions.UnlockAction
import com.spidey.assistant.calling.CallSessionManager
import com.spidey.assistant.safety.ConfirmationPolicy

/**
 * Local deterministic rule-based intent engine.
 * Converts natural speech text into a typed [AssistantAction] without external cloud or LLM dependencies.
 *
 * Deterministic Priority Order:
 * 1. Clarifications / Confirmations (active user reply to Spidey prompts)
 * 2. System Commands (stop, cancel, unlock my phone, time/date, feedback, greetings)
 * 3. Open App Commands ("open whatsapp", "go to whatsapp", "launch chrome", "switch to contacts")
 *    - ALWAYS wins over in-app search, even when YouTube is in front!
 * 4. In-App Commands (YouTube: "play the first one", "search <query>", "play <query>")
 * 5. Calling & Contact Commands ("call mom", "find john")
 * 6. Clarify / null (never fall back to typing raw transcripts into search bars)
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
        val previousSearchQuery = candidatesContext.firstOrNull { it.startsWith("query:") }?.removePrefix("query:")

        // 2. System commands
        // 2a. Stop / Cancel / Sleep commands
        if (isStopCommand(normalized)) {
            return StopAction()
        }

        // 2b. Unlock command ("unlock my phone", "unlock the phone", "unlock phone", "unlock")
        if (isUnlockCommand(normalized)) {
            return UnlockAction
        }

        // 2c. Time or Date request (e.g. "what is the time now", "what is today's date")
        val timeAction = matchTimeOrDate(normalized)
        if (timeAction != null) {
            return timeAction
        }

        // 2d. User feedback / "I don't see anything"
        val feedbackAction = matchFeedback(normalized)
        if (feedbackAction != null) {
            return feedbackAction
        }

        // 2e. Greeting intent
        if (isGreeting(normalized)) {
            return GreetingAction(queryText = text)
        }

        // 3. Open App intent (ALWAYS WINS OVER IN-APP SEARCH)
        // E.g. "open whatsapp", "go to whatsapp", "open contacts", "launch chrome"
        val openAppAction = matchOpenApp(normalized)
        if (openAppAction != null) {
            return openAppAction
        }

        // 4. In-App commands (when foreground app is known and supports them)
        // 4a. "play the first one" / "play first one" / "play the first video" / "play the first song"
        if (isPlayFirstCommand(normalized)) {
            return PlayFirstResultAction(
                query = previousSearchQuery,
                targetApp = "youtube"
            )
        }

        // 4b. YouTube Search / Play intent
        val ytSearchAction = matchYouTubeSearch(normalized, isYouTubeContext)
        if (ytSearchAction != null) {
            return ytSearchAction
        }

        // 5. Calling & Contact intents
        val callAction = matchCall(normalized)
        if (callAction != null) {
            return callAction
        }

        val contactAction = matchContactSearch(normalized)
        if (contactAction != null) {
            return contactAction
        }

        // 6. Fallback: Return null so Spidey speaks "I didn't understand that."
        // NEVER type raw transcript into active fields!
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
            if (ConfirmationPolicy.evaluate(reply) == ConfirmationPolicy.ConfirmationResult.CANCELLED) {
                return CallContactAction(
                    contactQuery = session?.contactQuery ?: "",
                    isCancelled = true
                )
            }
            val choices = candidatesContext.filter {
                it != "sim_choice" && !it.startsWith("context:") && !it.startsWith("query:")
            }
            val chosen = resolveClarification(reply, choices) ?: reply
            return CallContactAction(
                contactQuery = session?.contactQuery ?: "",
                selectedNumber = session?.selectedNumber,
                selectedSim = chosen,
                isConfirmed = true
            )
        }

        // C. Ambiguous Open App selection
        if (candidatesContext.contains("open_app_choice")) {
            val choices = candidatesContext.filter {
                it != "open_app_choice" && !it.startsWith("context:") && !it.startsWith("query:")
            }
            val chosen = resolveClarification(reply, choices) ?: reply
            return OpenAppAction(appName = chosen)
        }

        // D. Phone number selection
        if (candidatesContext.contains("number_choice")) {
            val session = CallSessionManager.getValidSession()
            val choices = candidatesContext.filter {
                it != "number_choice" && !it.startsWith("context:") && !it.startsWith("query:")
            }
            val chosen = resolveClarification(reply, choices) ?: reply
            return CallContactAction(
                contactQuery = session?.contactQuery ?: "",
                selectedNumber = chosen,
                selectedSim = session?.selectedSim
            )
        }

        // E. Ambiguous contact resolution (ONLY when actual candidate contact strings are present)
        val contactCandidates = candidatesContext.filter {
            !it.startsWith("context:") && !it.startsWith("query:") &&
            it != "confirm_call" && it != "sim_choice" && it != "open_app_choice" && it != "number_choice" &&
            it != "yes" && it != "no" && it != "cancel"
        }
        if (contactCandidates.isNotEmpty() && !isPlayFirstCommand(reply)) {
            val resolvedCandidate = resolveClarification(reply, contactCandidates)
            if (resolvedCandidate != null) {
                val session = CallSessionManager.getValidSession()
                return if (session != null) {
                    CallContactAction(contactQuery = resolvedCandidate)
                } else {
                    FindContactAction(query = resolvedCandidate)
                }
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

    private fun isUnlockCommand(text: String): Boolean {
        val clean = text.replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
        val unlockPhrases = setOf(
            "unlock my phone", "unlock the phone", "unlock phone", "unlock",
            "please unlock my phone", "please unlock the phone", "unlock device"
        )
        return clean in unlockPhrases
    }

    private fun isPlayFirstCommand(text: String): Boolean {
        val clean = text.replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
        val playFirstPhrases = setOf(
            "play the first one", "play first one", "play the first", "play first",
            "play the first video", "play first video", "play the first result", "play first result",
            "play the first song", "play first song", "play the 1st one", "play 1st one",
            "play 1st video", "play the 1st video", "play the 1st result", "play 1st result",
            "play the first track", "play first track", "play the first movie", "play first movie"
        )
        return clean in playFirstPhrases
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

        // STRICT SAFETY GUARD:
        // Any phrase starting with open/launch verbs NEVER produces a search action,
        // unless it is an explicit compound search like "open youtube and search..." or "go to search bar..."
        val openVerbs = listOf("open ", "go to ", "launch ", "switch to ", "start ")
        if (openVerbs.any { clean.startsWith(it) }) {
            val isExplicitCompoundSearch = clean.startsWith("open youtube and search") ||
                clean.startsWith("go to youtube and search") ||
                clean.startsWith("open youtube search") ||
                clean.startsWith("go to youtube search") ||
                clean.startsWith("open youtube and play") ||
                clean.startsWith("go to youtube and play") ||
                clean.startsWith("open youtube play") ||
                clean.startsWith("go to youtube play") ||
                clean.startsWith("go to search bar")
            if (!isExplicitCompoundSearch) {
                return null
            }
        }

        val isPlay = clean.startsWith("play ") || clean.contains(" and play ") || clean.contains(" play ")

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
            "open youtube search",
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
            "search on youtube for",
            "search on youtube",
            "search in youtube for",
            "search in youtube",
            "search youtube for",
            "search youtube",
            "play on youtube",
            "play in youtube",
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
                val sanitized = sanitizeSearchQuery(query)
                if (sanitized != null) {
                    val prefixIsPlay = isPlay || prefix.contains("play")
                    return SearchYouTubeAction(query = sanitized, isPlayDirectly = prefixIsPlay)
                }
            }
        }

        // 2. Trailing "on youtube" patterns: e.g. "search despacito on youtube", "play believer on youtube", "play Jailer 2 song on YouTube"
        if (clean.endsWith(" on youtube") || clean.endsWith(" in youtube")) {
            var trimmed = clean.removeSuffix(" on youtube").removeSuffix(" in youtube").trim()
            val leadingTriggers = listOf("search for", "search", "play", "find", "look for", "listen to")
            var matchedTrigger: String? = null
            for (trigger in leadingTriggers) {
                if (trimmed.startsWith("$trigger ")) {
                    matchedTrigger = trigger
                    trimmed = trimmed.removePrefix("$trigger ").trim()
                    break
                }
            }
            val sanitized = sanitizeSearchQuery(trimmed)
            if (sanitized != null) {
                val triggerIsPlay = matchedTrigger == "play" || isPlay
                return SearchYouTubeAction(query = sanitized, isPlayDirectly = triggerIsPlay)
            }
        }

        // 3. Media-specific keywords with search/find verbs (e.g. "search for infinity war trailer", "search for song ...", "search Jailer 2 song")
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
                    val sanitized = sanitizeSearchQuery(candidate)
                    if (sanitized != null) {
                        return SearchYouTubeAction(query = sanitized, isPlayDirectly = (trigger == "play" || isPlay))
                    }
                }
            }
        }

        // 4. In YouTube Context: explicit search/find/play query routes to YouTube
        if (isYouTubeContext) {
            val searchTriggers = listOf(
                "go to search bar and search for", "go to search bar and search",
                "search bar and search for", "search bar and search", "search bar search for", "search bar search",
                "search for song", "search for the song", "search for trailer", "search for video",
                "search for", "search", "find", "look for", "play song", "play the song", "play", "listen to"
            )
            for (trigger in searchTriggers) {
                if (clean.startsWith("$trigger ")) {
                    val candidate = clean.removePrefix("$trigger ").trim()
                    val sanitized = sanitizeSearchQuery(candidate)
                    if (sanitized != null) {
                        return SearchYouTubeAction(query = sanitized, isPlayDirectly = (trigger.startsWith("play") || isPlay))
                    }
                }
            }
            // Media queries with specific keywords (trailer, song, video, etc.) in YouTube context
            val queryWords = clean.split("\\s+".toRegex()).filter { it.isNotBlank() }
            if (queryWords.any { it in mediaKeywords }) {
                val sanitized = sanitizeSearchQuery(clean)
                if (sanitized != null) {
                    return SearchYouTubeAction(query = sanitized, isPlayDirectly = isPlay)
                }
            }
        }

        // 5. "play [song/artist]" intent (e.g. "play some song", "play believer")
        if (clean.startsWith("play ") && clean != "play youtube") {
            val query = clean.removePrefix("play ").trim()
            val sanitized = sanitizeSearchQuery(query)
            if (sanitized != null) {
                return SearchYouTubeAction(query = sanitized, isPlayDirectly = true)
            }
        }

        return null
    }

    /**
     * Sanitizes search queries and enforces strict routing rules:
     * - A search query must NEVER begin with open/launch verbs ("open", "go to", "launch", "switch to", "start").
     * - Returns null if the query is blank or invalid.
     */
    private fun sanitizeSearchQuery(query: String): String? {
        val clean = query.trim()
        if (clean.isBlank()) return null

        val forbiddenPrefixes = listOf("open ", "go to ", "launch ", "switch to ", "start ")
        if (forbiddenPrefixes.any { clean.startsWith(it) }) {
            return null
        }

        return clean
    }

    private fun matchOpenApp(text: String): AssistantAction? {
        val clean = text.replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
        val words = clean.split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (words.isEmpty()) return null

        if (clean == "play youtube") {
            return OpenAppAction(AllowedApp.YOUTUBE)
        }

        val appTriggers = listOf(
            "click open with", "click open", "open with", "open up", "open", "launch",
            "start", "go to", "show", "run", "click on", "switch to"
        )

        var candidateAppName: String? = null

        val nonAppPrefixes = listOf(
            "search bar", "search", "play", "find", "look for", "listen to", "call"
        )

        // 1. Starts with an explicit open trigger
        for (trigger in appTriggers) {
            if (clean.startsWith("$trigger ")) {
                val candidate = clean.removePrefix("$trigger ").trim()
                if (candidate.isNotBlank() && nonAppPrefixes.none { candidate.startsWith(it) }) {
                    candidateAppName = candidate
                    break
                }
            }
        }

        // 2. Mentions a known app name with an open verb anywhere in the sentence
        if (candidateAppName == null) {
            val openVerbs = setOf("open", "launch", "show", "start", "view", "go", "switch")
            if (words.any { it in openVerbs }) {
                for (allowed in AllowedApp.entries) {
                    val names = listOf(allowed.displayName.lowercase()) + allowed.aliases
                    if (names.any { name -> words.contains(name) }) {
                        candidateAppName = allowed.displayName
                        break
                    }
                }
            }
        }

        // 3. Standalone app name (1 to 2 words, e.g. "whatsapp", "youtube", "camera", "spotify", "chrome")
        if (candidateAppName == null && words.size in 1..2) {
            val known = AllowedApp.entries.firstOrNull {
                it.displayName.equals(clean, ignoreCase = true) ||
                it.aliases.any { alias -> alias.equals(clean, ignoreCase = true) }
            }
            if (known != null) {
                candidateAppName = known.displayName
            }
        }

        if (candidateAppName != null) {
            candidateAppName = candidateAppName.removePrefix("with ").trim()
            candidateAppName = candidateAppName.removeSuffix(" app").removeSuffix(" application").trim()

            // Check if matches AllowedApp enum
            val matchedAllowed = AllowedApp.entries.firstOrNull {
                it.displayName.equals(candidateAppName, ignoreCase = true) ||
                it.aliases.any { alias -> alias.equals(candidateAppName, ignoreCase = true) }
            }

            if (matchedAllowed != null) {
                return OpenAppAction(appName = matchedAllowed.displayName, packageName = matchedAllowed.packageName)
            }

            // Dynamic open for any installed app (Task 3: open any installed app by spoken name)
            val isExplicitOpenCommand = appTriggers.any { clean.startsWith("$it ") }
            if (isExplicitOpenCommand) {
                val appWords = candidateAppName.split("\\s+".toRegex()).filter { it.isNotBlank() }
                if (appWords.size <= 4 && !candidateAppName.contains(" and ") && !candidateAppName.contains(" on ") && !candidateAppName.contains(" in ")) {
                    return OpenAppAction(appName = candidateAppName)
                }
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
        val simPattern = Regex("^(.*?)\\s+(?:on|using|via|through)\\s+(sim\\s*\\d+|sim\\s*one|sim\\s*two|first\\s*sim|second\\s*sim|airtel.*|jio.*|vi.*|bsnl.*)$", RegexOption.IGNORE_CASE)
        val match = simPattern.find(target)

        return if (match != null) {
            val contact = match.groupValues[1].trim()
            val sim = match.groupValues[2].trim()
            CallContactAction(contactQuery = contact, selectedSim = sim, isConfirmed = true)
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
            "find contact",
            "lookup contact",
            "look up contact",
            "lookup",
            "look up",
            "who is",
            "get number for",
            "number for",
            "contact details for",
            "contact for",
            "contact",
            "search for",
            "search",
            "find"
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
        val clean = reply.replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

        // 1. First / SIM 1 indicators
        val firstPatterns = listOf(
            "first", "the first", "first one", "the first one", "1", "one", "number one",
            "sim 1", "sim1", "sim one", "first sim", "the first sim", "use sim 1", "use sim one"
        )
        for (pattern in firstPatterns) {
            if (clean == pattern || clean.startsWith("$pattern ") || clean.endsWith(" $pattern") || clean.contains(" $pattern ")) {
                return candidates.getOrNull(0)
            }
        }

        // 2. Second / SIM 2 indicators
        val secondPatterns = listOf(
            "second", "the second", "second one", "the second one", "2", "two", "number two",
            "sim 2", "sim2", "sim two", "second sim", "the second sim", "use sim 2", "use sim two"
        )
        for (pattern in secondPatterns) {
            if (clean == pattern || clean.startsWith("$pattern ") || clean.endsWith(" $pattern") || clean.contains(" $pattern ")) {
                return candidates.getOrNull(1)
            }
        }

        // 3. Third indicators
        val thirdPatterns = listOf(
            "third", "the third", "third one", "the third one", "3", "three", "number three",
            "sim 3", "sim3", "sim three", "third sim"
        )
        for (pattern in thirdPatterns) {
            if (clean == pattern || clean.startsWith("$pattern ") || clean.endsWith(" $pattern") || clean.contains(" $pattern ")) {
                return candidates.getOrNull(2)
            }
        }

        // 4. Fourth indicators
        val fourthPatterns = listOf(
            "fourth", "the fourth", "fourth one", "the fourth one", "4", "four", "number four"
        )
        for (pattern in fourthPatterns) {
            if (clean == pattern || clean.startsWith("$pattern ") || clean.endsWith(" $pattern") || clean.contains(" $pattern ")) {
                return candidates.getOrNull(3)
            }
        }

        // 5. Substring / token matching against candidate names
        for (candidate in candidates) {
            val normCandidate = candidate.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
            if (normCandidate.isNotBlank() && (normCandidate.contains(clean) || clean.contains(normCandidate))) {
                return candidate
            }
        }

        return null
    }
}
