package com.spidey.assistant.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.spidey.assistant.util.DiagnosticsTracker
import com.spidey.assistant.voice.WakeWordService
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SamsungChecklistScreen(
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val diagnostics by DiagnosticsTracker.data.collectAsState()
    val isServiceRunning by WakeWordService.isServiceRunning.collectAsState()

    var isBatteryUnrestricted by remember { mutableStateOf(false) }
    var isMicGranted by remember { mutableStateOf(false) }
    var isNotificationEnabled by remember { mutableStateOf(false) }
    var isAccessibilityEnabled by remember { mutableStateOf(false) }

    fun refreshChecks() {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        isBatteryUnrestricted = pm?.isIgnoringBatteryOptimizations(context.packageName) == true

        isMicGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        isNotificationEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()

        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        isAccessibilityEnabled = enabledServices.contains(context.packageName)
    }

    LaunchedEffect(Unit) {
        while (true) {
            refreshChecks()
            delay(1500)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Samsung One UI 7 Checklist",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF1F5F9)
                        )
                        Text(
                            text = "Phase 6: Background Reliability",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFFF1F5F9)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
            )
        },
        containerColor = Color(0xFF0B0F19)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Samsung Galaxy A14 5G Tuning",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Samsung One UI aggressively restricts background microphone services. Follow this 5-point checklist to ensure SPIDEY runs permanently with screen off.",
                        fontSize = 13.sp,
                        color = Color(0xFFCBD5E1),
                        lineHeight = 18.sp
                    )
                }
            }

            // Checklist Items
            ChecklistItem(
                title = "1. Unrestricted Battery",
                description = "Prevents Android from freezing the CPU wake-word loop during deep doze.",
                isPassed = isBatteryUnrestricted,
                icon = Icons.Default.Warning,
                buttonLabel = if (isBatteryUnrestricted) "Optimized" else "Set Unrestricted",
                onAction = {
                    try {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        context.startActivity(fallback)
                    }
                }
            )

            ChecklistItem(
                title = "2. Samsung 'Never Auto Sleeping Apps'",
                description = "Settings → Battery → Background usage limits → 'Never auto sleeping apps' → Add SPIDEY. Ensures One UI doesn't sleep the service after 3 days.",
                isPassed = isBatteryUnrestricted, // Proxy indicator
                icon = Icons.Default.Settings,
                buttonLabel = "Open Battery Settings",
                onAction = {
                    try {
                        val intent = Intent().apply {
                            action = "android.settings.APPLICATION_DETAILS_SETTINGS"
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                }
            )

            ChecklistItem(
                title = "3. Microphone Permission",
                description = "Required for offline keyword spotting on hardware wideband mic.",
                isPassed = isMicGranted,
                icon = Icons.Default.Info,
                buttonLabel = if (isMicGranted) "Granted" else "Grant Permission",
                onAction = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }
            )

            ChecklistItem(
                title = "4. Persistent Foreground Notification",
                description = "Android 15 requires an active foreground notification to permit background mic capture.",
                isPassed = isNotificationEnabled,
                icon = Icons.Default.Notifications,
                buttonLabel = if (isNotificationEnabled) "Active" else "Enable Notifications",
                onAction = {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                    context.startActivity(intent)
                }
            )

            ChecklistItem(
                title = "5. Hands-Free Accessibility Service",
                description = "Enables auto-clicking Send in WhatsApp and Gmail after spoken confirmation.",
                isPassed = isAccessibilityEnabled,
                icon = Icons.Default.Build,
                buttonLabel = if (isAccessibilityEnabled) "Enabled" else "Open Accessibility",
                onAction = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    context.startActivity(intent)
                }
            )

            // Diagnostics Summary
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Reliability Counters",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF1F5F9)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isServiceRunning) Color(0xFF22C55E) else Color(0xFFEF4444))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isServiceRunning) "ALIVE" else "STOPPED",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isServiceRunning) Color(0xFF22C55E) else Color(0xFFEF4444)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    DiagnosticStatRow("Uptime", diagnostics.formattedRunningTime)
                    DiagnosticStatRow("Service Starts", "${diagnostics.serviceStarts}")
                    DiagnosticStatRow("Wake Detections", "${diagnostics.wakeDetections}")
                    DiagnosticStatRow("False Wakes (silence)", "${diagnostics.falseWakes}")
                    DiagnosticStatRow("Speech Recognizer Errors", "${diagnostics.speechErrors}")
                    DiagnosticStatRow("Audio Focus Interruptions", "${diagnostics.focusInterruptions}")
                    DiagnosticStatRow("Estimated Battery Cost", diagnostics.batteryDrainPerHourEstimate)

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { DiagnosticsTracker.resetCounters() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Reset Counters", fontSize = 12.sp, color = Color(0xFF94A3B8))
                        }

                        Button(
                            onClick = {
                                WakeWordService.stop(context)
                                WakeWordService.start(context)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Restart Service", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChecklistItem(
    title: String,
    description: String,
    isPassed: Boolean,
    icon: ImageVector,
    buttonLabel: String,
    onAction: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isPassed) Color(0xFF22C55E) else Color(0xFFF59E0B),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFF1F5F9)
                    )
                }

                Icon(
                    imageVector = if (isPassed) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isPassed) Color(0xFF22C55E) else Color(0xFFF59E0B),
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = description,
                fontSize = 12.sp,
                color = Color(0xFF94A3B8),
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isPassed) Color(0xFF334155) else Color(0xFF2563EB)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = buttonLabel,
                    fontSize = 13.sp,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun DiagnosticStatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 13.sp, color = Color(0xFF94A3B8))
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFFF1F5F9)
        )
    }
}
