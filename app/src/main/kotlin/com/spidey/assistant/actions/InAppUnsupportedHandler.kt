package com.spidey.assistant.actions

import com.spidey.assistant.core.ActionHandler
import com.spidey.assistant.core.ActionResult

/**
 * Handles [InAppUnsupportedAction] by returning an honest rejection,
 * strictly preventing cross-app fallbacks.
 */
class InAppUnsupportedHandler : ActionHandler<InAppUnsupportedAction> {
    override val actionType: Class<InAppUnsupportedAction> = InAppUnsupportedAction::class.java

    override suspend fun handle(action: InAppUnsupportedAction): ActionResult {
        return ActionResult.Failure(
            spokenMessage = "${action.appName} does not support ${action.command}.",
            reason = "Unsupported in-app command for ${action.appName}"
        )
    }
}
