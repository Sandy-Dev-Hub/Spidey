package com.spidey.assistant.core

/**
 * Represents the typed outcome of executing an [AssistantAction].
 * Frozen contract established in Phase 1.
 */
sealed interface ActionResult {
    data class Success(val spokenMessage: String) : ActionResult
    data class Failure(val spokenMessage: String, val reason: String) : ActionResult
    data object Cancelled : ActionResult
    data class NeedsClarification(val question: String, val candidates: List<String> = emptyList()) : ActionResult
    data object NeedsUnlock : ActionResult
}
