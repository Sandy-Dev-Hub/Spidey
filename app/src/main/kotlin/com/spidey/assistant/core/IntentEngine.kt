package com.spidey.assistant.core

import com.spidey.assistant.actions.AllowedApp
import com.spidey.assistant.actions.AssistantAction
import com.spidey.assistant.actions.CallContactAction
import com.spidey.assistant.actions.FindContactAction
import com.spidey.assistant.actions.GreetingAction
import com.spidey.assistant.actions.OpenAppAction
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

        // 2. Check for Greeting intent
        if (isGreeting(normalized)) {
            return GreetingAction(queryText = text)
        }

        // 3. Check for Open App intent
        val openAppAction = matchOpenApp(normalized)
        if (openAppAction != null) {
            return openAppAction
        }

        // 4. Check for Calling intent (Phase 3)
        val callAction = matchCall(normalized)
        if (callAction != null) {
            return callAction
        }

        // 5. Check for Contact Search intent (Phase 2)
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

    private fun matchOpenApp(text: String): AssistantAction? {
        val clean = text.replace(Regex("[^a-z0-9 ]"), " ").trim()
        val words = clean.split("\\s+".toRegex()).filter { it.isNotBlank() }

        if ("youtube" in words) {
            val appTriggers = setOf("open", "launch", "start", "play", "go", "show", "run")
            if (words.size == 1 || words.any { it in appTriggers }) {
                return OpenAppAction(AllowedApp.YOUTUBE)
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
        val clean = text.replace(Regex("[^a-z0-9 ]"), " ").trim()

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
