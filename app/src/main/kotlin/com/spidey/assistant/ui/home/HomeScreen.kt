package com.spidey.assistant.ui.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.spidey.assistant.core.AssistantState
import com.spidey.assistant.voice.WakeWordService

@Composable
fun HomeScreen(
    onNavigateToSpikes: () -> Unit = {},
    onNavigateToAliases: () -> Unit = {}
) {
    val context = LocalContext.current
    val isServiceRunning by WakeWordService.isServiceRunning.collectAsState()
    val activeManager by WakeWordService.activeManager.collectAsState()

    val assistantState = activeManager?.state?.collectAsState()?.value ?: AssistantState.SLEEPING
    val lastHeard = activeManager?.lastHeardText?.collectAsState()?.value ?: ""
    val lastSpoken = activeManager?.lastSpokenResponse?.collectAsState()?.value ?: ""
    val errorText = activeManager?.errorText?.collectAsState()?.value

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }

    var hasContactsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val micPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted && !isServiceRunning) {
            WakeWordService.start(context)
        }
    }

    LaunchedEffect(hasMicPermission) {
        if (hasMicPermission && !isServiceRunning) {
            WakeWordService.start(context)
        }
    }

    val contactsPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasContactsPermission = granted }

    val notifPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasNotificationPermission = granted }

    val scrollState = rememberScrollState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Top Header ──────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "SPIDEY",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Voice & Contacts • Phase 2",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onNavigateToAliases,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Aliases", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = onNavigateToSpikes,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Science, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Spikes", fontSize = 12.sp)
                    }
                }
            }

            // ── Hero State Visualizer ───────────────────────────────
            AssistantStatusCard(
                state = assistantState,
                isServiceRunning = isServiceRunning
            )

            // ── Main Action Controls ────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Voice Assistant Controls",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isServiceRunning) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primary
                            ),
                            onClick = {
                                if (isServiceRunning) {
                                    WakeWordService.stop(context)
                                } else {
                                    if (hasMicPermission) {
                                        WakeWordService.start(context)
                                    } else {
                                        micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            }
                        ) {
                            Icon(
                                if (isServiceRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = null
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (isServiceRunning) "Stop Assistant" else "Start Assistant")
                        }

                        if (isServiceRunning) {
                            OutlinedButton(
                                shape = RoundedCornerShape(12.dp),
                                onClick = {
                                    activeManager?.onWakeWordDetected()
                                }
                            ) {
                                Icon(Icons.Default.Hearing, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Wake")
                            }
                        }
                    }

                    Text(
                        text = if (isServiceRunning)
                            "• Voice trigger: say \"Hey Spider\" or \"Spider\"\n• Core commands: \"Hello\", \"Open YouTube\""
                        else
                            "Press 'Start Assistant' to start the offline foreground voice loop.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Spoken Transcript Cards ─────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Last Heard Speech", fontWeight = FontWeight.SemiBold)
                    }

                    Text(
                        text = if (lastHeard.isNotBlank()) "\"$lastHeard\"" else "(No speech detected yet)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = if (lastHeard.isNotBlank()) FontFamily.SansSerif else FontFamily.Monospace,
                        color = if (lastHeard.isNotBlank()) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(4.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.VolumeUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Spidey Response", fontWeight = FontWeight.SemiBold)
                    }

                    Text(
                        text = if (lastSpoken.isNotBlank()) "\"$lastSpoken\"" else "(No response yet)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (lastSpoken.isNotBlank()) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (errorText != null) {
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = errorText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // ── Permissions Card ────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Permissions & Prerequisites", fontWeight = FontWeight.SemiBold)
                    }

                    PermissionRow(
                        title = "Microphone (RECORD_AUDIO)",
                        isGranted = hasMicPermission,
                        onRequest = { micPermLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                    )

                    PermissionRow(
                        title = "Contacts (READ_CONTACTS)",
                        isGranted = hasContactsPermission,
                        onRequest = { contactsPermLauncher.launch(Manifest.permission.READ_CONTACTS) }
                    )

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        PermissionRow(
                            title = "Notifications (POST_NOTIFICATIONS)",
                            isGranted = hasNotificationPermission,
                            onRequest = { notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistantStatusCard(
    state: AssistantState,
    isServiceRunning: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val (statusLabel, statusDesc, color) = when {
        !isServiceRunning -> Triple(
            "OFFLINE",
            "Service stopped. Tap Start Assistant to listen.",
            MaterialTheme.colorScheme.outline
        )
        state == AssistantState.LISTENING -> Triple(
            "LISTENING",
            "Capturing your command...",
            Color(0xFF00B0FF) // Vibrant Cyan
        )
        state == AssistantState.PROCESSING -> Triple(
            "PROCESSING",
            "Parsing intent and routing action...",
            Color(0xFFFFB300) // Amber
        )
        state == AssistantState.SPEAKING -> Triple(
            "SPEAKING",
            "Assistant is replying...",
            Color(0xFF00E676) // Bright Emerald
        )
        state == AssistantState.EXECUTING -> Triple(
            "EXECUTING",
            "Performing action...",
            Color(0xFFAB47BC) // Purple
        )
        state == AssistantState.AWAITING_UNLOCK -> Triple(
            "AWAITING UNLOCK",
            "Please unlock your phone to continue.",
            Color(0xFFFF7043) // Deep Orange
        )
        state == AssistantState.ERROR -> Triple(
            "ERROR",
            "Interaction error.",
            MaterialTheme.colorScheme.error
        )
        else -> Triple(
            "STANDBY (SLEEPING)",
            "Waiting for \"Hey Spider\"...",
            Color(0xFF7C4DFF) // Deep Violet
        )
    }

    val animatedColor by animateColorAsState(targetValue = color, label = "color")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = animatedColor.copy(alpha = 0.12f)
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(listOf(animatedColor, animatedColor.copy(alpha = 0.3f)))
        )
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .scale(if (isServiceRunning && (state == AssistantState.LISTENING || state == AssistantState.SPEAKING)) pulseScale else 1f)
                    .clip(CircleShape)
                    .background(animatedColor.copy(alpha = 0.25f))
                    .border(2.dp, animatedColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        !isServiceRunning -> Icons.Default.MicOff
                        state == AssistantState.SPEAKING -> Icons.Default.VolumeUp
                        state == AssistantState.LISTENING -> Icons.Default.Hearing
                        else -> Icons.Default.Mic
                    },
                    contentDescription = null,
                    tint = animatedColor,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = statusLabel,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    color = animatedColor
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = statusDesc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    isGranted: Boolean,
    onRequest: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
        }

        if (isGranted) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF00C853),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("Granted", fontSize = 12.sp, color = Color(0xFF00C853))
            }
        } else {
            Button(
                onClick = onRequest,
                shape = RoundedCornerShape(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("Allow", fontSize = 12.sp)
            }
        }
    }
}
