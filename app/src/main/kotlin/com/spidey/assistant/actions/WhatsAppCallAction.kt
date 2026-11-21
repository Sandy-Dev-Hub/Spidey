package com.spidey.assistant.actions

/**
 * Action representing an intent to place a WhatsApp voice or video call.
 */
data class WhatsAppCallAction(
    val contactQuery: String,
    val isVideo: Boolean = false
) : AssistantAction
