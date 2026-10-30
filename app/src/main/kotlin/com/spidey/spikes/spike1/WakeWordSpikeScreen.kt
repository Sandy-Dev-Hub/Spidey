package com.spidey.spikes.spike1

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.spidey.spikes.util.SpikeLogger
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.IOException

/**
 * Spike 1 – Wake Word (Offline Vosk Keyword Spotter)
 *
 * Runs 100% on-device using Vosk Indian English acoustic model.
 * Constrained by grammar to spot "spidey" and "hey spidey".
 * Zero API keys or cloud accounts needed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WakeWordSpikeScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var hasMicPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasMicPerm = granted }

    var model by remember { mutableStateOf<Model?>(null) }
    var isModelLoading by remember { mutableStateOf(false) }
    var modelError by remember { mutableStateOf<String?>(null) }

    var speechService by remember { mutableStateOf<SpeechService?>(null) }
    var isListening by remember { mutableStateOf(false) }

    var wakeCount by remember { mutableStateOf(0) }
    var falseWakeCount by remember { mutableStateOf(0) }
    var lastHypothesis by remember { mutableStateOf("") }
    val logLines = remember { mutableStateListOf<String>() }
    val listState = rememberLazyListState()

    fun addLog(msg: String) {
        SpikeLogger.log("SPIKE1_VOSK", msg)
        logLines.add(msg)
        scope.launch { listState.animateScrollToItem(maxOf(0, logLines.size - 1)) }
    }

    // Unpack and load Vosk model from assets on first appearance
    LaunchedEffect(Unit) {
        if (model == null && !isModelLoading) {
            isModelLoading = true
            addLog("Unpacking model-en-in from assets (one-time initialization)...")
            StorageService.unpack(
                ctx,
                "model-en-in",
                "model",
                { loadedModel: Model ->
                    model = loadedModel
                    isModelLoading = false
                    addLog("✅ Vosk Indian English Model loaded successfully into memory")
                },
                { exception: IOException ->
                    isModelLoading = false
                    modelError = exception.localizedMessage ?: "Failed to unpack model"
                    addLog("❌ Model unpack error: ${exception.message}")
                }
            )
        }
    }

    // Clean up microphone when leaving screen
    DisposableEffect(Unit) {
        onDispose {
            speechService?.stop()
            speechService?.shutdown()
            speechService = null
        }
    }

    fun playBeep() {
        try {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        } catch (_: Exception) {}
    }

    fun startListening() {
        val currentModel = model ?: return
        try {
            // Remove grammar restriction to see full vocabulary transcription for "Spidey"
            val rec = Recognizer(currentModel, 16000.0f)

            val service = SpeechService(rec, 16000.0f)
            service.startListening(object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) {
                    if (hypothesis.isNullOrBlank()) return
                    try {
                        val json = JSONObject(hypothesis)
                        val partial = json.optString("partial", "").trim()
                        if (partial.isNotBlank()) {
                            lastHypothesis = partial
                            if (partial.contains("spidey", ignoreCase = true) ||
                                partial.contains("spider", ignoreCase = true)
                            ) {
                                wakeCount++
                                playBeep()
                                addLog("⚡ WAKE WORD DETECTED (partial): \"$partial\"")
                                rec.reset()
                            }
                        }
                    } catch (_: Exception) {}
                }

                override fun onResult(hypothesis: String?) {
                    if (hypothesis.isNullOrBlank()) return
                    try {
                        val json = JSONObject(hypothesis)
                        val text = json.optString("text", "").trim()
                        if (text.isNotBlank()) {
                            lastHypothesis = text
                            addLog("Heard text: \"$text\"")
                            if (text.contains("spidey", ignoreCase = true) ||
                                text.contains("spider", ignoreCase = true)
                            ) {
                                wakeCount++
                                playBeep()
                                addLog("⚡ WAKE WORD DETECTED: \"$text\"")
                                rec.reset()
                            }
                        }
                    } catch (_: Exception) {}
                }

                override fun onFinalResult(hypothesis: String?) {}

                override fun onError(exception: Exception?) {
                    addLog("⚠️ SpeechService error: ${exception?.message}")
                }

                override fun onTimeout() {
                    addLog("⏱️ Listening timeout")
                }
            })

            speechService = service
            isListening = true
            addLog("🟢 Vosk Keyword Spotter RUNNING. Say \"Spidey\" or \"Hey Spidey\"!")
        } catch (e: Exception) {
            addLog("❌ Failed to start listener: ${e.message}")
        }
    }

    fun stopListening() {
        speechService?.stop()
        speechService?.shutdown()
        speechService = null
        isListening = false
        addLog("⚫ Vosk Keyword Spotter STOPPED. Mic released.")
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Spike 1 – Wake Word (Vosk)") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Status Cards ────────────────────────────────────
            StatusRow(
                "Model Status",
                when {
                    isModelLoading -> "⏳ Unpacking model..."
                    model != null -> "✅ Vosk en-IN (Offline)"
                    modelError != null -> "❌ Error: $modelError"
                    else -> "⚫ Not loaded"
                }
            )
            StatusRow("Microphone", if (hasMicPerm) "✅ Granted" else "❌ Denied")
            StatusRow("Spotter Engine", if (isListening) "🟢 Active & Spotting" else "⚫ Inactive")
            StatusRow("Wake Count", "🎯 $wakeCount real  |  🚫 $falseWakeCount false wakes")
            if (lastHypothesis.isNotBlank()) {
                StatusRow("Last Heard", "\"$lastHypothesis\"")
            }

            if (!hasMicPerm) {
                Button(onClick = { permLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                    Text("Grant Microphone Permission")
                }
            }

            // ── Controls ────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = hasMicPerm && model != null && !isListening,
                    onClick = { startListening() }
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Start Spotting")
                }

                OutlinedButton(
                    enabled = isListening,
                    onClick = { stopListening() }
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Stop")
                }

                OutlinedButton(
                    enabled = isListening || wakeCount > 0,
                    onClick = {
                        falseWakeCount++
                        addLog("🚫 Marked false trigger (total: $falseWakeCount)")
                    }
                ) {
                    Text("Mark False")
                }
            }

            HorizontalDivider()
            Text("Console Log", style = MaterialTheme.typography.labelMedium)
            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                items(logLines) { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
