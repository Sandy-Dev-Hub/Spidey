package com.spidey.assistant.contacts

import com.spidey.assistant.contacts.matcher.ContactMatcher
import com.spidey.assistant.contacts.model.ContactRecord
import com.spidey.assistant.contacts.model.ContactResolution
import com.spidey.assistant.contacts.model.PhoneNumberRecord
import com.spidey.assistant.data.ContactAlias
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ContactMatcherTest {

    private lateinit var matcher: ContactMatcher
    private lateinit var syntheticContacts: List<ContactRecord>

    @Before
    fun setUp() {
        matcher = ContactMatcher()

        // Required test dataset from PHASE_2_CONTACT_SEARCH.md:
        // Guru Anna LW, Guru Anna LiveWires, Guru Krishnan, Anna LiveWires
        syntheticContacts = listOf(
            ContactRecord(
                id = "1",
                lookupKey = "guru_anna_lw",
                displayName = "Guru Anna LW",
                phoneNumbers = listOf(PhoneNumberRecord("9876543210", "Mobile"))
            ),
            ContactRecord(
                id = "2",
                lookupKey = "guru_anna_livewires",
                displayName = "Guru Anna LiveWires",
                phoneNumbers = listOf(PhoneNumberRecord("9876543211", "Work"))
            ),
            ContactRecord(
                id = "3",
                lookupKey = "guru_krishnan",
                displayName = "Guru Krishnan",
                phoneNumbers = listOf(PhoneNumberRecord("9876543212", "Mobile"))
            ),
            ContactRecord(
                id = "4",
                lookupKey = "anna_livewires",
                displayName = "Anna LiveWires",
                phoneNumbers = listOf(PhoneNumberRecord("9876543213", "Home"))
            )
        )
    }

    @Test
    fun testExactFullNameMatchResolvesCleanly() {
        val result = matcher.match("Guru Krishnan", syntheticContacts)

        assertTrue("Expected Resolved, got: $result", result is ContactResolution.Resolved)
        val resolved = result as ContactResolution.Resolved
        assertEquals("Guru Krishnan", resolved.contact.displayName)
        assertEquals(1.0, resolved.score, 0.001)
    }

    @Test
    fun testAmbiguousCandidatesTriggerClarification() {
        // "Guru Anna" matches both "Guru Anna LW" and "Guru Anna LiveWires"
        val result = matcher.match("Guru Anna", syntheticContacts)

        assertTrue("Expected Ambiguous, got: $result", result is ContactResolution.Ambiguous)
        val ambiguous = result as ContactResolution.Ambiguous
        val matchedNames = ambiguous.candidates.map { it.displayName }

        assertTrue(matchedNames.contains("Guru Anna LW"))
        assertTrue(matchedNames.contains("Guru Anna LiveWires"))
    }

    @Test
    fun testAmbiguousLiveWiresQuery() {
        // "LiveWires" matches both "Guru Anna LiveWires" and "Anna LiveWires"
        val result = matcher.match("LiveWires", syntheticContacts)

        assertTrue("Expected Ambiguous for LiveWires, got: $result", result is ContactResolution.Ambiguous)
        val ambiguous = result as ContactResolution.Ambiguous
        val matchedNames = ambiguous.candidates.map { it.displayName }

        assertTrue(matchedNames.contains("Guru Anna LiveWires"))
        assertTrue(matchedNames.contains("Anna LiveWires"))
    }

    @Test
    fun testUserAliasResolution() {
        val aliases = listOf(
            ContactAlias(alias = "Boss", contactId = "3", contactName = "Guru Krishnan")
        )

        val result = matcher.match("Boss", syntheticContacts, aliases)

        assertTrue("Expected Resolved via alias, got: $result", result is ContactResolution.Resolved)
        val resolved = result as ContactResolution.Resolved
        assertEquals("Guru Krishnan", resolved.contact.displayName)
    }

    @Test
    fun testFuzzySpellingErrorTolerance() {
        // Slight spelling error in "Krishnan" -> "Krishnan"
        val result = matcher.match("Guru Krishnan", syntheticContacts)
        assertTrue(result is ContactResolution.Resolved)

        val typoResult = matcher.match("Guru Krishna", syntheticContacts)
        assertTrue("Expected Resolved for minor typo, got: $typoResult", typoResult is ContactResolution.Resolved)
        assertEquals("Guru Krishnan", (typoResult as ContactResolution.Resolved).contact.displayName)
    }

    @Test
    fun testNotFoundForUnrelatedQuery() {
        val result = matcher.match("Tony Stark", syntheticContacts)

        assertTrue("Expected NotFound, got: $result", result is ContactResolution.NotFound)
        assertEquals("Tony Stark", (result as ContactResolution.NotFound).searchedQuery)
    }
}
