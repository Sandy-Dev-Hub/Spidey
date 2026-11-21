package com.spidey.assistant.actions

/**
 * Action indicating an in-app command was issued for an app that does not support that command.
 * Strictly prevents cross-app fallback (e.g. asking WhatsApp to change song must never fall back to YouTube).
 */
data class InAppUnsupportedAction(
    val appName: String,
    val command: String
) : AssistantAction
