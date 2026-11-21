package com.spidey.assistant.actions

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.spidey.assistant.accessibility.SpideyAccessibilityService
import com.spidey.assistant.contacts.ContactRepository
import com.spidey.assistant.contacts.model.ContactResolution
import com.spidey.assistant.core.ActionHandler
import com.spidey.assistant.core.ActionResult
import kotlinx.coroutines.delay

/**
 * Handles [SendMessageAction] across WhatsApp, Email, and SMS with confirmation,
 * local contact resolution, prefill, and optional auto-tap via [SpideyAccessibilityService].
 */
class SendMessageHandler(
    private val context: Context,
    private val contactRepository: ContactRepository? = null
) : ActionHandler<SendMessageAction> {

    override val actionType: Class<SendMessageAction> = SendMessageAction::class.java

    override suspend fun handle(action: SendMessageAction): ActionResult {
        val recipient = action.recipient.trim()
        val body = action.body.trim()
        val platform = action.platform

        if (action.isCancelled) {
            return ActionResult.Success(spokenMessage = "Message cancelled boss.")
        }

        if (recipient.isBlank()) {
            return ActionResult.Failure("Who would you like to message boss?", "Missing recipient")
        }

        if (body.isBlank()) {
            return ActionResult.NeedsClarification(
                question = "What message would you like to send to $recipient boss?",
                candidates = listOf("dictate_message", "recipient:$recipient", "platform:${platform.name}")
            )
        }

        // Must confirm before sending per safety rules
        if (!action.isConfirmed) {
            val preview = if (platform == MessagePlatform.EMAIL) {
                "Email to $recipient: '$body'. Shall I send it boss?"
            } else {
                "Message to $recipient on ${platform.displayName}: '$body'. Shall I send it boss?"
            }
            return ActionResult.NeedsClarification(
                question = preview,
                candidates = listOf(
                    "confirm_message",
                    "recipient:$recipient",
                    "body:$body",
                    "platform:${platform.name}",
                    "subject:${action.subject}"
                )
            )
        }

        // User confirmed: execute sending
        return when (platform) {
            MessagePlatform.WHATSAPP -> sendWhatsApp(recipient, body)
            MessagePlatform.EMAIL -> sendEmail(recipient, body, action.subject)
            MessagePlatform.SMS -> sendSms(recipient, body)
        }
    }

    private suspend fun sendWhatsApp(recipient: String, body: String): ActionResult {
        var cleanNumber: String? = null
        var resolvedName: String = recipient
        val isRawNumber = recipient.all { it.isDigit() || it == '+' || it == ' ' || it == '-' } && recipient.length >= 7

        if (isRawNumber) {
            cleanNumber = recipient.replace(Regex("[^0-9+]"), "").removePrefix("+")
        } else if (contactRepository != null) {
            when (val res = contactRepository.findContact(recipient)) {
                is ContactResolution.Resolved -> {
                    resolvedName = res.contact.displayName
                    val phone = res.contact.phoneNumbers.firstOrNull()?.number
                    if (phone != null) {
                        cleanNumber = phone.replace(Regex("[^0-9+]"), "").removePrefix("+")
                    }
                }
                is ContactResolution.Ambiguous -> {
                    val names = res.candidates.map { it.displayName }
                    return ActionResult.NeedsClarification(
                        question = "I found multiple contacts for $recipient: ${names.joinToString(", ")}. Which one boss?",
                        candidates = names.map { "recipient:$it" } + listOf("dictate_message", "body:$body", "platform:WHATSAPP")
                    )
                }
                is ContactResolution.NotFound -> {}
                is ContactResolution.PermissionDenied -> {}
            }
        }

        val uri = if (!cleanNumber.isNullOrBlank()) {
            Uri.parse("https://api.whatsapp.com/send?phone=$cleanNumber&text=${Uri.encode(body)}")
        } else {
            Uri.parse("https://api.whatsapp.com/send?text=${Uri.encode(body)}")
        }

        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.whatsapp")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val launched = BackgroundAppLauncher.launch(context, intent)
        if (!launched) {
            return ActionResult.Failure("Could not open WhatsApp for $resolvedName boss.", "Launch failed")
        }

        if (SpideyAccessibilityService.isAvailable()) {
            delay(1200) // Allow WhatsApp UI to render
            val clicked = SpideyAccessibilityService.instance?.clickSendInWhatsApp() == true
            return if (clicked) {
                ActionResult.Success(spokenMessage = "Message sent to $resolvedName on WhatsApp boss.")
            } else {
                ActionResult.Success(spokenMessage = "I prepared your message for $resolvedName on WhatsApp boss. Please tap Send.")
            }
        } else {
            return ActionResult.Success(spokenMessage = "I prepared your message for $resolvedName on WhatsApp boss. Please tap Send to deliver it.")
        }
    }

    private suspend fun sendEmail(recipient: String, body: String, subject: String): ActionResult {
        val email = cleanEmail(recipient)

        val mailSubject = subject.ifBlank { "Message from Spidey" }
        val mailtoUri = Uri.parse("mailto:$email?subject=${Uri.encode(mailSubject)}&body=${Uri.encode(body)}")
        val emailIntent = Intent(Intent.ACTION_SENDTO, mailtoUri).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
            putExtra(Intent.EXTRA_SUBJECT, mailSubject)
            putExtra(Intent.EXTRA_TEXT, body)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val launched = BackgroundAppLauncher.launch(context, emailIntent)
        if (!launched) {
            return ActionResult.Failure("Could not open email app boss.", "Email launch failed")
        }

        if (SpideyAccessibilityService.isAvailable()) {
            delay(1200)
            val clicked = SpideyAccessibilityService.instance?.clickSendInGmail(timeoutMs = 4000L) == true
            return if (clicked) {
                ActionResult.Success(spokenMessage = "Email sent to $email boss.")
            } else {
                ActionResult.Success(spokenMessage = "I prepared your email for $email boss. Please tap Send.")
            }
        } else {
            return ActionResult.Success(spokenMessage = "I prepared your email for $email boss. Please tap Send to deliver it.")
        }
    }

    private fun cleanEmail(raw: String): String {
        var s = raw.trim().lowercase()
        s = s.replace(" at the rate of ", "@")
            .replace(" at the rate ", "@")
            .replace(" at ", "@")
            .replace(" dot ", ".")
        if (s.contains("@")) {
            s = s.replace(" ", "")
        }
        return s
    }

    private suspend fun sendSms(recipient: String, body: String): ActionResult {
        var cleanNumber = recipient.replace(Regex("[^0-9+]"), "")
        if (cleanNumber.isBlank() && contactRepository != null) {
            when (val res = contactRepository.findContact(recipient)) {
                is ContactResolution.Resolved -> {
                    cleanNumber = res.contact.phoneNumbers.firstOrNull()?.number ?: ""
                }
                else -> {}
            }
        }

        val uri = if (cleanNumber.isNotBlank()) Uri.parse("smsto:$cleanNumber") else Uri.parse("smsto:")
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra("sms_body", body)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val launched = BackgroundAppLauncher.launch(context, intent)
        return if (launched) {
            ActionResult.Success(spokenMessage = "I prepared your SMS for $recipient boss. Please tap Send.")
        } else {
            ActionResult.Failure("Could not open messaging app boss.", "SMS launch failed")
        }
    }
}
