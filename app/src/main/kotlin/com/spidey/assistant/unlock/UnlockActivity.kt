package com.spidey.assistant.unlock

import android.app.Activity
import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager

/**
 * Transparent helper activity shown over lock screen to call requestDismissKeyguard.
 */
class UnlockActivity : Activity() {

    private var hasPrompted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
        if (!hasPrompted) {
            hasPrompted = true
            promptDismiss()
        }
    }

    private fun promptDismiss() {
        val km = getSystemService(KeyguardManager::class.java)
        if (km == null || !km.isKeyguardLocked) {
            Log.d(TAG, "Keyguard not locked, treating as unlocked")
            UnlockCoordinator.notifyUnlockSuccess()
            finish()
            return
        }

        km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() {
                Log.d(TAG, "Keyguard dismiss succeeded")
                UnlockCoordinator.notifyUnlockSuccess()
                finish()
            }

            override fun onDismissError() {
                Log.w(TAG, "Keyguard dismiss error callback – keeping 30s unlock window active")
                // On Samsung One UI, focus changes or transient auth attempts trigger this.
                // Do not cancel coordinator; finish activity so the lockscreen bouncer has full control.
                finish()
            }

            override fun onDismissCancelled() {
                Log.i(TAG, "Keyguard dismiss cancelled or bouncer shown – keeping 30s unlock window active")
                // On Samsung One UI, onDismissCancelled is fired as soon as the bouncer takes foreground.
                // Keep the 30s coordinator window alive waiting for ACTION_USER_PRESENT.
                finish()
            }
        })
    }

    companion object {
        private const val TAG = "UnlockActivity"
    }
}
