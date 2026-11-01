package com.spidey.assistant.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.spidey.assistant.actions.FindContactHandler
import com.spidey.assistant.actions.GreetingHandler
import com.spidey.assistant.actions.OpenAppHandler
import com.spidey.assistant.contacts.ContactRepository
import com.spidey.assistant.core.ActionRouter
import com.spidey.assistant.core.AssistantState
import com.spidey.assistant.core.ConversationManager
import com.spidey.assistant.core.IntentEngine
import com.spidey.assistant.data.SpideyDatabase
import com.spidey.assistant.safety.PermissionManager
import com.spidey.assistant.unlock.UnlockCoordinator
import com.spidey.spikes.MainActivity
import com.spidey.spikes.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps SPIDEY running in the background listening for "Hey Spider".
 * Provides persistent notification with status updates and an explicit Stop button.
 */
class WakeWordService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    private lateinit var actionRouter: ActionRouter
    private lateinit var intentEngine: IntentEngine
    private lateinit var speechOutput: SpeechOutput
    private lateinit var speechRecognizerManager: SpeechRecognizerManager
    private lateinit var wakeWordEngine: WakeWordEngine
    private lateinit var conversationManager: ConversationManager

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Creating WakeWordService")
        createNotificationChannel()

        val db = SpideyDatabase.getInstance(this)
        val contactRepository = ContactRepository(
            context = this,
            permissionManager = PermissionManager(this),
            aliasDao = db.aliasDao()
        )

        actionRouter = ActionRouter().apply {
            register(GreetingHandler())
            register(OpenAppHandler(this@WakeWordService))
            register(FindContactHandler(contactRepository))
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
                Log.d(TAG, "Wake word triggered!")
                conversationManager.onWakeWordDetected()
            },
            onModelLoaded = {
                Log.d(TAG, "Model loaded into memory, starting wake word spotting")
                wakeWordEngine.start()
            },
            onError = { err -> Log.e(TAG, "WakeWordEngine error: $err") }
        )

        conversationManager = ConversationManager(
            actionRouter = actionRouter,
            intentEngine = intentEngine,
            scope = serviceScope,
            speak = { text, onDone -> speechOutput.speak(text, onDone) },
            playPromptSound = { speechOutput.playPromptBeep() },
            onWakeWordStateRequested = { enable ->
                if (enable) wakeWordEngine.resume() else wakeWordEngine.pause()
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
            }
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

        // Start listening for wake word
        wakeWordEngine.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Log.d(TAG, "Received ACTION_STOP intent, shutting down service")
            stopAssistant()
            return START_NOT_STICKY
        }
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
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, WakeWordService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val statusText = when (state) {
            AssistantState.SLEEPING -> "Listening for \"Hey Spider\"..."
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
        _isServiceRunning.value = false
        _activeManager.value = null
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
