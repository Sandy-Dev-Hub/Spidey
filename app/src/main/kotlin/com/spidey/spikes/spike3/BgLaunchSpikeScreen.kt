package com.spidey.spikes.spike3

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
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
 * Spike 3 – Background App Launch
 *
 * Press Start. You have 15 seconds to lock the phone or switch to another app.
 * The foreground service then tries two routes:
 *   Route 1 – startActivity(FLAG_ACTIVITY_NEW_TASK) → YouTube
 *   Route 2 – Full-screen-intent notification
 *
 * Results are logged on screen AND written to the log file so they survive
 * the app being in the background.
 *
 * Phase 0 only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BgLaunchSpikeScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var countdown by remember { mutableLongStateOf(-1L) }
    var running by remember { mutableStateOf(false) }
    val logLines = remember { mutableStateListOf<String>() }
    val listState = rememberLazyListState()

    fun addLog(msg: String) {
        logLines.add(msg)
        scope.launch { listState.animateScrollToItem(maxOf(0, logLines.size - 1)) }
    }

    // Receive results broadcast from service
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    SpikeForegroundService.ACTION_SPIKE3_TICK -> {
                        val sec = intent.getLongExtra(SpikeForegroundService.EXTRA_COUNTDOWN_SEC, 0)
                        countdown = sec
                    }
                    SpikeForegroundService.ACTION_SPIKE3_RESULT -> {
                        val log = intent.getStringExtra(SpikeForegroundService.EXTRA_LOG) ?: ""
                        running = false
                        countdown = -1L
                        addLog("✅ RESULT: $log")
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(SpikeForegroundService.ACTION_SPIKE3_TICK)
            addAction(SpikeForegroundService.ACTION_SPIKE3_RESULT)
        }
        LocalBroadcastManager.getInstance(ctx).registerReceiver(receiver, filter)
        // Also load existing log lines
        SpikeLogger.snapshot().filter { it.contains("SPIKE3") }.forEach { addLog(it) }

        onDispose {
            LocalBroadcastManager.getInstance(ctx).unregisterReceiver(receiver)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Spike 3 – Background Launch") }) }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Instructions", fontWeight = FontWeight.Bold)
                    Text("1. Press 'Start (15 s delay)'",
                        style = MaterialTheme.typography.bodySmall)
                    Text("2. Lock phone OR press Home to go to another app",
                        style = MaterialTheme.typography.bodySmall)
                    Text("3. After 15 s the service tries to launch YouTube",
                        style = MaterialTheme.typography.bodySmall)
                    Text("4. Return to this screen to read the result OR pull the log file",
                        style = MaterialTheme.typography.bodySmall)
                    Text("Run this test three times: screen-on-other-app, screen-off, locked",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold)
                }
            }

            if (running && countdown >= 0) {
                LinearProgressIndicator(
                    progress = { countdown / 15f },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Launching in $countdown s – lock your phone or switch apps now!",
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !running,
                    onClick = {
                        running = true
                        logLines.clear()
                        addLog("▶ Started – 15 s countdown. Lock or switch apps.")
                        SpikeLogger.log("SPIKE3", "User pressed Start")
                        val svcIntent = SpikeForegroundService.intentFor(
                            ctx, SpikeForegroundService.ACTION_START_SPIKE3_DELAYED)
                        ContextCompat.startForegroundService(ctx, svcIntent)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Start (15 s delay)")
                }

                OutlinedButton(
                    enabled = running,
                    onClick = {
                        running = false
                        countdown = -1L
                        ctx.stopService(SpikeForegroundService.intentFor(
                            ctx, SpikeForegroundService.ACTION_STOP))
                        addLog("⛔ Stopped by user")
                    }
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                }
            }

            HorizontalDivider()
            Text("Live log (also saved to file)", style = MaterialTheme.typography.labelMedium)
            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                items(logLines) { line ->
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
