package com.spidey.assistant.actions

enum class MessagePlatform(val displayName: String) {
    WHATSAPP("WhatsApp"),
    EMAIL("Email"),
    SMS("SMS")
}

/**
 * Action to compose and send messages across WhatsApp, Email, or SMS.
 */
data class SendMessageAction(
    val recipient: String,
    val body: String = "",
    val platform: MessagePlatform = MessagePlatform.WHATSAPP,
    val subject: String = "",
    val isConfirmed: Boolean = false,
    val isCancelled: Boolean = false
) : AssistantAction
