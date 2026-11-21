package com.spidey.assistant

import com.spidey.assistant.actions.AllowedApp
import com.spidey.assistant.actions.CallContactAction
import com.spidey.assistant.actions.FindContactAction
import com.spidey.assistant.actions.GreetingAction
import com.spidey.assistant.actions.InAppUnsupportedAction
import com.spidey.assistant.actions.OpenAppAction
import com.spidey.assistant.actions.PlayFirstResultAction
import com.spidey.assistant.actions.PlaySpotifyAction
import com.spidey.assistant.actions.SearchYouTubeAction
import com.spidey.assistant.actions.StopAction
import com.spidey.assistant.actions.TimeAction
import com.spidey.assistant.actions.UnlockAction
import com.spidey.assistant.actions.WhatsAppCallAction
import com.spidey.assistant.core.IntentEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            assertTrue("Phrase '$phrase' should produce StopAction, got: $action", action is StopAction)
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
            assertTrue("Phrase '$phrase' should produce CallContactAction, got: $action", action is CallContactAction)
            val callAction = action as CallContactAction
            assertEquals(expected.first, callAction.contactQuery)
            assertEquals(expected.second, callAction.selectedSim)
        }
    }

    @Test
    fun testCallingConfirmation() {
        com.spidey.assistant.calling.CallSessionManager.startSession("Guru Anna")
        val candidates = listOf("confirm_call", "yes", "no", "cancel")

        val yesAction = intentEngine.parse("yes", candidates)
        assertTrue(yesAction is CallContactAction)
        assertTrue((yesAction as CallContactAction).isConfirmed)

        val goAheadAction = intentEngine.parse("go ahead", candidates)
        assertTrue(goAheadAction is CallContactAction)
        assertTrue((goAheadAction as CallContactAction).isConfirmed)

        val cancelAction = intentEngine.parse("cancel", candidates)
        assertTrue(cancelAction is CallContactAction)
        assertTrue((cancelAction as CallContactAction).isCancelled)

        val noAction = intentEngine.parse("no", candidates)
        assertTrue(noAction is CallContactAction)
        assertTrue((noAction as CallContactAction).isCancelled)
    }

    @Test
    fun testCallingSimClarification() {
        com.spidey.assistant.calling.CallSessionManager.startSession("Guru Anna")
        val candidates = listOf("sim_choice", "Airtel", "Jio")

        val jioAction = intentEngine.parse("Jio", candidates)
        assertTrue(jioAction is CallContactAction)
        assertEquals("Jio", (jioAction as CallContactAction).selectedSim)
        assertTrue((jioAction as CallContactAction).isConfirmed)

        val secondAction = intentEngine.parse("second", candidates)
        assertTrue(secondAction is CallContactAction)
        assertEquals("Jio", (secondAction as CallContactAction).selectedSim)
        assertTrue((secondAction as CallContactAction).isConfirmed)

        val sim1Action = intentEngine.parse("sim 1", candidates)
        assertTrue(sim1Action is CallContactAction)
        assertEquals("Airtel", (sim1Action as CallContactAction).selectedSim)
        assertTrue((sim1Action as CallContactAction).isConfirmed)

        val simOneAction = intentEngine.parse("sim one", candidates)
        assertTrue(simOneAction is CallContactAction)
        assertEquals("Airtel", (simOneAction as CallContactAction).selectedSim)
        assertTrue((simOneAction as CallContactAction).isConfirmed)
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
        assertTrue(timeAction is TimeAction)

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
            "blah blah blah",
            "xyz 123",
            "random noise 999"
        )

        for (phrase in unknownPhrases) {
            val action = intentEngine.parse(phrase)
            assertNull("Phrase '$phrase' should return null", action)
        }
    }

    /**
     * Task 4: Explicit 30+ phrases test suite asserting deterministic priority:
     * 1. System commands (stop, cancel, unlock my phone, time)
     * 2. Open app always wins over in-app search, even when YouTube is in front
     * 3. In-app commands (play the first one, search <query>)
     * 4. Search queries cleanly strip command words and NEVER begin with open/launch verbs
     * 5. Gibberish returns null
     */
    @Test
    fun testTask4FailingExamplesAndDeterministicRouting() {
        val ytContext = listOf("context:youtube")

        // 1. "open WhatsApp" -> MUST be OpenAppAction, NEVER search
        val openWhatsApp = intentEngine.parse("open WhatsApp", ytContext)
        assertNotNull(openWhatsApp)
        assertTrue("Expected OpenAppAction, got: $openWhatsApp", openWhatsApp is OpenAppAction)
        assertEquals("WhatsApp", (openWhatsApp as OpenAppAction).appName)

        // 2. "go to WhatsApp" -> MUST be OpenAppAction, NEVER search
        val goToWhatsApp = intentEngine.parse("go to WhatsApp", ytContext)
        assertNotNull(goToWhatsApp)
        assertTrue("Expected OpenAppAction, got: $goToWhatsApp", goToWhatsApp is OpenAppAction)
        assertEquals("WhatsApp", (goToWhatsApp as OpenAppAction).appName)

        // 3. "open contacts" -> MUST be OpenAppAction, NEVER search
        val openContacts = intentEngine.parse("open contacts", ytContext)
        assertNotNull(openContacts)
        assertTrue("Expected OpenAppAction, got: $openContacts", openContacts is OpenAppAction)
        assertEquals("Contacts", (openContacts as OpenAppAction).appName)

        // 4. "go to YouTube" -> MUST be OpenAppAction, NEVER search
        val goToYouTube = intentEngine.parse("go to YouTube", ytContext)
        assertNotNull(goToYouTube)
        assertTrue("Expected OpenAppAction, got: $goToYouTube", goToYouTube is OpenAppAction)
        assertEquals("YouTube", (goToYouTube as OpenAppAction).appName)

        // 5. "play Jailer 2 song on YouTube" -> MUST be SearchYouTubeAction with clean query "Jailer 2 song"
        val playJailer = intentEngine.parse("play Jailer 2 song on YouTube")
        assertNotNull(playJailer)
        assertTrue("Expected SearchYouTubeAction, got: $playJailer", playJailer is SearchYouTubeAction)
        assertEquals("jailer 2 song", (playJailer as SearchYouTubeAction).query.lowercase())

        // 6. "search Jailer 2 song" -> MUST be SearchYouTubeAction with clean query "Jailer 2 song"
        val searchJailer = intentEngine.parse("search Jailer 2 song", ytContext)
        assertNotNull(searchJailer)
        assertTrue("Expected SearchYouTubeAction, got: $searchJailer", searchJailer is SearchYouTubeAction)
        assertEquals("jailer 2 song", (searchJailer as SearchYouTubeAction).query.lowercase())

        // 7. "play the first one" -> MUST be PlayFirstResultAction, NEVER search
        val playFirst = intentEngine.parse("play the first one", ytContext + "query:jailer 2 song")
        assertNotNull(playFirst)
        assertTrue("Expected PlayFirstResultAction, got: $playFirst", playFirst is PlayFirstResultAction)
        assertEquals("jailer 2 song", (playFirst as PlayFirstResultAction).query)

        // 8. "unlock my phone" -> MUST be UnlockAction
        val unlock = intentEngine.parse("unlock my phone")
        assertNotNull(unlock)
        assertTrue("Expected UnlockAction, got: $unlock", unlock is UnlockAction)
    }

    /**
     * Task 4: Assert that NO phrase starting with an open/launch verb ever produces a search action.
     * Evaluates across 35 different open commands in both default and YouTube foreground context.
     */
    @Test
    fun testNoOpenVerbPhraseEverProducesSearchAction() {
        val testOpenPhrases = listOf(
            "open whatsapp",
            "go to whatsapp",
            "launch whatsapp",
            "switch to whatsapp",
            "start whatsapp",
            "open contacts",
            "go to contacts",
            "launch contacts",
            "switch to contacts",
            "start contacts",
            "open chrome",
            "go to chrome",
            "launch chrome",
            "switch to chrome",
            "start chrome",
            "open youtube",
            "go to youtube",
            "launch youtube",
            "switch to youtube",
            "start youtube",
            "open settings",
            "go to settings",
            "launch settings",
            "switch to settings",
            "start settings",
            "open camera",
            "go to camera",
            "launch camera",
            "switch to camera",
            "start camera",
            "open telegram",
            "go to telegram",
            "launch telegram",
            "switch to telegram",
            "start telegram",
            "open calculator",
            "go to calculator",
            "launch calculator",
            "switch to calculator",
            "start calculator"
        )

        // Test in YouTube foreground context
        val ytContext = listOf("context:youtube")
        for (phrase in testOpenPhrases) {
            val action = intentEngine.parse(phrase, ytContext)
            assertNotNull("Phrase '$phrase' should produce an action", action)
            assertTrue("Phrase '$phrase' in YouTube context must NEVER produce SearchYouTubeAction, got: $action", action !is SearchYouTubeAction)
            assertTrue("Phrase '$phrase' in YouTube context must produce OpenAppAction, got: $action", action is OpenAppAction)
        }

        // Test in default context
        for (phrase in testOpenPhrases) {
            val action = intentEngine.parse(phrase)
            assertNotNull("Phrase '$phrase' should produce an action", action)
            assertTrue("Phrase '$phrase' in default context must NEVER produce SearchYouTubeAction, got: $action", action !is SearchYouTubeAction)
            assertTrue("Phrase '$phrase' in default context must produce OpenAppAction, got: $action", action is OpenAppAction)
        }
    }

    /**
     * Task 4: In-app commands for YouTube ("play the first one", "play first video", etc.)
     */
    @Test
    fun testPlayFirstResultInAppCommands() {
        val playFirstVariants = listOf(
            "play the first one",
            "play first one",
            "play the first video",
            "play first video",
            "play the first result",
            "play first result",
            "play the first song",
            "play first song",
            "play the 1st one",
            "play 1st one",
            "play the 1st video",
            "play the first",
            "play first"
        )

        val contextWithQuery = listOf("context:youtube", "query:jailer 2 song")
        for (phrase in playFirstVariants) {
            val action = intentEngine.parse(phrase, contextWithQuery)
            assertNotNull("Phrase '$phrase' should produce an action", action)
            assertTrue("Phrase '$phrase' must produce PlayFirstResultAction, got: $action", action is PlayFirstResultAction)
            val playAction = action as PlayFirstResultAction
            assertEquals("jailer 2 song", playAction.query)
            assertEquals("youtube", playAction.targetApp)
        }
    }

    /**
     * Task 4: Clean stripping of command words.
     * Query must never contain command words or start with open/launch verbs.
     */
    @Test
    fun testSearchQueryCommandWordStripping() {
        val stripTestCases = listOf(
            "play Jailer 2 song on YouTube" to "jailer 2 song",
            "search Jailer 2 song" to "jailer 2 song",
            "search for Jailer 2 song" to "jailer 2 song",
            "search for song Jailer 2" to "jailer 2",
            "search on YouTube for Jailer 2 song" to "jailer 2 song",
            "search in YouTube for Jailer 2" to "jailer 2",
            "play believer on youtube" to "believer",
            "search believer on youtube" to "believer",
            "go to search bar and search Jailer 2 trailer" to "jailer 2 trailer",
            "open youtube and search for believer" to "believer",
            "go to youtube and search for infinity war trailer" to "infinity war trailer"
        )

        for ((phrase, expectedQuery) in stripTestCases) {
            val action = intentEngine.parse(phrase)
            assertNotNull("Phrase '$phrase' should produce an action", action)
            assertTrue("Phrase '$phrase' should produce SearchYouTubeAction, got: $action", action is SearchYouTubeAction)
            val ytAction = action as SearchYouTubeAction
            assertEquals(expectedQuery.lowercase(), ytAction.query.lowercase())

            // Assert that the extracted query NEVER begins with any open or launch verb
            val openVerbs = listOf("open", "go to", "launch", "switch to", "start")
            for (verb in openVerbs) {
                assertTrue("Query '${ytAction.query}' must not start with '$verb'", !ytAction.query.lowercase().startsWith(verb))
            }
        }
    }

    /**
     * Task 4: System commands (stop, cancel, unlock, time).
     */
    @Test
    fun testSystemCommandsPriority() {
        val unlockPhrases = listOf(
            "unlock my phone",
            "unlock the phone",
            "unlock phone",
            "unlock",
            "please unlock my phone",
            "unlock device"
        )
        for (phrase in unlockPhrases) {
            val action = intentEngine.parse(phrase)
            assertNotNull("Phrase '$phrase' should produce an action", action)
            assertTrue("Phrase '$phrase' should produce UnlockAction, got: $action", action is UnlockAction)
        }

        val stopPhrases = listOf("stop", "cancel", "sleep", "quit")
        for (phrase in stopPhrases) {
            val action = intentEngine.parse(phrase)
            assertNotNull("Phrase '$phrase' should produce an action", action)
            assertTrue("Phrase '$phrase' should produce StopAction, got: $action", action is StopAction)
        }
    }

    @Test
    fun testGenericPlayRequestsRouteToSearchSong() {
        val genericPhrases = listOf(
            "play",
            "play song",
            "play a song",
            "play the song",
            "play some song",
            "play music",
            "play some music",
            "play songs"
        )
        for (phrase in genericPhrases) {
            val action = intentEngine.parse(phrase)
            assertNotNull("Phrase '$phrase' should produce an action", action)
            assertTrue("Phrase '$phrase' should produce SearchYouTubeAction, got: $action", action is SearchYouTubeAction)
            val ytAction = action as SearchYouTubeAction
            assertEquals("song", ytAction.query)
            assertTrue(ytAction.isPlayDirectly)
        }
    }

    @Test
    fun testMusicClarificationResolution() {
        val candidates = listOf("music_query_choice")

        // 1. User answers with direct song name
        val directSong = intentEngine.parse("believer", candidates)
        assertTrue(directSong is SearchYouTubeAction)
        assertEquals("believer", (directSong as SearchYouTubeAction).query)
        assertTrue(directSong.isPlayDirectly)

        // 2. User answers with "play <song>"
        val playSong = intentEngine.parse("play Shape of You", candidates)
        assertTrue(playSong is SearchYouTubeAction)
        assertEquals("shape of you", (playSong as SearchYouTubeAction).query.lowercase())
        assertTrue(playSong.isPlayDirectly)

        // 3. User cancels
        val cancelAction = intentEngine.parse("stop", candidates)
        assertTrue(cancelAction is StopAction)
    }

    @Test
    fun testConsecutiveChangeSongOrdinalIncrementation() {
        // Initial state: song playing at index 1
        val context1 = listOf("context:youtube", "query:believer", "index:1")
        val action1 = intentEngine.parse("change the song", context1)
        assertTrue(action1 is PlayFirstResultAction)
        val playAction1 = action1 as PlayFirstResultAction
        assertEquals("believer", playAction1.query)
        assertEquals(2, playAction1.itemIndex)

        // 2nd change: index now 2 -> should increment to 3
        val context2 = listOf("context:youtube", "query:believer", "index:2")
        val action2 = intentEngine.parse("change the song", context2)
        assertTrue(action2 is PlayFirstResultAction)
        val playAction2 = action2 as PlayFirstResultAction
        assertEquals("believer", playAction2.query)
        assertEquals(3, playAction2.itemIndex)

        // 3rd change: index now 3 -> should increment to 4
        val context3 = listOf("context:youtube", "query:believer", "index:3")
        val action3 = intentEngine.parse("next song", context3)
        assertTrue(action3 is PlayFirstResultAction)
        val playAction3 = action3 as PlayFirstResultAction
        assertEquals("believer", playAction3.query)
        assertEquals(4, playAction3.itemIndex)
    }

    // =========================================================================
    // TASK J5 TESTS: Issue #1 Fix & Context Routing across Skills (60+ phrases)
    // =========================================================================

    @Test
    fun testIssue1PlaySpotifyOpensSpotifyNotYouTube() {
        // Issue #1: "play spotify" must open Spotify, NOT search YouTube!
        val action = intentEngine.parse("play spotify")
        assertNotNull(action)
        assertTrue("Expected OpenAppAction but got $action", action is OpenAppAction)
        val openApp = action as OpenAppAction
        assertEquals("Spotify", openApp.appName)
        assertEquals("com.spotify.music", openApp.packageName)
    }

    @Test
    fun testExplicitAppInPhraseRouting() {
        val testCases = listOf(
            // Spotify phrases (8)
            "play believer on spotify" to "believer",
            "search believer on spotify" to "believer",
            "listen to believer on spotify" to "believer",
            "play song starboy on spotify" to "starboy",
            "play the song shape of you on spotify" to "shape of you",
            "spotify play counting stars" to "counting stars",
            "play on spotify memories" to "memories",
            "play taylor swift in spotify" to "taylor swift"
        )
        for ((phrase, expectedQuery) in testCases) {
            val action = intentEngine.parse(phrase)
            assertNotNull("Phrase '$phrase' should produce action", action)
            assertTrue("Phrase '$phrase' should produce PlaySpotifyAction, got $action", action is PlaySpotifyAction)
            assertEquals(expectedQuery, (action as PlaySpotifyAction).query)
        }

        val ytCases = listOf(
            // YouTube phrases (6)
            "play believer on youtube" to ("believer" to true),
            "search believer on youtube" to ("believer" to false),
            "search trailer on youtube" to ("trailer" to false),
            "play on youtube despacito" to ("despacito" to true),
            "open youtube and play believer" to ("believer" to true),
            "open youtube and search for trailer" to ("trailer" to false)
        )
        for ((phrase, expected) in ytCases) {
            val action = intentEngine.parse(phrase)
            assertNotNull("Phrase '$phrase' should produce action", action)
            assertTrue("Phrase '$phrase' should produce SearchYouTubeAction, got $action", action is SearchYouTubeAction)
            val yt = action as SearchYouTubeAction
            assertEquals(expected.first, yt.query)
            assertEquals(expected.second, yt.isPlayDirectly)
        }

        val waCases = listOf(
            // WhatsApp phrases (6)
            "call mom on whatsapp" to "mom",
            "call dad via whatsapp" to "dad",
            "whatsapp call alex" to "alex",
            "call on whatsapp sarah" to "sarah",
            "call vaseem on whatsapp" to "vaseem",
            "voice call john on whatsapp" to "john"
        )
        for ((phrase, expectedTarget) in waCases) {
            val action = intentEngine.parse(phrase)
            assertNotNull("Phrase '$phrase' should produce action", action)
            assertTrue("Phrase '$phrase' should produce WhatsAppCallAction, got $action", action is WhatsAppCallAction)
            assertEquals(expectedTarget, (action as WhatsAppCallAction).contactQuery)
        }

        val phoneCases = listOf(
            // Cellular Phone SIM phrases (5)
            "call mom on sim 1" to ("mom" to "sim 1"),
            "call dad on sim 2" to ("dad" to "sim 2"),
            "call alex on airtel" to ("alex" to "airtel"),
            "call sarah on jio" to ("sarah" to "jio"),
            "call vaseem using sim 1" to ("vaseem" to "sim 1")
        )
        for ((phrase, expected) in phoneCases) {
            val action = intentEngine.parse(phrase)
            assertNotNull("Phrase '$phrase' should produce action", action)
            assertTrue("Phrase '$phrase' should produce CallContactAction, got $action", action is CallContactAction)
            val call = action as CallContactAction
            assertEquals(expected.first, call.contactQuery)
            assertEquals(expected.second, call.selectedSim)
        }
    }

    @Test
    fun testOpenAppVsGenericPlayRouting() {
        val appPhrases = listOf(
            "play spotify" to "Spotify",
            "play on spotify" to "Spotify",
            "play youtube" to "YouTube",
            "open spotify" to "Spotify",
            "launch spotify" to "Spotify",
            "start spotify" to "Spotify",
            "go to spotify" to "Spotify",
            "open whatsapp" to "WhatsApp",
            "launch whatsapp" to "WhatsApp",
            "open camera" to "Camera",
            "open chrome" to "Chrome",
            "launch chrome" to "Chrome",
            "open settings" to "Settings",
            "open contacts" to "Contacts",
            "open calculator" to "Calculator"
        )
        for ((phrase, expectedAppName) in appPhrases) {
            val action = intentEngine.parse(phrase)
            assertNotNull("Phrase '$phrase' should produce action", action)
            assertTrue("Phrase '$phrase' should produce OpenAppAction, got $action", action is OpenAppAction)
            assertEquals(expectedAppName.lowercase(), (action as OpenAppAction).appName.lowercase())
        }
    }

    @Test
    fun testSpotifyContextInAppRouting() {
        val spotifyContext = listOf("context:spotify", "query:believer")

        // 1. Change / Next commands inside Spotify
        val changePhrases = listOf(
            "change song",
            "change the song",
            "next song",
            "play next song",
            "skip",
            "skip song",
            "different song",
            "another song"
        )
        for (phrase in changePhrases) {
            val action = intentEngine.parse(phrase, spotifyContext)
            assertNotNull("Phrase '$phrase' in Spotify context should produce action", action)
            assertTrue("Expected PlaySpotifyAction for '$phrase', got $action", action is PlaySpotifyAction)
            assertEquals("next", (action as PlaySpotifyAction).query)
        }

        // 2. Play first result inside Spotify
        val firstAction = intentEngine.parse("play the first one", spotifyContext)
        assertTrue(firstAction is PlaySpotifyAction)
        assertEquals("first", (firstAction as PlaySpotifyAction).query)

        // 3. Play new query inside Spotify
        val songAction = intentEngine.parse("play starboy", spotifyContext)
        assertTrue(songAction is PlaySpotifyAction)
        assertEquals("starboy", (songAction as PlaySpotifyAction).query)

        // 4. Resume inside Spotify
        val resumeAction = intentEngine.parse("resume", spotifyContext)
        assertTrue(resumeAction is PlaySpotifyAction)
        assertEquals("resume", (resumeAction as PlaySpotifyAction).query)
    }

    @Test
    fun testYouTubeContextInAppRouting() {
        val ytContext = listOf("context:youtube", "query:believer", "index:1")

        // 1. Change song inside YouTube increments ordinal
        val action1 = intentEngine.parse("change song", ytContext)
        assertTrue(action1 is PlayFirstResultAction)
        assertEquals(2, (action1 as PlayFirstResultAction).itemIndex)

        // 2. Play ordinal directly
        val action2 = intentEngine.parse("play the third video", ytContext)
        assertTrue(action2 is PlayFirstResultAction)
        assertEquals(3, (action2 as PlayFirstResultAction).itemIndex)

        // 3. Search inside YouTube
        val action3 = intentEngine.parse("search for avengers trailer", ytContext)
        assertTrue(action3 is SearchYouTubeAction)
        assertEquals("avengers trailer", (action3 as SearchYouTubeAction).query)
    }

    @Test
    fun testWhatsAppContextInAppRouting() {
        val waContext = listOf("context:whatsapp")

        // 1. Call commands in WhatsApp context route to WhatsAppCallAction directly
        val callCases = listOf(
            "call mom" to "mom",
            "call dad" to "dad",
            "call vaseem" to "vaseem",
            "voice call john" to "john",
            "dial alex" to "alex"
        )
        for ((phrase, expected) in callCases) {
            val action = intentEngine.parse(phrase, waContext)
            assertNotNull(action)
            assertTrue("Expected WhatsAppCallAction in WhatsApp context for '$phrase', got $action", action is WhatsAppCallAction)
            assertEquals(expected, (action as WhatsAppCallAction).contactQuery)
        }

        // 2. In-app media command in WhatsApp context: strictly NO cross-app fallback to YouTube!
        val mediaPhrases = listOf(
            "change song",
            "change the song",
            "next song",
            "play next song",
            "play believer",
            "play some song"
        )
        for (phrase in mediaPhrases) {
            val action = intentEngine.parse(phrase, waContext)
            assertNotNull("Phrase '$phrase' should produce InAppUnsupportedAction in WhatsApp context", action)
            assertTrue("Expected InAppUnsupportedAction, got $action", action is InAppUnsupportedAction)
            val unsupp = action as InAppUnsupportedAction
            assertEquals("WhatsApp", unsupp.appName)
            assertEquals("media playback", unsupp.command)
        }
    }

    @Test
    fun testOtherAppContextNoCrossAppFallback() {
        val chromeContext = listOf("context:chrome")
        val settingsContext = listOf("context:settings")

        val mediaPhrases = listOf(
            "change song",
            "next song",
            "play believer"
        )
        for (phrase in mediaPhrases) {
            val actChrome = intentEngine.parse(phrase, chromeContext)
            assertTrue("Expected InAppUnsupportedAction in Chrome context for '$phrase', got $actChrome", actChrome is InAppUnsupportedAction)
            assertEquals("Chrome", (actChrome as InAppUnsupportedAction).appName)

            val actSettings = intentEngine.parse(phrase, settingsContext)
            assertTrue("Expected InAppUnsupportedAction in Settings context for '$phrase', got $actSettings", actSettings is InAppUnsupportedAction)
            assertEquals("Settings", (actSettings as InAppUnsupportedAction).appName)
        }
    }

    @Test
    fun testWhatsAppMessagePhrases() {
        val action1 = intentEngine.parse("send message to Amma on whatsapp saying I will be home by 9")
        assertTrue("Expected SendMessageAction, got $action1", action1 is com.spidey.assistant.actions.SendMessageAction)
        val msg1 = action1 as com.spidey.assistant.actions.SendMessageAction
        assertEquals("amma", msg1.recipient)
        assertEquals("i will be home by 9", msg1.body)
        assertEquals(com.spidey.assistant.actions.MessagePlatform.WHATSAPP, msg1.platform)

        val action2 = intentEngine.parse("message Vaseem on whatsapp hello")
        assertTrue(action2 is com.spidey.assistant.actions.SendMessageAction)
        val msg2 = action2 as com.spidey.assistant.actions.SendMessageAction
        assertEquals("vaseem", msg2.recipient)
        assertEquals("hello", msg2.body)

        val action3 = intentEngine.parse("send whatsapp message to John see you soon")
        assertTrue(action3 is com.spidey.assistant.actions.SendMessageAction)
        val msg3 = action3 as com.spidey.assistant.actions.SendMessageAction
        assertEquals("john", msg3.recipient)
        assertEquals("see you soon", msg3.body)
    }

    @Test
    fun testEmailMessagePhrasesWithNormalization() {
        val action = intentEngine.parse("send email to vikki at gmail dot com saying hello world")
        assertTrue("Expected SendMessageAction, got $action", action is com.spidey.assistant.actions.SendMessageAction)
        val msg = action as com.spidey.assistant.actions.SendMessageAction
        assertEquals("vikki@gmail.com", msg.recipient)
        assertEquals("hello world", msg.body)
        assertEquals(com.spidey.assistant.actions.MessagePlatform.EMAIL, msg.platform)
    }

    @Test
    fun testSmsMessagePhrases() {
        val action = intentEngine.parse("send sms to Amma saying on my way")
        assertTrue(action is com.spidey.assistant.actions.SendMessageAction)
        val msg = action as com.spidey.assistant.actions.SendMessageAction
        assertEquals("amma", msg.recipient)
        assertEquals("on my way", msg.body)
        assertEquals(com.spidey.assistant.actions.MessagePlatform.SMS, msg.platform)
    }

    @Test
    fun testMessageClarificationConfirmationAndCancellation() {
        val confirmContext = listOf("confirm_message", "recipient:Amma", "body:I will be there at 9", "platform:WHATSAPP")

        val yesAction = intentEngine.parse("send it", confirmContext)
        assertTrue(yesAction is com.spidey.assistant.actions.SendMessageAction)
        assertTrue((yesAction as com.spidey.assistant.actions.SendMessageAction).isConfirmed)

        val cancelAction = intentEngine.parse("cancel", confirmContext)
        assertTrue(cancelAction is com.spidey.assistant.actions.SendMessageAction)
        assertTrue((cancelAction as com.spidey.assistant.actions.SendMessageAction).isCancelled)

        val editAction = intentEngine.parse("change 9 to 10", confirmContext)
        assertTrue(editAction is com.spidey.assistant.actions.SendMessageAction)
        val edited = editAction as com.spidey.assistant.actions.SendMessageAction
        assertFalse(edited.isConfirmed)
        assertEquals("I will be there at 10", edited.body)

        // Variations of quick edit: "change 5 to 6", "change 5 2 6", "change five to six", "no change 5 to 6"
        val meetContext = listOf("confirm_message", "recipient:Amma", "body:meet me at 5", "platform:WHATSAPP")

        val edit5to6 = intentEngine.parse("change 5 to 6", meetContext) as com.spidey.assistant.actions.SendMessageAction
        assertEquals("meet me at 6", edit5to6.body)

        val edit526 = intentEngine.parse("change 5 2 6", meetContext) as com.spidey.assistant.actions.SendMessageAction
        assertEquals("meet me at 6", edit526.body)

        val editWords = intentEngine.parse("change five to six", meetContext) as com.spidey.assistant.actions.SendMessageAction
        assertEquals("meet me at 6", editWords.body)

        val editWithNo = intentEngine.parse("no change 5 to 6", meetContext) as com.spidey.assistant.actions.SendMessageAction
        assertFalse(editWithNo.isCancelled)
        assertEquals("meet me at 6", editWithNo.body)

        val editMakeIt = intentEngine.parse("make it 6", meetContext) as com.spidey.assistant.actions.SendMessageAction
        assertEquals("meet me at 6", editMakeIt.body)

        val dictateContext = listOf("dictate_message", "recipient:Amma", "platform:WHATSAPP")
        val dictateAction = intentEngine.parse("Please call me back", dictateContext)
        assertTrue(dictateAction is com.spidey.assistant.actions.SendMessageAction)
        val dictated = dictateAction as com.spidey.assistant.actions.SendMessageAction
        assertFalse(dictated.isConfirmed)
        assertEquals("please call me back", dictated.body)
    }

    @Test
    fun testEmailParsingVariations() {
        val a1 = intentEngine.parse("send an email to vikki@gmail.com") as com.spidey.assistant.actions.SendMessageAction
        assertEquals("vikki@gmail.com", a1.recipient)
        assertEquals("", a1.body)
        assertEquals(com.spidey.assistant.actions.MessagePlatform.EMAIL, a1.platform)

        val a2 = intentEngine.parse("send a email to vikki@gmail.com saying meet me at 5") as com.spidey.assistant.actions.SendMessageAction
        assertEquals("vikki@gmail.com", a2.recipient)
        assertEquals("meet me at 5", a2.body)

        val a3 = intentEngine.parse("send email to vikki at gmail dot com") as com.spidey.assistant.actions.SendMessageAction
        assertEquals("vikki@gmail.com", a3.recipient)
        assertEquals("", a3.body)

        val a4 = intentEngine.parse("send message to Amma saying hello") as com.spidey.assistant.actions.SendMessageAction
        assertEquals("amma", a4.recipient)
        assertEquals("hello", a4.body)
        assertEquals(com.spidey.assistant.actions.MessagePlatform.WHATSAPP, a4.platform)
    }
}
