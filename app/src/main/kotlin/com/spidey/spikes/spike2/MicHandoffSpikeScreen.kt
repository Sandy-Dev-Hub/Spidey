package com.spidey.spikes.spike2

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.spidey.spikes.util.SpikeLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Spike 2 – Mic Handoff Chain
 *
 * Simulates the full handoff sequence:
 *   [T0] Wake word detected (button press simulates this)
 *   [T1] Porcupine engine stopped + mic released (stub – 50 ms delay simulation)
 *   [T2] Beep started (ToneGenerator)
 *   [T3] Beep finished (after BEEP_DURATION_MS)
 *   [T4] SpeechRecognizer.startListening called
 *   [T5] onReadyForSpeech callback (recognizer accepting audio)
 *   [T6] onBeginningOfSpeech callback (first speech detected)
 *   [T7] onResults callback (recognition complete)
 *
 * Key metric: T5-T0 = total user-perceived delay.
 *             T4-T0 = delay before recognizer is even started.
 *             T5-T4 = recognizer startup latency.
 *             Words spoken in [T0..T5] window are potentially lost.
 *
 * Phase 0 only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MicHandoffSpikeScreen() {
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

    var chainRunning by remember { mutableStateOf(false) }
    var lastResult by remember { mutableStateOf("") }
    val logLines = remember { mutableStateListOf<String>() }
    val listState = rememberLazyListState()

    // Timing state
    var t0 by remember { mutableLongStateOf(0L) }
    var timings by remember { mutableStateOf("") }

    fun addLog(msg: String) {
        SpikeLogger.log("SPIKE2", msg)
        logLines.add(msg)
        scope.launch { listState.animateScrollToItem(maxOf(0, logLines.size - 1)) }
    }

    // SpeechRecognizer must live on the main thread
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            recognizer?.destroy()
            recognizer = null
        }
    }

    fun runChain() {
        if (chainRunning || !hasMicPerm) return
        chainRunning = true
        recognizer?.destroy()

        scope.launch(Dispatchers.Main) {
            // ── T0: Wake simulated ────────────────────────────
            t0 = SystemClock.elapsedRealtime()
            addLog("[T0] Wake word simulated at t=0")

            // ── Stub: engine stop + mic release (~50 ms) ──────
            delay(50)
            val t1 = SystemClock.elapsedRealtime()
            addLog("[T1] Engine stopped / mic released | Δ=${t1 - t0}ms from T0")

            // ── Beep ──────────────────────────────────────────
            val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            val t2 = SystemClock.elapsedRealtime()
            addLog("[T2] Beep started | Δ=${t2 - t0}ms from T0")
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, BEEP_DURATION_MS.toInt())
            delay(BEEP_DURATION_MS)
            toneGen.release()
            val t3 = SystemClock.elapsedRealtime()
            addLog("[T3] Beep finished | Δ=${t3 - t0}ms from T0")

            // ── SpeechRecognizer ──────────────────────────────
            val newRecognizer = SpeechRecognizer.createSpeechRecognizer(ctx)
            recognizer = newRecognizer

            val listenIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }

            newRecognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    val t5 = SystemClock.elapsedRealtime()
                    val msg = "[T5] onReadyForSpeech | Δ=${t5 - t0}ms from T0 – SPEAK NOW"
                    addLog(msg)
                    timings = "Ready in ${t5 - t0}ms"
                }

                override fun onBeginningOfSpeech() {
                    val t6 = SystemClock.elapsedRealtime()
                    addLog("[T6] onBeginningOfSpeech | Δ=${t6 - t0}ms from T0")
                }

                override fun onRmsChanged(rmsdB: Float) { /* suppress */ }
                override fun onBufferReceived(buffer: ByteArray?) { /* suppress */ }

                override fun onEndOfSpeech() {
                    addLog("[7a] onEndOfSpeech – processing…")
                }

                override fun onResults(results: Bundle?) {
                    val t7 = SystemClock.elapsedRealtime()
                    val words = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull() ?: "(empty)"
                    lastResult = words
                    addLog("[T7] onResults | Δ=${t7 - t0}ms from T0 | Text: \"$words\"")
                    addLog("── Chain complete. Re-arming wake word (stub) ──")

                    // Stub: re-arm wake engine
                    // TODO: porcupineManager.start()
                    addLog("[T8] Wake engine re-armed (stub)")
                    chainRunning = false
                }

                override fun onPartialResults(partial: Bundle?) {
                    val text = partial?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!text.isNullOrBlank()) addLog("  [partial] \"$text\"")
                }

                override fun onEvent(eventType: Int, params: Bundle?) { /* suppress */ }

                override fun onError(error: Int) {
                    val msg = "[ERR] SpeechRecognizer error code=$error (${speechErrorName(error)})"
                    addLog(msg)
                    SpikeLogger.log("SPIKE2", msg)
                    chainRunning = false
                    // Stub: re-arm wake engine even on error
                    addLog("[T8] Wake engine re-armed after error (stub)")
                }
            })

            val t4 = SystemClock.elapsedRealtime()
            addLog("[T4] startListening called | Δ=${t4 - t0}ms from T0")
            newRecognizer.startListening(listenIntent)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Spike 2 – Mic Handoff") }) }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("How to test", fontWeight = FontWeight.Bold)
                    Text("1. Press 'Simulate Wake' – you have ${BEEP_DURATION_MS}ms of beep before the recognizer is ready",
                        style = MaterialTheme.typography.bodySmall)
                    Text("2. Start speaking immediately after you press the button to measure word loss",
                        style = MaterialTheme.typography.bodySmall)
                    Text("3. Watch the T5 delta – that is how many ms you cannot speak",
                        style = MaterialTheme.typography.bodySmall)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Mic:", style = MaterialTheme.typography.labelMedium)
                Text(if (hasMicPerm) "✅ Granted" else "❌ Denied",
                    style = MaterialTheme.typography.bodySmall)
                if (!hasMicPerm)
                    TextButton(onClick = { permLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                        Text("Grant")
                    }
            }

            if (lastResult.isNotBlank())
                Text("Last recognised: \"$lastResult\"", fontWeight = FontWeight.SemiBold)
            if (timings.isNotBlank())
                Text("⏱ $timings", color = MaterialTheme.colorScheme.primary)

            Button(
                enabled = hasMicPerm && !chainRunning,
                onClick = { runChain() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(if (chainRunning) "Chain running…" else "Simulate Wake + Run Chain")
            }

            HorizontalDivider()
            Text("Timing log", style = MaterialTheme.typography.labelMedium)
            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                items(logLines) { line ->
                    Text(line, style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

private const val BEEP_DURATION_MS = 400L

private fun speechErrorName(code: Int) = when (code) {
    SpeechRecognizer.ERROR_AUDIO -> "AUDIO"
    SpeechRecognizer.ERROR_CLIENT -> "CLIENT"
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "PERMISSIONS"
    SpeechRecognizer.ERROR_NETWORK -> "NETWORK"
    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "NETWORK_TIMEOUT"
    SpeechRecognizer.ERROR_NO_MATCH -> "NO_MATCH"
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "BUSY"
    SpeechRecognizer.ERROR_SERVER -> "SERVER"
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "SPEECH_TIMEOUT"
    else -> "UNKNOWN($code)"
}
