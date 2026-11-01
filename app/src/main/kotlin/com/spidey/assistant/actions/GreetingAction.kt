package com.spidey.assistant.actions

import com.spidey.assistant.core.ActionHandler
import com.spidey.assistant.core.ActionResult

/**
 * Action representing a spoken greeting or general polite opening.
 */
data class GreetingAction(val queryText: String = "") : AssistantAction

/**
 * Handles [GreetingAction] by returning a polite spoken reply.
 */
class GreetingHandler(
    private val greetingResponse: String = "Hello! Spidey is listening."
) : ActionHandler<GreetingAction> {
    override val actionType: Class<GreetingAction> = GreetingAction::class.java

    override suspend fun handle(action: GreetingAction): ActionResult {
        return ActionResult.Success(spokenMessage = greetingResponse)
    }
}
