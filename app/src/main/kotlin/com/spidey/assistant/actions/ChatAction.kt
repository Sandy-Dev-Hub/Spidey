package com.spidey.assistant.actions

import com.spidey.assistant.ai.GroqChatService
import com.spidey.assistant.core.ActionHandler
import com.spidey.assistant.core.ActionResult
import com.spidey.assistant.engine.BrainEngine
import com.spidey.assistant.engine.FeatureFlags
import com.spidey.assistant.engine.LegacyBrainEngine

/**
 * Action representing a conversational utterance routed to the AI bro persona (Groq or other brain).
 */
data class ChatAction(val message: String) : AssistantAction

/**
 * Handles [ChatAction] by consulting the configured [BrainEngine] and returning a friendly voice response.
 */
class ChatHandler(
    private val brainEngine: BrainEngine = LegacyBrainEngine()
) : ActionHandler<ChatAction> {
    override val actionType: Class<ChatAction> = ChatAction::class.java

    override suspend fun handle(action: ChatAction): ActionResult {
        val reply = brainEngine.reply(action.message)
        return ActionResult.Success(spokenMessage = reply)
    }
}
