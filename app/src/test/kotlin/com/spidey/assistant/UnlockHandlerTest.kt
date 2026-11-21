package com.spidey.assistant

import com.spidey.assistant.actions.UnlockAction
import com.spidey.assistant.actions.UnlockHandler
import com.spidey.assistant.core.ActionResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnlockHandlerTest {

    @Test
    fun testLockedDeviceReturnsNeedsUnlock() = runBlocking {
        val handler = UnlockHandler(isDeviceLocked = { true })
        val result = handler.handle(UnlockAction())
        assertTrue("Expected NeedsUnlock when device is locked", result is ActionResult.NeedsUnlock)
    }

    @Test
    fun testAlreadyUnlockedReturnsSpokenMessage() = runBlocking {
        val handler = UnlockHandler(isDeviceLocked = { false })
        val result = handler.handle(UnlockAction(wasPrompted = false))
        assertTrue("Expected Success when already unlocked", result is ActionResult.Success)
        assertEquals("Phone is already unlocked.", (result as ActionResult.Success).spokenMessage)
    }

    @Test
    fun testPromptedUnlockReturnsUnlockedSpokenMessage() = runBlocking {
        val handler = UnlockHandler(isDeviceLocked = { false })
        val result = handler.handle(UnlockAction(wasPrompted = true))
        assertTrue("Expected Success when unlocked after prompt", result is ActionResult.Success)
        assertEquals("Unlocked.", (result as ActionResult.Success).spokenMessage)
    }
}

