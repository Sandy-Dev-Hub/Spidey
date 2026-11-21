package com.spidey.assistant.voice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.spidey.assistant.safety.PermissionManager

/**
 * Receives BOOT_COMPLETED and restarts SPIDEY.
 *
 * Android 14+ (API 34) no longer grants a BFSL (Background Foreground Service Launch)
 * exemption to BOOT_COMPLETED receivers for foreground services with `foregroundServiceType=microphone`.
 * Calling startForegroundService() directly from here silently fails on Android 15.
 *
 * Fix: on API 34+, we post a high-visibility "Tap to start SPIDEY" notification instead.
 * The user taps it → the system launches the service from the PendingIntent context,
 * which IS allowed. This is the only reliable restart path after reboot.
 *
 * On API < 34 (Android 13 and below), direct start still works.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "BootReceiver received action: $action")

        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON"
        ) return

        val permManager = PermissionManager(context)
        if (!permManager.hasRecordAudio()) {
            Log.w(TAG, "Cannot start WakeWordService: RECORD_AUDIO permission missing")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+: direct startForegroundService from BOOT_COMPLETED is blocked for
            // microphone-type services. Post a notification so the user can tap to start.
            Log.i(TAG, "Android 14+ boot: posting tap-to-start notification")
            WakeWordService.postRestartNotification(context, "Phone restarted")
        } else {
            // Android 13 and below: direct start is still allowed
            Log.i(TAG, "Boot completed, starting WakeWordService directly")
            WakeWordService.start(context)
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
