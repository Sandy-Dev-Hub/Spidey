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
                // Apply active voice profile from FeatureFlags (defaults to British Jarvis)
                applyVoiceProfile(com.spidey.assistant.engine.FeatureFlags.selectedVoice)

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
                try {
                    // Task J3: Pre-warm TTS synthesis engine
                    tts?.speak("", TextToSpeech.QUEUE_FLUSH, null, null)
                } catch (_: Exception) {}
                onInitComplete(true)
            } else {
                Log.e("SpeechOutput", "TTS initialization failed with status $status")
                isReady = false
                onInitComplete(false)
            }
        }
    }

    fun playPromptBeep(durationMs: Int = 180) {
        // Disabled per Task J2 rules: No beeps under any circumstance
    }

    fun speak(text: String, onDone: () -> Unit) {
        val currentTts = tts
        if (!isReady || currentTts == null) {
            Log.w("SpeechOutput", "TTS not initialized, skipping speech: $text")
            onDone()
            return
        }

        applyVoiceProfile(com.spidey.assistant.engine.FeatureFlags.selectedVoice)

        val utteranceId = UUID.randomUUID().toString()
        pendingCallbacks[utteranceId] = onDone

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        currentTts.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    fun speakStream(chunk: String, isFirst: Boolean, isLast: Boolean, onDone: (() -> Unit)? = null) {
        val currentTts = tts
        if (!isReady || currentTts == null) {
            if (isLast) onDone?.invoke()
            return
        }

        if (isFirst) {
            applyVoiceProfile(com.spidey.assistant.engine.FeatureFlags.selectedVoice)
        }

        if (chunk.isBlank()) {
            if (isLast) {
                if (pendingCallbacks.isEmpty()) {
                    onDone?.invoke()
                } else {
                    val lastId = pendingCallbacks.keys.lastOrNull()
                    if (lastId != null && onDone != null) {
                        val prevCb = pendingCallbacks[lastId]
                        pendingCallbacks[lastId] = {
                            prevCb?.invoke()
                            onDone.invoke()
                        }
                    } else {
                        onDone?.invoke()
                    }
                }
            }
            return
        }

        val utteranceId = UUID.randomUUID().toString()
        if (isLast && onDone != null) {
            pendingCallbacks[utteranceId] = onDone
        }

        val queueMode = if (isFirst) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        currentTts.speak(chunk, queueMode, params, utteranceId)
    }

    fun applyVoiceProfile(profileName: String) {
        val engine = tts ?: return
        when (profileName) {
            com.spidey.assistant.engine.FeatureFlags.VOICE_JARVIS_BRITISH -> {
                engine.language = Locale.UK
                engine.setPitch(0.92f)
                engine.setSpeechRate(1.02f)
                selectBestVoice(listOf("en-gb-x-rjs-local", "en-gb-x-rjs-network", "en-gb-x-rjs", "en-gb"))
            }
            com.spidey.assistant.engine.FeatureFlags.VOICE_JARVIS_DEEP -> {
                engine.language = Locale.UK
                engine.setPitch(0.85f)
                engine.setSpeechRate(0.98f)
                selectBestVoice(listOf("en-gb-x-gbd-local", "en-gb-x-gbd-network", "en-gb-x-gbd", "en-gb"))
            }
            com.spidey.assistant.engine.FeatureFlags.VOICE_JARVIS_CRISP -> {
                engine.language = Locale.UK
                engine.setPitch(0.95f)
                engine.setSpeechRate(1.05f)
                selectBestVoice(listOf("en-gb-x-gbc-local", "en-gb-x-gbc-network", "en-gb-x-gbc", "en-gb"))
            }
            com.spidey.assistant.engine.FeatureFlags.VOICE_BRO_MALE -> {
                engine.language = Locale.US
                engine.setPitch(0.70f)
                engine.setSpeechRate(1.00f)
                selectBestVoice(listOf("en-us-x-sfg-local", "en-in-x-enc-local", "en-us-x-sfg", "en-us"))
            }
            else -> {
                engine.language = Locale.UK
                engine.setPitch(0.92f)
                engine.setSpeechRate(1.02f)
                selectBestVoice(listOf("en-gb-x-rjs-local", "en-gb-x-rjs-network", "en-gb-x-rjs", "en-gb"))
            }
        }
    }

    private fun selectBestVoice(candidateNames: List<String>) {
        try {
            val voices = tts?.voices ?: return
            for (pattern in candidateNames) {
                val match = voices.firstOrNull { it.name.contains(pattern, ignoreCase = true) }
                if (match != null) {
                    tts?.voice = match
                    Log.i("SpeechOutput", "Active TTS voice selected: ${match.name}")
                    return
                }
            }
        } catch (e: Exception) {
            Log.w("SpeechOutput", "Voice selection exception: ${e.message}")
        }
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
