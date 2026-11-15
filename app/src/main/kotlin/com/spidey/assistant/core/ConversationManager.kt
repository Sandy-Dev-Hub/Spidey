package com.spidey.assistant.core

import com.spidey.assistant.actions.AssistantAction
import com.spidey.assistant.actions.StopAction
import com.spidey.assistant.actions.UnlockAction
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
    private var lastSearchQuery: String? = null
    private var currentSongIndex: Int = 1

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
        returnToSleeping(keepSession = false)
    }

    /**
     * Called when the wake word is detected ("Hey Spider" / "Spider").
     * If an inline command was spoken in the same breath (e.g. "hey spider unlock my phone"),
     * it can execute immediately without waiting for a re-prompt.
     */
    fun onWakeWordDetected(inlineCommand: String? = null) {
        scope.launch {
            mutex.withLock {
                if (_state.value == AssistantState.PROCESSING ||
                    _state.value == AssistantState.SPEAKING
                ) {
                    return@withLock
                }

                val isNewSession = !isSessionActive
                if (followUpTimeoutMs > 0L) {
                    isSessionActive = true
                    resetSessionTimeout()
                } else {
                    isSessionActive = false
                }

                emptySpeechAttempts = 0
                _errorText.value = null

                // 1. Explicitly pause wake word to release mic
                onWakeWordStateRequested(false)

                // If user spoke the command in the same breath, try executing it immediately
                if (!inlineCommand.isNullOrBlank()) {
                    val baseCandidates = activeClarificationCandidates
                    activeClarificationCandidates = emptyList()
                    val candidates = mutableListOf<String>()
                    candidates.addAll(baseCandidates)
                    if (lastOpenedApp != null) {
                        candidates.add(if (lastOpenedApp == "youtube") "context:youtube" else "context:$lastOpenedApp")
                    }
                    if (!lastSearchQuery.isNullOrBlank()) {
                        candidates.add("query:$lastSearchQuery")
                    }
                    val action = intentEngine.parse(inlineCommand.trim(), candidates)
                    if (action != null) {
                        _lastHeardText.value = inlineCommand.trim()
                        executeAction(action)
                        return@withLock
                    }
                }

                // If followUpTimeoutMs is enabled (active session mode), greet with bro greeting on fresh wake
                if (followUpTimeoutMs > 0L) {
                    if (isNewSession) {
                        val greeting = com.spidey.assistant.personality.SpideyPersona.randomGreeting()
                        speakResponse(greeting) {
                            _state.value = AssistantState.LISTENING
                            if (isSessionActive) {
                                onStartSpeechListeningRequested()
                            }
                        }
                    } else {
                        // Session already active: NO greeting, NO beep — keep listening silently
                        _state.value = AssistantState.LISTENING
                        kotlinx.coroutines.delay(100)
                        onStartSpeechListeningRequested()
                    }
                } else {
                    // Backward-compatible single-turn mode (followUpTimeoutMs = 0)
                    _state.value = AssistantState.LISTENING
                    kotlinx.coroutines.delay(100)
                    onStartSpeechListeningRequested()
                }
            }
        }
    }

    /**
     * Called when the speech recognizer returns final recognized text.
     */
    fun onSpeechRecognized(text: String, alternatives: List<String> = emptyList()) {
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
                val candidates = mutableListOf<String>()
                candidates.addAll(baseCandidates)
                if (lastOpenedApp != null) {
                    candidates.add(if (lastOpenedApp == "youtube") "context:youtube" else "context:$lastOpenedApp")
                }
                if (!lastSearchQuery.isNullOrBlank()) {
                    candidates.add("query:$lastSearchQuery")
                    candidates.add("index:$currentSongIndex")
                }

                var action = intentEngine.parse(trimmed, candidates)
                if (action == null && alternatives.isNotEmpty()) {
                    for (alt in alternatives) {
                        val altTrimmed = alt.trim()
                        if (altTrimmed.isNotBlank() && !altTrimmed.equals(trimmed, ignoreCase = true)) {
                            action = intentEngine.parse(altTrimmed, candidates)
                            if (action != null) {
                                _lastHeardText.value = altTrimmed
                                break
                            }
                        }
                    }
                }

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
                    // Turn timed out without speech; be subtle and wait on wake word while session remains active
                    returnToSleeping(keepSession = true)
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
                    // Turn encountered silence or error in follow-up mode; be subtle and wait on wake word while session remains active
                    returnToSleeping(keepSession = true)
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
                executeAction(action)
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
            lastSearchQuery = action.query
            currentSongIndex = 1
        } else if (action is com.spidey.assistant.actions.PlayFirstResultAction) {
            lastOpenedApp = action.targetApp
            if (!action.query.isNullOrBlank()) {
                lastSearchQuery = action.query
            }
            currentSongIndex = action.itemIndex
        }

        _state.value = AssistantState.EXECUTING
        when (val result = actionRouter.route(action)) {
            is ActionResult.Success -> {
                speakResponse(result.spokenMessage) {
                    if (action is StopAction) {
                        endSessionAndSleep()
                    } else if (action is com.spidey.assistant.actions.PlayFirstResultAction ||
                        action is com.spidey.assistant.actions.SearchYouTubeAction ||
                        action is com.spidey.assistant.actions.OpenAppAction ||
                        action is com.spidey.assistant.actions.CallContactAction
                    ) {
                        // Media playing or app launched: be subtle, return to sleeping while keeping session active
                        returnToSleeping(keepSession = isSessionActive)
                    } else if (isSessionActive) {
                        enterFollowUpListening()
                    } else {
                        returnToSleeping()
                    }
                }
            }
            is ActionResult.NeedsUnlock -> {
                pendingActionAfterUnlock = if (action is UnlockAction) {
                    UnlockAction(wasPrompted = true)
                } else {
                    action
                }
                _state.value = AssistantState.AWAITING_UNLOCK
                onUnlockRequested(action)
                speakResponse("Please enter your PIN or use your fingerprint.") {
                    if (pendingActionAfterUnlock != null) {
                        _state.value = AssistantState.AWAITING_UNLOCK
                    }
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
                    scope.launch {
                        kotlinx.coroutines.delay(250)
                        mutex.withLock {
                            if (_state.value == AssistantState.LISTENING) {
                                onStartSpeechListeningRequested()
                            }
                        }
                    }
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
        resetSessionTimeout()
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

    private fun returnToSleeping(keepSession: Boolean = false) {
        activeClarificationCandidates = emptyList()
        emptySpeechAttempts = 0
        if (!keepSession) {
            isSessionActive = false
            lastOpenedApp = null
            lastSearchQuery = null
            currentSongIndex = 1
            sessionTimeoutJob?.cancel()
            sessionTimeoutJob = null
        }
        _state.value = AssistantState.SLEEPING
        _errorText.value = null
        onStopSpeechListeningRequested()
        // Re-arm wake word spotter
        onWakeWordStateRequested(true)
    }
}
