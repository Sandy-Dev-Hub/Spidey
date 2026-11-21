package com.spidey.assistant.actions

import com.spidey.assistant.core.ActionHandler
import com.spidey.assistant.core.ActionResult
import com.spidey.assistant.personality.SpideyPersona

/**
 * Action representing an explicit request to stop, go to sleep, or close the assistant session.
 */
class StopAction : AssistantAction {
    override fun equals(other: Any?): Boolean = other is StopAction
    override fun hashCode(): Int = javaClass.hashCode()
    override fun toString(): String = "StopAction"
}

/**
 * Handles [StopAction] by confirming closure with a friendly parting phrase.
 */
class StopHandler(
    private val stopResponse: String? = null
) : ActionHandler<StopAction> {
    override val actionType: Class<StopAction> = StopAction::class.java

    override suspend fun handle(action: StopAction): ActionResult {
        val reply = stopResponse ?: SpideyPersona.stopResponse()
        return ActionResult.Success(spokenMessage = reply)
    }
}
