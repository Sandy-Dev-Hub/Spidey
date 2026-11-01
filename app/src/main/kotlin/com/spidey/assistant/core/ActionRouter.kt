package com.spidey.assistant.core

import com.spidey.assistant.actions.AssistantAction

/**
 * Thread-safe router that dispatches [AssistantAction]s to their registered [ActionHandler].
 * New capabilities register their handlers here without modifying existing handling logic.
 */
class ActionRouter {
    private val handlers = mutableMapOf<Class<out AssistantAction>, ActionHandler<out AssistantAction>>()

    fun <T : AssistantAction> register(handler: ActionHandler<T>) {
        handlers[handler.actionType] = handler
    }

    suspend fun route(action: AssistantAction): ActionResult {
        @Suppress("UNCHECKED_CAST")
        val handler = handlers[action::class.java] as? ActionHandler<AssistantAction>
            ?: return ActionResult.Failure(
                spokenMessage = "I don't know how to do that yet.",
                reason = "No handler registered for action type: ${action::class.java.simpleName}"
            )
        return handler.handle(action)
    }

    fun hasHandlerFor(actionClass: Class<out AssistantAction>): Boolean {
        return handlers.containsKey(actionClass)
    }
}
