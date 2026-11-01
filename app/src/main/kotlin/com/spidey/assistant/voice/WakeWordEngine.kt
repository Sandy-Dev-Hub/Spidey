package com.spidey.assistant.voice

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.IOException

/**
 * On-device keyword spotter powered by offline Vosk Indian English engine.
 * Specifically detects "Hey Spider" and "Spider" triggers with zero network access.
 */
class WakeWordEngine(
    private val context: Context,
    private val onWakeWordDetected: () -> Unit,
    private val onModelLoaded: () -> Unit = {},
    private val onError: (String) -> Unit = {}
) {
    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var recognizer: Recognizer? = null
    private var isListening = false
    private var isPaused = false

    init {
        loadModel()
    }

    private fun loadModel() {
        StorageService.unpack(
            context.applicationContext,
            "model-en-in",
            "model",
            { loadedModel: Model ->
                model = loadedModel
                Log.d(TAG, "Vosk model loaded successfully")
                onModelLoaded()
            },
            { exception: IOException ->
                val err = "Failed to unpack Vosk model: ${exception.message}"
                Log.e(TAG, err, exception)
                onError(err)
            }
        )
    }

    /**
     * Starts continuous wake word spotting on the microphone.
     */
    @Synchronized
    fun start() {
        val currentModel = model ?: run {
            Log.w(TAG, "Model not loaded yet, cannot start")
            return
        }

        if (isListening && !isPaused) return

        try {
            stopMicService()

            // Initialize recognizer with 16kHz audio
            val rec = Recognizer(currentModel, 16000.0f)
            recognizer = rec

            val service = SpeechService(rec, 16000.0f)
            service.startListening(createListener(rec))
            speechService = service
            isListening = true
            isPaused = false
            Log.d(TAG, "WakeWordEngine started spotting")
        } catch (e: Exception) {
            val err = "Failed to start wake word engine: ${e.message}"
            Log.e(TAG, err, e)
            onError(err)
        }
    }

    /**
     * Temporarily pauses wake spotting and releases the microphone for SpeechRecognizer or TTS.
     */
    @Synchronized
    fun pause() {
        if (!isListening) return
        isPaused = true
        stopMicService()
        Log.d(TAG, "WakeWordEngine paused and mic released")
    }

    /**
     * Resumes wake word spotting after speech input or TTS completes.
     */
    @Synchronized
    fun resume() {
        if (isPaused) {
            start()
        }
    }

    /**
     * Stops the engine completely.
     */
    @Synchronized
    fun stop() {
        isListening = false
        isPaused = false
        stopMicService()
        Log.d(TAG, "WakeWordEngine fully stopped")
    }

    private fun stopMicService() {
        try {
            speechService?.stop()
            speechService?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping SpeechService: ${e.message}")
        }
        speechService = null
        recognizer = null
    }

    private fun createListener(rec: Recognizer) = object : RecognitionListener {
        override fun onPartialResult(hypothesis: String?) {
            if (hypothesis.isNullOrBlank() || isPaused) return
            try {
                val json = JSONObject(hypothesis)
                val partial = json.optString("partial", "").trim()
                if (partial.isNotBlank()) {
                    if (partial.contains("spidey", ignoreCase = true) ||
                        partial.contains("spider", ignoreCase = true)
                    ) {
                        Log.d(TAG, "Wake word spotted in partial: \"$partial\"")
                        rec.reset()
                        onWakeWordDetected()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing partial: ${e.message}")
            }
        }

        override fun onResult(hypothesis: String?) {
            if (hypothesis.isNullOrBlank() || isPaused) return
            try {
                val json = JSONObject(hypothesis)
                val text = json.optString("text", "").trim()
                if (text.isNotBlank()) {
                    if (text.contains("spidey", ignoreCase = true) ||
                        text.contains("spider", ignoreCase = true)
                    ) {
                        Log.d(TAG, "Wake word spotted in result: \"$text\"")
                        rec.reset()
                        onWakeWordDetected()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing result: ${e.message}")
            }
        }

        override fun onFinalResult(hypothesis: String?) {}

        override fun onError(exception: java.lang.Exception?) {
            Log.w(TAG, "Vosk RecognitionListener error: ${exception?.message}")
        }

        override fun onTimeout() {}
    }

    companion object {
        private const val TAG = "WakeWordEngine"
    }
}
