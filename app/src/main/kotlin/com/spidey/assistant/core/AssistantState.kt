package com.spidey.assistant.core

/**
 * Authoritative assistant state representing the current step in the voice/action lifecycle.
 * Frozen contract established in Phase 1.
 */
enum class AssistantState {
    SLEEPING,
    LISTENING,
    PROCESSING,
    AWAITING_CONFIRMATION,
    AWAITING_UNLOCK,
    EXECUTING,
    SPEAKING,
    ERROR
}
