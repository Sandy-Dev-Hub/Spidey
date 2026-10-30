package com.spidey.assistant.unlock

import com.spidey.assistant.actions.AssistantAction

/**
 * Encapsulates an in-memory pending action awaiting device unlock.
 * Strictly in-memory with a 30-second TTL to prevent stale execution.
 */
data class PendingAction(
    val action: AssistantAction,
    val createdAtMs: Long = System.currentTimeMillis(),
    val timeoutMs: Long = 30_000L
) {
    fun isExpired(): Boolean = (System.currentTimeMillis() - createdAtMs) > timeoutMs
}
