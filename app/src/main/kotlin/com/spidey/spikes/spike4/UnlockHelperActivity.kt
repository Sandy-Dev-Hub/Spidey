package com.spidey.spikes.spike4

import android.app.Activity
import android.app.KeyguardManager
import android.os.Bundle
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.content.Intent
import android.os.Build
import android.view.WindowManager
import com.spidey.spikes.service.SpikeForegroundService
import com.spidey.spikes.util.SpikeLogger

/**
 * Transparent helper activity launched by the service to perform the unlock prompt.
 *
 * The Activity framework is required for requestDismissKeyguard – it can only be
 * called on an Activity's window. This activity finishes immediately after the callback.
 *
 * Phase 0 spike only.
 */
class UnlockHelperActivity : Activity() {

    private var hasPrompted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show over lock screen and turn screen on
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
    }

    override fun onResume() {
        super.onResume()
        promptDismissKeyguard()
    }

    private fun promptDismissKeyguard() {
        if (hasPrompted) return
        hasPrompted = true

        val km = getSystemService(KeyguardManager::class.java)

        if (!km.isKeyguardLocked) {
            SpikeLogger.log("SPIKE4", "UnlockHelper: keyguard not locked – nothing to dismiss")
            broadcast("keyguard was NOT locked when UnlockHelper ran")
            stopService(SpikeForegroundService.intentFor(this, SpikeForegroundService.ACTION_STOP))
            finish()
            return
        }

        SpikeLogger.log("SPIKE4", "UnlockHelper: calling requestDismissKeyguard")

        km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() {
                SpikeLogger.log("SPIKE4", "PASS: onDismissSucceeded – phone unlocked!")
                broadcast("PASS: onDismissSucceeded fired – phone unlocked")
                stopService(SpikeForegroundService.intentFor(this@UnlockHelperActivity, SpikeForegroundService.ACTION_STOP))
                finish()
            }

            override fun onDismissError() {
                SpikeLogger.log("SPIKE4", "FAIL: onDismissError – biometric/PIN failed")
                broadcast("FAIL: onDismissError – biometric or PIN failed")
                stopService(SpikeForegroundService.intentFor(this@UnlockHelperActivity, SpikeForegroundService.ACTION_STOP))
                finish()
            }

            override fun onDismissCancelled() {
                SpikeLogger.log("SPIKE4", "INFO: onDismissCancelled – user cancelled prompt")
                broadcast("INFO: onDismissCancelled – user cancelled prompt")
                stopService(SpikeForegroundService.intentFor(this@UnlockHelperActivity, SpikeForegroundService.ACTION_STOP))
                finish()
            }
        })
    }

    private fun broadcast(result: String) {
        LocalBroadcastManager.getInstance(this).sendBroadcast(
            Intent(SpikeForegroundService.ACTION_SPIKE4_RESULT)
                .putExtra(SpikeForegroundService.EXTRA_LOG, result)
        )
    }
}
