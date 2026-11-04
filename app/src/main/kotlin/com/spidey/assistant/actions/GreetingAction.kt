package com.spidey.assistant.actions

import com.spidey.assistant.core.ActionHandler
import com.spidey.assistant.core.ActionResult
import com.spidey.assistant.personality.SpideyPersona

/**
 * Action representing a spoken greeting or general polite opening.
 */
data class GreetingAction(val queryText: String = "") : AssistantAction

/**
 * Handles [GreetingAction] with a friendly, casual persona.
 */
class GreetingHandler(
    private val greetingResponse: String? = null
) : ActionHandler<GreetingAction> {
    override val actionType: Class<GreetingAction> = GreetingAction::class.java

    override suspend fun handle(action: GreetingAction): ActionResult {
        val reply = greetingResponse ?: SpideyPersona.greeting()
        return ActionResult.Success(spokenMessage = reply)
    }
}
