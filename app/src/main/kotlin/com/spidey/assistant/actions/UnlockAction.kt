package com.spidey.assistant.actions

import com.spidey.assistant.core.ActionHandler
import com.spidey.assistant.core.ActionResult

/**
 * Action representing an explicit system request to unlock the device.
 */
data object UnlockAction : AssistantAction

/**
 * Handles [UnlockAction] by requesting device keyguard dismissal.
 */
class UnlockHandler(
    private val isDeviceLocked: () -> Boolean = { false }
) : ActionHandler<UnlockAction> {

    override val actionType: Class<UnlockAction> = UnlockAction::class.java

    override suspend fun handle(action: UnlockAction): ActionResult {
        return if (isDeviceLocked()) {
            ActionResult.NeedsUnlock
        } else {
            ActionResult.Success(spokenMessage = "Phone is already unlocked.")
        }
    }
}

