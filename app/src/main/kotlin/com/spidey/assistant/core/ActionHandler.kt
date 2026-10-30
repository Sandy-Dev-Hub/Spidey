package com.spidey.assistant.core

import com.spidey.assistant.actions.AssistantAction

/**
 * Generic handler for executing an [AssistantAction] of type [T].
 * Frozen contract established in Phase 1.
 */
interface ActionHandler<T : AssistantAction> {
    val actionType: Class<T>
    suspend fun handle(action: T): ActionResult
}
