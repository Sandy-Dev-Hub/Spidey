package com.spidey.spikes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

/**
 * Top-level spike selection menu.
 * Phase 0 only – not part of the real assistant.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpikeMenuScreen(nav: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("🕷️ Spidey – Phase 0 Spikes", fontWeight = FontWeight.Bold)
                        Text("Tap a spike to open its test screen",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SpikeCard("Spike 1", "Wake Word",
                "Porcupine engine stub (AccessKey needed). Measures false wakes.",
                Icons.Default.Mic, MaterialTheme.colorScheme.primary) { nav.navigate("spike1") }

            SpikeCard("Spike 2", "Mic Handoff Chain",
                "Wake → stop engine → beep → SpeechRecognizer(en-IN) → re-arm. Times each step.",
                Icons.Default.SwapHoriz, MaterialTheme.colorScheme.secondary) { nav.navigate("spike2") }

            SpikeCard("Spike 3", "Background App Launch",
                "Press Start, lock phone or switch app. After 15 s the service tries to launch YouTube. Logs which route works.",
                Icons.Default.OpenInNew, MaterialTheme.colorScheme.tertiary) { nav.navigate("spike3") }

            SpikeCard("Spike 4", "Unlock Prompt",
                "Press Start, lock the phone. After 15 s the service calls requestDismissKeyguard. Logs the callback.",
                Icons.Default.Lock, MaterialTheme.colorScheme.error) { nav.navigate("spike4") }

            SpikeCard("Spike 5", "Test Call",
                "Type a number, list phone accounts, place call via ACTION_CALL or TelecomManager.",
                Icons.Default.Call, MaterialTheme.colorScheme.primary) { nav.navigate("spike5") }

            SpikeCard("Spike 6", "Screen-Off Stability",
                "Runs a foreground service for 30+ min screen-off. Heartbeat every 10 s. Optional partial wake lock.",
                Icons.Default.BatteryFull, MaterialTheme.colorScheme.secondary) { nav.navigate("spike6") }

            Spacer(Modifier.height(8.dp))
            Text(
                "Logs → adb pull /sdcard/Android/data/com.spidey.spikes.debug/files/spidey_spike_log.txt",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SpikeCard(
    number: String,
    title: String,
    description: String,
    icon: ImageVector,
    accentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(32.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("$number – $title", fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = "Open")
        }
    }
}
