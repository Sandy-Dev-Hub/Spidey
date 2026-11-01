package com.spidey.spikes.spike1

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.spidey.spikes.BuildConfig
import com.spidey.spikes.util.SpikeLogger
import kotlinx.coroutines.launch

/**
 * Spike 1 – Wake Word
 *
 * Status: ENGINE STUB – no Porcupine calls are made yet.
 * When the AccessKey and .ppn file are provided:
 *   1. Uncomment the Porcupine dependency in app/build.gradle.kts
 *   2. Add picovoice.accessKey to local.properties
 *   3. Copy the .ppn keyword file to app/src/main/assets/
 *   4. Replace the TODO below with PorcupineManager.Builder(...).build()
 *
 * Phase 0 only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WakeWordSpikeScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val accessKey = BuildConfig.PICOVOICE_ACCESS_KEY.takeIf { it.isNotBlank() }
    val hasKey = accessKey != null

    var hasMicPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasMicPerm = granted }

    var engineRunning by remember { mutableStateOf(false) }
    var wakeCount by remember { mutableStateOf(0) }
    var falseWakeCount by remember { mutableStateOf(0) }
    val logLines = remember { mutableStateListOf<String>() }
    val listState = rememberLazyListState()

    fun addLog(msg: String) {
        SpikeLogger.log("SPIKE1", msg)
        logLines.add(msg)
        scope.launch { listState.animateScrollToItem(maxOf(0, logLines.size - 1)) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Spike 1 – Wake Word") }) }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Status cards ────────────────────────────────────
            StatusRow("AccessKey", if (hasKey) "✅ Present in local.properties" else "❌ Not set – add picovoice.accessKey to local.properties")
            StatusRow("Microphone", if (hasMicPerm) "✅ Granted" else "❌ Denied")
            StatusRow("Engine", if (engineRunning) "🟢 Running (stub)" else "⚫ Stopped")
            StatusRow("Wake detections", "$wakeCount real  |  $falseWakeCount marked as false")

            if (!hasMicPerm) {
                Button(onClick = { permLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                    Text("Grant Microphone Permission")
                }
            }

            // ── Engine stub controls ─────────────────────────────
            if (!hasKey) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("AccessKey missing", fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer)
                        Text("Add to local.properties:\n  picovoice.accessKey=YOUR_KEY",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = hasMicPerm && hasKey && !engineRunning,
                    onClick = {
                        // TODO: replace with real Porcupine engine when key+ppn provided:
                        //
                        // val manager = PorcupineManager.Builder()
                        //     .setAccessKey(accessKey!!)
                        //     .setKeywordPath("hey_spidey_android.ppn")
                        //     .setSensitivity(0.5f)
                        //     .build(ctx) { keywordIndex ->
                        //         wakeCount++
                        //         addLog("WAKE DETECTED (index $keywordIndex)")
                        //     }
                        // manager.start()
                        //
                        engineRunning = true
                        addLog("Engine STARTED (stub – listening simulated)")
                    }
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Start Engine")
                }

                OutlinedButton(
                    enabled = engineRunning,
                    onClick = {
                        // TODO: manager.stop(); manager.delete()
                        engineRunning = false
                        addLog("Engine STOPPED")
                    }
                ) { Text("Stop") }

                OutlinedButton(
                    enabled = engineRunning,
                    onClick = {
                        // Simulate a false wake for testing the counter
                        falseWakeCount++
                        addLog("Marked last event as FALSE WAKE (total=$falseWakeCount)")
                    }
                ) { Text("Mark False") }
            }

            HorizontalDivider()
            Text("Log", style = MaterialTheme.typography.labelMedium)
            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                items(logLines) { line ->
                    Text(line, style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace)
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
        Text(value, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
