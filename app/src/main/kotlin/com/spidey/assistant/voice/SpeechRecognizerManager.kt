package com.spidey.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * Manages Google's on-device/network [SpeechRecognizer] tailored for Indian English (en-IN).
 * Provides bounded listening with explicit timeouts and structured result/error callbacks.
 */
class SpeechRecognizerManager(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onPartialResult: (String) -> Unit = {},
    private val onEmpty: () -> Unit,
    private val onError: (errorCode: Int, errorMessage: String) -> Unit,
    private val onReadyForSpeech: () -> Unit = {}
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var isListening = false
    private var hasSpeechStarted = false

    private val timeoutRunnable = Runnable {
        if (isListening && !hasSpeechStarted) {
            Log.d(TAG, "Speech silence timeout fired")
            stopListening()
            onEmpty()
        }
    }

    fun startListening(timeoutMs: Long = 6000L) {
        mainHandler.post {
            try {
                mainHandler.removeCallbacks(timeoutRunnable)
                try {
                    recognizer?.destroy()
                } catch (_: Exception) {}

                val newRecognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
                newRecognizer.setRecognitionListener(createListener())
                recognizer = newRecognizer

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-IN")
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }

                hasSpeechStarted = false
                isListening = true
                newRecognizer.startListening(intent)

                // Silence watchdog
                mainHandler.removeCallbacks(timeoutRunnable)
                mainHandler.postDelayed(timeoutRunnable, timeoutMs)
                Log.d(TAG, "SpeechRecognizer started listening (timeout=${timeoutMs}ms)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start speech recognizer: ${e.message}", e)
                isListening = false
                onError(-1, e.message ?: "Failed to start SpeechRecognizer")
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            mainHandler.removeCallbacks(timeoutRunnable)
            if (isListening) {
                isListening = false
                try {
                    recognizer?.stopListening()
                } catch (e: Exception) {
                    Log.w(TAG, "Error stopping recognizer: ${e.message}")
                }
            }
        }
    }

    fun cancel() {
        mainHandler.post {
            mainHandler.removeCallbacks(timeoutRunnable)
            isListening = false
            hasSpeechStarted = false
            try {
                recognizer?.cancel()
            } catch (e: Exception) {
                Log.w(TAG, "Error cancelling recognizer: ${e.message}")
            }
        }
    }

    fun destroy() {
        mainHandler.post {
            mainHandler.removeCallbacks(timeoutRunnable)
            isListening = false
            try {
                recognizer?.destroy()
            } catch (e: Exception) {
                Log.w(TAG, "Error destroying recognizer: ${e.message}")
            }
            recognizer = null
        }
    }

    private fun createListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "SpeechRecognizer ready for speech")
            onReadyForSpeech()
        }

        override fun onBeginningOfSpeech() {
            hasSpeechStarted = true
            mainHandler.removeCallbacks(timeoutRunnable)
            Log.d(TAG, "Speech detected, waiting for utterance to finish")
        }

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d(TAG, "Speech ended, processing audio")
        }

        override fun onError(error: Int) {
            mainHandler.removeCallbacks(timeoutRunnable)
            isListening = false
            val errorMsg = mapErrorCode(error)
            Log.w(TAG, "SpeechRecognizer onError: $error ($errorMsg)")

            // Codes 6 (ERROR_SPEECH_TIMEOUT), 7 (ERROR_NO_MATCH), 11 (ERROR_SERVER_DISCONNECTED)
            // are idle/silence timeouts that should trigger follow-up or re-listen rather than fatal errors.
            if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                error == 11
            ) {
                onEmpty()
            } else {
                onError(error, errorMsg)
            }
        }

        override fun onResults(results: Bundle?) {
            mainHandler.removeCallbacks(timeoutRunnable)
            isListening = false
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val recognizedText = matches?.firstOrNull()?.trim() ?: ""
            Log.d(TAG, "SpeechRecognizer final results: \"$recognizedText\"")

            if (recognizedText.isBlank()) {
                onEmpty()
            } else {
                onResult(recognizedText)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partial = matches?.firstOrNull()?.trim() ?: ""
            if (partial.isNotBlank()) {
                onPartialResult(partial)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun mapErrorCode(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
        SpeechRecognizer.ERROR_CLIENT -> "Client-side error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
        SpeechRecognizer.ERROR_NETWORK -> "Network error"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
        SpeechRecognizer.ERROR_NO_MATCH -> "No speech match"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
        SpeechRecognizer.ERROR_SERVER -> "Server error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input timeout"
        else -> "Error code $error"
    }

    companion object {
        private const val TAG = "SpeechRecognizerManager"
    }
}
