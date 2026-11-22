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

        val isEmailInvalid = platform == MessagePlatform.EMAIL && !cleanEmail(recipient).contains("@")
        if (recipient.isBlank() || isEmailInvalid) {
            if (platform == MessagePlatform.EMAIL) {
                return ActionResult.NeedsClarification(
                    question = "What is the recipient email address boss?",
                    candidates = listOf("dictate_email_recipient", "platform:EMAIL")
                )
            } else {
                return ActionResult.NeedsClarification(
                    question = "Who would you like to message on ${platform.displayName} boss?",
                    candidates = listOf("dictate_recipient", "platform:${platform.name}")
                )
            }
        }

        if (body.isBlank()) {
            val q = if (platform == MessagePlatform.EMAIL) {
                "What is the message content boss?"
            } else {
                "What message would you like to send to $recipient boss?"
            }
            return ActionResult.NeedsClarification(
                question = q,
                candidates = listOf("dictate_message", "recipient:$recipient", "platform:${platform.name}")
            )
        }

        // Must confirm before sending per safety rules
        if (!action.isConfirmed) {
            // Open Gmail Compose with recipient and body populated!
            if (platform == MessagePlatform.EMAIL && recipient.isNotBlank()) {
                val email = cleanEmail(recipient)
                val mailSubject = action.subject.ifBlank { "" }
                val encodedBody = Uri.encode(body)
                val uriString = if (mailSubject.isNotBlank()) {
                    "mailto:$email?subject=${Uri.encode(mailSubject)}&body=$encodedBody"
                } else {
                    "mailto:$email?body=$encodedBody"
                }
                val previewIntent = Intent(Intent.ACTION_SENDTO, Uri.parse(uriString)).apply {
                    setPackage("com.google.android.gm")
                    putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
                    if (mailSubject.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, mailSubject)
                    putExtra(Intent.EXTRA_TEXT, body)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                BackgroundAppLauncher.launch(context, previewIntent)
            }

            val preview = if (platform == MessagePlatform.EMAIL) {
                "Email to $recipient: '$body'. Can I send it boss?"
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

        var intlNumber = cleanNumber ?: ""
        if (intlNumber.isNotBlank()) {
            intlNumber = intlNumber.replace(Regex("[^0-9]"), "")
            if (intlNumber.startsWith("0") && intlNumber.length == 11) {
                intlNumber = intlNumber.removePrefix("0")
            }
            if (intlNumber.length == 10 && !intlNumber.startsWith("91")) {
                intlNumber = "91$intlNumber"
            }
        }

        val intent = if (intlNumber.isNotBlank()) {
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                setPackage("com.whatsapp")
                putExtra(Intent.EXTRA_TEXT, body)
                putExtra("jid", "$intlNumber@s.whatsapp.net")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        } else {
            val uri = Uri.parse("https://api.whatsapp.com/send?text=${Uri.encode(body)}")
            Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.whatsapp")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
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

        // If Gmail Compose is already open with the body and accessibility is active, click send immediately!
        if (SpideyAccessibilityService.isAvailable()) {
            val clicked = SpideyAccessibilityService.instance?.clickSendInGmail(timeoutMs = 4000L) == true
            if (clicked) {
                return ActionResult.Success(spokenMessage = "Email sent to $email boss.")
            }
        }

        // Fallback: launch intent if Gmail was closed or click didn't find button
        val mailSubject = subject.ifBlank { "" }
        val encodedBody = Uri.encode(body)
        val uriString = if (mailSubject.isNotBlank()) {
            "mailto:$email?subject=${Uri.encode(mailSubject)}&body=$encodedBody"
        } else {
            "mailto:$email?body=$encodedBody"
        }
        val emailIntent = Intent(Intent.ACTION_SENDTO, Uri.parse(uriString)).apply {
            setPackage("com.google.android.gm")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
            if (mailSubject.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, mailSubject)
            putExtra(Intent.EXTRA_TEXT, body)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val launched = BackgroundAppLauncher.launch(context, emailIntent)
        if (!launched) {
            return ActionResult.Failure("Could not open Gmail boss.", "Email launch failed")
        }

        if (SpideyAccessibilityService.isAvailable()) {
            delay(1000)
            val clicked = SpideyAccessibilityService.instance?.clickSendInGmail(timeoutMs = 4000L) == true
            if (clicked) {
                return ActionResult.Success(spokenMessage = "Email sent to $email boss.")
            }
        }

        return ActionResult.Success(spokenMessage = "I prepared your email for $email boss. Please tap Send.")
    }

    private fun cleanEmail(raw: String): String {
        var s = raw.trim().lowercase().removePrefix("mailto:").trim()
        val prefixes = listOf(
            "send an email to ", "send a email to ", "send email to ",
            "send a mail to ", "send mail to ", "write an email to ",
            "write a email to ", "write email to ", "write to ",
            "email to ", "mail to ", "to "
        )
        for (prefix in prefixes) {
            if (s.startsWith(prefix)) {
                s = s.removePrefix(prefix).trim()
                break
            }
        }
        s = s.replace(" at the rate of ", "@")
            .replace(" at the rate ", "@")
            .replace(" at the rateof ", "@")
            .replace(" @ ", "@")
            .replace(" @", "@")
            .replace("@ ", "@")
            .replace(" at ", "@")
            .replace(" dot ", ".")
            .replace(" dot", ".")
            .replace("dot ", ".")
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
