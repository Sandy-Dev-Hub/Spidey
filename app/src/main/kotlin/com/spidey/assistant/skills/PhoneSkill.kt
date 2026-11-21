package com.spidey.assistant.skills

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.spidey.assistant.actions.AssistantAction
import com.spidey.assistant.actions.BackgroundAppLauncher
import com.spidey.assistant.actions.CallActionHandler
import com.spidey.assistant.actions.CallContactAction
import com.spidey.assistant.actions.OpenAppAction
import com.spidey.assistant.core.ActionResult

/**
 * AppSkill for Phone Dialer.
 * Handles opening Phone dialer, SIM account choice, and placing cellular calls.
 */
class PhoneSkill(
    private val callActionHandler: CallActionHandler? = null
) : AppSkill {
    override val packageName: String = "com.samsung.android.dialer"
    override val supportedActions: List<String> = listOf("call", "sim_choice", "open")

    override suspend fun execute(action: AssistantAction, context: Context): ActionResult {
        return when (action) {
            is CallContactAction -> {
                if (callActionHandler != null) {
                    callActionHandler.handle(action)
                } else {
                    ActionResult.Failure("Phone calling service not available.", "CallActionHandler null")
                }
            }
            is OpenAppAction -> {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                    ?: context.packageManager.getLaunchIntentForPackage("com.google.android.dialer")
                if (launchIntent != null) {
                    launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    BackgroundAppLauncher.launch(context, launchIntent)
                    ActionResult.Success(spokenMessage = "Opening Phone.")
                } else {
                    ActionResult.Failure("Phone dialer app not found.", "Dialer package not found")
                }
            }
            else -> ActionResult.Failure("Phone cannot perform this action.", "Unsupported action for PhoneSkill")
        }
    }

    override suspend fun verify(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
    }
}
