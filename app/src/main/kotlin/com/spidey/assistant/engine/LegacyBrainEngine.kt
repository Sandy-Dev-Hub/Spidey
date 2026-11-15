package com.spidey.assistant.engine

import com.spidey.assistant.ai.GroqChatService

/**
 * J0: Legacy Brain adapter — wraps the existing GroqChatService behind the BrainEngine interface.
 * This is the default engine when FeatureFlags.safeMode = true.
 */
class LegacyBrainEngine(
    private val groq: GroqChatService = GroqChatService()
) : BrainEngine {

    override suspend fun reply(utterance: String): String = groq.chat(utterance)

    override fun clearHistory() = groq.clearHistory()
}

