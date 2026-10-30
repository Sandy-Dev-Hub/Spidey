package com.spidey.assistant.actions

import com.spidey.assistant.contacts.ContactRepository
import com.spidey.assistant.contacts.model.ContactResolution
import com.spidey.assistant.core.ActionHandler
import com.spidey.assistant.core.ActionResult

/**
 * Action representing a search for a contact by name or alias.
 */
data class FindContactAction(val query: String) : AssistantAction

/**
 * Handles [FindContactAction] by executing the [ContactMatcher] pipeline against [ContactRepository].
 */
class FindContactHandler(
    private val contactRepository: ContactRepository
) : ActionHandler<FindContactAction> {

    override val actionType: Class<FindContactAction> = FindContactAction::class.java

    override suspend fun handle(action: FindContactAction): ActionResult {
        return when (val resolution = contactRepository.findContact(action.query)) {
            is ContactResolution.Resolved -> {
                val contact = resolution.contact
                val phoneSummary = if (contact.phoneNumbers.isNotEmpty()) {
                    "with ${contact.phoneNumbers.size} number" + if (contact.phoneNumbers.size > 1) "s" else ""
                } else {
                    "with no phone numbers listed"
                }
                ActionResult.Success(
                    spokenMessage = "Found ${contact.displayName} $phoneSummary."
                )
            }
            is ContactResolution.Ambiguous -> {
                val candidateNames = resolution.candidates.map { it.displayName }
                val question = "I found ${candidateNames.size} contacts: ${candidateNames.joinToString(", ")}. Which one?"
                ActionResult.NeedsClarification(
                    question = question,
                    candidates = candidateNames
                )
            }
            is ContactResolution.NotFound -> {
                ActionResult.Failure(
                    spokenMessage = "I couldn't find a contact named ${action.query}.",
                    reason = "No matching contact found"
                )
            }
            is ContactResolution.PermissionDenied -> {
                ActionResult.Failure(
                    spokenMessage = "I need permission to access your contacts. Please allow contacts access in settings.",
                    reason = "READ_CONTACTS permission denied"
                )
            }
        }
    }
}
