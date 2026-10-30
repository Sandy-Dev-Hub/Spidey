package com.spidey.assistant

import com.spidey.assistant.actions.AllowedApp
import com.spidey.assistant.actions.GreetingAction
import com.spidey.assistant.actions.OpenAppAction
import com.spidey.assistant.core.IntentEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class IntentEngineTest {

    private lateinit var intentEngine: IntentEngine

    @Before
    fun setUp() {
        intentEngine = IntentEngine()
    }

    @Test
    fun testGreetingIntents() {
        val greetings = listOf(
            "hello",
            "hi",
            "hey",
            "hey spidey",
            "hey spider",
            "spider",
            "good morning",
            "what's up",
            "how are you"
        )

        for (phrase in greetings) {
            val action = intentEngine.parse(phrase)
            assertNotNull("Phrase '$phrase' should produce an action", action)
            assertTrue("Phrase '$phrase' should produce GreetingAction, got: $action", action is GreetingAction)
        }
    }

    @Test
    fun testOpenYouTubeIntents() {
        val youtubePhrases = listOf(
            "open youtube",
            "launch youtube",
            "start youtube",
            "play youtube",
            "youtube",
            "open up youtube"
        )

        for (phrase in youtubePhrases) {
            val action = intentEngine.parse(phrase)
            assertNotNull("Phrase '$phrase' should produce an action", action)
            assertTrue("Phrase '$phrase' should produce OpenAppAction", action is OpenAppAction)
            assertEquals(AllowedApp.YOUTUBE, (action as OpenAppAction).app)
        }
    }

    @Test
    fun testUnrecognizedOrGibberishIntentsReturnNull() {
        val unknownPhrases = listOf(
            "",
            "   ",
            "asdfghjkl",
            "make me coffee",
            "book a flight to mars",
            "what is the weather in antarctica"
        )

        for (phrase in unknownPhrases) {
            val action = intentEngine.parse(phrase)
            assertNull("Phrase '$phrase' should return null in Phase 1 intent engine", action)
        }
    }
}
