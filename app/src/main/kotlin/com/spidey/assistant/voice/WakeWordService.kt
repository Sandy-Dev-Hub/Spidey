package com.spidey.assistant.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
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

    override fun onCreate() {
        super.onCreate()
        SpikeLogger.log("SPIDEY", "Creating WakeWordService (Android 15 Foreground Service)")
        createNotificationChannel()
        acquireWakeLock()

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
            register(PlayFirstResultHandler())
            register(SearchYouTubeHandler(this@WakeWordService))
            register(FindContactHandler(contactRepository))
            register(CallActionHandler(contactRepository, callingAccountRepository, callCoordinator, permManager))
        }

        intentEngine = IntentEngine()

        speechOutput = SpeechOutput(this)

        speechRecognizerManager = SpeechRecognizerManager(
            context = this,
            onResult = { text -> conversationManager.onSpeechRecognized(text) },
            onEmpty = { conversationManager.onSpeechEmpty() },
            onError = { code, msg -> conversationManager.onSpeechError(code, msg) }
        )

        wakeWordEngine = WakeWordEngine(
            context = this,
            onWakeWordDetected = {
                SpikeLogger.log("SPIDEY", "Wake word detected callback received! Ducking audio & starting conversation")
                requestDuckingAudioFocus()
                conversationManager.onWakeWordDetected()
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
            Intent(this, WakeWordService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val statusText = when (state) {
            AssistantState.SLEEPING -> "Listening for \"Hey Spidey\" / \"Spider\"..."
            AssistantState.LISTENING -> "Listening to your command..."
            AssistantState.PROCESSING -> "Processing command..."
            AssistantState.AWAITING_CONFIRMATION -> "Waiting for confirmation..."
            AssistantState.AWAITING_UNLOCK -> "Waiting for device unlock..."
            AssistantState.EXECUTING -> "Executing action..."
            AssistantState.SPEAKING -> "Speaking..."
            AssistantState.ERROR -> "Encountered an issue"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SPIDEY Voice Assistant")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SPIDEY Voice Assistant",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows the active status of SPIDEY voice assistant"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun stopAssistant() {
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

    companion object {
        private const val TAG = "WakeWordService"
        const val CHANNEL_ID = "spidey_voice_assistant_channel"
        const val NOTIFICATION_ID = 2001
        const val ACTION_STOP = "com.spidey.assistant.ACTION_STOP"

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
    }
}
