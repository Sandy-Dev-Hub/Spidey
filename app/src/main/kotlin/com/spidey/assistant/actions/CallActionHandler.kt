package com.spidey.assistant.actions

import com.spidey.assistant.calling.CallCoordinator
import com.spidey.assistant.calling.CallInitiationResult
import com.spidey.assistant.calling.CallSessionManager
import com.spidey.assistant.calling.CallingAccount
import com.spidey.assistant.calling.CallingAccountRepository
import com.spidey.assistant.contacts.ContactRepository
import com.spidey.assistant.contacts.model.ContactRecord
import com.spidey.assistant.contacts.model.ContactResolution
import com.spidey.assistant.contacts.model.PhoneNumberRecord
import com.spidey.assistant.core.ActionHandler
import com.spidey.assistant.core.ActionResult
import com.spidey.assistant.safety.ConfirmationPolicy
import com.spidey.assistant.safety.PermissionManager

/**
 * Handles [CallContactAction] through a complete, safe, multi-turn conversational workflow:
 * 1. Checks device lock status (reports NeedsUnlock if phone is locked).
 * 2. Resolves contact via [ContactRepository] (or disambiguates ambiguous candidates).
 * 3. Resolves phone number if contact has multiple numbers (e.g. Mobile vs Work).
 * 4. Resolves SIM account if multiple dynamic phone accounts are detected (e.g. Airtel vs Jio).
 * 5. Requires explicit spoken confirmation via [ConfirmationPolicy].
 * 6. Initiates call via [CallCoordinator] with direct SIM selection or dialer fallback.
 */
class CallActionHandler(
    private val contactRepository: ContactRepository? = null,
    private val callingAccountRepository: CallingAccountRepository? = null,
    private val callCoordinator: CallCoordinator? = null,
    private val permissionManager: PermissionManager? = null,
    private val contactResolver: (suspend (String) -> ContactResolution)? = null,
    private val callingAccountsProvider: (() -> List<CallingAccount>)? = null,
    private val deviceLockChecker: (() -> Boolean)? = null,
    private val callInitiator: ((String, android.telecom.PhoneAccountHandle?) -> CallInitiationResult)? = null
) : ActionHandler<CallContactAction> {

    override val actionType: Class<CallContactAction> = CallContactAction::class.java

    override suspend fun handle(action: CallContactAction): ActionResult {
        // 1. Check if user cancelled
        if (action.isCancelled) {
            CallSessionManager.clear()
            return ActionResult.Success("Call cancelled.")
        }

        // 2. Check if device is locked
        val isLocked = deviceLockChecker?.invoke() ?: (callCoordinator?.isDeviceLocked() == true)
        if (isLocked) {
            return ActionResult.NeedsUnlock
        }

        // 3. Resolve Contact or Direct Number
        val contact: ContactRecord = if (isRawPhoneNumber(action.contactQuery)) {
            val num = action.contactQuery.filter { it.isDigit() || it == '+' }
            ContactRecord(
                id = "direct_number",
                lookupKey = "direct_number",
                displayName = num,
                phoneNumbers = listOf(
                    PhoneNumberRecord(
                        number = num,
                        label = "Mobile"
                    )
                )
            )
        } else {
            val resolution = contactResolver?.invoke(action.contactQuery)
                ?: contactRepository?.findContact(action.contactQuery)
                ?: ContactResolution.NotFound(action.contactQuery)
            when (resolution) {
                is ContactResolution.NotFound -> {
                    CallSessionManager.clear()
                    return ActionResult.Failure(
                        spokenMessage = "I couldn't find a contact named ${action.contactQuery}.",
                        reason = "No matching contact found"
                    )
                }
                is ContactResolution.PermissionDenied -> {
                    CallSessionManager.clear()
                    return ActionResult.Failure(
                        spokenMessage = "I need permission to access your contacts to place a call.",
                        reason = "READ_CONTACTS permission denied"
                    )
                }
                is ContactResolution.Ambiguous -> {
                    val candidateNames = resolution.candidates.map { it.displayName }
                    CallSessionManager.startSession(action.contactQuery)
                    val question = "I found ${candidateNames.size} contacts: ${candidateNames.joinToString(", ")}. Which one?"
                    return ActionResult.NeedsClarification(
                        question = question,
                        candidates = candidateNames
                    )
                }
                is ContactResolution.Resolved -> {
                    resolution.contact
                }
            }
        }

        // 4. Resolve Phone Number
        if (contact.phoneNumbers.isEmpty()) {
            CallSessionManager.clear()
            return ActionResult.Failure(
                spokenMessage = "${contact.displayName} doesn't have any phone numbers listed.",
                reason = "No phone numbers for contact"
            )
        }

        val chosenNumber: PhoneNumberRecord = if (contact.phoneNumbers.size == 1) {
            contact.phoneNumbers.first()
        } else {
            val matched = if (action.selectedNumber != null) {
                matchPhoneNumber(action.selectedNumber, contact.phoneNumbers)
            } else null

            if (matched != null) {
                matched
            } else {
                CallSessionManager.startSession(contact.displayName)
                val labels = contact.phoneNumbers.mapIndexed { idx, p ->
                    p.label.ifBlank { "Number ${idx + 1}" }
                }
                val question = "${contact.displayName} has ${contact.phoneNumbers.size} numbers: ${labels.joinToString(" and ")}. Which one?"
                return ActionResult.NeedsClarification(
                    question = question,
                    candidates = listOf("number_choice") + labels
                )
            }
        }

        // 5. Resolve SIM / Phone Account
        val accounts = callingAccountsProvider?.invoke() ?: callingAccountRepository?.getCallingAccounts() ?: emptyList()
        val chosenAccount: CallingAccount? = if (accounts.size <= 1) {
            accounts.firstOrNull()
        } else {
            val matched = if (action.selectedSim != null) {
                callingAccountRepository?.matchAccount(action.selectedSim, accounts)
                    ?: CallingAccountRepository().matchAccount(action.selectedSim, accounts)
            } else null

            if (matched != null) {
                matched
            } else {
                CallSessionManager.startSession(contact.displayName)
                val labels = accounts.map { it.label }
                val question = "Which SIM would you like to use: ${labels.joinToString(" or ")}?"
                return ActionResult.NeedsClarification(
                    question = question,
                    candidates = listOf("sim_choice") + labels
                )
            }
        }

        // Update session with resolved details
        CallSessionManager.updateSession(
            resolvedContact = contact,
            selectedNumber = chosenNumber.number,
            selectedSim = chosenAccount?.label
        )

        // 6. Enforce Spoken Confirmation
        if (!action.isConfirmed) {
            val prompt = ConfirmationPolicy.formatPrompt(contact.displayName, chosenAccount?.label)
            return ActionResult.NeedsClarification(
                question = prompt,
                candidates = listOf("confirm_call", "yes", "no", "cancel")
            )
        }

        // 7. Place Call via CallCoordinator
        CallSessionManager.clear()
        val dialTarget = chosenNumber.number
        val callResult = callInitiator?.invoke(dialTarget, chosenAccount?.handle)
            ?: callCoordinator?.initiateCall(dialTarget, chosenAccount?.handle)
            ?: CallInitiationResult.Direct("Direct")
        return when (callResult) {
            is CallInitiationResult.Direct -> {
                ActionResult.Success(spokenMessage = "Calling ${contact.displayName}.")
            }
            is CallInitiationResult.FallbackDialer -> {
                ActionResult.Success(spokenMessage = "Opening dialer for ${contact.displayName}.")
            }
            is CallInitiationResult.NeedsUnlock -> {
                ActionResult.NeedsUnlock
            }
            is CallInitiationResult.Failure -> {
                ActionResult.Failure(
                    spokenMessage = callResult.message,
                    reason = callResult.reason
                )
            }
        }
    }

    private fun isRawPhoneNumber(text: String): Boolean {
        val clean = text.trim()
        val digitCount = clean.count { it.isDigit() }
        return digitCount >= 5 && clean.all { it.isDigit() || it in "+- ()" }
    }

    private fun matchPhoneNumber(query: String, phoneNumbers: List<PhoneNumberRecord>): PhoneNumberRecord? {
        val norm = query.trim().lowercase()

        // Ordinals
        if (norm in setOf("first", "the first", "first one", "the first one", "1", "one", "number one")) {
            return phoneNumbers.getOrNull(0)
        }
        if (norm in setOf("second", "the second", "second one", "the second one", "2", "two", "number two")) {
            return phoneNumbers.getOrNull(1)
        }
        if (norm in setOf("third", "the third", "third one", "the third one", "3", "three", "number three")) {
            return phoneNumbers.getOrNull(2)
        }

        // Label matches ("mobile", "work", "home")
        for (p in phoneNumbers) {
            val labelNorm = p.label.trim().lowercase()
            if (labelNorm.isNotEmpty() && (labelNorm.contains(norm) || norm.contains(labelNorm))) {
                return p
            }
        }

        // Digit substring matching
        val queryDigits = norm.filter { it.isDigit() }
        if (queryDigits.length >= 3) {
            for (p in phoneNumbers) {
                if (p.number.contains(queryDigits)) {
                    return p
                }
            }
        }

        return null
    }
}
