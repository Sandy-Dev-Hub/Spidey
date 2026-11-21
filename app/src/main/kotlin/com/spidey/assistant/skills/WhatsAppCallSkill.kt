package com.spidey.assistant.skills

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.spidey.assistant.actions.AssistantAction
import com.spidey.assistant.actions.BackgroundAppLauncher
import com.spidey.assistant.actions.OpenAppAction
import com.spidey.assistant.actions.WhatsAppCallAction
import com.spidey.assistant.contacts.ContactRepository
import com.spidey.assistant.contacts.model.ContactResolution
import com.spidey.assistant.core.ActionResult

/**
 * AppSkill for WhatsApp Calling.
 * Handles opening WhatsApp, initiating WhatsApp calls directly without cellular SIM dialogs.
 */
class WhatsAppCallSkill(
    private val contactRepository: ContactRepository? = null
) : AppSkill {
    override val packageName: String = "com.whatsapp"
    override val supportedActions: List<String> = listOf("call", "voice_call", "video_call", "open")

    override suspend fun execute(action: AssistantAction, context: Context): ActionResult {
        return when (action) {
            is WhatsAppCallAction -> {
                val query = action.contactQuery.trim()
                if (query.isBlank()) {
                    return ActionResult.Failure("Who would you like to call on WhatsApp?", "Blank contact query")
                }

                val isRawNumber = query.all { it.isDigit() || it == '+' || it == ' ' || it == '-' } && query.length >= 7
                val targetNumber: String? = if (isRawNumber) {
                    query.replace(Regex("[^0-9+]"), "")
                } else if (contactRepository != null) {
                    when (val res = contactRepository.findContact(query)) {
                        is ContactResolution.Resolved -> res.contact.phoneNumbers.firstOrNull()?.number
                        is ContactResolution.Ambiguous -> {
                            val names = res.candidates.map { it.displayName }
                            return ActionResult.NeedsClarification(
                                question = "I found ${names.size} contacts on WhatsApp: ${names.joinToString(", ")}. Which one?",
                                candidates = names
                            )
                        }
                        is ContactResolution.NotFound -> null
                        is ContactResolution.PermissionDenied -> null
                    }
                } else null

                if (targetNumber != null) {
                    val cleanNumber = targetNumber.replace(Regex("[^0-9+]"), "").removePrefix("+")
                    val uri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanNumber")
                    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                        setPackage(packageName)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    val launched = BackgroundAppLauncher.launch(context, intent)
                    if (launched) {
                        ActionResult.Success(spokenMessage = "Calling $query on WhatsApp.")
                    } else {
                        ActionResult.Failure("Could not open WhatsApp for $query.", "Launch failed")
                    }
                } else {
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                    if (launchIntent != null) {
                        launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        BackgroundAppLauncher.launch(context, launchIntent)
                        ActionResult.Success(spokenMessage = "Opening WhatsApp to call $query.")
                    } else {
                        ActionResult.Failure("WhatsApp is not installed on this device.", "Package not found")
                    }
                }
            }
            is OpenAppAction -> {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                if (launchIntent != null) {
                    launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    BackgroundAppLauncher.launch(context, launchIntent)
                    ActionResult.Success(spokenMessage = "Opening WhatsApp.")
                } else {
                    ActionResult.Failure("WhatsApp is not installed on this device.", "Package not found")
                }
            }
            else -> ActionResult.Failure("WhatsApp cannot perform this action.", "Unsupported action for WhatsAppCallSkill")
        }
    }

    override suspend fun verify(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }
    }
}
