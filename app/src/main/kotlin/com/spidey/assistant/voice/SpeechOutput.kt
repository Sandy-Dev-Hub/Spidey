package com.spidey.assistant.voice

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.UUID

/**
 * Text-to-speech output manager.
 * Ensures the assistant's own voice does not trigger speech recognition or wake word detection.
 */
class SpeechOutput(
    private val context: Context,
    private val onInitComplete: (Boolean) -> Unit = {}
) {
    private var tts: TextToSpeech? = null
    private var isReady = false
    private val pendingCallbacks = java.util.concurrent.ConcurrentHashMap<String, () -> Unit>()

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale("en", "IN"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.language = Locale.ENGLISH
                }
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}

                    override fun onDone(utteranceId: String?) {
                        utteranceId?.let { id ->
                            val cb = pendingCallbacks.remove(id)
                            cb?.invoke()
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        utteranceId?.let { id ->
                            val cb = pendingCallbacks.remove(id)
                            cb?.invoke()
                        }
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        utteranceId?.let { id ->
                            val cb = pendingCallbacks.remove(id)
                            cb?.invoke()
                        }
                    }
                })
                isReady = true
                onInitComplete(true)
            } else {
                Log.e("SpeechOutput", "TTS initialization failed with status $status")
                isReady = false
                onInitComplete(false)
            }
        }
    }

    fun playPromptBeep(durationMs: Int = 150) {
        try {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, durationMs)
        } catch (e: Exception) {
            Log.w("SpeechOutput", "Could not play prompt tone: ${e.message}")
        }
    }

    fun speak(text: String, onDone: () -> Unit) {
        val currentTts = tts
        if (!isReady || currentTts == null) {
            Log.w("SpeechOutput", "TTS not initialized, skipping speech: $text")
            onDone()
            return
        }

        val utteranceId = UUID.randomUUID().toString()
        pendingCallbacks[utteranceId] = onDone

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        currentTts.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    fun stop() {
        tts?.stop()
        pendingCallbacks.clear()
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
        pendingCallbacks.clear()
    }
}
