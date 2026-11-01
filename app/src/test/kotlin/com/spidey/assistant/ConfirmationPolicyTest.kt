package com.spidey.assistant

import com.spidey.assistant.safety.ConfirmationPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class ConfirmationPolicyTest {

    @Test
    fun testConfirmationTokens() {
        val positivePhrases = listOf(
            "yes",
            "yeah",
            "yep",
            "sure",
            "go ahead",
            "okay",
            "ok",
            "do it",
            "call",
            "yes please",
            "yes go ahead",
            "dial",
            "make the call"
        )

        for (phrase in positivePhrases) {
            val result = ConfirmationPolicy.evaluate(phrase)
            assertEquals("Expected CONFIRMED for '$phrase'", ConfirmationPolicy.ConfirmationResult.CONFIRMED, result)
        }
    }

    @Test
    fun testCancellationTokens() {
        val negativePhrases = listOf(
            "no",
            "nope",
            "cancel",
            "stop",
            "don't",
            "dont",
            "never mind",
            "nevermind",
            "abort",
            "don't call",
            "no don't"
        )

        for (phrase in negativePhrases) {
            val result = ConfirmationPolicy.evaluate(phrase)
            assertEquals("Expected CANCELLED for '$phrase'", ConfirmationPolicy.ConfirmationResult.CANCELLED, result)
        }
    }

    @Test
    fun testUnknownResponses() {
        val unknown = listOf(
            "",
            "what time is it",
            "pizza",
            "random words"
        )

        for (phrase in unknown) {
            val result = ConfirmationPolicy.evaluate(phrase)
            assertEquals("Expected UNKNOWN for '$phrase'", ConfirmationPolicy.ConfirmationResult.UNKNOWN, result)
        }
    }

    @Test
    fun testFormatPrompt() {
        val withSim = ConfirmationPolicy.formatPrompt("Guru Anna", "Jio")
        assertEquals("Calling Guru Anna on Jio. Shall I go ahead?", withSim)

        val withoutSim = ConfirmationPolicy.formatPrompt("Guru Anna", null)
        assertEquals("Calling Guru Anna. Shall I go ahead?", withoutSim)
    }
}
