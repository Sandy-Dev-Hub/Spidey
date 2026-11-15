package com.spidey.assistant.personality

import android.content.Context
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

/**
 * Persona responses for Spidey voice assistant.
 * Clean, casual English bro style (no Thanglish).
 * Loads lines from `assets/persona_lines.json` with fallback defaults.
 * Guarantees never repeating the same greeting twice in a row.
 */
object SpideyPersona {

    private val defaultGreetings = listOf(
        "Hey bro, wassup?",
        "Yes boss, tell me!",
        "Haan bro, go ahead!",
        "What's up bro?",
        "I'm here bro, tell me!"
    )

    private var greetings: List<String> = defaultGreetings
    private var lastGreetingIndex = -1

    fun loadFromAssets(context: Context) {
        try {
            val jsonStr = context.assets.open("persona_lines.json").bufferedReader().use { it.readText() }
            val obj = JSONObject(jsonStr)
            val arr = obj.optJSONArray("greetings")
            if (arr != null && arr.length() > 0) {
                val list = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    val line = arr.optString(i)
                    if (line.isNotBlank()) list.add(line)
                }
                if (list.isNotEmpty()) greetings = list
            }
        } catch (_: Exception) {
            greetings = defaultGreetings
        }
    }

    /**
     * Spoken greeting when a NEW session starts.
     * Guarantees never the same greeting twice in a row.
     */
    @Synchronized
    fun randomGreeting(): String {
        if (greetings.isEmpty()) return "Hey bro, wassup?"
        if (greetings.size == 1) return greetings[0]

        var next = (greetings.indices).random()
        while (next == lastGreetingIndex) {
            next = (greetings.indices).random()
        }
        lastGreetingIndex = next
        return greetings[next]
    }

    fun greeting(): String = randomGreeting()

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
