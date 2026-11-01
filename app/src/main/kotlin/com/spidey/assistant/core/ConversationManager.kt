package com.spidey.assistant.core

import com.spidey.assistant.actions.AssistantAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single authoritative, thread-safe state manager for SPIDEY.
 * Coordinates transitions between [AssistantState]s and manages the interaction loop.
 */
class ConversationManager(
    private val actionRouter: ActionRouter,
    private val intentEngine: IntentEngine,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job()),
    private val speak: (text: String, onDone: () -> Unit) -> Unit = { _, onDone -> onDone() },
    private val playPromptSound: () -> Unit = {},
    private val onWakeWordStateRequested: (enable: Boolean) -> Unit = {},
    private val onStartSpeechListeningRequested: () -> Unit = {},
    private val onStopSpeechListeningRequested: () -> Unit = {},
    private val onUnlockRequested: (pendingAction: AssistantAction) -> Unit = {}
) {
    private val mutex = Mutex()

    private val _state = MutableStateFlow(AssistantState.SLEEPING)
    val state: StateFlow<AssistantState> = _state.asStateFlow()

    private val _lastHeardText = MutableStateFlow("")
    val lastHeardText: StateFlow<String> = _lastHeardText.asStateFlow()

    private val _lastSpokenResponse = MutableStateFlow("")
    val lastSpokenResponse: StateFlow<String> = _lastSpokenResponse.asStateFlow()

    private val _errorText = MutableStateFlow<String?>(null)
    val errorText: StateFlow<String?> = _errorText.asStateFlow()

    private var emptySpeechAttempts = 0
    private var pendingActionAfterUnlock: AssistantAction? = null
    private var activeClarificationCandidates: List<String> = emptyList()

    /**
     * Called when the wake word is detected ("Hey Spider" / "Spider").
     */
    fun onWakeWordDetected() {
        scope.launch {
            mutex.withLock {
                if (_state.value == AssistantState.LISTENING ||
                    _state.value == AssistantState.PROCESSING ||
                    _state.value == AssistantState.SPEAKING
                ) {
                    return@withLock
                }

                emptySpeechAttempts = 0
                _errorText.value = null
                _state.value = AssistantState.LISTENING

                // 1. Explicitly pause wake word to release mic
                onWakeWordStateRequested(false)

                // 2. Play acoustic prompt cue (beep or tone)
                playPromptSound()

                // Short delay to ensure mic hardware finishes releasing and prompt tone sounds
                kotlinx.coroutines.delay(200)

                // 3. Start bounded speech recognition
                onStartSpeechListeningRequested()
            }
        }
    }

    /**
     * Called when the speech recognizer returns final recognized text.
     */
    fun onSpeechRecognized(text: String) {
        scope.launch {
            mutex.withLock {
                if (_state.value != AssistantState.LISTENING) return@withLock

                val trimmed = text.trim()
                _lastHeardText.value = trimmed

                if (trimmed.isBlank()) {
                    handleEmptySpeech()
                    return@withLock
                }

                _state.value = AssistantState.PROCESSING

                val candidates = activeClarificationCandidates
                activeClarificationCandidates = emptyList()

                val action = intentEngine.parse(trimmed, candidates)
                if (action != null) {
                    executeAction(action)
                } else {
                    speakResponse(
                        text = "I didn't understand that.",
                        andThen = { returnToSleeping() }
                    )
                }
            }
        }
    }

    /**
     * Called when speech recognition captures no speech or times out.
     */
    fun onSpeechEmpty() {
        scope.launch {
            mutex.withLock {
                if (_state.value != AssistantState.LISTENING) return@withLock
                handleEmptySpeech()
            }
        }
    }

    /**
     * Called when speech recognition encounters an error.
     */
    fun onSpeechError(errorCode: Int, errorMessage: String) {
        scope.launch {
            mutex.withLock {
                if (_state.value != AssistantState.LISTENING) return@withLock

                // Error code 7 = ERROR_NO_MATCH, code 6 = ERROR_SPEECH_TIMEOUT
                if (errorCode == 7 || errorCode == 6) {
                    handleEmptySpeech()
                } else {
                    _errorText.value = "Speech recognition error: $errorMessage ($errorCode)"
                    returnToSleeping()
                }
            }
        }
    }

    /**
     * Called when device is unlocked to resume a pending action.
     */
    fun onDeviceUnlocked() {
        scope.launch {
            mutex.withLock {
                val action = pendingActionAfterUnlock ?: return@withLock
                pendingActionAfterUnlock = null
                if (_state.value == AssistantState.AWAITING_UNLOCK) {
                    executeAction(action)
                }
            }
        }
    }

    /**
     * User explicitly pressed Stop / Cancel.
     */
    fun stop() {
        scope.launch {
            mutex.withLock {
                pendingActionAfterUnlock = null
                onStopSpeechListeningRequested()
                returnToSleeping()
            }
        }
    }

    private suspend fun executeAction(action: AssistantAction) {
        _state.value = AssistantState.EXECUTING
        when (val result = actionRouter.route(action)) {
            is ActionResult.Success -> {
                speakResponse(result.spokenMessage) {
                    returnToSleeping()
                }
            }
            is ActionResult.NeedsUnlock -> {
                pendingActionAfterUnlock = action
                speakResponse("Please unlock your phone to continue.") {
                    _state.value = AssistantState.AWAITING_UNLOCK
                    onUnlockRequested(action)
                }
            }
            is ActionResult.Failure -> {
                _errorText.value = result.reason
                speakResponse(result.spokenMessage) {
                    returnToSleeping()
                }
            }
            is ActionResult.NeedsClarification -> {
                activeClarificationCandidates = result.candidates
                speakResponse(result.question) {
                    // Re-enter listening for the user's answer
                    _state.value = AssistantState.LISTENING
                    onStartSpeechListeningRequested()
                }
            }
            is ActionResult.Cancelled -> {
                returnToSleeping()
            }
        }
    }

    private fun handleEmptySpeech() {
        if (emptySpeechAttempts == 0) {
            emptySpeechAttempts++
            speakResponse("I'm listening...") {
                _state.value = AssistantState.LISTENING
                onStartSpeechListeningRequested()
            }
        } else {
            returnToSleeping()
        }
    }

    private fun speakResponse(text: String, andThen: () -> Unit) {
        _lastSpokenResponse.value = text
        _state.value = AssistantState.SPEAKING
        // Wake engine must stay disabled while speaking
        onWakeWordStateRequested(false)

        speak(text) {
            scope.launch {
                mutex.withLock {
                    andThen()
                }
            }
        }
    }

    private fun returnToSleeping() {
        activeClarificationCandidates = emptyList()
        _state.value = AssistantState.SLEEPING
        // Re-arm wake word spotter
        onWakeWordStateRequested(true)
    }
}
