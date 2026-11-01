package com.spidey.assistant.core

import com.spidey.assistant.actions.AllowedApp
import com.spidey.assistant.actions.AssistantAction
import com.spidey.assistant.actions.GreetingAction
import com.spidey.assistant.actions.OpenAppAction

/**
 * Version 1 local rule-based intent engine.
 * Converts natural speech text into a typed [AssistantAction] without external cloud or LLM dependencies.
 */
class IntentEngine {

    fun parse(text: String): AssistantAction? {
        val normalized = text.trim().lowercase()
        if (normalized.isBlank()) return null

        // 1. Check for Greeting intent
        if (isGreeting(normalized)) {
            return GreetingAction(queryText = text)
        }

        // 2. Check for Open App intent
        val openAppAction = matchOpenApp(normalized)
        if (openAppAction != null) {
            return openAppAction
        }

        // Unrecognized intent
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

        // Starting with hello/hi/hey
        if (words.isNotEmpty() && words[0] in setOf("hello", "hi", "hey")) {
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
}
