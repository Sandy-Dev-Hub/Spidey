package com.spidey.assistant.voice

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.spidey.assistant.actions.CallActionHandler
import com.spidey.assistant.actions.FindContactHandler
import com.spidey.assistant.actions.GreetingHandler
import com.spidey.assistant.actions.OpenAppHandler
import com.spidey.assistant.actions.PlayFirstResultHandler
import com.spidey.assistant.actions.SearchYouTubeHandler
import com.spidey.assistant.actions.StopHandler
import com.spidey.assistant.actions.TimeHandler
import com.spidey.assistant.actions.UnlockHandler
import com.spidey.assistant.calling.CallCoordinator
import com.spidey.assistant.calling.CallingAccountRepository
import com.spidey.assistant.contacts.ContactRepository
import com.spidey.assistant.core.ActionRouter
import com.spidey.assistant.core.AssistantState
import com.spidey.assistant.core.ConversationManager
import com.spidey.assistant.core.IntentEngine
import com.spidey.assistant.data.SpideyDatabase
import com.spidey.assistant.safety.PermissionManager
import com.spidey.assistant.unlock.UnlockCoordinator
import com.spidey.spikes.MainActivity
import com.spidey.spikes.util.SpikeLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Robust background foreground service that keeps SPIDEY running continuously.
 * Listens for "Hey Spidey", "Spidey", "Hey Spider", and "Spider" on the home screen,
 * inside other apps, and when the screen is off/locked.
 *
 * Holds a partial wake lock to prevent CPU suspension when the screen turns off.
 */
class WakeWordService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    private lateinit var actionRouter: ActionRouter
    private lateinit var intentEngine: IntentEngine
    private lateinit var speechOutput: SpeechOutput
    private lateinit var speechRecognizerManager: SpeechRecognizerManager
    private lateinit var wakeWordEngine: WakeWordEngine
    private lateinit var conversationManager: ConversationManager

    private var wakeLock: PowerManager.WakeLock? = null

    private val userPresentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_USER_PRESENT) {
                SpikeLogger.log("SPIDEY", "Received ACTION_USER_PRESENT broadcast")
                UnlockCoordinator.notifyUnlockSuccess()
                conversationManager.onDeviceUnlocked()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        SpikeLogger.log("SPIDEY", "Creating WakeWordService (Android 15 Foreground Service)")
        createNotificationChannel()
        acquireWakeLock()

        // Register for system unlock broadcast
        try {
            val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
            registerReceiver(userPresentReceiver, filter)
        } catch (e: Exception) {
            SpikeLogger.log("SPIDEY", "Failed to register userPresentReceiver: ${e.message}")
        }

        val db = SpideyDatabase.getInstance(this)
        val permManager = PermissionManager(this)
        val contactRepository = ContactRepository(
            context = this,
            permissionManager = permManager,
            aliasDao = db.aliasDao()
        )
        val callingAccountRepository = CallingAccountRepository(this)
        val callCoordinator = CallCoordinator(this, permManager)

        val audioManager = getSystemService(AudioManager::class.java)
        var audioFocusRequest: AudioFocusRequest? = null

        fun requestDuckingAudioFocus() {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        .setAcceptsDelayedFocusGain(false)
                        .setOnAudioFocusChangeListener { /* no-op */ }
                        .build()
                    audioFocusRequest = req
                    audioManager?.requestAudioFocus(req)
                } else {
                    @Suppress("DEPRECATION")
                    audioManager?.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                }
            } catch (_: Exception) {}
        }

        fun abandonDuckingAudioFocus() {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
                    audioFocusRequest = null
                } else {
                    @Suppress("DEPRECATION")
                    audioManager?.abandonAudioFocus(null)
                }
            } catch (_: Exception) {}
        }

        actionRouter = ActionRouter().apply {
            register(GreetingHandler())
            register(StopHandler())
            register(TimeHandler())
            register(UnlockHandler {
                val km = getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
                km?.isDeviceLocked == true
            })
            register(OpenAppHandler(this@WakeWordService))
            register(PlayFirstResultHandler(this@WakeWordService))
            register(SearchYouTubeHandler(this@WakeWordService))
            register(FindContactHandler(contactRepository))
            register(CallActionHandler(contactRepository, callingAccountRepository, callCoordinator, permManager))
        }

        intentEngine = IntentEngine()

        speechOutput = SpeechOutput(this)

        speechRecognizerManager = SpeechRecognizerManager(
            context = this,
            onResult = { text, matches -> conversationManager.onSpeechRecognized(text, matches) },
            onEmpty = { conversationManager.onSpeechEmpty() },
            onError = { code, msg -> conversationManager.onSpeechError(code, msg) }
        )

        wakeWordEngine = WakeWordEngine(
            context = this,
            onWakeWordDetected = { trailingCommand ->
                SpikeLogger.log("SPIDEY", "Wake word detected callback received! trailingCommand='$trailingCommand'")
                requestDuckingAudioFocus()
                conversationManager.onWakeWordDetected(trailingCommand)
            },
            onModelLoaded = {
                SpikeLogger.log("SPIDEY", "Vosk model ready in memory, starting wake word loop")
                wakeWordEngine.start()
            },
            onError = { err -> SpikeLogger.log("SPIDEY", "WakeWordEngine error: $err") }
        )

        conversationManager = ConversationManager(
            actionRouter = actionRouter,
            intentEngine = intentEngine,
            scope = serviceScope,
            speak = { text, onDone -> speechOutput.speak(text, onDone) },
            playPromptSound = { speechOutput.playPromptBeep() },
            onWakeWordStateRequested = { enable ->
                if (enable) {
                    abandonDuckingAudioFocus()
                    wakeWordEngine.resume()
                } else {
                    requestDuckingAudioFocus()
                    wakeWordEngine.pause()
                }
            },
            onStartSpeechListeningRequested = {
                speechRecognizerManager.startListening()
            },
            onStopSpeechListeningRequested = {
                speechRecognizerManager.stopListening()
            },
            onUnlockRequested = { pendingAction ->
                UnlockCoordinator.requestUnlock(
                    context = this,
                    pendingAction = pendingAction,
                    onSuccess = { conversationManager.onDeviceUnlocked() },
                    onFailed = { conversationManager.stop() }
                )
            },
            followUpTimeoutMs = 8_000L
        )

        _activeManager.value = conversationManager
        _isServiceRunning.value = true

        startInForeground(AssistantState.SLEEPING)

        // Observe assistant state to update notification dynamically
        serviceScope.launch {
            conversationManager.state.collectLatest { state ->
                updateNotification(state)
            }
        }

        // Bridge engine diagnostics to service StateFlow
        serviceScope.launch {
            wakeWordEngine.diagnostics.collectLatest { diag ->
                _diagnostics.value = diag.copy(isServiceAlive = _isServiceRunning.value)
            }
        }

        // Start listening for wake word
        wakeWordEngine.start()
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(PowerManager::class.java)
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Spidey::WakeWordService")?.apply {
                setReferenceCounted(false)
            }
        }
        wakeLock?.let {
            if (!it.isHeld) {
                it.acquire()
                SpikeLogger.log("SPIDEY", "PARTIAL_WAKE_LOCK acquired – CPU active during screen-off")
            }
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                SpikeLogger.log("SPIDEY", "WakeLock released")
            }
        }
        wakeLock = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        SpikeLogger.log("SPIDEY", "WakeWordService onStartCommand action=$action")
        if (action == ACTION_STOP) {
            SpikeLogger.log("SPIDEY", "Received ACTION_STOP intent, shutting down assistant")
            stopAssistant()
            return START_NOT_STICKY
        }
        // ACTION_START or null (normal start) — just ensure wake lock is held
        if (action == ACTION_START) {
            SpikeLogger.log("SPIDEY", "Received ACTION_START intent (notification or tile)")
        }
        acquireWakeLock()
        return START_STICKY
    }

    private fun startInForeground(state: AssistantState) {
        val notification = buildNotification(state)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(state: AssistantState) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.notify(NOTIFICATION_ID, buildNotification(state))
    }

    private fun buildNotification(state: AssistantState): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, WakeWordService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val startIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, WakeWordService::class.java).apply {
                action = ACTION_START
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stateText = when (state) {
            AssistantState.SLEEPING -> "Listening for 'Hey Spidey'..."
            AssistantState.LISTENING -> "Listening to your request..."
            AssistantState.PROCESSING -> "Thinking..."
            AssistantState.EXECUTING -> "Taking action..."
            AssistantState.SPEAKING -> "Speaking..."
            AssistantState.AWAITING_UNLOCK -> "Waiting for device unlock..."
            AssistantState.AWAITING_CONFIRMATION -> "Waiting for confirmation..."
            AssistantState.ERROR -> "An error occurred."
            else -> "Active"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SPIDEY Voice Assistant")
            .setContentText(stateText)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .addAction(android.R.drawable.ic_btn_speak_now, "Start", startIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SPIDEY Foreground Assistant",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live status of SPIDEY continuous voice listener"
                setShowBadge(false)
            }
            // Restart prompt channel (shown by BootReceiver when direct start is blocked)
            val restartChannel = NotificationChannel(
                RESTART_CHANNEL_ID,
                "SPIDEY Restart Prompt",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Tap to restart SPIDEY voice listener"
                setShowBadge(true)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
            nm.createNotificationChannel(restartChannel)
        }
    }

    private fun stopAssistant() {
        try {
            unregisterReceiver(userPresentReceiver)
        } catch (_: Exception) {}

        releaseWakeLock()
        _isServiceRunning.value = false
        _activeManager.value = null
        _diagnostics.value = _diagnostics.value.copy(
            isServiceAlive = false,
            isRunning = false,
            audioFps = 0.0
        )
        wakeWordEngine.stop()
        speechRecognizerManager.destroy()
        speechOutput.release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAssistant()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Samsung One UI: swiping from Recents sends a force-stop signal.
     * Schedule a 3-second AlarmManager restart so the service can recover
     * as long as the app was NOT explicitly force-stopped by the user in Settings.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        SpikeLogger.log("SPIDEY", "onTaskRemoved – scheduling AlarmManager restart in 3s")
        val restartIntent = PendingIntent.getService(
            this,
            99,
            Intent(this, WakeWordService::class.java).apply { action = ACTION_START },
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.set(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + 3_000L,
            restartIntent
        )
    }

    companion object {
        private const val TAG = "WakeWordService"
        const val CHANNEL_ID = "spidey_voice_assistant_channel"
        const val NOTIFICATION_ID = 2001
        const val ACTION_STOP = "com.spidey.assistant.ACTION_STOP"
        const val ACTION_START = "com.spidey.assistant.ACTION_START"
        const val RESTART_NOTIFICATION_ID = 2002
        const val RESTART_CHANNEL_ID = "spidey_restart_channel"

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning = _isServiceRunning.asStateFlow()

        private val _diagnostics = MutableStateFlow(WakeWordDiagnostics())
        val diagnostics: StateFlow<WakeWordDiagnostics> = _diagnostics.asStateFlow()

        private val _activeManager = MutableStateFlow<ConversationManager?>(null)
        val activeManager = _activeManager.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, WakeWordService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, WakeWordService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        /**
         * Posts a high-priority "Tap to start SPIDEY" notification.
         * Used by BootReceiver because Android 15 blocks direct startForegroundService
         * from a BOOT_COMPLETED receiver that does not have a BFSL exemption.
         * The user taps the notification → startForegroundService fires from foreground context.
         */
        fun postRestartNotification(context: Context, reason: String) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            // Ensure channel exists
            if (nm.getNotificationChannel(RESTART_CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        RESTART_CHANNEL_ID,
                        "SPIDEY Restart Prompt",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply { description = "Tap to restart SPIDEY voice listener" }
                )
            }

            val tapIntent = PendingIntent.getForegroundService(
                context,
                200,
                Intent(context, WakeWordService::class.java).apply { action = ACTION_START },
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, RESTART_CHANNEL_ID)
                .setContentTitle("SPIDEY is not listening")
                .setContentText("Tap to start: $reason")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setAutoCancel(true)
                .setContentIntent(tapIntent)
                .addAction(android.R.drawable.ic_btn_speak_now, "Start Listening", tapIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build()

            nm.notify(RESTART_NOTIFICATION_ID, notification)
            Log.i("WakeWordService", "postRestartNotification posted: $reason")
        }
    }
}
