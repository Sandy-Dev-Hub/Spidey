package com.spidey.assistant.calling

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import com.spidey.assistant.safety.PermissionManager

sealed interface CallInitiationResult {
    data class Direct(val route: String) : CallInitiationResult
    data class FallbackDialer(val message: String) : CallInitiationResult
    data class Failure(val reason: String, val message: String) : CallInitiationResult
    data object NeedsUnlock : CallInitiationResult
}

/**
 * Coordinates placing phone calls via TelecomManager with direct SIM selection,
 * or gracefully falling back to ACTION_CALL or the system dialer (ACTION_DIAL).
 * Always validates keyguard lock status first.
 */
class CallCoordinator(
    private val context: Context? = null,
    private val permissionManager: PermissionManager? = null
) {
    fun isDeviceLocked(): Boolean {
        val ctx = context ?: return false
        val km = ctx.getSystemService(KeyguardManager::class.java) ?: return false
        return km.isKeyguardLocked || km.isDeviceLocked
    }

    fun initiateCall(
        phoneNumber: String,
        accountHandle: PhoneAccountHandle? = null
    ): CallInitiationResult {
        if (isDeviceLocked()) {
            return CallInitiationResult.NeedsUnlock
        }

        val cleanNumber = phoneNumber.filter { it.isDigit() || it == '+' }
        if (cleanNumber.isBlank()) {
            return CallInitiationResult.Failure(
                reason = "Invalid phone number",
                message = "The phone number is invalid."
            )
        }

        val ctx = context ?: return CallInitiationResult.Direct("Dummy")
        val uri = Uri.parse("tel:$cleanNumber")

        // 1. If CALL_PHONE is granted, try TelecomManager.placeCall with SIM account
        if (permissionManager?.hasCallPhone() == true) {
            try {
                val tm = ctx.getSystemService(TelecomManager::class.java)
                if (tm != null) {
                    val extras = Bundle()
                    if (accountHandle != null) {
                        extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, accountHandle)
                    }
                    tm.placeCall(uri, extras)
                    Log.i(TAG, "Call placed via TelecomManager.placeCall with account: ${accountHandle?.id}")
                    return CallInitiationResult.Direct("TelecomManager")
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "TelecomManager SecurityException: ${e.message}")
            } catch (e: Exception) {
                Log.w(TAG, "TelecomManager placeCall failed: ${e.message}")
            }

            // Fallback to ACTION_CALL
            try {
                val callIntent = Intent(Intent.ACTION_CALL, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(callIntent)
                Log.i(TAG, "Call placed via Intent.ACTION_CALL")
                return CallInitiationResult.Direct("ACTION_CALL")
            } catch (e: Exception) {
                Log.w(TAG, "ACTION_CALL failed: ${e.message}")
            }
        }

        // 2. Fallback: Open system dialer (ACTION_DIAL)
        return try {
            val dialIntent = Intent(Intent.ACTION_DIAL, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(dialIntent)
            Log.i(TAG, "Opened system dialer via ACTION_DIAL")
            CallInitiationResult.FallbackDialer("I've opened the dialer with the number ready.")
        } catch (e: Exception) {
            Log.e(TAG, "ACTION_DIAL failed: ${e.message}")
            CallInitiationResult.Failure(
                reason = e.message ?: "Could not open dialer",
                message = "I couldn't place the call or open the dialer."
            )
        }
    }

    companion object {
        private const val TAG = "CallCoordinator"
    }
}
