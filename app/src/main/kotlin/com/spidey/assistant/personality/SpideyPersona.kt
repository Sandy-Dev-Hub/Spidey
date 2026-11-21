package com.spidey.assistant.personality

import android.content.Context
import com.spidey.assistant.engine.FeatureFlags
import org.json.JSONObject

/**
 * Persona responses for Spidey voice assistant.
 * Supports selectable presets:
 * - Jarvis: Calm, intelligent, British-butler-style wit, addresses user as "boss".
 * - Bro: Casual, friendly, direct, addresses user as "bro".
 *
 * Guaranteed never to repeat the same greeting twice in a row.
 */
object SpideyPersona {

    private val defaultJarvisGreetings = listOf(
        "At your service boss.",
        "Always a pleasure boss.",
        "Right here boss. How can I assist?",
        "Good to see you boss. What's on your mind?",
        "Ready when you are boss."
    )

    private val defaultBroGreetings = listOf(
        "Hey bro, wassup?",
        "Yes boss, tell me!",
        "Haan bro, go ahead!",
        "What's up bro?",
        "I'm here bro, tell me!"
    )

    private var greetings: List<String> = defaultJarvisGreetings
    private var lastGreetingIndex = -1

    val activePreset: String
        get() = FeatureFlags.personaPreset

    fun isJarvis(): Boolean = activePreset == FeatureFlags.PERSONA_JARVIS

    fun loadFromAssets(context: Context) {
        val assetFileName = if (isJarvis()) "persona_jarvis.json" else "persona_bro.json"
        try {
            val jsonStr = context.assets.open(assetFileName).bufferedReader().use { it.readText() }
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
            greetings = if (isJarvis()) defaultJarvisGreetings else defaultBroGreetings
        }
    }

    fun setPreset(context: Context, preset: String) {
        FeatureFlags.personaPreset = preset
        loadFromAssets(context)
    }

    /**
     * Spoken greeting when a NEW session starts.
     * Guarantees never the same greeting twice in a row.
     */
    @Synchronized
    fun randomGreeting(): String {
        if (greetings.isEmpty()) {
            return if (isJarvis()) "At your service boss." else "Hey bro, wassup?"
        }
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
        return if (isJarvis()) {
            "Opening $appName for you now boss."
        } else {
            "Opening $appName for you, bro!"
        }
    }

    fun appUnverified(appName: String): String {
        return if (isJarvis()) {
            "I sent the request to open $appName, but could not confirm its status boss."
        } else {
            "I sent the request to open $appName, but can't confirm it worked."
        }
    }

    fun appNotFound(appName: String): String {
        return if (isJarvis()) {
            "I couldn't locate $appName on your device boss."
        } else {
            "Bro, I couldn't find $appName on your phone."
        }
    }

    fun timeResponse(timeString: String): String {
        return if (isJarvis()) {
            "The time is $timeString boss."
        } else {
            "The time is $timeString, bro!"
        }
    }

    fun dateResponse(dateString: String): String {
        return if (isJarvis()) {
            "Today is $dateString boss."
        } else {
            "Today is $dateString, bro!"
        }
    }

    fun stopResponse(): String {
        return if (isJarvis()) {
            "Standing by boss."
        } else {
            "Catch you later bro, going to sleep!"
        }
    }

    fun playingSong(query: String, ordinal: String? = null): String {
        val target = if (!ordinal.isNullOrBlank()) "the $ordinal video" else query
        return if (isJarvis()) {
            "Playing $target on YouTube for you now boss."
        } else {
            "Playing $target on YouTube, enjoy bro!"
        }
    }
}
