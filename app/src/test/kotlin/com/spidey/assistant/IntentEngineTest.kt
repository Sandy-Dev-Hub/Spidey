package com.spidey.assistant

import com.spidey.assistant.actions.AllowedApp
import com.spidey.assistant.actions.FindContactAction
import com.spidey.assistant.actions.GreetingAction
import com.spidey.assistant.actions.OpenAppAction
import com.spidey.assistant.actions.SearchYouTubeAction
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
        com.spidey.assistant.calling.CallSessionManager.clear()
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
    fun testOpenOtherAppsIntents() {
        val appTestCases = listOf(
            "open whatsapp" to ("WhatsApp" to AllowedApp.WHATSAPP),
            "launch whatsapp" to ("WhatsApp" to AllowedApp.WHATSAPP),
            "open chrome" to ("Chrome" to AllowedApp.CHROME),
            "start chrome" to ("Chrome" to AllowedApp.CHROME),
            "open camera" to ("Camera" to AllowedApp.CAMERA),
            "open settings" to ("Settings" to AllowedApp.SETTINGS),
            "open spotify" to ("Spotify" to AllowedApp.SPOTIFY),
            "open instagram" to ("Instagram" to AllowedApp.INSTAGRAM),
            "open maps" to ("Maps" to AllowedApp.MAPS),
            "open calculator" to ("Calculator" to AllowedApp.CALCULATOR)
        )

        for ((phrase, expected) in appTestCases) {
            val action = intentEngine.parse(phrase)
            assertNotNull("Phrase '$phrase' should produce an action", action)
            assertTrue("Phrase '$phrase' should produce OpenAppAction, got: $action", action is OpenAppAction)
            val openApp = action as OpenAppAction
            assertEquals(expected.first.lowercase(), openApp.appName.lowercase())
            assertEquals(expected.second, openApp.app)
        }
    }

    @Test
    fun testYouTubeSearchIntents() {
        val searchTestCases = listOf(
            "go to search bar and search some song" to "some song",
            "go to search bar and search believer" to "believer",
            "search song shape of you on youtube" to "shape of you",
            "search believer on youtube" to "believer",
            "play believer on youtube" to "believer",
            "play shape of you" to "shape of you",
            "search on youtube faded" to "faded",
            "open youtube and search for song magale from baththa movie" to "magale from baththa movie",
            "search for song magale from baththa movie" to "magale from baththa movie",
            "open youtube and search believer" to "believer",
            "open youtube and play believer" to "believer",
            "go to youtube and search for infinity war trailer" to "infinity war trailer"
        )

        for ((phrase, expectedQuery) in searchTestCases) {
            val action = intentEngine.parse(phrase)
            assertNotNull("Phrase '$phrase' should produce an action", action)
            assertTrue("Phrase '$phrase' should produce SearchYouTubeAction, got: $action", action is SearchYouTubeAction)
            assertEquals(expectedQuery.lowercase(), (action as SearchYouTubeAction).query.lowercase())
        }
    }

    @Test
    fun testStopIntents() {
        val stopPhrases = listOf("stop", "cancel", "bye", "goodbye", "sleep", "shut down", "go to sleep")
        for (phrase in stopPhrases) {
            val action = intentEngine.parse(phrase)
            assertNotNull("Phrase '$phrase' should produce an action", action)
            assertTrue("Phrase '$phrase' should produce StopAction, got: $action", action is com.spidey.assistant.actions.StopAction)
        }
    }

    @Test
    fun testContactSearchIntents() {
        val queries = mapOf(
            "find Guru Anna" to "guru anna",
            "who is Guru Krishnan" to "guru krishnan",
            "search contact Mom" to "mom",
            "search for contact Dad" to "dad",
            "lookup Anna" to "anna",
            "contact details for John" to "john"
        )

        for ((phrase, expectedQuery) in queries) {
            val action = intentEngine.parse(phrase)
            assertNotNull("Phrase '$phrase' should produce an action", action)
            assertTrue("Phrase '$phrase' should produce FindContactAction", action is FindContactAction)
            assertEquals(expectedQuery, (action as FindContactAction).query)
        }
    }

    @Test
    fun testCallingIntents() {
        val callQueries = listOf(
            "call Guru Anna" to ("guru anna" to null),
            "call Guru Anna on Jio" to ("guru anna" to "jio"),
            "call Guru Anna on Airtel" to ("guru anna" to "airtel"),
            "call Mom on SIM 1" to ("mom" to "sim 1"),
            "dial 9876543210" to ("9876543210" to null),
            "make a call to Dad" to ("dad" to null),
            "phone Doctor" to ("doctor" to null)
        )

        for ((phrase, expected) in callQueries) {
            val action = intentEngine.parse(phrase)
            assertNotNull("Phrase '$phrase' should produce an action", action)
            assertTrue("Phrase '$phrase' should produce CallContactAction, got: $action", action is com.spidey.assistant.actions.CallContactAction)
            val callAction = action as com.spidey.assistant.actions.CallContactAction
            assertEquals(expected.first, callAction.contactQuery)
            assertEquals(expected.second, callAction.selectedSim)
        }
    }

    @Test
    fun testCallingConfirmation() {
        com.spidey.assistant.calling.CallSessionManager.startSession("Guru Anna")
        val candidates = listOf("confirm_call", "yes", "no", "cancel")

        val yesAction = intentEngine.parse("yes", candidates)
        assertTrue(yesAction is com.spidey.assistant.actions.CallContactAction)
        assertTrue((yesAction as com.spidey.assistant.actions.CallContactAction).isConfirmed)

        val goAheadAction = intentEngine.parse("go ahead", candidates)
        assertTrue(goAheadAction is com.spidey.assistant.actions.CallContactAction)
        assertTrue((goAheadAction as com.spidey.assistant.actions.CallContactAction).isConfirmed)

        val cancelAction = intentEngine.parse("cancel", candidates)
        assertTrue(cancelAction is com.spidey.assistant.actions.CallContactAction)
        assertTrue((cancelAction as com.spidey.assistant.actions.CallContactAction).isCancelled)

        val noAction = intentEngine.parse("no", candidates)
        assertTrue(noAction is com.spidey.assistant.actions.CallContactAction)
        assertTrue((noAction as com.spidey.assistant.actions.CallContactAction).isCancelled)
    }

    @Test
    fun testCallingSimClarification() {
        com.spidey.assistant.calling.CallSessionManager.startSession("Guru Anna")
        val candidates = listOf("sim_choice", "Airtel", "Jio")

        val jioAction = intentEngine.parse("Jio", candidates)
        assertTrue(jioAction is com.spidey.assistant.actions.CallContactAction)
        assertEquals("Jio", (jioAction as com.spidey.assistant.actions.CallContactAction).selectedSim)

        val secondAction = intentEngine.parse("second", candidates)
        assertTrue(secondAction is com.spidey.assistant.actions.CallContactAction)
        assertEquals("Jio", (secondAction as com.spidey.assistant.actions.CallContactAction).selectedSim)
    }

    @Test
    fun testClarificationResolution() {
        val candidates = listOf("Guru Anna LW", "Guru Anna LiveWires", "Guru Krishnan")

        // Ordinal responses
        val firstResult = intentEngine.parse("the first one", candidates)
        assertTrue(firstResult is FindContactAction)
        assertEquals("Guru Anna LW", (firstResult as FindContactAction).query)

        val secondResult = intentEngine.parse("second", candidates)
        assertTrue(secondResult is FindContactAction)
        assertEquals("Guru Anna LiveWires", (secondResult as FindContactAction).query)

        // Substring responses
        val nameResult = intentEngine.parse("LiveWires", candidates)
        assertTrue(nameResult is FindContactAction)
        assertEquals("Guru Anna LiveWires", (nameResult as FindContactAction).query)
    }

    @Test
    fun testYouTubeTrailerAndSearchContext() {
        // "search for infinity war trailer"
        val trailerAction = intentEngine.parse("search for infinity war trailer")
        assertNotNull(trailerAction)
        assertTrue(trailerAction is SearchYouTubeAction)
        assertEquals("infinity war trailer", (trailerAction as SearchYouTubeAction).query)

        // YouTube context follow-up query: "search avengers"
        val contextSearchAction = intentEngine.parse("search avengers", listOf("context:youtube"))
        assertNotNull(contextSearchAction)
        assertTrue(contextSearchAction is SearchYouTubeAction)
        assertEquals("avengers", (contextSearchAction as SearchYouTubeAction).query)

        // YouTube context follow-up media title without 'search': "infinity war trailer"
        val directTitleAction = intentEngine.parse("infinity war trailer", listOf("context:youtube"))
        assertNotNull(directTitleAction)
        assertTrue(directTitleAction is SearchYouTubeAction)
        assertEquals("infinity war trailer", (directTitleAction as SearchYouTubeAction).query)
    }

    @Test
    fun testTimeAndFeedbackIntents() {
        val timeAction = intentEngine.parse("what is the time now")
        assertNotNull(timeAction)
        assertTrue(timeAction is com.spidey.assistant.actions.TimeAction)

        val feedbackAction = intentEngine.parse("i m not see anything")
        assertNotNull(feedbackAction)
        assertTrue(feedbackAction is GreetingAction)
    }

    @Test
    fun testTelegramAndOpenWith() {
        val tgAction = intentEngine.parse("open telegram")
        assertNotNull(tgAction)
        assertTrue(tgAction is OpenAppAction)
        assertEquals("Telegram", (tgAction as OpenAppAction).appName)

        val openWithAction = intentEngine.parse("click open with youtube")
        assertNotNull(openWithAction)
        assertTrue(openWithAction is OpenAppAction)
        assertEquals("YouTube", (openWithAction as OpenAppAction).appName)

        val galleryAction = intentEngine.parse("gallery open the recent")
        assertNotNull(galleryAction)
        assertTrue(galleryAction is OpenAppAction)
        assertEquals("Gallery", (galleryAction as OpenAppAction).appName)
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
            assertNull("Phrase '$phrase' should return null", action)
        }
    }
}
