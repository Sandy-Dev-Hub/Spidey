package com.spidey.assistant.actions

import com.spidey.assistant.ai.GroqChatService
import com.spidey.assistant.core.ActionHandler
import com.spidey.assistant.core.ActionResult

/**
 * Action representing a conversational utterance routed to the AI bro persona (Groq).
 */
data class ChatAction(val message: String) : AssistantAction

/**
 * Handles [ChatAction] by consulting Groq Llama-3 and returning a friendly voice response.
 */
class ChatHandler(
    private val groqChatService: GroqChatService = GroqChatService()
) : ActionHandler<ChatAction> {
    override val actionType: Class<ChatAction> = ChatAction::class.java

    override suspend fun handle(action: ChatAction): ActionResult {
        val reply = groqChatService.chat(action.message)
        return ActionResult.Success(spokenMessage = reply)
    }
}

