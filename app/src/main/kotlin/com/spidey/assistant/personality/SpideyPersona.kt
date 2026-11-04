package com.spidey.assistant.personality

/**
 * Persona style for Spidey voice assistant.
 * BRO_MIX: Casual Indian English / Tamil flavor ("bro", "machan", "chill panunga", "done bro").
 * CASUAL_ENGLISH: Universal casual, friendly male English for global/American users ("Hey man", "Got you bro", "On it").
 */
enum class PersonaStyle {
    BRO_MIX,
    CASUAL_ENGLISH
}

object SpideyPersona {

    var currentStyle: PersonaStyle = PersonaStyle.BRO_MIX

    fun greeting(): String {
        return when (currentStyle) {
            PersonaStyle.BRO_MIX -> listOf(
                "Hey bro! Spidey here, sollunga!",
                "Yo machan! What's up?",
                "Hey bro, what can I do for you?",
                "Spidey is here bro, tell me!"
            ).random()
            PersonaStyle.CASUAL_ENGLISH -> listOf(
                "Hey man! Spidey here, what's up?",
                "Yo! What's going on bro?",
                "Hey bro, how can I help you?",
                "What's up man, I'm listening!"
            ).random()
        }
    }

    fun appOpening(appName: String): String {
        return when (currentStyle) {
            PersonaStyle.BRO_MIX -> listOf(
                "Opening $appName for you, bro!",
                "On it bro! Launching $appName.",
                "Opening $appName right away machan!"
            ).random()
            PersonaStyle.CASUAL_ENGLISH -> listOf(
                "Opening $appName for you, bro!",
                "On it man, opening $appName.",
                "Got you bro, launching $appName."
            ).random()
        }
    }

    fun appUnverified(appName: String): String {
        return "I sent the request to open $appName, but can't confirm it worked."
    }

    fun appNotFound(appName: String): String {
        return when (currentStyle) {
            PersonaStyle.BRO_MIX -> "Bro, I couldn't find $appName on your phone."
            PersonaStyle.CASUAL_ENGLISH -> "Bro, I couldn't find $appName on your phone."
        }
    }

    fun timeResponse(timeString: String): String {
        return when (currentStyle) {
            PersonaStyle.BRO_MIX -> listOf(
                "It's $timeString bro!",
                "Time is $timeString machan.",
                "It's $timeString right now, bro."
            ).random()
            PersonaStyle.CASUAL_ENGLISH -> listOf(
                "It's $timeString bro!",
                "It's $timeString right now man.",
                "Time is $timeString, bro."
            ).random()
        }
    }

    fun dateResponse(dateString: String): String {
        return when (currentStyle) {
            PersonaStyle.BRO_MIX -> "Today is $dateString, bro!"
            PersonaStyle.CASUAL_ENGLISH -> "Today is $dateString, man!"
        }
    }

    fun stopResponse(): String {
        return when (currentStyle) {
            PersonaStyle.BRO_MIX -> listOf(
                "Catch you later bro, going to sleep!",
                "Cool machan, chilling out. Call me anytime!",
                "Done bro, going to sleep."
            ).random()
            PersonaStyle.CASUAL_ENGLISH -> listOf(
                "Catch you later bro, heading to sleep!",
                "Got it man, I'm here whenever you need me!",
                "Peace out bro, going to sleep."
            ).random()
        }
    }

    fun playingSong(query: String, ordinal: String? = null): String {
        val target = if (!ordinal.isNullOrBlank()) "the $ordinal video" else query
        return when (currentStyle) {
            PersonaStyle.BRO_MIX -> "Playing $target on YouTube, enjoy the vibes bro!"
            PersonaStyle.CASUAL_ENGLISH -> "Playing $target on YouTube, enjoy bro!"
        }
    }
}

