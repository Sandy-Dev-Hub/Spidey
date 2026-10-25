package com.spidey.spikes.spike6

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.spidey.spikes.service.SpikeForegroundService
import com.spidey.spikes.util.SpikeLogger
import kotlinx.coroutines.launch

/**
 * Spike 6 – Screen-Off Stability
 *
 * Starts a foreground service that logs a heartbeat every 10 seconds.
 * Lock/turn off the screen and leave the phone for 30+ minutes.
 * Return to the app and check how many heartbeats were received.
 *
 * Gap in heartbeats = service was killed by Samsung battery optimisation.
 * Missing heartbeats after a long gap = audio stall (relevant once real mic capture is added).
 *
 * Toggle "Use partial wake lock" to compare battery drain with and without.
 *
 * Phase 0 only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenOffSpikeScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var running by remember { mutableStateOf(false) }
    var useWakeLock by remember { mutableStateOf(false) }
    var heartbeatCount by remember { mutableIntStateOf(0) }
    var lastHeartbeat by remember { mutableStateOf("–") }
    val logLines = remember { mutableStateListOf<String>() }
    val listState = rememberLazyListState()

    fun addLog(msg: String) {
        logLines.add(msg)
        scope.launch { listState.animateScrollToItem(maxOf(0, logLines.size - 1)) }
    }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == SpikeForegroundService.ACTION_SPIKE6_HEARTBEAT) {
                    val log = intent.getStringExtra(SpikeForegroundService.EXTRA_LOG) ?: ""
                    heartbeatCount++
                    lastHeartbeat = log
                    addLog(log)
                }
            }
        }
        LocalBroadcastManager.getInstance(ctx)
            .registerReceiver(receiver, IntentFilter(SpikeForegroundService.ACTION_SPIKE6_HEARTBEAT))

        // Show any heartbeats already in memory from before the screen was re-opened
        SpikeLogger.snapshot().filter { it.contains("SPIKE6") }.takeLast(20).forEach { addLog(it) }

        onDispose {
            LocalBroadcastManager.getInstance(ctx).unregisterReceiver(receiver)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Spike 6 – Screen-Off Stability") }) }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Instructions", fontWeight = FontWeight.Bold)
                    Text("1. Press Start (with or without wake lock)",
                        style = MaterialTheme.typography.bodySmall)
                    Text("2. Turn off the screen or lock the phone",
                        style = MaterialTheme.typography.bodySmall)
                    Text("3. Leave it for 30+ minutes (ideally 2 hours)",
                        style = MaterialTheme.typography.bodySmall)
                    Text("4. Return and count heartbeats. Expected: 1 per 10 s = 180 in 30 min",
                        style = MaterialTheme.typography.bodySmall)
                    Text("5. Any gap > 10 s indicates the service was paused or killed",
                        style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text("Samsung tip: Set battery to Unrestricted and add to 'Never sleeping apps'",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold)
                }
            }

            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Heartbeats received: $heartbeatCount", fontWeight = FontWeight.Bold)
                    Text("Last: $lastHeartbeat",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (running)
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }

            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = useWakeLock, onCheckedChange = { useWakeLock = it },
                    enabled = !running)
                Text("Use partial wake lock (compare battery drain)")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !running,
                    onClick = {
                        running = true
                        heartbeatCount = 0
                        logLines.clear()
                        addLog("▶ Started. Wake lock: $useWakeLock. Turn off screen now.")
                        SpikeLogger.log("SPIKE6", "User pressed Start. wakeLock=$useWakeLock")
                        val svcIntent = SpikeForegroundService.intentFor(
                            ctx, SpikeForegroundService.ACTION_START_SPIKE6_SCREEN_OFF)
                            .putExtra(SpikeForegroundService.EXTRA_USE_WAKE_LOCK, useWakeLock)
                        ContextCompat.startForegroundService(ctx, svcIntent)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.BatteryFull, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Start")
                }

                OutlinedButton(
                    enabled = running,
                    onClick = {
                        running = false
                        ctx.stopService(SpikeForegroundService.intentFor(
                            ctx, SpikeForegroundService.ACTION_STOP))
                        addLog("⛔ Stopped. Total heartbeats: $heartbeatCount")
                        SpikeLogger.log("SPIKE6", "Stopped. Total heartbeats: $heartbeatCount")
                    }
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Stop")
                }
            }

            HorizontalDivider()
            Text("Heartbeat log (last 20 shown, all saved to file)",
                style = MaterialTheme.typography.labelMedium)
            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                items(logLines.takeLast(100)) { line ->
                    Text(line, style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace)
                }
            }

            Text("Pull log: adb pull /sdcard/Android/data/com.spidey.spikes.debug/files/spidey_spike_log.txt",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace)
        }
    }
}
