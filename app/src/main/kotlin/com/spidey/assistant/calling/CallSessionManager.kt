package com.spidey.assistant.calling

import com.spidey.assistant.contacts.model.ContactRecord

/**
 * In-memory state tracking active call intent resolution across conversational turns.
 * Expires after 30 seconds to ensure stale call states are discarded.
 */
data class CallSession(
    val contactQuery: String,
    val resolvedContact: ContactRecord? = null,
    val selectedNumber: String? = null,
    val selectedSim: String? = null,
    val timestampMs: Long = System.currentTimeMillis()
) {
    fun isExpired(maxAgeMs: Long = 30_000L): Boolean =
        (System.currentTimeMillis() - timestampMs) > maxAgeMs
}

/**
 * Thread-safe singleton holding the current in-memory [CallSession].
 */
object CallSessionManager {
    @Volatile
    private var activeSession: CallSession? = null

    fun startSession(query: String): CallSession {
        val session = CallSession(contactQuery = query)
        activeSession = session
        return session
    }

    fun updateSession(
        resolvedContact: ContactRecord? = null,
        selectedNumber: String? = null,
        selectedSim: String? = null
    ): CallSession? {
        val current = getValidSession() ?: return null
        val updated = current.copy(
            resolvedContact = resolvedContact ?: current.resolvedContact,
            selectedNumber = selectedNumber ?: current.selectedNumber,
            selectedSim = selectedSim ?: current.selectedSim,
            timestampMs = System.currentTimeMillis()
        )
        activeSession = updated
        return updated
    }

    fun getValidSession(): CallSession? {
        val current = activeSession
        if (current != null && current.isExpired()) {
            activeSession = null
            return null
        }
        return current
    }

    fun clear() {
        activeSession = null
    }
}
