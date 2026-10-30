package com.spidey.assistant

import com.spidey.assistant.calling.CallingAccount
import com.spidey.assistant.calling.CallingAccountRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CallingAccountRepositoryTest {

    private val accounts = listOf(
        CallingAccount(id = "sub_1", label = "Airtel FastLane", simSlotIndex = 0),
        CallingAccount(id = "sub_2", label = "Jio 5G", simSlotIndex = 1)
    )

    // Context is not needed for matchAccount
    private val repo = CallingAccountRepository()

    @Test
    fun testMatchByCarrierName() {
        val airtelMatch = repo.matchAccount("Airtel", accounts)
        assertNotNull(airtelMatch)
        assertEquals("Airtel FastLane", airtelMatch?.label)

        val jioMatch = repo.matchAccount("jio", accounts)
        assertNotNull(jioMatch)
        assertEquals("Jio 5G", jioMatch?.label)
    }

    @Test
    fun testMatchBySlotOrOrdinal() {
        val sim1Match = repo.matchAccount("SIM 1", accounts)
        assertNotNull(sim1Match)
        assertEquals("Airtel FastLane", sim1Match?.label)

        val firstMatch = repo.matchAccount("the first one", accounts)
        assertNotNull(firstMatch)
        assertEquals("Airtel FastLane", firstMatch?.label)

        val sim2Match = repo.matchAccount("sim 2", accounts)
        assertNotNull(sim2Match)
        assertEquals("Jio 5G", sim2Match?.label)

        val secondMatch = repo.matchAccount("second", accounts)
        assertNotNull(secondMatch)
        assertEquals("Jio 5G", secondMatch?.label)
    }

    @Test
    fun testUnmatchedReturnsNull() {
        val vodafoneMatch = repo.matchAccount("Vodafone", accounts)
        assertNull(vodafoneMatch)

        val thirdMatch = repo.matchAccount("SIM 3", accounts)
        assertNull(thirdMatch)
    }
}
