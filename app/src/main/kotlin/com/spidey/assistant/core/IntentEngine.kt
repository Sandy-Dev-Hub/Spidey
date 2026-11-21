package com.spidey.assistant.core

import com.spidey.assistant.actions.AllowedApp
import com.spidey.assistant.actions.AssistantAction
import com.spidey.assistant.actions.CallContactAction
import com.spidey.assistant.actions.FindContactAction
import com.spidey.assistant.actions.GreetingAction
import com.spidey.assistant.actions.InAppUnsupportedAction
import com.spidey.assistant.actions.OpenAppAction
import com.spidey.assistant.actions.PlayFirstResultAction
import com.spidey.assistant.actions.PlaySpotifyAction
import com.spidey.assistant.actions.SearchYouTubeAction
import com.spidey.assistant.actions.StopAction
import com.spidey.assistant.actions.TimeAction
import com.spidey.assistant.actions.UnlockAction
import com.spidey.assistant.actions.WhatsAppCallAction
import com.spidey.assistant.calling.CallSessionManager
import com.spidey.assistant.safety.ConfirmationPolicy

/**
 * Local deterministic rule-based intent engine.
 * Converts natural speech text into a typed [AssistantAction] without external cloud or LLM dependencies.
 *
 * Deterministic Priority Order:
 * 1. System Commands (clarifications/confirmations, stop, cancel, unlock, time/date, feedback, greetings)
 * 2. Explicit app in phrase (e.g. "play believer on spotify", "call mom on whatsapp", "search trailer on youtube")
 * 3. Open App Commands ("open whatsapp", "play spotify", "launch chrome", "switch to contacts")
 * 4. In-App Commands (routes ONLY to foreground context app skill: YouTube, Spotify, WhatsApp, etc. No cross-app fallbacks!)
 * 5. Default per intent (unqualified play/search -> YouTube, unqualified call -> Phone cellular call)
 * 6. Conversational Chat (routed to Groq via ChatAction)
 * 7. Clarify / null (never fall back to typing raw transcripts into search bars)
 */
class IntentEngine {

    fun parse(text: String, candidatesContext: List<String> = emptyList()): AssistantAction? {
        val normalized = text.trim().lowercase()
        if (normalized.isBlank()) return null

        // 1. Clarifications / Confirmations (active user reply to Spidey prompts)
        if (candidatesContext.isNotEmpty()) {
            val clarifiedAction = handleClarification(normalized, candidatesContext)
            if (clarifiedAction != null) {
                return clarifiedAction
            }
        }

        val previousSearchQuery = candidatesContext.firstOrNull { it.startsWith("query:") }?.removePrefix("query:")
        val currentIndex = candidatesContext.firstOrNull { it.startsWith("index:") }?.removePrefix("index:")?.toIntOrNull() ?: 1

        // 2. System commands
        // 2a. Stop / Cancel / Sleep commands
        if (isStopCommand(normalized)) {
            return StopAction()
        }

        // 2b. Unlock command ("unlock my phone", "unlock the phone", "unlock phone", "unlock")
        if (isUnlockCommand(normalized)) {
            return UnlockAction()
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

        // 2f. Messaging intent (WhatsApp, Email, SMS with dictation)
        val messageAction = matchMessage(normalized, text)
        if (messageAction != null) {
            return messageAction
        }

        // 3. Explicit app in phrase (Tier 2: "play believer on spotify", "call mom on whatsapp", etc.)
        val explicitAction = matchExplicitAppInPhrase(normalized, text)
        if (explicitAction != null) {
            return explicitAction
        }

        // 4. Open App intent (Tier 3: ALWAYS WINS OVER GENERIC PLAY OR SEARCH)
        // E.g. "open whatsapp", "play spotify", "launch chrome", "switch to contacts"
        val openAppAction = matchOpenApp(normalized)
        if (openAppAction != null) {
            return openAppAction
        }

        // 5. In-App commands (Tier 4: routes ONLY to foreground context app skill)
        val inAppAction = matchInAppContext(normalized, candidatesContext, previousSearchQuery, currentIndex)
        if (inAppAction != null) {
            return inAppAction
        }

        // 6. Default per intent (Tier 5: when no foreground app context is active)
        // 6a. Play ordinal result ("play the first one", "play the third video on the search", "play 2nd video")
        val playOrdinalAction = matchPlayOrdinal(normalized, previousSearchQuery, false, currentIndex)
        if (playOrdinalAction != null) {
            return playOrdinalAction
        }

        // 6b. YouTube Search / Play intent
        val ytSearchAction = matchYouTubeSearch(normalized, false)
        if (ytSearchAction != null) {
            return ytSearchAction
        }

        // 6c. Calling & Contact intents
        val callAction = matchCall(normalized)
        if (callAction != null) {
            return callAction
        }

        val contactAction = matchContactSearch(normalized)
        if (contactAction != null) {
            return contactAction
        }

        // 7. Conversational / Chat:
        // When user asks conversational questions, advice, vents, or chats
        val chatAction = matchChat(normalized, text)
        if (chatAction != null) {
            return chatAction
        }

        // 8. Fallback: Return null for pure gibberish / unrecognized noise
        return null
    }

    private fun matchExplicitAppInPhrase(normalized: String, originalText: String): AssistantAction? {
        val clean = normalized.replace(Regex("[^a-z0-9 +]"), " ").replace(Regex("\\s+"), " ").trim()

        if (clean == "play spotify" || clean == "play on spotify") {
            return OpenAppAction(AllowedApp.SPOTIFY)
        }
        if (clean == "play youtube" || clean == "play on youtube") {
            return OpenAppAction(AllowedApp.YOUTUBE)
        }

        // 1. Spotify in phrase
        if (clean.endsWith(" on spotify") || clean.endsWith(" in spotify") || clean.startsWith("play on spotify ") || clean.startsWith("spotify play ")) {
            var query = when {
                clean.endsWith(" on spotify") -> clean.removeSuffix(" on spotify").trim()
                clean.endsWith(" in spotify") -> clean.removeSuffix(" in spotify").trim()
                clean.startsWith("play on spotify ") -> clean.removePrefix("play on spotify ").trim()
                clean.startsWith("spotify play ") -> clean.removePrefix("spotify play ").trim()
                else -> clean
            }
            val stripVerbs = listOf(
                "play the song ", "play song ", "play a song ", "play ",
                "search for the song ", "search for song ", "search for ", "search ", "listen to "
            )
            for (verb in stripVerbs) {
                if (query.startsWith(verb)) {
                    query = query.removePrefix(verb).trim()
                    break
                }
            }
            val stripLeading = listOf("the song ", "song ", "the track ", "track ", "the music ", "music ")
            for (lead in stripLeading) {
                if (query.startsWith(lead)) {
                    query = query.removePrefix(lead).trim()
                    break
                }
            }
            if (query.isNotBlank() && query != "spotify" && query != "play") {
                return PlaySpotifyAction(query = query, isPlayDirectly = true)
            }
        }

        // 2. YouTube in phrase
        if (clean.endsWith(" on youtube") || clean.endsWith(" in youtube") || clean.startsWith("open youtube and ") || clean.startsWith("go to youtube and ")) {
            val isPlay = clean.contains("play")
            var query = clean.removeSuffix(" on youtube").removeSuffix(" in youtube").trim()
            val stripVerbs = listOf(
                "open youtube and search for the song ", "open youtube and search for song ",
                "open youtube and search for ", "open youtube and search ", "open youtube and play song ", "open youtube and play ",
                "go to youtube and search for the song ", "go to youtube and search for song ",
                "go to youtube and search for ", "go to youtube and search ", "go to youtube and play song ", "go to youtube and play ",
                "search for the song ", "search for song ", "search song ", "search for ", "search ",
                "play the song ", "play song ", "play a song ", "play "
            )
            for (verb in stripVerbs) {
                if (query.startsWith(verb)) {
                    query = query.removePrefix(verb).trim()
                    break
                }
            }
            val stripLeading = listOf("the song ", "song ", "the movie ", "movie ", "the video ", "video ", "the trailer ", "trailer ")
            for (lead in stripLeading) {
                if (query.startsWith(lead)) {
                    query = query.removePrefix(lead).trim()
                    break
                }
            }
            if (query.isNotBlank() && query != "youtube" && query != "play") {
                return SearchYouTubeAction(query = query, isPlayDirectly = isPlay)
            }
        }

        // 3. WhatsApp in phrase
        if (clean.endsWith(" on whatsapp") || clean.endsWith(" via whatsapp") || clean.endsWith(" in whatsapp") ||
            clean.startsWith("whatsapp call ") || clean.startsWith("call on whatsapp ") || clean.startsWith("call via whatsapp ")
        ) {
            var target = when {
                clean.endsWith(" on whatsapp") -> clean.removeSuffix(" on whatsapp").trim()
                clean.endsWith(" via whatsapp") -> clean.removeSuffix(" via whatsapp").trim()
                clean.endsWith(" in whatsapp") -> clean.removeSuffix(" in whatsapp").trim()
                clean.startsWith("whatsapp call ") -> clean.removePrefix("whatsapp call ").trim()
                clean.startsWith("call on whatsapp ") -> clean.removePrefix("call on whatsapp ").trim()
                clean.startsWith("call via whatsapp ") -> clean.removePrefix("call via whatsapp ").trim()
                else -> clean
            }
            val callVerbs = listOf("call ", "voice call ", "video call ", "dial ", "phone ")
            for (verb in callVerbs) {
                if (target.startsWith(verb)) {
                    target = target.removePrefix(verb).trim()
                    break
                }
            }
            if (target.isNotBlank() && target != "whatsapp") {
                return WhatsAppCallAction(contactQuery = target, isVideo = clean.contains("video"))
            }
        }

        // 4. Phone with explicit SIM in phrase
        val simPattern = Regex("^(.*?)\\s+(?:on|using|via|through)\\s+(sim\\s*\\d+|sim\\s*one|sim\\s*two|first\\s*sim|second\\s*sim|airtel.*|jio.*|vi.*|bsnl.*)$", RegexOption.IGNORE_CASE)
        val simMatch = simPattern.find(clean)
        if (simMatch != null) {
            var callPrefix = simMatch.groupValues[1].trim()
            val sim = simMatch.groupValues[2].trim()
            val callVerbs = listOf("make a call to ", "place a call to ", "give a call to ", "call ", "dial ", "phone ")
            for (verb in callVerbs) {
                if (callPrefix.startsWith(verb)) {
                    callPrefix = callPrefix.removePrefix(verb).trim()
                    break
                }
            }
            if (callPrefix.isNotBlank()) {
                return CallContactAction(contactQuery = callPrefix, selectedSim = sim, isConfirmed = true)
            }
        }

        return null
    }

    private fun matchInAppContext(
        normalized: String,
        candidatesContext: List<String>,
        previousSearchQuery: String?,
        currentIndex: Int
    ): AssistantAction? {
        val clean = normalized.replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
        val isSpotifyContext = candidatesContext.contains("context:spotify") || candidatesContext.contains("context:com.spotify.music")
        val isYouTubeContext = candidatesContext.contains("context:youtube") || candidatesContext.contains("context:com.google.android.youtube")
        val isWhatsAppContext = candidatesContext.contains("context:whatsapp") || candidatesContext.contains("context:com.whatsapp")
        val contextApp = candidatesContext.firstOrNull { it.startsWith("context:") }?.removePrefix("context:")

        val changePhrases = listOf(
            "change song", "change the song", "change this song", "next song", "play next song", "next video",
            "play another song", "different song", "play a different song", "another song", "skip song", "skip", "next"
        )
        val expressesChange = changePhrases.any { clean == it || clean.contains(it) } ||
                ((clean.contains("dont like") || clean.contains("don t like") || clean.contains("hate")) &&
                        (clean.contains("change") || clean.contains("next") || clean.contains("another") || clean.contains("different") || clean.contains("song")))

        // 1. Spotify context
        if (isSpotifyContext) {
            if (expressesChange) {
                return PlaySpotifyAction(query = "next")
            }
            if (clean == "play the first one" || clean == "play first" || clean == "play 1st") {
                return PlaySpotifyAction(query = "first")
            }
            if (clean.startsWith("play ") || clean.startsWith("search ") || clean.startsWith("listen to ")) {
                var q = clean.removePrefix("play ").removePrefix("search ").removePrefix("listen to ").trim()
                if (q.startsWith("the song ") || q.startsWith("song ")) {
                    q = q.removePrefix("the song ").removePrefix("song ").trim()
                }
                if (q.isNotBlank()) {
                    return PlaySpotifyAction(query = q)
                }
            }
            if (clean == "resume" || clean == "play") {
                return PlaySpotifyAction(query = "resume")
            }
        }

        // 2. YouTube context
        if (isYouTubeContext) {
            val playOrdinalAction = matchPlayOrdinal(clean, previousSearchQuery, true, currentIndex)
            if (playOrdinalAction != null) {
                return playOrdinalAction
            }
            val ytSearchAction = matchYouTubeSearch(clean, true)
            if (ytSearchAction != null) {
                return ytSearchAction
            }
        }

        // 3. WhatsApp context
        if (isWhatsAppContext) {
            val callVerbs = listOf("call ", "voice call ", "video call ", "dial ", "phone ")
            for (verb in callVerbs) {
                if (clean.startsWith(verb)) {
                    val target = clean.removePrefix(verb).trim()
                    if (target.isNotBlank()) {
                        return WhatsAppCallAction(contactQuery = target, isVideo = clean.contains("video"))
                    }
                }
            }
            if (expressesChange || clean.startsWith("play ") || clean.startsWith("search ")) {
                // Strict rule: No cross-app fallback! Report honestly
                return InAppUnsupportedAction(appName = "WhatsApp", command = "media playback")
            }
        }

        // 4. Other foreground app context
        if (contextApp != null && contextApp != "youtube" && contextApp != "spotify" && contextApp != "phone") {
            if (expressesChange || clean.startsWith("play ") || clean.startsWith("play song")) {
                val appName = contextApp.replaceFirstChar { it.uppercase() }
                return InAppUnsupportedAction(appName = appName, command = "media playback")
            }
        }

        return null
    }

    private fun matchChat(normalized: String, originalText: String): AssistantAction? {
        val gibberishWords = setOf(
            "blah", "asdfghjkl", "xyz", "random noise", "gibberish"
        )
        if (gibberishWords.any { normalized.contains(it) }) {
            return null
        }

        // If it contains at least two conversational words or an emotion/question/statement, route to Groq
        val words = normalized.split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (words.size >= 2) {
            return com.spidey.assistant.actions.ChatAction(originalText.trim())
        }

        val singleChatTriggers = setOf(
            "hi", "hello", "hey", "sup", "bored", "sad", "happy", "tired", "joke", "help", "bro", "why", "who", "what", "how"
        )
        if (words.size == 1 && words[0] in singleChatTriggers) {
            return com.spidey.assistant.actions.ChatAction(originalText.trim())
        }

        return null
    }

    private fun handleClarification(reply: String, candidatesContext: List<String>): AssistantAction? {
        // 0. Email recipient dictation
        if (candidatesContext.contains("dictate_email_recipient") ||
            (candidatesContext.contains("dictate_recipient") && candidatesContext.contains("platform:EMAIL"))
        ) {
            if (isStopCommand(reply)) return StopAction()
            val cleanEmail = normalizeEmail(reply)
            return com.spidey.assistant.actions.SendMessageAction(
                recipient = cleanEmail,
                body = "",
                platform = com.spidey.assistant.actions.MessagePlatform.EMAIL,
                isConfirmed = false
            )
        }

        // A. Confirmation or Cancellation of a pending message
        if (candidatesContext.contains("confirm_message")) {
            val recipient = candidatesContext.firstOrNull { it.startsWith("recipient:") }?.removePrefix("recipient:") ?: ""
            val body = candidatesContext.firstOrNull { it.startsWith("body:") }?.removePrefix("body:") ?: ""
            val platformStr = candidatesContext.firstOrNull { it.startsWith("platform:") }?.removePrefix("platform:") ?: "WHATSAPP"
            val subject = candidatesContext.firstOrNull { it.startsWith("subject:") }?.removePrefix("subject:") ?: ""
            val platform = try { com.spidey.assistant.actions.MessagePlatform.valueOf(platformStr) } catch (_: Exception) { com.spidey.assistant.actions.MessagePlatform.WHATSAPP }

            val cleanReply = reply.trim().lowercase()
            var normalizedReply = cleanReply
            normalizedReply = normalizedReply
                .replace(Regex("(?<=\\d)2(?=\\d)"), " to ")
                .replace(Regex("(?<=\\d)\\s*2\\s*(?=\\d)"), " to ")
                .replace(Regex("(?<=\\w)\\s+2\\s+(?=\\w)"), " to ")

            if (normalizedReply.startsWith("no ") || normalizedReply.startsWith("no, ")) {
                val withoutNo = normalizedReply.replace(Regex("^no[,\\s]+"), "").trim()
                if (withoutNo.startsWith("change ") || withoutNo.startsWith("replace ") || withoutNo.startsWith("make it ") || withoutNo.startsWith("set it to ")) {
                    normalizedReply = withoutNo
                }
            }

            // 1. Direct message rewrite: "change message to <body>" or "change to <body>"
            if (normalizedReply.startsWith("change message to ") || normalizedReply.startsWith("change to ") || normalizedReply.startsWith("change message ") || normalizedReply.startsWith("change body to ")) {
                val newBody = normalizedReply
                    .removePrefix("change message to ")
                    .removePrefix("change to ")
                    .removePrefix("change message ")
                    .removePrefix("change body to ")
                    .trim()
                if (newBody.isNotBlank()) {
                    return com.spidey.assistant.actions.SendMessageAction(
                        recipient = recipient,
                        body = newBody,
                        platform = platform,
                        subject = subject,
                        isConfirmed = false
                    )
                }
            }

            // 2. Quick edit: "change <target> to <replacement>" or "replace <target> with <replacement>"
            val changeRegex = Regex("^(?:change|replace)\\s+(?:the\\s+)?(.*?)\\s+(?:to|with)\\s+(.*)$", RegexOption.IGNORE_CASE)
            val match = changeRegex.find(normalizedReply)
            if (match != null) {
                val target = match.groupValues[1].trim()
                val replacement = match.groupValues[2].trim()
                val updatedBody = applyBodyEdit(body, target, replacement)
                return com.spidey.assistant.actions.SendMessageAction(
                    recipient = recipient,
                    body = updatedBody,
                    platform = platform,
                    subject = subject,
                    isConfirmed = false
                )
            }

            // 3. Direct "<x> to <y>" without "change" verb: e.g. "5 to 6", "526", "5 2 6"
            val directXtoYRegex = Regex("^(\\d+|zero|one|two|three|four|five|six|seven|eight|nine|ten)\\s+(?:to|with)\\s+(\\d+|zero|one|two|three|four|five|six|seven|eight|nine|ten)$", RegexOption.IGNORE_CASE)
            val directMatch = directXtoYRegex.find(normalizedReply)
            if (directMatch != null) {
                val target = directMatch.groupValues[1].trim()
                val replacement = directMatch.groupValues[2].trim()
                val updatedBody = applyBodyEdit(body, target, replacement)
                return com.spidey.assistant.actions.SendMessageAction(
                    recipient = recipient,
                    body = updatedBody,
                    platform = platform,
                    subject = subject,
                    isConfirmed = false
                )
            }

            // 4. "make it <replacement>" or "set it to <replacement>"
            val makeItRegex = Regex("^(?:make\\s+it|set\\s+it\\s+to)\\s+(.*)$", RegexOption.IGNORE_CASE)
            val makeItMatch = makeItRegex.find(normalizedReply)
            if (makeItMatch != null) {
                val replacement = makeItMatch.groupValues[1].trim()
                val updatedBody = applyBodyEdit(body, "it", replacement)
                return com.spidey.assistant.actions.SendMessageAction(
                    recipient = recipient,
                    body = updatedBody,
                    platform = platform,
                    subject = subject,
                    isConfirmed = false
                )
            }

            // 5. Confirm / Cancel words
            val confirmWords = setOf(
                "yes", "yeah", "yep", "send", "send it", "send the mail", "send mail", "send email",
                "send this mail", "confirm", "sure", "ok", "okay", "do it", "please send", "go ahead",
                "deliver", "deliver it", "that's all", "thats all", "that is all", "done"
            )
            val cancelWords = setOf("no", "nope", "cancel", "stop", "don't send", "dont send", "cancel it", "discard", "abort")

            if (confirmWords.any { cleanReply == it || cleanReply.startsWith("$it ") }) {
                return com.spidey.assistant.actions.SendMessageAction(
                    recipient = recipient,
                    body = body,
                    platform = platform,
                    subject = subject,
                    isConfirmed = true
                )
            }
            if (cancelWords.any { cleanReply == it || cleanReply.startsWith("$it ") }) {
                return com.spidey.assistant.actions.SendMessageAction(
                    recipient = recipient,
                    body = body,
                    platform = platform,
                    subject = subject,
                    isCancelled = true
                )
            }

            // 6. Direct dictated revision without explicit "change" verb
            if (cleanReply.isNotBlank() && !isStopCommand(reply)) {
                val finalBody = if (normalizedReply.startsWith("change ")) {
                    normalizedReply.removePrefix("change ").trim()
                } else {
                    reply.trim()
                }
                return com.spidey.assistant.actions.SendMessageAction(
                    recipient = recipient,
                    body = finalBody,
                    platform = platform,
                    subject = subject,
                    isConfirmed = false
                )
            }
        }

        // B. Dictation of message body
        if (candidatesContext.contains("dictate_message")) {
            val recipient = candidatesContext.firstOrNull { it.startsWith("recipient:") }?.removePrefix("recipient:") ?: ""
            val platformStr = candidatesContext.firstOrNull { it.startsWith("platform:") }?.removePrefix("platform:") ?: "WHATSAPP"
            val platform = try { com.spidey.assistant.actions.MessagePlatform.valueOf(platformStr) } catch (_: Exception) { com.spidey.assistant.actions.MessagePlatform.WHATSAPP }
            if (isStopCommand(reply)) {
                return StopAction()
            }

            var dictatedBody = reply.trim()
            val stopPhrases = listOf(
                "that's all", "thats all", "that is all",
                "that's it", "thats it", "that is it",
                "all done", "done", "finished"
            )
            for (phrase in stopPhrases) {
                val phraseRegex = Regex("(?i)[,\\s]*\\b${Regex.escape(phrase)}\\b[.\\s]*$")
                if (phraseRegex.containsMatchIn(dictatedBody)) {
                    dictatedBody = dictatedBody.replace(phraseRegex, "").trim()
                    break
                }
            }

            return com.spidey.assistant.actions.SendMessageAction(
                recipient = recipient,
                body = dictatedBody,
                platform = platform,
                isConfirmed = false
            )
        }

        // C. Confirmation or Cancellation of a pending call
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
                it != "number_choice" && !it.startsWith("context:") && !it.startsWith("query:") && !it.startsWith("index:")
            }
            val chosen = resolveClarification(reply, choices) ?: reply
            return CallContactAction(
                contactQuery = session?.contactQuery ?: "",
                selectedNumber = chosen,
                selectedSim = session?.selectedSim
            )
        }

        // E. Music query clarification ("What song would you like me to play, bro?")
        if (candidatesContext.contains("music_query_choice")) {
            if (isStopCommand(reply)) {
                return StopAction()
            }
            var songQuery = reply
            val playPrefixes = listOf(
                "play song ", "play the song ", "play a song ",
                "play ", "search for song ", "search for ", "search ",
                "listen to ", "open "
            )
            for (p in playPrefixes) {
                if (songQuery.startsWith(p)) {
                    songQuery = songQuery.removePrefix(p).trim()
                    break
                }
            }
            if (songQuery.isBlank()) songQuery = reply
            return SearchYouTubeAction(query = songQuery, isPlayDirectly = true)
        }

        // F. Ambiguous contact resolution (ONLY when actual candidate contact strings are present)
        val contactCandidates = candidatesContext.filter {
            !it.startsWith("context:") && !it.startsWith("query:") && !it.startsWith("index:") &&
            !it.startsWith("recipient:") && !it.startsWith("body:") && !it.startsWith("platform:") && !it.startsWith("subject:") &&
            it != "confirm_call" && it != "sim_choice" && it != "open_app_choice" && it != "number_choice" &&
            it != "music_query_choice" && it != "confirm_message" && it != "dictate_message" &&
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
        val clean = text.replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
        val stopPhrases = setOf(
            "stop", "cancel", "bye", "goodbye", "sleep", "shut down", "go to sleep",
            "exit", "close", "turn off", "quit", "stop listening", "stop assistant",
            "that s all", "thats all", "that is all", "thanks", "thank you", "i m done", "im done"
        )
        return clean in stopPhrases
    }

    private fun isUnlockCommand(text: String): Boolean {
        val clean = text.replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
        if (clean == "unlock" || clean.startsWith("unlock ") || clean.endsWith(" unlock") || clean.contains(" unlock ")) {
            if (clean.contains("phone") || clean.contains("mobile") || clean.contains("device") || clean.contains("screen") || clean == "unlock") {
                return true
            }
        }
        val unlockPhrases = setOf(
            "unlock my phone", "unlock the phone", "unlock phone", "unlock",
            "please unlock my phone", "please unlock the phone", "unlock device",
            "unlock my mobile", "unlock the mobile", "unlock mobile",
            "unlock the screen", "unlock screen", "unlock it", "open my phone", "open phone"
        )
        return clean in unlockPhrases
    }

    private fun matchPlayOrdinal(
        text: String,
        previousSearchQuery: String?,
        isYouTubeContext: Boolean,
        currentIndex: Int = 1
    ): PlayFirstResultAction? {
        var clean = text.replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

        // Handle "change song", "different song", "next song", "play another song", "i don't like this song change it"
        val changePhrases = listOf(
            "change song", "change the song", "change this song", "next song", "play next song", "next video",
            "play another song", "different song", "play a different song", "another song", "skip song"
        )
        val expressesDislikeOrChange = changePhrases.any { clean.contains(it) } ||
                ((clean.contains("dont like") || clean.contains("don t like") || clean.contains("hate")) &&
                        (clean.contains("change") || clean.contains("next") || clean.contains("another") || clean.contains("different") || clean.contains("song")))

        if (expressesDislikeOrChange) {
            val nextIndex = currentIndex + 1
            return PlayFirstResultAction(
                query = previousSearchQuery ?: "trending songs",
                targetApp = "youtube",
                itemIndex = nextIndex
            )
        }

        val searchSuffixes = listOf(
            "on the search results", "in the search results", "from the search results",
            "on the search", "in the search", "from the search",
            "on search", "in search", "from search",
            "on the list", "in the list", "from the list",
            "on list", "in list"
        )
        for (suffix in searchSuffixes) {
            if (clean.endsWith(" $suffix")) {
                clean = clean.removeSuffix(" $suffix").trim()
                break
            }
        }

        val ordinalMap = mapOf(
            "first" to 1, "1st" to 1, "one" to 1,
            "second" to 2, "2nd" to 2, "two" to 2,
            "third" to 3, "3rd" to 3, "three" to 3,
            "fourth" to 4, "4th" to 4, "four" to 4,
            "fifth" to 5, "5th" to 5, "five" to 5,
            "sixth" to 6, "6th" to 6, "six" to 6,
            "seventh" to 7, "7th" to 7, "seven" to 7,
            "eighth" to 8, "8th" to 8, "eight" to 8,
            "ninth" to 9, "9th" to 9, "nine" to 9,
            "tenth" to 10, "10th" to 10, "ten" to 10
        )

        val digitMap = (1..10).associate { it.toString() to it }

        val words = clean.split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (!words.contains("play") && !clean.startsWith("open ") && !clean.startsWith("select ") && !clean.startsWith("choose ")) {
            return null
        }

        if (!isYouTubeContext && previousSearchQuery.isNullOrBlank()) {
            return null
        }

        val allowedStructuralWords = setOf(
            "play", "open", "select", "choose", "the", "video", "song", "result", "track", "one", "movie",
            "trailer", "item", "number", "no"
        )

        var matchedIndex: Int? = null

        // 1. Check for "number <digit>"
        for ((digitStr, index) in digitMap) {
            if (clean.contains("number $digitStr") || clean.contains("no $digitStr") || clean.contains("item $digitStr")) {
                matchedIndex = index
                break
            }
        }

        // 2. Check for ordinal words
        if (matchedIndex == null) {
            for ((word, index) in ordinalMap) {
                if (words.contains(word)) {
                    matchedIndex = index
                    break
                }
            }
        }

        if (matchedIndex == null) return null

        // 3. Strict guard: every word in clean must be either an allowed structural word or the matched ordinal/digit
        val validWords = allowedStructuralWords + ordinalMap.keys + digitMap.keys
        val allWordsValid = words.all { it in validWords }
        if (!allWordsValid) {
            return null
        }

        return PlayFirstResultAction(
            query = previousSearchQuery,
            targetApp = "youtube",
            itemIndex = matchedIndex
        )
    }

    private fun isPlayFirstCommand(text: String): Boolean {
        return matchPlayOrdinal(text, null, true) != null
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

        // Fast handling for generic play / search music requests
        // Routes to SearchYouTubeAction("song") which triggers conversational clarification
        val genericPlayPhrases = setOf(
            "play", "play song", "play a song", "play the song", "play some song",
            "play songs", "play some songs", "play music", "play some music",
            "play track", "play a track", "play video", "play a video", "play something",
            "search song", "search a song", "search the song", "search music", "search video"
        )
        if (clean in genericPlayPhrases) {
            return SearchYouTubeAction(query = "song", isPlayDirectly = true)
        }

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

        // Specific play-app requests (e.g. "play spotify", "play on spotify", "play youtube" -> opens the app, never search!)
        if (clean.startsWith("play ") || clean.startsWith("play on ")) {
            val appTarget = clean.removePrefix("play on ").removePrefix("play ").trim()
            val genericWords = setOf("music", "some music", "a music", "song", "a song", "the song", "some song", "songs", "some songs", "video", "a video", "the video", "some video", "videos", "track", "a track", "the track", "tracks", "something", "anything")
            if (appTarget !in genericWords) {
                val matchedAllowed = AllowedApp.entries.firstOrNull {
                    it.displayName.equals(appTarget, ignoreCase = true) ||
                    (appTarget != "music" && it.aliases.any { alias -> alias.equals(appTarget, ignoreCase = true) })
                }
                if (matchedAllowed != null) {
                    return OpenAppAction(appName = matchedAllowed.displayName, packageName = matchedAllowed.packageName)
                }
            }
        }

        val appTriggers = listOf(
            "click open with", "click open", "open with", "open up", "open", "launch",
            "start", "go to", "show", "run", "click on", "switch to"
        )

        var candidateAppName: String? = null

        val nonAppPrefixes = listOf(
            "search bar", "search", "find", "look for", "listen to", "call",
            "mail and write", "mail and send", "email and write", "email and send",
            "gmail and write", "gmail and send", "mail to write", "email to write"
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

    private fun matchMessage(normalized: String, originalText: String): AssistantAction? {
        val clean = normalized.replace(Regex("[^a-z0-9 +@.:_'-]"), " ").replace(Regex("\\s+"), " ").trim()

        // 1. WhatsApp Search in Chat: "search Vaseem on whatsapp", "search Amma in whatsapp"
        val waSearchPattern = Regex("^(?:search|find)\\s+(.+?)\\s+(?:on|in)\\s+whatsapp$", RegexOption.IGNORE_CASE)
        val waSearchMatch = waSearchPattern.find(clean)
        if (waSearchMatch != null) {
            val contact = waSearchMatch.groupValues[1].trim()
            return WhatsAppCallAction(contactQuery = contact, isVideo = false)
        }

        // 2. WhatsApp Messaging
        val waOnPattern = Regex("^(?:send\\s+(?:a\\s+)?message\\s+to|message)\\s+(.+?)\\s+(?:on|via|in)\\s+whatsapp(?:\\s+(?:saying|that|:))?(?:\\s+(.*))?$", RegexOption.IGNORE_CASE)
        val waOnMatch = waOnPattern.find(clean)
        if (waOnMatch != null) {
            val recipient = waOnMatch.groupValues[1].trim()
            val body = waOnMatch.groupValues[2].trim()
            if (recipient.isNotBlank() && recipient != "whatsapp") {
                return com.spidey.assistant.actions.SendMessageAction(
                    recipient = recipient,
                    body = body,
                    platform = com.spidey.assistant.actions.MessagePlatform.WHATSAPP
                )
            }
        }

        // B. "send whatsapp [message] to <recipient> [saying/that/: <body>]"
        if (clean.startsWith("send whatsapp message to ") || clean.startsWith("send whatsapp to ") || clean.startsWith("whatsapp message to ")) {
            val after = when {
                clean.startsWith("send whatsapp message to ") -> clean.removePrefix("send whatsapp message to ")
                clean.startsWith("send whatsapp to ") -> clean.removePrefix("send whatsapp to ")
                else -> clean.removePrefix("whatsapp message to ")
            }.trim()

            val (recipient, body) = splitRecipientAndBody(after)
            if (recipient.isNotBlank()) {
                return com.spidey.assistant.actions.SendMessageAction(
                    recipient = recipient,
                    body = body,
                    platform = com.spidey.assistant.actions.MessagePlatform.WHATSAPP
                )
            }
        }

        // 3. Email Messaging
        val bareEmailTriggers = setOf(
            "send email", "send an email", "send a email",
            "send mail", "send a mail",
            "compose email", "compose an email", "compose a mail",
            "write email", "write an email", "write a mail",
            "open mail and write email", "open mail and write an email", "open mail and write a email",
            "open mail and send email", "open mail and send an email", "open mail and send a email",
            "open email and write email", "open email and write an email", "open email and write a email",
            "open email and send email", "open email and send an email", "open email and send a email",
            "open gmail and write email", "open gmail and write an email", "open gmail and write a email",
            "new email", "create email", "email"
        )
        if (bareEmailTriggers.contains(clean)) {
            return com.spidey.assistant.actions.SendMessageAction(
                recipient = "",
                body = "",
                platform = com.spidey.assistant.actions.MessagePlatform.EMAIL
            )
        }

        val emailPrefixes = listOf(
            "open mail and write an email to ", "open mail and write a email to ", "open mail and write email to ",
            "open mail and send an email to ", "open mail and send a email to ", "open mail and send email to ",
            "open mail and email to ", "open mail to write an email to ", "open mail to write a email to ",
            "open mail to write email to ", "open mail to send an email to ", "open mail to send email to ",
            "open mail to email to ", "open mail write an email to ", "open mail write a email to ", "open mail write email to ",
            "open email and write an email to ", "open email and write a email to ", "open email and write email to ",
            "open email and send an email to ", "open email and send a email to ", "open email and send email to ",
            "open gmail and write an email to ", "open gmail and write a email to ", "open gmail and write email to ",
            "open gmail and send an email to ", "open gmail and send email to ",
            "write an email to ", "write a email to ", "write email to ", "write a mail to ", "write mail to ",
            "compose an email to ", "compose a email to ", "compose email to ", "compose a mail to ", "compose mail to ",
            "send an email to ", "send a email to ", "send email to ", "send a mail to ", "send mail to ",
            "email to ", "email "
        )
        val matchedEmail = emailPrefixes.firstOrNull { clean.startsWith(it) }
        if (matchedEmail != null) {
            var after = clean.removePrefix(matchedEmail).trim()
            if (after.startsWith("to ")) {
                after = after.removePrefix("to ").trim()
            }
            if (after.isBlank()) {
                return com.spidey.assistant.actions.SendMessageAction(
                    recipient = "",
                    body = "",
                    platform = com.spidey.assistant.actions.MessagePlatform.EMAIL
                )
            }

            var subject = ""
            var remaining = after
            if (remaining.contains(" subject ")) {
                val parts = remaining.split(" subject ", limit = 2)
                val rawRecipient = parts[0].trim()
                val rest = parts[1].trim()
                if (rest.contains(" body ")) {
                    val sParts = rest.split(" body ", limit = 2)
                    subject = sParts[0].trim()
                    remaining = sParts[1].trim()
                } else if (rest.contains(" saying ")) {
                    val sParts = rest.split(" saying ", limit = 2)
                    subject = sParts[0].trim()
                    remaining = sParts[1].trim()
                } else {
                    subject = rest
                    remaining = ""
                }
                val recipient = normalizeEmail(rawRecipient)
                return com.spidey.assistant.actions.SendMessageAction(
                    recipient = recipient,
                    body = remaining,
                    platform = com.spidey.assistant.actions.MessagePlatform.EMAIL,
                    subject = subject
                )
            }

            val (rawRecipient, body) = splitRecipientAndBody(remaining, isEmail = true)
            val recipient = normalizeEmail(rawRecipient)
            if (recipient.isNotBlank()) {
                return com.spidey.assistant.actions.SendMessageAction(
                    recipient = recipient,
                    body = body,
                    platform = com.spidey.assistant.actions.MessagePlatform.EMAIL,
                    subject = subject
                )
            }
        }

        // 4. SMS Messaging
        val smsPrefixes = listOf(
            "send an sms to ", "send sms to ", "send a text message to ",
            "send a text to ", "send text to ", "text to ", "text "
        )
        val matchedSms = smsPrefixes.firstOrNull { clean.startsWith(it) }
        if (matchedSms != null) {
            var after = clean.removePrefix(matchedSms).trim()
            if (after.startsWith("to ")) {
                after = after.removePrefix("to ").trim()
            }

            val (recipient, body) = splitRecipientAndBody(after)
            if (recipient.isNotBlank()) {
                return com.spidey.assistant.actions.SendMessageAction(
                    recipient = recipient,
                    body = body,
                    platform = com.spidey.assistant.actions.MessagePlatform.SMS
                )
            }
        }

        // 5. Generic Messaging: "send message to <recipient> [saying <body>]", "send a message to <recipient>"
        val genericMsgPrefixes = listOf(
            "send a message to ", "send message to ", "message to ", "message "
        )
        val matchedGeneric = genericMsgPrefixes.firstOrNull { clean.startsWith(it) }
        if (matchedGeneric != null) {
            val after = clean.removePrefix(matchedGeneric).trim()
            val (recipient, body) = splitRecipientAndBody(after)
            if (recipient.isNotBlank() && recipient != "whatsapp" && recipient != "telegram" && recipient != "sms") {
                val isEmail = recipient.contains("@") || (recipient.contains(" at ") && recipient.contains(" dot "))
                val platform = if (isEmail) {
                    com.spidey.assistant.actions.MessagePlatform.EMAIL
                } else {
                    com.spidey.assistant.actions.MessagePlatform.WHATSAPP
                }
                return com.spidey.assistant.actions.SendMessageAction(
                    recipient = if (isEmail) normalizeEmail(recipient) else recipient,
                    body = body,
                    platform = platform
                )
            }
        }

        return null
    }

    private fun splitRecipientAndBody(text: String, isEmail: Boolean = false): Pair<String, String> {
        val delimiters = listOf(" saying ", " that ", ": ")
        for (delim in delimiters) {
            if (text.contains(delim)) {
                val parts = text.split(delim, limit = 2)
                return Pair(parts[0].trim(), parts[1].trim())
            }
        }

        if (isEmail) {
            val spokenEmailRegex = Regex("^([a-z0-9._%+-]+(?:@|\\s+at\\s+|\\s+at\\s+the\\s+rate\\s+(?:of\\s+)?)[a-z0-9.-]+(?:\\.|\\s+dot\\s+)[a-z]{2,})(.*)$", RegexOption.IGNORE_CASE)
            val match = spokenEmailRegex.find(text.trim())
            if (match != null) {
                val emailPart = match.groupValues[1].trim()
                var rest = match.groupValues[2].trim()
                for (d in listOf("saying ", "that ", ": ")) {
                    if (rest.startsWith(d)) {
                        rest = rest.removePrefix(d).trim()
                        break
                    }
                }
                return Pair(emailPart, rest)
            }
        }

        val words = text.split("\\s+".toRegex())
        return if (words.size > 1) {
            Pair(words[0].trim(), words.drop(1).joinToString(" ").trim())
        } else {
            Pair(text.trim(), "")
        }
    }

    private fun applyBodyEdit(body: String, target: String, replacement: String): String {
        val numberMap = mapOf(
            "zero" to "0", "one" to "1", "two" to "2", "three" to "3", "four" to "4",
            "five" to "5", "six" to "6", "seven" to "7", "eight" to "8", "nine" to "9", "ten" to "10"
        )
        val targetLower = target.lowercase()
        val replacementDigit = numberMap[replacement.lowercase()] ?: replacement

        // 1. Time format support: target is "5", body has "5:00" or "5:30"
        if (target.all { it.isDigit() }) {
            val timeRegex = Regex("\\b${Regex.escape(target)}:(\\d{2})\\b")
            val match = timeRegex.find(body)
            if (match != null) {
                val mins = match.groupValues[1]
                val repTime = if (replacement.contains(":")) {
                    replacement
                } else if (replacement.all { it.isDigit() }) {
                    if (mins == "00") "$replacement:00" else "$replacement:$mins"
                } else {
                    replacement
                }
                return body.replace(timeRegex, repTime)
            }
        }

        // 2. Direct case-insensitive replacement
        val direct = body.replace(Regex(Regex.escape(target), RegexOption.IGNORE_CASE), replacement)
        if (direct != body) return direct

        // If target is a number word ("five") and body has digit ("5")
        val digitForTarget = numberMap[targetLower]
        if (digitForTarget != null) {
            val replaced = body.replace(Regex(Regex.escape(digitForTarget), RegexOption.IGNORE_CASE), replacementDigit)
            if (replaced != body) return replaced
        }
        // If target is a digit ("5") and body has word ("five")
        val wordForTarget = numberMap.entries.firstOrNull { it.value == target }?.key
        if (wordForTarget != null) {
            val replaced = body.replace(Regex(Regex.escape(wordForTarget), RegexOption.IGNORE_CASE), replacement)
            if (replaced != body) return replaced
        }

        // If target refers to "it", "that", "the time", "time", "the number", "number"
        if (targetLower in setOf("it", "that", "the time", "time", "the number", "number") || target.isBlank()) {
            val numRegex = Regex("\\b(\\d+|zero|one|two|three|four|five|six|seven|eight|nine|ten)\\b", RegexOption.IGNORE_CASE)
            val numMatch = numRegex.find(body)
            if (numMatch != null) {
                val matchedStr = numMatch.value
                val finalReplacement = if (matchedStr.all { it.isDigit() }) replacementDigit else replacement
                return body.substring(0, numMatch.range.first) + finalReplacement + body.substring(numMatch.range.last + 1)
            }
        }

        // Fallback: if target wasn't found at all, but replacement is a full sentence
        if (replacement.split("\\s+".toRegex()).size >= 3) {
            return replacement
        }

        return body.replace(target, replacement, ignoreCase = true)
    }

    private fun normalizeEmail(raw: String): String {
        var res = raw.trim().lowercase()
        res = res
            .replace(" at the rate of ", "@")
            .replace(" at the rate ", "@")
            .replace(" at the rateof ", "@")
            .replace(" @ ", "@")
            .replace(" @", "@")
            .replace("@ ", "@")
            .replace(" at ", "@")
            .replace(" dot ", ".")
            .replace(" dot", ".")
            .replace("dot ", ".")
        if (res.contains("@")) {
            res = res.replace(" ", "")
        }
        return res
    }
}
