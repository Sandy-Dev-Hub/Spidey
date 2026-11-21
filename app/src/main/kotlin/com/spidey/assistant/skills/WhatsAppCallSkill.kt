package com.spidey.assistant.skills

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import com.spidey.assistant.actions.AssistantAction
import com.spidey.assistant.actions.BackgroundAppLauncher
import com.spidey.assistant.actions.OpenAppAction
import com.spidey.assistant.actions.WhatsAppCallAction
import com.spidey.assistant.contacts.ContactRepository
import com.spidey.assistant.contacts.model.ContactResolution
import com.spidey.assistant.core.ActionResult

/**
 * AppSkill for WhatsApp Calling.
 * Handles opening WhatsApp, initiating WhatsApp calls directly via ContactsContract VoIP data
 * or directly opening the contact chat and instructing the user to tap call.
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
                var contactId: String? = null
                var resolvedName: String = query
                val targetNumber: String? = if (isRawNumber) {
                    query.replace(Regex("[^0-9+]"), "")
                } else if (contactRepository != null) {
                    when (val res = contactRepository.findContact(query)) {
                        is ContactResolution.Resolved -> {
                            contactId = res.contact.id
                            resolvedName = res.contact.displayName
                            res.contact.phoneNumbers.firstOrNull()?.number
                        }
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

                // 1. Attempt direct VoIP call via Android ContactsContract WhatsApp sync
                val dataId = findWhatsAppCallDataId(context, contactId, targetNumber, resolvedName, action.isVideo)
                val mimeType = if (action.isVideo) {
                    "vnd.android.cursor.item/vnd.com.whatsapp.video.call"
                } else {
                    "vnd.android.cursor.item/vnd.com.whatsapp.voip.call"
                }

                if (dataId != null) {
                    val directCallIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.parse("content://com.android.contacts/data/$dataId"), mimeType)
                        setPackage(packageName)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    val launched = BackgroundAppLauncher.launch(context, directCallIntent)
                    if (launched) {
                        return ActionResult.Success(spokenMessage = "Calling $resolvedName on WhatsApp.")
                    }
                }

                // 2. Fallback: Open chat screen and inform user to tap the call button
                if (targetNumber != null) {
                    val cleanNumber = targetNumber.replace(Regex("[^0-9+]"), "").removePrefix("+")
                    val uri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanNumber")
                    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                        setPackage(packageName)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    val launched = BackgroundAppLauncher.launch(context, intent)
                    if (launched) {
                        ActionResult.Success(spokenMessage = "Opening $resolvedName on WhatsApp boss. Please tap the call button at the top right to start the call.")
                    } else {
                        ActionResult.Failure("Could not open WhatsApp for $resolvedName.", "Launch failed")
                    }
                } else {
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                    if (launchIntent != null) {
                        launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        BackgroundAppLauncher.launch(context, launchIntent)
                        ActionResult.Success(spokenMessage = "Opening WhatsApp to call $resolvedName.")
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

    private fun findWhatsAppCallDataId(
        context: Context,
        contactId: String?,
        phoneNumber: String?,
        displayName: String?,
        isVideo: Boolean
    ): Long? {
        val mimeType = if (isVideo) {
            "vnd.android.cursor.item/vnd.com.whatsapp.video.call"
        } else {
            "vnd.android.cursor.item/vnd.com.whatsapp.voip.call"
        }
        val cr = context.contentResolver
        val projection = arrayOf(ContactsContract.Data._ID, ContactsContract.Data.DATA1, ContactsContract.Data.CONTACT_ID)

        // A. Match by contactId if present
        if (!contactId.isNullOrBlank()) {
            try {
                cr.query(
                    ContactsContract.Data.CONTENT_URI,
                    projection,
                    "${ContactsContract.Data.MIMETYPE} = ? AND ${ContactsContract.Data.CONTACT_ID} = ?",
                    arrayOf(mimeType, contactId),
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.Data._ID)
                        return cursor.getLong(idIdx)
                    }
                }
            } catch (_: Exception) {}
        }

        // B. Match by phone number suffix in DATA1
        if (!phoneNumber.isNullOrBlank()) {
            val digits = phoneNumber.filter { it.isDigit() }
            val suffix = if (digits.length >= 10) digits.takeLast(10) else digits
            try {
                cr.query(
                    ContactsContract.Data.CONTENT_URI,
                    projection,
                    "${ContactsContract.Data.MIMETYPE} = ? AND ${ContactsContract.Data.DATA1} LIKE ?",
                    arrayOf(mimeType, "%$suffix%"),
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.Data._ID)
                        return cursor.getLong(idIdx)
                    }
                }
            } catch (_: Exception) {}
        }

        // C. Match by display name
        if (!displayName.isNullOrBlank()) {
            try {
                cr.query(
                    ContactsContract.Data.CONTENT_URI,
                    projection,
                    "${ContactsContract.Data.MIMETYPE} = ? AND ${ContactsContract.Data.DISPLAY_NAME} LIKE ?",
                    arrayOf(mimeType, "%$displayName%"),
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.Data._ID)
                        return cursor.getLong(idIdx)
                    }
                }
            } catch (_: Exception) {}
        }

        return null
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
