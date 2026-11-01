package com.spidey.assistant.actions

/**
 * Base sealed interface for all typed assistant actions.
 * Each phase expands this interface with its own subtypes.
 * Frozen contract established in Phase 1.
 */
sealed interface AssistantAction
