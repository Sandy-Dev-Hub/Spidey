package com.spidey.assistant.unlock

import android.content.Context
import android.content.Intent
import com.spidey.assistant.actions.AssistantAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Coordinates prompting the user to unlock the phone when a requested action needs it.
 * Uses the proven approach from Phase 0 (wake window + keyguard dismiss prompt).
 */
object UnlockCoordinator {
    private var onUnlockSuccessCallback: (() -> Unit)? = null
    private var onUnlockFailedCallback: (() -> Unit)? = null

    private val _isAwaitingUnlock = MutableStateFlow(false)
    val isAwaitingUnlock = _isAwaitingUnlock.asStateFlow()

    fun requestUnlock(
        context: Context,
        pendingAction: AssistantAction,
        onSuccess: () -> Unit,
        onFailed: () -> Unit = {}
    ) {
        onUnlockSuccessCallback = onSuccess
        onUnlockFailedCallback = onFailed
        _isAwaitingUnlock.value = true

        val intent = Intent(context, UnlockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        context.startActivity(intent)
    }

    internal fun notifyUnlockSuccess() {
        _isAwaitingUnlock.value = false
        val cb = onUnlockSuccessCallback
        onUnlockSuccessCallback = null
        onUnlockFailedCallback = null
        cb?.invoke()
    }

    internal fun notifyUnlockCancelledOrFailed() {
        _isAwaitingUnlock.value = false
        val cb = onUnlockFailedCallback
        onUnlockSuccessCallback = null
        onUnlockFailedCallback = null
        cb?.invoke()
    }
}
