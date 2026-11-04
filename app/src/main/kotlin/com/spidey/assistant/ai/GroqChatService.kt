package com.spidey.assistant.ai

import android.util.Log
import com.spidey.spikes.BuildConfig
import com.spidey.spikes.util.SpikeLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Lightweight Groq AI client for friendly conversations.
 * Uses LPU-powered Llama 3 for sub-300ms conversational responses.
 *
 * Privacy guarantee:
 * - Only the spoken utterance text and short conversational history are sent.
 * - No audio, contacts, phone numbers, or personal app data are ever transmitted.
 */
class GroqChatService(
    private val apiKey: String = BuildConfig.GROQ_API_KEY
) {
    private val history = mutableListOf<Pair<String, String>>() // user, assistant

    suspend fun chat(userUtterance: String): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext "Bro, I don't have a Groq API key set up yet in local.properties."
        }

        try {
            val url = URL("https://api.groq.com/openai/v1/chat/completions")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 5000
            conn.readTimeout = 7000
            conn.doOutput = true

            val systemPrompt = """
                You are Spidey, the user's best bro and personal voice assistant.
                Personality traits:
                - You speak like a warm, funny, loyal, casual friend/bro (you can call the user "bro" or "man").
                - When the user is down or had a bad day, listen, sympathize, validate their feelings, and cheer them up like a true friend.
                - Keep your spoken replies CONCISE: 1 to 2 short punchy sentences max, because your response will be read aloud through text-to-speech.
                - Never use bullet points, markdown formatting, emojis, or code blocks in your output. Just clean spoken English text.
            """.trimIndent()

            val messagesArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })

                // Include last 3 exchanges for conversational context
                synchronized(history) {
                    val recent = history.takeLast(3)
                    for ((u, a) in recent) {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", u)
                        })
                        put(JSONObject().apply {
                            put("role", "assistant")
                            put("content", a)
                        })
                    }
                }

                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userUtterance)
                })
            }

            val requestJson = JSONObject().apply {
                put("model", "llama-3.3-70b-versatile")
                put("messages", messagesArray)
                put("temperature", 0.7)
                put("max_tokens", 80)
            }

            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(requestJson.toString())
                writer.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                val rootJson = JSONObject(responseText)
                val choices = rootJson.getJSONArray("choices")
                if (choices.length() > 0) {
                    val reply = choices.getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                        .trim()
                        .replace(Regex("[*#_`~]"), "") // Strip markdown for clean TTS

                    synchronized(history) {
                        history.add(userUtterance to reply)
                        if (history.size > 8) history.removeAt(0)
                    }

                    SpikeLogger.log("SPIDEY", "Groq AI reply: \"$reply\"")
                    return@withContext reply
                }
            } else {
                val errText = conn.errorStream?.bufferedReader()?.use { it.readText() }
                Log.w(TAG, "Groq API error ($responseCode): $errText")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Groq call failed: ${e.message}")
        }

        // Friendly fallback if offline or network error
        return@withContext "I'm right here with you bro. What happened?"
    }

    companion object {
        private const val TAG = "GroqChatService"
    }
}

