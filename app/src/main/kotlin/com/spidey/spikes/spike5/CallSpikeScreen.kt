package com.spidey.spikes.spike5

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.spidey.spikes.util.SpikeLogger
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.telephony.SubscriptionManager
import kotlinx.coroutines.launch

/**
 * Spike 5 – Test Call
 *
 * The number is typed by the user – never hard-coded.
 *
 * Tests:
 *   Button A  "Call via ACTION_CALL"   – system dialer handles everything
 *   Button B  "Call via TelecomManager" – direct SIM selection
 *
 * READ_PHONE_STATE and READ_PHONE_NUMBERS are needed to list phone accounts.
 * CALL_PHONE is needed for both routes.
 *
 * Phase 0 only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallSpikeScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var hasCallPerm by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CALL_PHONE)
            == PackageManager.PERMISSION_GRANTED)
    }
    var hasStatePerm by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE)
            == PackageManager.PERMISSION_GRANTED)
    }
    var hasNumbersPerm by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasCallPerm = grants[Manifest.permission.CALL_PHONE] == true
        hasStatePerm = grants[Manifest.permission.READ_PHONE_STATE] == true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            hasNumbersPerm = grants[Manifest.permission.READ_PHONE_NUMBERS] == true
        }
    }

    var phoneNumber by remember { mutableStateOf("") }
    var accounts by remember { mutableStateOf<List<Pair<String, PhoneAccountHandle>>>(emptyList()) }
    var selectedAccountIdx by remember { mutableIntStateOf(0) }
    val logLines = remember { mutableStateListOf<String>() }
    val listState = rememberLazyListState()

    fun addLog(msg: String) {
        SpikeLogger.log("SPIKE5", msg)
        logLines.add(msg)
        scope.launch { listState.animateScrollToItem(maxOf(0, logLines.size - 1)) }
    }

    fun loadAccounts() {
        if (!hasStatePerm) { addLog("READ_PHONE_STATE not granted"); return }
        try {
            val tm = ctx.getSystemService(TelecomManager::class.java)
            val sm = ctx.getSystemService(SubscriptionManager::class.java)

            // Collect carrier names from SubscriptionManager
            val carrierBySlot = mutableMapOf<Int, String>()
            val carrierBySubId = mutableMapOf<String, String>()
            try {
                sm?.activeSubscriptionInfoList?.forEach { sub ->
                    val carrier = sub.displayName?.toString()
                        ?: sub.carrierName?.toString()
                        ?: "SIM ${sub.simSlotIndex + 1}"
                    carrierBySlot[sub.simSlotIndex] = carrier
                    carrierBySubId[sub.subscriptionId.toString()] = carrier
                    carrierBySubId[sub.iccId ?: ""] = carrier
                }
            } catch (e: Exception) {
                addLog("SubscriptionManager error: ${e.message}")
            }

            val list = tm.callCapablePhoneAccounts
            accounts = list.mapIndexed { i, handle ->
                val account: PhoneAccount? = tm.getPhoneAccount(handle)
                val labelFromAccount = account?.label?.toString()
                val carrier = carrierBySubId[handle.id]
                    ?: carrierBySlot[i]
                    ?: labelFromAccount
                    ?: "SIM ${i + 1}"
                carrier to handle
            }
            addLog("Found ${accounts.size} phone account(s): ${accounts.map { it.first }}")
        } catch (e: SecurityException) {
            addLog("SecurityException listing accounts: ${e.message}")
        }
    }

    LaunchedEffect(hasStatePerm, hasNumbersPerm) {
        if (hasStatePerm) loadAccounts()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Spike 5 – Test Call") }) }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Permissions ─────────────────────────────────────────────
            val allPermsGranted = hasCallPerm && hasStatePerm && hasNumbersPerm
            Card(colors = CardDefaults.cardColors(
                containerColor = if (allPermsGranted)
                    MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("CALL_PHONE: ${if (hasCallPerm) "✅" else "❌"}",
                        style = MaterialTheme.typography.bodySmall)
                    Text("READ_PHONE_STATE: ${if (hasStatePerm) "✅" else "❌"}",
                        style = MaterialTheme.typography.bodySmall)
                    Text("READ_PHONE_NUMBERS: ${if (hasNumbersPerm) "✅" else "❌"}",
                        style = MaterialTheme.typography.bodySmall)
                    if (!allPermsGranted) {
                        Button(onClick = {
                            val permsToRequest = mutableListOf(
                                Manifest.permission.CALL_PHONE,
                                Manifest.permission.READ_PHONE_STATE
                            )
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                permsToRequest.add(Manifest.permission.READ_PHONE_NUMBERS)
                            }
                            permLauncher.launch(permsToRequest.toTypedArray())
                        }) { Text("Grant Permissions") }
                    }
                }
            }

            // ── Phone accounts ───────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Phone accounts (${accounts.size}):", style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f))
                IconButton(onClick = { loadAccounts() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reload accounts")
                }
            }
            if (accounts.isNotEmpty()) {
                accounts.forEachIndexed { idx, (label, _) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selectedAccountIdx == idx,
                            onClick = { selectedAccountIdx = idx })
                        Text("$label (index $idx)", style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                Text("No accounts found (need READ_PHONE_STATE + active SIM)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            HorizontalDivider()

            // ── Number input ─────────────────────────────────────────────
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it.filter { c -> c.isDigit() || c == '+' } },
                label = { Text("Phone number to dial") },
                placeholder = { Text("+91XXXXXXXXXX") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // ── Call buttons ─────────────────────────────────────────────
            val canCall = hasCallPerm && phoneNumber.isNotBlank()

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Route A: ACTION_CALL (simplest, no SIM selection)
                Button(
                    enabled = canCall,
                    onClick = {
                        addLog("Attempting ACTION_CALL to ${phoneNumber.take(4)}****")
                        try {
                            val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNumber"))
                            callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            ctx.startActivity(callIntent)
                            addLog("ACTION_CALL intent fired – check call screen")
                        } catch (e: Exception) {
                            addLog("ACTION_CALL failed: ${e.message}")
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Call, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("via ACTION_CALL")
                }

                // Route B: TelecomManager.placeCall (allows SIM selection)
                OutlinedButton(
                    enabled = canCall && accounts.isNotEmpty(),
                    onClick = {
                        val handle = accounts.getOrNull(selectedAccountIdx)?.second
                        addLog("Attempting TelecomManager.placeCall | account=${accounts.getOrNull(selectedAccountIdx)?.first}")
                        try {
                            val tm = ctx.getSystemService(TelecomManager::class.java)
                            val extras = Bundle().apply {
                                if (handle != null)
                                    putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
                            }
                            tm.placeCall(Uri.parse("tel:$phoneNumber"), extras)
                            addLog("placeCall called – check call screen. Handle=$handle")
                        } catch (e: SecurityException) {
                            addLog("SecurityException: ${e.message}")
                        } catch (e: Exception) {
                            addLog("placeCall failed: ${e.message}")
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("via TelecomManager")
                }
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
