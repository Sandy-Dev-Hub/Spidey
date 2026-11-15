package com.spidey.assistant.engine

import com.spidey.assistant.voice.SpeechOutput

/**
 * J0: Legacy TTS adapter wrapping [SpeechOutput] behind [TtsEngine].
 */
class LegacyTtsEngine(
    private val speechOutput: SpeechOutput
) : TtsEngine {

    override fun speak(text: String, onDone: () -> Unit) {
        speechOutput.speak(text, onDone)
    }

    override fun speakStream(chunk: String, isFirst: Boolean, isLast: Boolean, onDone: (() -> Unit)?) {
        speechOutput.speakStream(chunk, isFirst, isLast, onDone)
    }

    override fun stop() {
        speechOutput.stop()
    }

    override fun destroy() {
        speechOutput.release()
    }
}

