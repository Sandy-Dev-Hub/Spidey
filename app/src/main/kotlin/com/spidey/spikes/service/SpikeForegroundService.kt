package com.spidey.spikes.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.*
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.spidey.spikes.SpikeApp
import com.spidey.spikes.util.SpikeLogger

/**
 * Single foreground service covering all Phase-0 spikes.
 *
 * Started with one of the ACTION_* constants; results are broadcast locally
 * so spike screens can display them in real-time.
 *
 * Phase 0 only – to be deleted before Phase 1.
 */
class SpikeForegroundService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var countDownTimer: CountDownTimer? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var heartbeatTimer: CountDownTimer? = null

    // ── Lifecycle ──────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        SpikeLogger.log("SERVICE", "SpikeForegroundService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY
        SpikeLogger.log("SERVICE", "onStartCommand action=$action")

        when (action) {
            ACTION_START_SPIKE3_DELAYED -> startDelayedBgLaunch(DELAY_MS)
            ACTION_START_SPIKE4_DELAYED -> startDelayedUnlock(DELAY_MS)
            ACTION_START_SPIKE6_SCREEN_OFF -> startScreenOffStability(
                useWakeLock = intent.getBooleanExtra(EXTRA_USE_WAKE_LOCK, false)
            )
            ACTION_STOP -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        SpikeLogger.log("SERVICE", "SpikeForegroundService destroyed")
        countDownTimer?.cancel()
        heartbeatTimer?.cancel()
        speechRecognizer?.destroy()
        releaseWakeLock()
        super.onDestroy()
    }

    // ── Spike 3: Delayed background launch ────────────────────────────

    private fun startDelayedBgLaunch(delayMs: Long) {
        startInForeground("Spike 3: BG Launch in ${delayMs / 1000}s…")
        SpikeLogger.log("SPIKE3", "Countdown started – ${delayMs / 1000}s. Lock phone or switch app now.")
        broadcast(ACTION_SPIKE3_TICK, EXTRA_COUNTDOWN_SEC, delayMs / 1000)

        countDownTimer = object : CountDownTimer(delayMs, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val sec = millisUntilFinished / 1000
                updateNotification("Spike 3: BG Launch in ${sec}s…")
                broadcast(ACTION_SPIKE3_TICK, EXTRA_COUNTDOWN_SEC, sec)
            }

            override fun onFinish() {
                SpikeLogger.log("SPIKE3", "Countdown finished – attempting background launch")
                performBgLaunch()
            }
        }.start()
    }

    private fun performBgLaunch() {
        val km = getSystemService(KeyguardManager::class.java)
        val pm = getSystemService(PowerManager::class.java)
        val isLocked = km.isKeyguardLocked
        val isScreenOn = pm.isInteractive

        SpikeLogger.log("SPIKE3", "Phone state: locked=$isLocked screenOn=$isScreenOn")

        // ── Route 1: Plain startActivity with NEW_TASK ─────────────────
        try {
            val ytIntent = packageManager.getLaunchIntentForPackage("com.google.android.youtube")
                ?: Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com"))
            ytIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(ytIntent)
            SpikeLogger.log("SPIKE3", "Route 1 (startActivity) called – no exception. locked=$isLocked screenOn=$isScreenOn")
            broadcast(ACTION_SPIKE3_RESULT, EXTRA_LOG,
                "Route 1 startActivity: called OK | locked=$isLocked screenOn=$isScreenOn")
        } catch (e: Exception) {
            SpikeLogger.log("SPIKE3", "Route 1 failed: ${e.message}")
        }

        // ── Route 2: High-priority full-screen-intent notification ─────
        val fullScreenIntent = PendingIntent.getActivity(
            this, 200,
            packageManager.getLaunchIntentForPackage("com.google.android.youtube")
                ?: Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, SpikeApp.CHANNEL_SPIKE_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Spike 3 – BG Launch")
            .setContentText("Tap to open YouTube (full-screen-intent route)")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setFullScreenIntent(fullScreenIntent, true)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID_RESULT, notif)
        SpikeLogger.log("SPIKE3", "Route 2 (full-screen notification) fired")

        updateNotification("Spike 3: done – see log")
        stopSelf()
    }

    // ── Spike 4: Delayed unlock prompt ────────────────────────────────

    private fun startDelayedUnlock(delayMs: Long) {
        startInForeground("Spike 4: Unlock prompt in ${delayMs / 1000}s…")
        SpikeLogger.log("SPIKE4", "Countdown started – ${delayMs / 1000}s. Lock phone now.")
        broadcast(ACTION_SPIKE4_TICK, EXTRA_COUNTDOWN_SEC, delayMs / 1000)

        countDownTimer = object : CountDownTimer(delayMs, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val sec = millisUntilFinished / 1000
                updateNotification("Spike 4: Unlock in ${sec}s…")
                broadcast(ACTION_SPIKE4_TICK, EXTRA_COUNTDOWN_SEC, sec)
            }

            override fun onFinish() {
                SpikeLogger.log("SPIKE4", "Countdown finished – launching UnlockHelperActivity")
                performUnlockAttempt()
            }
        }.start()
    }

    private fun performUnlockAttempt() {
        val km = getSystemService(KeyguardManager::class.java)
        val pm = getSystemService(PowerManager::class.java)
        val isLocked = km.isKeyguardLocked
        val isScreenOn = pm.isInteractive
        SpikeLogger.log("SPIKE4", "Before unlock: locked=$isLocked screenOn=$isScreenOn")

        // 1. Physically turn the screen on using WakeLock
        try {
            @Suppress("DEPRECATION")
            val screenWakeLock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "spidey:unlock_spike_screen"
            )
            screenWakeLock.acquire(5000L)
            SpikeLogger.log("SPIKE4", "Acquired screen-bright wake lock (5s)")
        } catch (e: Exception) {
            SpikeLogger.log("SPIKE4", "WakeLock error: ${e.message}")
        }

        val helperIntent = Intent(this, com.spidey.spikes.spike4.UnlockHelperActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)

        // 2. Direct startActivity attempt
        try {
            startActivity(helperIntent)
            SpikeLogger.log("SPIKE4", "startActivity(UnlockHelperActivity) called")
        } catch (e: Exception) {
            SpikeLogger.log("SPIKE4", "startActivity failed: ${e.message}")
        }

        // 3. High-priority Full-Screen Intent notification (ensures display over keyguard on Android 14/15)
        try {
            val pendingIntent = PendingIntent.getActivity(
                this, 401, helperIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notif = NotificationCompat.Builder(this, SpikeApp.CHANNEL_SPIKE_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setContentTitle("Spidey – Unlock Prompt")
                .setContentText("Tap or authenticate to unlock")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(pendingIntent, true)
                .setAutoCancel(true)
                .build()
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID_RESULT, notif)
            SpikeLogger.log("SPIKE4", "FullScreenIntent notification posted")
        } catch (e: Exception) {
            SpikeLogger.log("SPIKE4", "Notification post failed: ${e.message}")
        }

        broadcast(ACTION_SPIKE4_RESULT, EXTRA_LOG,
            "Unlock requested | locked=$isLocked screenOn=$isScreenOn")
        updateNotification("Spike 4: Waiting for unlock prompt…")
        // Don't stopSelf immediately – let UnlockHelperActivity finish and stop the service
    }

    // ── Spike 6: Screen-off stability ─────────────────────────────────

    private fun startScreenOffStability(useWakeLock: Boolean) {
        startInForeground("Spike 6: Screen-off stability running…")
        SpikeLogger.log("SPIKE6", "Started. wakeLock=$useWakeLock")

        if (useWakeLock) {
            val pm = getSystemService(PowerManager::class.java)
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Spidey::Spike6")
                .also { it.acquire(3_600_000L) } // 1-hour max
            SpikeLogger.log("SPIKE6", "Partial wake lock acquired")
        }

        var count = 0
        heartbeatTimer = object : CountDownTimer(Long.MAX_VALUE, 10_000L) {
            override fun onTick(millisUntilFinished: Long) {
                count++
                val msg = "Heartbeat #$count at ${System.currentTimeMillis()}"
                SpikeLogger.log("SPIKE6", msg)
                broadcast(ACTION_SPIKE6_HEARTBEAT, EXTRA_LOG, msg)
                updateNotification("Spike 6: alive – ${count * 10}s elapsed")
            }

            override fun onFinish() { /* won't fire with MAX_VALUE */ }
        }.start()
    }

    // ── Notification helpers ───────────────────────────────────────────

    private fun startInForeground(text: String) {
        val notif = buildNotification(text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID_SERVICE,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIF_ID_SERVICE, notif)
        }
    }

    private fun buildNotification(text: String): Notification {
        val tapIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pi = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, SpikeApp.CHANNEL_SPIKE_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Spidey Spike")
            .setContentText(text)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID_SERVICE, buildNotification(text))
    }

    // ── Broadcast helpers ──────────────────────────────────────────────

    private fun broadcast(action: String, extraKey: String, value: Long) {
        LocalBroadcastManager.getInstance(this).sendBroadcast(
            Intent(action).putExtra(extraKey, value)
        )
    }

    private fun broadcast(action: String, extraKey: String, value: String) {
        LocalBroadcastManager.getInstance(this).sendBroadcast(
            Intent(action).putExtra(extraKey, value)
        )
    }

    // ── Wake lock ──────────────────────────────────────────────────────

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    // ── Constants ──────────────────────────────────────────────────────

    companion object {
        const val ACTION_START_SPIKE3_DELAYED = "com.spidey.spikes.START_SPIKE3"
        const val ACTION_START_SPIKE4_DELAYED = "com.spidey.spikes.START_SPIKE4"
        const val ACTION_START_SPIKE6_SCREEN_OFF = "com.spidey.spikes.START_SPIKE6"
        const val ACTION_STOP = "com.spidey.spikes.STOP"

        const val ACTION_SPIKE3_TICK = "com.spidey.spikes.SPIKE3_TICK"
        const val ACTION_SPIKE3_RESULT = "com.spidey.spikes.SPIKE3_RESULT"
        const val ACTION_SPIKE4_TICK = "com.spidey.spikes.SPIKE4_TICK"
        const val ACTION_SPIKE4_RESULT = "com.spidey.spikes.SPIKE4_RESULT"
        const val ACTION_SPIKE6_HEARTBEAT = "com.spidey.spikes.SPIKE6_HEARTBEAT"

        const val EXTRA_COUNTDOWN_SEC = "countdown_sec"
        const val EXTRA_LOG = "log"
        const val EXTRA_USE_WAKE_LOCK = "use_wake_lock"

        private const val NOTIF_ID_SERVICE = 1001
        private const val NOTIF_ID_RESULT = 1002
        private const val DELAY_MS = 15_000L

        fun intentFor(context: Context, action: String): Intent =
            Intent(context, SpikeForegroundService::class.java).setAction(action)
    }
}
