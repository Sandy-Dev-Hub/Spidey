package com.spidey.assistant.voice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.spidey.assistant.safety.PermissionManager

/**
 * Automatically starts SPIDEY WakeWordService on device boot so the user
 * can say "Hey Spider" immediately without needing to open the app or click Start.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "BootReceiver received action: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED || action == "android.intent.action.QUICKBOOT_POWERON") {
            val permManager = PermissionManager(context)
            if (permManager.hasRecordAudio()) {
                Log.i(TAG, "RECORD_AUDIO granted, starting WakeWordService on boot")
                WakeWordService.start(context)
            } else {
                Log.w(TAG, "Cannot start WakeWordService on boot: RECORD_AUDIO permission missing")
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
