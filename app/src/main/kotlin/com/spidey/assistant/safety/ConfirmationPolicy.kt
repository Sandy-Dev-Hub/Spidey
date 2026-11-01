package com.spidey.assistant.safety

/**
 * Enforces explicit spoken confirmation before placing phone calls.
 * Ensures the user has explicitly confirmed dialing via "yes", "go ahead", etc.,
 * and respects cancellations like "no", "cancel", or "stop".
 */
object ConfirmationPolicy {

    enum class ConfirmationResult {
        CONFIRMED,
        CANCELLED,
        UNKNOWN
    }

    private val CONFIRMATION_TOKENS = listOf(
        "yes please", "yes go ahead", "make the call", "call now", "yes call", "do call",
        "go ahead", "do it", "yes", "yeah", "yep", "sure", "okay", "ok", "proceed",
        "call", "please", "correct", "right", "yup", "ha", "haan", "dial",
        "definitely", "absolutely"
    )

    private val CANCELLATION_TOKENS = listOf(
        "never mind", "nevermind", "don't call", "dont call", "no don't", "no dont",
        "no cancel", "hold on", "not now", "no", "nope", "cancel", "stop",
        "don't", "dont", "abort", "nah", "wait"
    )

    fun evaluate(text: String): ConfirmationResult {
        val clean = text.trim().lowercase()
            .replace("'", "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (clean.isEmpty()) return ConfirmationResult.UNKNOWN

        // 1. Check cancellation FIRST (takes precedence)
        for (token in CANCELLATION_TOKENS) {
            val cleanToken = token.replace("'", "")
            if (clean == cleanToken || clean.startsWith("$cleanToken ") || clean.endsWith(" $cleanToken") || clean.contains(" $cleanToken ")) {
                return ConfirmationResult.CANCELLED
            }
        }

        // 2. Check confirmation
        for (token in CONFIRMATION_TOKENS) {
            val cleanToken = token.replace("'", "")
            if (clean == cleanToken || clean.startsWith("$cleanToken ") || clean.endsWith(" $cleanToken") || clean.contains(" $cleanToken ")) {
                return ConfirmationResult.CONFIRMED
            }
        }

        return ConfirmationResult.UNKNOWN
    }

    fun formatPrompt(contactName: String, simLabel: String?): String {
        val onSim = if (!simLabel.isNullOrBlank()) " on $simLabel" else ""
        return "Calling $contactName$onSim. Shall I go ahead?"
    }
}
