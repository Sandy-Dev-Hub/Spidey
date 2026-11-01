
package com.spidey.assistant.calling

import android.content.Context
import android.content.pm.PackageManager
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat

data class CallingAccount(
    val id: String,
    val label: String,
    val simSlotIndex: Int,
    val handle: PhoneAccountHandle? = null
)

/**
 * Discovers live SIM and phone accounts via Android Telecom and Subscription APIs.
 * Never assumes static SIM 1/SIM 2 mappings; reads dynamic carrier labels (e.g. Airtel, Jio).
 */
class CallingAccountRepository(private val context: Context? = null) {

    fun getCallingAccounts(): List<CallingAccount> {
        val ctx = context ?: return emptyList()
        val hasStatePerm = ContextCompat.checkSelfPermission(
            ctx,
            android.Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasStatePerm) return emptyList()

        return try {
            val tm = ctx.getSystemService(TelecomManager::class.java) ?: return emptyList()
            val sm = ctx.getSystemService(SubscriptionManager::class.java)

            // Dynamic carrier name lookup from SubscriptionManager
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
            } catch (_: Exception) {}

            val capableAccounts = tm.callCapablePhoneAccounts ?: emptyList()
            capableAccounts.mapIndexed { i, handle ->
                val account: PhoneAccount? = try {
                    tm.getPhoneAccount(handle)
                } catch (_: Exception) { null }

                val labelFromAccount = account?.label?.toString()
                val label = carrierBySubId[handle.id]
                    ?: carrierBySlot[i]
                    ?: labelFromAccount
                    ?: "SIM ${i + 1}"

                CallingAccount(
                    id = handle.id ?: i.toString(),
                    label = label,
                    simSlotIndex = i,
                    handle = handle
                )
            }
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun matchAccount(query: String, accounts: List<CallingAccount>): CallingAccount? {
        val norm = query.trim().lowercase()

        // 1. Exact or partial carrier match (e.g. "airtel", "jio")
        for (acc in accounts) {
            val normLabel = acc.label.lowercase()
            if (normLabel.contains(norm) || norm.contains(normLabel)) {
                return acc
            }
        }

        // 2. Slot index / ordinal matching ("sim 1", "sim 2", "1", "2", "first", "second")
        if (norm in setOf("sim 1", "sim1", "1", "first", "the first one", "first one")) {
            return accounts.getOrNull(0)
        }
        if (norm in setOf("sim 2", "sim2", "2", "second", "the second one", "second one")) {
            return accounts.getOrNull(1)
        }

        return null
    }
}
