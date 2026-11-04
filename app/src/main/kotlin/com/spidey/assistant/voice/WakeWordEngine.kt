package com.spidey.assistant.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import com.spidey.spikes.util.SpikeLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService
import java.io.IOException
import java.util.Locale

/**
 * Diagnostics information exposed for the on-screen diagnostics panel
 * and background reliability tracking.
 */
data class WakeWordDiagnostics(
    val isServiceAlive: Boolean = false,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val audioFps: Double = 0.0,
    val isSilenced: Boolean = false,
    val detectionCount: Int = 0,
    val lastWakeDetectionTime: Long = 0L,
    val lastKeyword: String = "",
    val error: String? = null
)

/**
 * On-device keyword spotter powered by offline Vosk Indian English engine.
 * Specifically detects "Hey Spidey", "Spidey", "Hey Spider", and "Spider" triggers.
 *
 * Runs an explicit AudioRecord audio capture loop on a dedicated thread,
 * computing real-time audio FPS and detecting OS-level mic silencing.
 */
class WakeWordEngine(
    private val context: Context,
    private val onWakeWordDetected: (trailingCommand: String?) -> Unit,
    private val onModelLoaded: () -> Unit = {},
    private val onError: (String) -> Unit = {}
) {
    private var model: Model? = null
    private var recognizer: Recognizer? = null

    @Volatile
    private var isListening = false

    @Volatile
    private var isPaused = false

    @Volatile
    private var isRecording = false

    private var recordThread: Thread? = null
    private var audioRecord: AudioRecord? = null

    private var detectionCount = 0

    private val _diagnostics = MutableStateFlow(WakeWordDiagnostics())
    val diagnostics: StateFlow<WakeWordDiagnostics> = _diagnostics.asStateFlow()

    init {
        loadModel()
    }

    private fun loadModel() {
        SpikeLogger.log("SPIDEY", "Loading Vosk model-en-in from assets...")
        StorageService.unpack(
            context.applicationContext,
            "model-en-in",
            "model",
            { loadedModel: Model ->
                model = loadedModel
                SpikeLogger.log("SPIDEY", "Vosk Indian English model loaded successfully")
                onModelLoaded()
            },
            { exception: IOException ->
                val err = "Failed to unpack Vosk model: ${exception.message}"
                SpikeLogger.log("SPIDEY", "Error: $err")
                _diagnostics.value = _diagnostics.value.copy(error = err)
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
            stopAudioThreadAndReleaseRecord()

            // Initialize recognizer with 16kHz audio
            recognizer = Recognizer(currentModel, 16000.0f)
            isListening = true
            isPaused = false

            startAudioLoop()
            SpikeLogger.log("SPIDEY", "WakeWordEngine started listening loop")
        } catch (e: Exception) {
            val err = "Failed to start wake word engine: ${e.message}"
            SpikeLogger.log("SPIDEY", "Error: $err")
            _diagnostics.value = _diagnostics.value.copy(error = err)
            onError(err)
        }
    }

    private fun startAudioLoop() {
        val rec = recognizer ?: return
        isRecording = true

        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSizeInShorts = maxOf(minBufferSize / 2, 3200) // 0.2s = 3200 shorts (5 fps nominal)

        // Use MediaRecorder.AudioSource.MIC with hardware AGC/NS if available,
        // which does NOT gate out or attenuate far-field speech like VOICE_RECOGNITION does on Samsung devices
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSizeInShorts * 2
            )
        } catch (_: Exception) {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSizeInShorts * 2
            )
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            val err = "AudioRecord failed to initialize (state=${record.state}). Mic may be busy."
            SpikeLogger.log("SPIDEY", "Error: $err")
            _diagnostics.value = _diagnostics.value.copy(error = err, isSilenced = true)
            record.release()
            return
        }

        audioRecord = record

        // Attach AcousticEchoCanceler and AutomaticGainControl if supported by device
        try {
            val audioSessionId = record.audioSessionId
            if (android.media.audiofx.AutomaticGainControl.isAvailable()) {
                val agc = android.media.audiofx.AutomaticGainControl.create(audioSessionId)
                agc?.enabled = true
                SpikeLogger.log("SPIDEY", "Hardware AutomaticGainControl enabled for far-field capture")
            }
            if (android.media.audiofx.NoiseSuppressor.isAvailable()) {
                val ns = android.media.audiofx.NoiseSuppressor.create(audioSessionId)
                ns?.enabled = true
                SpikeLogger.log("SPIDEY", "Hardware NoiseSuppressor enabled for far-field capture")
            }
        } catch (e: Exception) {
            SpikeLogger.log("SPIDEY", "Audio effects init exception: ${e.message}")
        }

        recordThread = Thread({
            try {
                record.startRecording()
                val shortBuffer = ShortArray(bufferSizeInShorts)
                var framesThisSec = 0
                var lastFpsCheck = SystemClock.elapsedRealtime()

                _diagnostics.value = _diagnostics.value.copy(
                    isRunning = true,
                    isPaused = false,
                    isSilenced = false,
                    error = null
                )

                while (isRecording && !isPaused) {
                    val read = record.read(shortBuffer, 0, shortBuffer.size)
                    if (read > 0) {
                        framesThisSec++
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastFpsCheck >= 1000L) {
                            val elapsedSec = (now - lastFpsCheck) / 1000.0
                            val fps = if (elapsedSec > 0) framesThisSec / elapsedSec else 0.0
                            framesThisSec = 0
                            lastFpsCheck = now

                            _diagnostics.value = _diagnostics.value.copy(
                                audioFps = (fps * 10).toInt() / 10.0,
                                isSilenced = false,
                                isRunning = true
                            )
                        }

                        // Apply far-field software gain boost (~4.0x / +12 dB) with soft clamp
                        // to reliably pick up wake word from a distance
                        for (i in 0 until read) {
                            val boosted = (shortBuffer[i] * 4.0f).toInt()
                            shortBuffer[i] = boosted.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                        }

                        val isFinal = rec.acceptWaveForm(shortBuffer, read)
                        val jsonStr = if (isFinal) rec.result else rec.partialResult
                        checkAndDispatchHypothesis(jsonStr, isFinal, rec)
                    } else if (read == 0) {
                        SystemClock.sleep(10)
                    } else {
                        Log.w(TAG, "AudioRecord.read returned error: $read")
                        _diagnostics.value = _diagnostics.value.copy(
                            isSilenced = true,
                            error = "AudioRecord read error: $read"
                        )
                        SystemClock.sleep(100)
                    }
                }
            } catch (e: Exception) {
                if (isRecording) {
                    SpikeLogger.log("SPIDEY", "Audio thread exception: ${e.message}")
                    _diagnostics.value = _diagnostics.value.copy(error = e.message)
                }
            }
        }, "SpideyWakeWordThread").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    private fun checkAndDispatchHypothesis(jsonStr: String?, isFinal: Boolean, rec: Recognizer) {
        if (jsonStr.isNullOrBlank() || isPaused) return
        try {
            val json = JSONObject(jsonStr)
            val rawText = if (isFinal) json.optString("text", "") else json.optString("partial", "")
            val text = rawText.trim()
            if (text.isNotBlank() && isWakeWordMatch(text)) {
                val now = System.currentTimeMillis()
                val trailing = extractTrailingCommand(text)
                SpikeLogger.log("SPIDEY", "⚡ Wake word detected: \"$text\" (isFinal=$isFinal, trailing='$trailing')")
                rec.reset()
                detectionCount++
                _diagnostics.value = _diagnostics.value.copy(
                    detectionCount = detectionCount,
                    lastWakeDetectionTime = now,
                    lastKeyword = text
                )
                onWakeWordDetected(trailing)
            }
        } catch (e: Exception) {
            Log.w(TAG, "JSON parsing error: ${e.message}")
        }
    }

    fun extractTrailingCommand(text: String): String? {
        val clean = text.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
        val wakeTriggers = listOf(
            "hey spider", "hey spidey", "hi spider", "hi spidey", "hello spidey", "hello spider",
            "a spider", "ok spider", "spider man", "spiderman", "spider", "spidey", "spidy", "spyder"
        )
        for (trigger in wakeTriggers) {
            val idx = clean.indexOf(trigger)
            if (idx != -1) {
                val remainder = clean.substring(idx + trigger.length).trim()
                if (remainder.isNotBlank() && remainder.length > 2) {
                    return remainder
                }
            }
        }
        return null
    }

    /**
     * Temporarily pauses wake spotting and releases the microphone hardware
     * so that SpeechRecognizer (command recognition) can record audio without conflicts.
     */
    @Synchronized
    fun pause() {
        if (!isListening) return
        isPaused = true
        isRecording = false
        stopAudioThreadAndReleaseRecord()
        _diagnostics.value = _diagnostics.value.copy(isPaused = true, audioFps = 0.0)
        SpikeLogger.log("SPIDEY", "WakeWordEngine paused & mic released for speech input")
    }

    /**
     * Resumes wake word spotting after speech input or TTS completes.
     */
    @Synchronized
    fun resume() {
        if (isPaused) {
            isPaused = false
            startAudioLoop()
            SpikeLogger.log("SPIDEY", "WakeWordEngine resumed background spotting")
        }
    }

    /**
     * Stops the engine completely.
     */
    @Synchronized
    fun stop() {
        isListening = false
        isPaused = false
        isRecording = false
        stopAudioThreadAndReleaseRecord()
        try {
            recognizer?.close()
        } catch (_: Exception) {}
        recognizer = null
        _diagnostics.value = _diagnostics.value.copy(
            isRunning = false,
            isPaused = false,
            audioFps = 0.0
        )
        SpikeLogger.log("SPIDEY", "WakeWordEngine fully stopped")
    }

    private fun stopAudioThreadAndReleaseRecord() {
        isRecording = false
        try {
            recordThread?.join(300)
        } catch (_: Exception) {}
        recordThread = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing AudioRecord: ${e.message}")
        }
        audioRecord = null
    }

    fun isWakeWordMatch(raw: String): Boolean {
        val clean = raw.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9 ]"), " ")
        val words = clean.split("\\s+".toRegex()).filter { it.isNotBlank() }

        val wakeKeywords = setOf(
            "spider", "spidey", "spidy", "spyder", "spydar", "spida", "speeder", "speedy",
            "spide", "spidee", "friday", "priday", "spider man", "spiderman"
        )

        if (words.any { it in wakeKeywords }) return true
        if (clean.contains("spider") || clean.contains("spidey") || clean.contains("spyder") ||
            clean.contains("spidy") || clean.contains("spida") || clean.contains("speeder") ||
            clean.contains("speedy") || clean.contains("spy der") || clean.contains("spy the") ||
            clean.contains("hey speeder") || clean.contains("by the") || clean.contains("hey spider")
        ) {
            return true
        }
        return false
    }

    companion object {
        private const val TAG = "WakeWordEngine"
    }
}
