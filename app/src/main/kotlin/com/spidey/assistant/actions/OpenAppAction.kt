package com.spidey.assistant.actions

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import com.spidey.assistant.core.ActionHandler
import com.spidey.assistant.core.ActionResult

/**
 * Strict allowlist of apps SPIDEY is allowed to open directly.
 */
enum class AllowedApp(val displayName: String, val packageName: String) {
    YOUTUBE("YouTube", "com.google.android.youtube")
}

/**
 * Action representing an explicit request to open a whitelisted app.
 */
data class OpenAppAction(val app: AllowedApp) : AssistantAction

/**
 * Handles [OpenAppAction] safely.
 * If the device is locked, returns [ActionResult.NeedsUnlock] to prompt the unlock flow.
 */
class OpenAppHandler(
    private val context: Context,
    private val isDeviceLocked: () -> Boolean = {
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        km?.isDeviceLocked == true
    }
) : ActionHandler<OpenAppAction> {
    override val actionType: Class<OpenAppAction> = OpenAppAction::class.java

    override suspend fun handle(action: OpenAppAction): ActionResult {
        if (isDeviceLocked()) {
            return ActionResult.NeedsUnlock
        }

        val launchIntent = context.packageManager.getLaunchIntentForPackage(action.app.packageName)
            ?: return ActionResult.Failure(
                spokenMessage = "I could not find ${action.app.displayName} on this device.",
                reason = "Launch intent not found for package ${action.app.packageName}"
            )

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return try {
            context.startActivity(launchIntent)
            ActionResult.Success(spokenMessage = "Opening ${action.app.displayName}")
        } catch (e: Exception) {
            ActionResult.Failure(
                spokenMessage = "Failed to open ${action.app.displayName}.",
                reason = e.message ?: "Unknown error starting activity"
            )
        }
    }
}
