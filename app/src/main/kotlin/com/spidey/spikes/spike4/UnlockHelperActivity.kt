package com.spidey.spikes.spike4

import android.app.Activity
import android.app.KeyguardManager
import android.os.Bundle
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.content.Intent
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Tell the window to show over the lock screen and turn on the screen
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        val km = getSystemService(KeyguardManager::class.java)

        if (!km.isKeyguardLocked) {
            SpikeLogger.log("SPIKE4", "UnlockHelper: keyguard not locked – nothing to dismiss")
            broadcast("keyguard was NOT locked when UnlockHelper ran")
            finish()
            return
        }

        SpikeLogger.log("SPIKE4", "UnlockHelper: calling requestDismissKeyguard")

        km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() {
                SpikeLogger.log("SPIKE4", "onDismissSucceeded – phone unlocked!")
                broadcast("PASS: onDismissSucceeded fired – phone unlocked")
                finish()
            }

            override fun onDismissError() {
                SpikeLogger.log("SPIKE4", "onDismissError – biometric/PIN failed or cancelled")
                broadcast("FAIL: onDismissError – biometric or PIN failed/cancelled")
                finish()
            }

            override fun onDismissCancelled() {
                SpikeLogger.log("SPIKE4", "onDismissCancelled – user cancelled or no lock set")
                broadcast("INFO: onDismissCancelled – no secure lock or user dismissed")
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
