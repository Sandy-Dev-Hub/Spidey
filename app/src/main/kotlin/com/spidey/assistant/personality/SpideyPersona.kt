package com.spidey.assistant.personality

/**
 * Persona responses for Spidey voice assistant.
 * Clean, casual English bro style (no Thanglish).
 */
object SpideyPersona {

    fun greeting(): String {
        return "Hey bro, Spidey here! How can I help you?"
    }

    fun appOpening(appName: String): String {
        return "Opening $appName for you, bro!"
    }

    fun appUnverified(appName: String): String {
        return "I sent the request to open $appName, but can't confirm it worked."
    }

    fun appNotFound(appName: String): String {
        return "Bro, I couldn't find $appName on your phone."
    }

    fun timeResponse(timeString: String): String {
        return "The time is $timeString, bro!"
    }

    fun dateResponse(dateString: String): String {
        return "Today is $dateString, bro!"
    }

    fun stopResponse(): String {
        return "Catch you later bro, going to sleep!"
    }

    fun playingSong(query: String, ordinal: String? = null): String {
        val target = if (!ordinal.isNullOrBlank()) "the $ordinal video" else query
        return "Playing $target on YouTube, enjoy bro!"
    }
}

