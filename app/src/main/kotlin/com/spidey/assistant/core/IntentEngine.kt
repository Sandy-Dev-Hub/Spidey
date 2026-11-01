package com.spidey.assistant.core

import com.spidey.assistant.actions.AllowedApp
import com.spidey.assistant.actions.AssistantAction
import com.spidey.assistant.actions.FindContactAction
import com.spidey.assistant.actions.GreetingAction
import com.spidey.assistant.actions.OpenAppAction

/**
 * Local rule-based intent engine.
 * Converts natural speech text into a typed [AssistantAction] without external cloud or LLM dependencies.
 */
class IntentEngine {

    fun parse(text: String, candidatesContext: List<String> = emptyList()): AssistantAction? {
        val normalized = text.trim().lowercase()
        if (normalized.isBlank()) return null

        // 1. If clarifying between ambiguous options (e.g., "Which one?")
        if (candidatesContext.isNotEmpty()) {
            val resolvedCandidate = resolveClarification(normalized, candidatesContext)
            if (resolvedCandidate != null) {
                return FindContactAction(query = resolvedCandidate)
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

        // 4. Check for Contact Search intent
        val contactAction = matchContactSearch(normalized)
        if (contactAction != null) {
            return contactAction
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
            "contact",
            "call" // Call intent maps to finding contact in Phase 2
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
