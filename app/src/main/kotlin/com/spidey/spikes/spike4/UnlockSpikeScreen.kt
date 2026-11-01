package com.spidey.spikes.spike4

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
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
 * Spike 4 – Unlock Prompt
 *
 * Press Start. Lock your phone within 15 seconds.
 * The foreground service then starts UnlockHelperActivity over the lock screen,
 * which calls KeyguardManager.requestDismissKeyguard.
 * The KeyguardDismissCallback is logged on screen and to the log file.
 *
 * Tests:
 *   – Screen locked with biometric/PIN: expect onDismissSucceeded or onDismissError
 *   – Screen off but NOT locked: expect transparent activity, possibly onDismissCancelled
 *   – Screen locked, user presses Cancel: expect onDismissCancelled
 *
 * Phase 0 only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnlockSpikeScreen() {
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

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    SpikeForegroundService.ACTION_SPIKE4_TICK -> {
                        val sec = intent.getLongExtra(SpikeForegroundService.EXTRA_COUNTDOWN_SEC, 0)
                        countdown = sec
                    }
                    SpikeForegroundService.ACTION_SPIKE4_RESULT -> {
                        val log = intent.getStringExtra(SpikeForegroundService.EXTRA_LOG) ?: ""
                        running = false
                        countdown = -1L
                        addLog("✅ RESULT: $log")
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(SpikeForegroundService.ACTION_SPIKE4_TICK)
            addAction(SpikeForegroundService.ACTION_SPIKE4_RESULT)
        }
        LocalBroadcastManager.getInstance(ctx).registerReceiver(receiver, filter)
        SpikeLogger.snapshot().filter { it.contains("SPIKE4") }.forEach { addLog(it) }

        onDispose {
            LocalBroadcastManager.getInstance(ctx).unregisterReceiver(receiver)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Spike 4 – Unlock Prompt") }) }
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
                    Text("2. LOCK your phone within 15 seconds (power button → lock)",
                        style = MaterialTheme.typography.bodySmall)
                    Text("3. After 15 s the service shows an unlock prompt over the lock screen",
                        style = MaterialTheme.typography.bodySmall)
                    Text("4. Authenticate (fingerprint / PIN) or press Cancel",
                        style = MaterialTheme.typography.bodySmall)
                    Text("5. Return to this screen to read the callback result",
                        style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    Text("Run twice: (a) with biometric/PIN lock, (b) screen-off only (no lock)",
                        style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }
            }

            if (running && countdown >= 0) {
                LinearProgressIndicator(
                    progress = { countdown / 15f },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Lock your phone NOW! Triggering in $countdown s…",
                    color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !running,
                    onClick = {
                        running = true
                        logLines.clear()
                        addLog("▶ Started – 15 s. LOCK your phone!")
                        SpikeLogger.log("SPIKE4", "User pressed Start")
                        val svcIntent = SpikeForegroundService.intentFor(
                            ctx, SpikeForegroundService.ACTION_START_SPIKE4_DELAYED)
                        ContextCompat.startForegroundService(ctx, svcIntent)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null)
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
            Text("Callback log", style = MaterialTheme.typography.labelMedium)
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
