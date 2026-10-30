package com.spidey.assistant.core

import com.spidey.assistant.actions.AssistantAction
import com.spidey.assistant.actions.StopAction
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
    private val onUnlockRequested: (pendingAction: AssistantAction) -> Unit = {},
    private val followUpTimeoutMs: Long = 0L
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

    private var isSessionActive = false
    private var sessionTimeoutJob: Job? = null
    private var lastOpenedApp: String? = null

    private fun resetSessionTimeout() {
        if (followUpTimeoutMs <= 0L) return
        sessionTimeoutJob?.cancel()
        sessionTimeoutJob = scope.launch {
            kotlinx.coroutines.delay(followUpTimeoutMs)
            mutex.withLock {
                if (isSessionActive) {
                    endSessionAndSleep()
                }
            }
        }
    }

    private fun endSessionAndSleep() {
        isSessionActive = false
        lastOpenedApp = null
        sessionTimeoutJob?.cancel()
        sessionTimeoutJob = null
        onStopSpeechListeningRequested()
        returnToSleeping()
    }

    /**
     * Called when the wake word is detected ("Hey Spider" / "Spider").
     */
    fun onWakeWordDetected() {
        scope.launch {
            mutex.withLock {
                if (_state.value == AssistantState.PROCESSING ||
                    _state.value == AssistantState.SPEAKING
                ) {
                    return@withLock
                }

                if (followUpTimeoutMs > 0L) {
                    isSessionActive = true
                    resetSessionTimeout()
                } else {
                    isSessionActive = false
                }

                emptySpeechAttempts = 0
                _errorText.value = null
                _state.value = AssistantState.LISTENING

                // 1. Explicitly pause wake word to release mic
                onWakeWordStateRequested(false)

                // 2. Play acoustic prompt cue (beep or tone)
                playPromptSound()

                // Delay to ensure mic hardware finishes releasing and prompt tone sounds
                kotlinx.coroutines.delay(350)

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

                resetSessionTimeout()
                _state.value = AssistantState.PROCESSING

                val baseCandidates = activeClarificationCandidates
                activeClarificationCandidates = emptyList()
                val candidates = when {
                    lastOpenedApp == "youtube" -> baseCandidates + "context:youtube"
                    lastOpenedApp != null -> baseCandidates + "context:$lastOpenedApp"
                    else -> baseCandidates
                }

                val action = intentEngine.parse(trimmed, candidates)
                if (action != null) {
                    executeAction(action)
                } else {
                    speakResponse(
                        text = "I didn't understand that.",
                        andThen = {
                            if (isSessionActive) {
                                enterFollowUpListening()
                            } else {
                                returnToSleeping()
                            }
                        }
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
                if (isSessionActive) {
                    // In continuous conversation window: seamlessly re-arm speech recognition
                    scope.launch {
                        kotlinx.coroutines.delay(250)
                        mutex.withLock {
                            if (isSessionActive && _state.value == AssistantState.LISTENING) {
                                onStartSpeechListeningRequested()
                            }
                        }
                    }
                } else {
                    handleEmptySpeech()
                }
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

                val isTransientOrSilence = errorCode in setOf(1, 2, 3, 5, 6, 7, 8, 11)

                if (isSessionActive) {
                    // Seamlessly re-arm speech recognition in follow-up mode
                    scope.launch {
                        kotlinx.coroutines.delay(350)
                        mutex.withLock {
                            if (isSessionActive && _state.value == AssistantState.LISTENING) {
                                onStartSpeechListeningRequested()
                            }
                        }
                    }
                } else {
                    if (isTransientOrSilence && emptySpeechAttempts == 0) {
                        handleEmptySpeech()
                    } else {
                        if (!isTransientOrSilence) {
                            _errorText.value = "Speech recognition error: $errorMessage ($errorCode)"
                        }
                        returnToSleeping()
                    }
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
                endSessionAndSleep()
            }
        }
    }

    private suspend fun executeAction(action: AssistantAction) {
        if (action is com.spidey.assistant.actions.OpenAppAction) {
            lastOpenedApp = if (action.appName.contains("youtube", ignoreCase = true) || (action.packageName?.contains("youtube", ignoreCase = true) == true)) {
                "youtube"
            } else {
                action.appName.lowercase()
            }
        } else if (action is com.spidey.assistant.actions.SearchYouTubeAction) {
            lastOpenedApp = "youtube"
        }

        _state.value = AssistantState.EXECUTING
        when (val result = actionRouter.route(action)) {
            is ActionResult.Success -> {
                speakResponse(result.spokenMessage) {
                    if (action is StopAction) {
                        endSessionAndSleep()
                    } else if (isSessionActive) {
                        enterFollowUpListening()
                    } else {
                        returnToSleeping()
                    }
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
                    if (isSessionActive) {
                        enterFollowUpListening()
                    } else {
                        returnToSleeping()
                    }
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
                endSessionAndSleep()
            }
        }
    }

    private fun enterFollowUpListening() {
        emptySpeechAttempts = 0
        _state.value = AssistantState.LISTENING
        onWakeWordStateRequested(false)
        scope.launch {
            kotlinx.coroutines.delay(200)
            mutex.withLock {
                if (isSessionActive && _state.value == AssistantState.LISTENING) {
                    onStartSpeechListeningRequested()
                }
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
        emptySpeechAttempts = 0
        lastOpenedApp = null
        _state.value = AssistantState.SLEEPING
        _errorText.value = null
        // Re-arm wake word spotter
        onWakeWordStateRequested(true)
    }
}
