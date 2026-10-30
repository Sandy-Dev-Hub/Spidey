package com.spidey.assistant

import com.spidey.assistant.actions.AllowedApp
import com.spidey.assistant.actions.GreetingAction
import com.spidey.assistant.actions.GreetingHandler
import com.spidey.assistant.actions.OpenAppAction
import com.spidey.assistant.core.ActionHandler
import com.spidey.assistant.core.ActionResult
import com.spidey.assistant.core.ActionRouter
import com.spidey.assistant.actions.AssistantAction
import com.spidey.assistant.core.AssistantState
import com.spidey.assistant.core.ConversationManager
import com.spidey.assistant.core.IntentEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationManagerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var router: ActionRouter
    private lateinit var intentEngine: IntentEngine
    private lateinit var conversationManager: ConversationManager

    private val spokenMessages = mutableListOf<String>()
    private var wakeWordState = true
    private var isSpeechListening = false
    private var unlockRequestedAction: AssistantAction? = null

    @Before
    fun setUp() {
        router = ActionRouter()
        intentEngine = IntentEngine()
        spokenMessages.clear()
        wakeWordState = true
        isSpeechListening = false
        unlockRequestedAction = null

        router.register(GreetingHandler(greetingResponse = "Hello! Spidey here."))

        conversationManager = ConversationManager(
            actionRouter = router,
            intentEngine = intentEngine,
            scope = testScope,
            speak = { text, onDone ->
                spokenMessages.add(text)
                onDone()
            },
            playPromptSound = {},
            onWakeWordStateRequested = { enabled ->
                wakeWordState = enabled
            },
            onStartSpeechListeningRequested = {
                isSpeechListening = true
            },
            onStopSpeechListeningRequested = {
                isSpeechListening = false
            },
            onUnlockRequested = { pending ->
                unlockRequestedAction = pending
            }
        )
    }

    @Test
    fun testInitialStateIsSleeping() {
        assertEquals(AssistantState.SLEEPING, conversationManager.state.value)
    }

    @Test
    fun testWakeWordTriggerTransitionsToListeningAndPausesWakeEngine() = testScope.runTest {
        conversationManager.onWakeWordDetected()
        advanceUntilIdle()

        assertEquals(AssistantState.LISTENING, conversationManager.state.value)
        assertEquals(false, wakeWordState) // Wake engine paused to free mic
        assertEquals(true, isSpeechListening) // SpeechRecognizer started
    }

    @Test
    fun testGreetingFlowCompletesSuccessfully() = testScope.runTest {
        conversationManager.onWakeWordDetected()
        advanceUntilIdle()

        conversationManager.onSpeechRecognized("hello")
        advanceUntilIdle()

        assertEquals(listOf("Hello! Spidey here."), spokenMessages)
        assertEquals("hello", conversationManager.lastHeardText.value)
        assertEquals("Hello! Spidey here.", conversationManager.lastSpokenResponse.value)
        assertEquals(AssistantState.SLEEPING, conversationManager.state.value)
        assertEquals(true, wakeWordState) // Wake word re-armed
    }

    @Test
    fun testEmptySpeechRepromptsOnceThenSleeps() = testScope.runTest {
        conversationManager.onWakeWordDetected()
        advanceUntilIdle()

        // 1st empty speech
        conversationManager.onSpeechEmpty()
        advanceUntilIdle()

        assertEquals(listOf("I'm listening..."), spokenMessages)
        assertEquals(AssistantState.LISTENING, conversationManager.state.value)

        // 2nd empty speech
        conversationManager.onSpeechEmpty()
        advanceUntilIdle()

        assertEquals(AssistantState.SLEEPING, conversationManager.state.value)
        assertEquals(true, wakeWordState)
    }

    @Test
    fun testUnrecognizedSpeechRespondsPolitelyAndSleeps() = testScope.runTest {
        conversationManager.onWakeWordDetected()
        advanceUntilIdle()

        conversationManager.onSpeechRecognized("blah blah gibberish xyz")
        advanceUntilIdle()

        assertEquals(listOf("I didn't understand that."), spokenMessages)
        assertEquals(AssistantState.SLEEPING, conversationManager.state.value)
        assertEquals(true, wakeWordState)
    }

    @Test
    fun testLockedDeviceRequestsUnlockThenResumesOnUnlock() = testScope.runTest {
        var isLocked = true
        router.register(object : ActionHandler<OpenAppAction> {
            override val actionType = OpenAppAction::class.java
            override suspend fun handle(action: OpenAppAction): ActionResult {
                return if (isLocked) ActionResult.NeedsUnlock else ActionResult.Success("Opened YouTube")
            }
        })

        conversationManager.onWakeWordDetected()
        advanceUntilIdle()

        conversationManager.onSpeechRecognized("open youtube")
        advanceUntilIdle()

        assertEquals(AssistantState.AWAITING_UNLOCK, conversationManager.state.value)
        assertTrue(unlockRequestedAction is OpenAppAction)

        // Simulate device unlock
        isLocked = false
        conversationManager.onDeviceUnlocked()
        advanceUntilIdle()

        assertEquals(AssistantState.SLEEPING, conversationManager.state.value)
        assertTrue(spokenMessages.contains("Opened YouTube"))
    }

    @Test
    fun testStopCancelsAndReturnsToSleeping() = testScope.runTest {
        conversationManager.onWakeWordDetected()
        advanceUntilIdle()
        assertEquals(AssistantState.LISTENING, conversationManager.state.value)

        conversationManager.stop()
        advanceUntilIdle()

        assertEquals(AssistantState.SLEEPING, conversationManager.state.value)
        assertEquals(false, isSpeechListening)
        assertEquals(true, wakeWordState)
    }
}
