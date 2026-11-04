package com.spidey.assistant.personality

/**
 * Persona style for Spidey voice assistant.
 * BRO_MIX: Casual Indian English / Tamil friendly flavor ("Hey bro! Spidey here, sollunga!").
 * CASUAL_ENGLISH: Universal casual, friendly male English for global/American users ("Hey man, Spidey here!").
 */
enum class PersonaStyle {
    BRO_MIX,
    CASUAL_ENGLISH
}

object SpideyPersona {

    var currentStyle: PersonaStyle = PersonaStyle.BRO_MIX

    fun greeting(): String {
        return when (currentStyle) {
            PersonaStyle.BRO_MIX -> "Hey bro! Spidey here, sollunga!"
            PersonaStyle.CASUAL_ENGLISH -> "Hey bro! Spidey here, what's up?"
        }
    }

    fun appOpening(appName: String): String {
        return when (currentStyle) {
            PersonaStyle.BRO_MIX -> "Opening $appName for you, bro!"
            PersonaStyle.CASUAL_ENGLISH -> "Opening $appName for you, bro!"
        }
    }

    fun appUnverified(appName: String): String {
        return "I sent the request to open $appName, but can't confirm it worked."
    }

    fun appNotFound(appName: String): String {
        return "Bro, I couldn't find $appName on your phone."
    }

    fun timeResponse(timeString: String): String {
        return when (currentStyle) {
            PersonaStyle.BRO_MIX -> "The time is $timeString, bro!"
            PersonaStyle.CASUAL_ENGLISH -> "The time is $timeString, bro!"
        }
    }

    fun dateResponse(dateString: String): String {
        return when (currentStyle) {
            PersonaStyle.BRO_MIX -> "Today is $dateString, bro!"
            PersonaStyle.CASUAL_ENGLISH -> "Today is $dateString, bro!"
        }
    }

    fun stopResponse(): String {
        return when (currentStyle) {
            PersonaStyle.BRO_MIX -> "Catch you later bro, going to sleep!"
            PersonaStyle.CASUAL_ENGLISH -> "Catch you later bro, going to sleep!"
        }
    }

    fun playingSong(query: String, ordinal: String? = null): String {
        val target = if (!ordinal.isNullOrBlank()) "the $ordinal video" else query
        return "Playing $target on YouTube, enjoy the vibes bro!"
    }
}

