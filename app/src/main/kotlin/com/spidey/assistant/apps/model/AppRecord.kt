package com.spidey.assistant.apps.model

/**
 * Represents an installed launchable application.
 */
data class AppRecord(
    val label: String,
    val packageName: String,
    val normalizedLabel: String = label.trim().lowercase()
        .replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
)

