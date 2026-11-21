package com.spidey.assistant.engine

import com.spidey.assistant.voice.SpeechRecognizerManager

/**
 * J0: Legacy STT adapter wrapping [SpeechRecognizerManager] behind [SttEngine].
 */
class LegacySttEngine(
    private val speechRecognizerManager: SpeechRecognizerManager
) : SttEngine {

    private var listening: Boolean = false

    override val isListening: Boolean
        get() = listening

    override fun startListening(onResult: (text: String) -> Unit, onError: (code: Int) -> Unit) {
        listening = true
        // SpeechRecognizerManager handles its own listeners and dispatches to ConversationManager
        speechRecognizerManager.startListening()
    }

    override fun stopListening() {
        listening = false
        speechRecognizerManager.stopListening()
    }

    override fun destroy() {
        listening = false
        speechRecognizerManager.destroy()
    }
}

