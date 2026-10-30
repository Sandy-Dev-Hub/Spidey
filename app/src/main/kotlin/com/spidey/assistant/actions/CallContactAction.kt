package com.spidey.assistant.actions

/**
 * Action representing an intent to place a phone call to a contact or phone number.
 * Supports explicit number selection, SIM selection, confirmation, and cancellation.
 */
data class CallContactAction(
    val contactQuery: String,
    val selectedNumber: String? = null,
    val selectedSim: String? = null,
    val isConfirmed: Boolean = false,
    val isCancelled: Boolean = false
) : AssistantAction
