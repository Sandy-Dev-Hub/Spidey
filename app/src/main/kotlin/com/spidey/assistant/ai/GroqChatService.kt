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
 *
 * J1 Fix summary:
 * - Model changed to llama-3.1-8b-instant (always available on Groq free tier, ~200ms latency)
 * - Key length logged at construction; value is NEVER printed or logged
 * - Each HTTP error code speaks a unique message (not the same fallback)
 * - testConnection() lets the app show "AI: connected" or "AI: error N" at startup
 * - IOException / network error gives a unique offline message
 *
 * Privacy guarantee:
 * - Only the spoken utterance text and short conversational history are sent.
 * - No audio, contacts, phone numbers, or personal app data are ever transmitted.
 */
class GroqChatService(
    private val apiKey: String = BuildConfig.GROQ_API_KEY,
    private val model: String = "llama-3.1-8b-instant",
    private val baseUrl: String = "https://api.groq.com/openai/v1"
) {
    private val history = mutableListOf<Pair<String, String>>() // user, assistant pairs

    init {
        // Log key presence and length — NEVER the value
        val keyLen = apiKey.trim().length
        if (keyLen == 0) {
            Log.w(TAG, "SPIDEY: Groq key is EMPTY — set GROQ_API_KEY in local.properties")
            SpikeLogger.log(TAG, "Groq key: NOT SET")
        } else {
            SpikeLogger.log(TAG, "Groq key present, length=$keyLen  model=$model")
        }
    }

    /**
     * Send a one-shot ping to Groq to verify connectivity and authentication.
     * Returns a [ConnectionResult] the UI can display as "AI: connected" or "AI: error".
     */
    suspend fun testConnection(): ConnectionResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext ConnectionResult.NoKey
        }
        try {
            val t0 = System.currentTimeMillis()
            val url = URL("$baseUrl/chat/completions")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 6000
            conn.readTimeout = 8000
            conn.doOutput = true

            val body = JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", "ping")
                    })
                })
                put("max_tokens", 1)
            }

            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()); it.flush() }

            val code = conn.responseCode
            val latency = System.currentTimeMillis() - t0
            SpikeLogger.log(TAG, "Groq test: HTTP $code  latency=${latency}ms")
            return@withContext when (code) {
                200 -> ConnectionResult.Ok(latencyMs = latency)
                401, 403 -> ConnectionResult.AuthError(code)
                429 -> ConnectionResult.RateLimit
                else -> {
                    val body2 = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    SpikeLogger.log(TAG, "Groq test error body: $body2")
                    ConnectionResult.HttpError(code)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Groq testConnection failed: ${e.javaClass.simpleName}: ${e.message}")
            return@withContext ConnectionResult.NetworkError(e.message ?: "unknown")
        }
    }

    suspend fun chat(userUtterance: String): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext "Bro, the Groq API key isn't set yet. Add GROQ_API_KEY to local.properties."
        }

        try {
            val t0 = System.currentTimeMillis()
            val url = URL("$baseUrl/chat/completions")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 6000
            conn.readTimeout = 10_000
            conn.doOutput = true

            val systemPrompt = """
                You are Spidey, the user's best bro and personal voice assistant.
                Speak like a warm, funny, loyal, casual friend — you can call the user "bro" or "man".
                When the user is down or had a bad day, listen, sympathize, and cheer them up.
                Keep replies CONCISE: 1 to 2 short punchy sentences max — your reply is read aloud.
                Never use bullet points, markdown, emojis, or code blocks. Plain spoken English only.
            """.trimIndent()

            val messagesArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                synchronized(history) {
                    for ((u, a) in history.takeLast(4)) {
                        put(JSONObject().apply { put("role", "user"); put("content", u) })
                        put(JSONObject().apply { put("role", "assistant"); put("content", a) })
                    }
                }
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userUtterance)
                })
            }

            val requestJson = JSONObject().apply {
                put("model", model)
                put("messages", messagesArray)
                put("temperature", 0.75)
                put("max_tokens", 90)
            }

            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(requestJson.toString())
                writer.flush()
            }

            val responseCode = conn.responseCode
            val latency = System.currentTimeMillis() - t0
            SpikeLogger.log(TAG, "Groq chat HTTP $responseCode  latency=${latency}ms")

            when (responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val responseText = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                    SpikeLogger.log(TAG, "Groq raw response length=${responseText.length}")

                    return@withContext try {
                        val root = JSONObject(responseText)
                        val choices = root.getJSONArray("choices")
                        if (choices.length() > 0) {
                            val reply = choices.getJSONObject(0)
                                .getJSONObject("message")
                                .getString("content")
                                .trim()
                                .replace(Regex("[*#_`~]"), "") // Strip markdown for TTS

                            synchronized(history) {
                                history.add(userUtterance to reply)
                                if (history.size > 10) history.removeAt(0)
                            }

                            SpikeLogger.log(TAG, "Groq reply: \"$reply\"")
                            reply
                        } else {
                            Log.w(TAG, "Groq returned 200 but choices array is empty")
                            "Got a weird response from the AI bro, try again."
                        }
                    } catch (parseEx: Exception) {
                        Log.e(TAG, "Groq JSON parse error: ${parseEx.message}")
                        "AI gave me a confusing answer bro, try saying that again."
                    }
                }
                429 -> {
                    Log.w(TAG, "Groq rate limit (429)")
                    "Whoa bro, slow down a bit! The AI is rate-limited, give me a second."
                }
                401 -> {
                    Log.e(TAG, "Groq auth error 401 — key rejected")
                    "Bro, my Groq API key isn't being accepted. Check local properties."
                }
                403 -> {
                    Log.e(TAG, "Groq forbidden 403 — no access to model $model")
                    "Bro, I can't access the AI model right now. Might be a permissions issue."
                }
                404 -> {
                    Log.e(TAG, "Groq 404 — model $model not found on this account")
                    "Bro, the AI model isn't available on this Groq account. Need to change the model."
                }
                else -> {
                    val errBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    Log.e(TAG, "Groq HTTP $responseCode: $errBody")
                    "The AI server returned an error ($responseCode) bro, try again in a bit."
                }
            }
        } catch (e: java.net.UnknownHostException) {
            Log.e(TAG, "Groq offline: UnknownHostException — ${e.message}")
            "Can't reach the AI right now bro, looks like I'm offline."
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "Groq timeout: ${e.message}")
            "The AI is taking too long to respond bro, try again."
        } catch (e: Exception) {
            Log.e(TAG, "Groq call failed: ${e.javaClass.simpleName}: ${e.message}")
            "Something went wrong talking to the AI bro — ${e.javaClass.simpleName}."
        }
    }

    fun clearHistory() {
        synchronized(history) { history.clear() }
    }

    sealed class ConnectionResult {
        object NoKey : ConnectionResult()
        data class Ok(val latencyMs: Long) : ConnectionResult()
        data class AuthError(val code: Int) : ConnectionResult()
        object RateLimit : ConnectionResult()
        data class HttpError(val code: Int) : ConnectionResult()
        data class NetworkError(val message: String) : ConnectionResult()
    }

    companion object {
        private const val TAG = "GroqChatService"
    }
}
