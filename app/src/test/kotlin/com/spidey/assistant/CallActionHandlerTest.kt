package com.spidey.assistant

import com.spidey.assistant.actions.CallActionHandler
import com.spidey.assistant.actions.CallContactAction
import com.spidey.assistant.calling.CallInitiationResult
import com.spidey.assistant.calling.CallSessionManager
import com.spidey.assistant.calling.CallingAccount
import com.spidey.assistant.contacts.model.ContactRecord
import com.spidey.assistant.contacts.model.ContactResolution
import com.spidey.assistant.contacts.model.PhoneNumberRecord
import com.spidey.assistant.core.ActionResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CallActionHandlerTest {

    @Before
    fun setUp() {
        CallSessionManager.clear()
    }

    @Test
    fun testDeviceLockedReturnsNeedsUnlock() = runTest {
        val handler = CallActionHandler(
            deviceLockChecker = { true }
        )

        val result = handler.handle(CallContactAction(contactQuery = "Guru Anna"))
        assertEquals(ActionResult.NeedsUnlock, result)
    }

    @Test
    fun testCancelActionReturnsSuccess() = runTest {
        val handler = CallActionHandler(
            deviceLockChecker = { false }
        )

        val result = handler.handle(CallContactAction(contactQuery = "Guru Anna", isCancelled = true))
        assertTrue(result is ActionResult.Success)
        assertEquals("Call cancelled.", (result as ActionResult.Success).spokenMessage)
    }

    @Test
    fun testContactNotFoundReturnsFailure() = runTest {
        val handler = CallActionHandler(
            deviceLockChecker = { false },
            contactResolver = { ContactResolution.NotFound("Unknown Guy") }
        )

        val result = handler.handle(CallContactAction(contactQuery = "Unknown Guy"))
        assertTrue(result is ActionResult.Failure)
        val fail = result as ActionResult.Failure
        assertEquals("No matching contact found", fail.reason)
        assertEquals("I couldn't find a contact named Unknown Guy.", fail.spokenMessage)
    }

    @Test
    fun testAmbiguousContactReturnsNeedsClarification() = runTest {
        val candidates = listOf(
            ContactRecord(id = "1", lookupKey = "1", displayName = "Guru Anna LW", phoneNumbers = listOf(PhoneNumberRecord("9876543210", "Mobile"))),
            ContactRecord(id = "2", lookupKey = "2", displayName = "Guru Anna LiveWires", phoneNumbers = listOf(PhoneNumberRecord("9876543211", "Work")))
        )
        val handler = CallActionHandler(
            deviceLockChecker = { false },
            contactResolver = { ContactResolution.Ambiguous("Guru Anna", candidates) }
        )

        val result = handler.handle(CallContactAction(contactQuery = "Guru Anna"))
        assertTrue(result is ActionResult.NeedsClarification)
        val clar = result as ActionResult.NeedsClarification
        assertEquals(listOf("Guru Anna LW", "Guru Anna LiveWires"), clar.candidates)
    }

    @Test
    fun testContactWithoutPhoneReturnsFailure() = runTest {
        val contactNoPhone = ContactRecord(id = "1", lookupKey = "1", displayName = "Guru", phoneNumbers = emptyList())
        val handler = CallActionHandler(
            deviceLockChecker = { false },
            contactResolver = { ContactResolution.Resolved(contactNoPhone, 1.0) }
        )

        val result = handler.handle(CallContactAction(contactQuery = "Guru"))
        assertTrue(result is ActionResult.Failure)
        assertEquals("Guru doesn't have any phone numbers listed.", (result as ActionResult.Failure).spokenMessage)
    }

    @Test
    fun testContactWithMultipleNumbersPromptsNumberChoice() = runTest {
        val contact = ContactRecord(
            id = "1",
            lookupKey = "1",
            displayName = "Guru Anna",
            phoneNumbers = listOf(
                PhoneNumberRecord("9876543210", "Mobile"),
                PhoneNumberRecord("9876543211", "Work")
            )
        )
        val handler = CallActionHandler(
            deviceLockChecker = { false },
            contactResolver = { ContactResolution.Resolved(contact, 1.0) }
        )

        val result = handler.handle(CallContactAction(contactQuery = "Guru Anna"))
        assertTrue(result is ActionResult.NeedsClarification)
        val clar = result as ActionResult.NeedsClarification
        assertTrue(clar.question.contains("2 numbers: Mobile and Work"))
        assertEquals(listOf("number_choice", "Mobile", "Work"), clar.candidates)
    }

    @Test
    fun testDualSimPromptsSimChoice() = runTest {
        val contact = ContactRecord(
            id = "1",
            lookupKey = "1",
            displayName = "Guru Anna",
            phoneNumbers = listOf(PhoneNumberRecord("9876543210", "Mobile"))
        )
        val accounts = listOf(
            CallingAccount(id = "1", label = "Airtel FastLane", simSlotIndex = 0),
            CallingAccount(id = "2", label = "Jio 5G", simSlotIndex = 1)
        )
        val handler = CallActionHandler(
            deviceLockChecker = { false },
            contactResolver = { ContactResolution.Resolved(contact, 1.0) },
            callingAccountsProvider = { accounts }
        )

        val result = handler.handle(CallContactAction(contactQuery = "Guru Anna"))
        assertTrue(result is ActionResult.NeedsClarification)
        val clar = result as ActionResult.NeedsClarification
        assertTrue(clar.question.contains("Airtel FastLane or Jio 5G"))
        assertEquals(listOf("sim_choice", "Airtel FastLane", "Jio 5G"), clar.candidates)
    }

    @Test
    fun testSingleSimPromptsConfirmationDirectly() = runTest {
        val contact = ContactRecord(
            id = "1",
            lookupKey = "1",
            displayName = "Guru Anna",
            phoneNumbers = listOf(PhoneNumberRecord("9876543210", "Mobile"))
        )
        val accounts = listOf(
            CallingAccount(id = "1", label = "Jio 5G", simSlotIndex = 0)
        )
        val handler = CallActionHandler(
            deviceLockChecker = { false },
            contactResolver = { ContactResolution.Resolved(contact, 1.0) },
            callingAccountsProvider = { accounts }
        )

        val result = handler.handle(CallContactAction(contactQuery = "Guru Anna"))
        assertTrue(result is ActionResult.NeedsClarification)
        val clar = result as ActionResult.NeedsClarification
        assertEquals("Calling Guru Anna on Jio 5G. Shall I go ahead?", clar.question)
        assertEquals(listOf("confirm_call", "yes", "no", "cancel"), clar.candidates)
    }

    @Test
    fun testConfirmedCallInitiatesDirectCall() = runTest {
        val contact = ContactRecord(
            id = "1",
            lookupKey = "1",
            displayName = "Guru Anna",
            phoneNumbers = listOf(PhoneNumberRecord("9876543210", "Mobile"))
        )
        val accounts = listOf(
            CallingAccount(id = "1", label = "Jio 5G", simSlotIndex = 0)
        )
        var dialedNumber: String? = null
        val handler = CallActionHandler(
            deviceLockChecker = { false },
            contactResolver = { ContactResolution.Resolved(contact, 1.0) },
            callingAccountsProvider = { accounts },
            callInitiator = { num, _ ->
                dialedNumber = num
                CallInitiationResult.Direct("TelecomManager")
            }
        )

        val result = handler.handle(CallContactAction(contactQuery = "Guru Anna", isConfirmed = true))
        assertTrue(result is ActionResult.Success)
        assertEquals("Calling Guru Anna.", (result as ActionResult.Success).spokenMessage)
        assertEquals("9876543210", dialedNumber)
    }

    @Test
    fun testDialerFallbackReportedHonestMessage() = runTest {
        val contact = ContactRecord(
            id = "1",
            lookupKey = "1",
            displayName = "Guru Anna",
            phoneNumbers = listOf(PhoneNumberRecord("9876543210", "Mobile"))
        )
        val handler = CallActionHandler(
            deviceLockChecker = { false },
            contactResolver = { ContactResolution.Resolved(contact, 1.0) },
            callingAccountsProvider = { emptyList() },
            callInitiator = { _, _ ->
                CallInitiationResult.FallbackDialer("Opened dialer")
            }
        )

        val result = handler.handle(CallContactAction(contactQuery = "Guru Anna", isConfirmed = true))
        assertTrue(result is ActionResult.Success)
        assertEquals("Opening dialer for Guru Anna.", (result as ActionResult.Success).spokenMessage)
    }
}
