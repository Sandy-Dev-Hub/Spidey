package com.spidey.assistant.unlock

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.spidey.assistant.actions.AssistantAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Coordinates prompting the user to unlock the phone when a requested action needs it.
 * Uses the proven approach from Phase 0 (wake window + keyguard dismiss prompt).
 * Keeps pending action strictly in-memory with a 30s timeout.
 */
object UnlockCoordinator {
    private var onUnlockSuccessCallback: (() -> Unit)? = null
    private var onUnlockFailedCallback: (() -> Unit)? = null
    private var pendingAction: PendingAction? = null

    private val _isAwaitingUnlock = MutableStateFlow(false)
    val isAwaitingUnlock = _isAwaitingUnlock.asStateFlow()

    fun requestUnlock(
        context: Context,
        pendingAction: AssistantAction,
        onSuccess: () -> Unit,
        onFailed: () -> Unit = {}
    ) {
        this.pendingAction = PendingAction(pendingAction)
        onUnlockSuccessCallback = onSuccess
        onUnlockFailedCallback = onFailed
        _isAwaitingUnlock.value = true

        // 1. Physically turn the screen on using WakeLock
        try {
            val pm = context.getSystemService(PowerManager::class.java)
            @Suppress("DEPRECATION")
            val wakeLock = pm?.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "spidey:unlock_screen"
            )
            wakeLock?.acquire(5000L)
        } catch (_: Exception) {}

        // 2. Launch transparent UnlockActivity
        val intent = Intent(context, UnlockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        context.startActivity(intent)
    }

    fun getPendingAction(): PendingAction? {
        val current = pendingAction
        if (current != null && current.isExpired()) {
            pendingAction = null
            return null
        }
        return current
    }

    internal fun notifyUnlockSuccess() {
        _isAwaitingUnlock.value = false
        val current = pendingAction
        val cb = onUnlockSuccessCallback
        onUnlockSuccessCallback = null
        onUnlockFailedCallback = null

        if (current != null && current.isExpired()) {
            pendingAction = null
            return
        }

        cb?.invoke()
    }

    internal fun notifyUnlockCancelledOrFailed() {
        _isAwaitingUnlock.value = false
        pendingAction = null
        val cb = onUnlockFailedCallback
        onUnlockSuccessCallback = null
        onUnlockFailedCallback = null
        cb?.invoke()
    }
}

