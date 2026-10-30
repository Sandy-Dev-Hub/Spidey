package com.spidey.assistant.actions

import com.spidey.assistant.core.ActionResult
import com.spidey.assistant.core.ActionRouter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ActionRouterTest {

    private lateinit var router: ActionRouter

    @Before
    fun setUp() {
        router = ActionRouter()
    }

    @Test
    fun testRegisteredGreetingHandlerReturnsSuccess() = runBlocking {
        router.register(GreetingHandler(greetingResponse = "Hello boss!"))

        val result = router.route(GreetingAction("hello"))

        assertTrue("Expected ActionResult.Success", result is ActionResult.Success)
        assertEquals("Hello boss!", (result as ActionResult.Success).spokenMessage)
    }

    @Test
    fun testUnregisteredActionReturnsFailure() = runBlocking {
        // Router only has GreetingHandler registered, so OpenAppAction has no handler
        val result = router.route(OpenAppAction(AllowedApp.YOUTUBE))

        assertTrue("Expected ActionResult.Failure for unregistered action", result is ActionResult.Failure)
        val failure = result as ActionResult.Failure
        assertTrue(failure.reason.contains("No handler registered"))
    }
}
