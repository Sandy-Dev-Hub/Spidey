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
 * Uses `qwen/qwen3.8-27b` which is active on this account, has zero reasoning overhead,
 * ultra-fast response times (~70ms generation time), and produces direct conversational text.
 *
 * Privacy guarantee:
 * - Only the spoken utterance text and short conversational history are sent.
 * - No audio, contacts, phone numbers, or personal app data are ever transmitted.
 */
class GroqChatService(
    private val apiKey: String = BuildConfig.GROQ_API_KEY,
    private val model: String = "qwen/qwen3.8-27b",
    private val baseUrl: String = "https://api.groq.com/openai/v1"
) {
    private val history = mutableListOf<Pair<String, String>>() // user, assistant pairs

    init {
        val keyLen = apiKey.trim().length
        if (keyLen == 0) {
            Log.w(TAG, "SPIDEY: Groq key is EMPTY — set GROQ_API_KEY in local.properties")
            SpikeLogger.log(TAG, "Groq key: NOT SET")
        } else {
            SpikeLogger.log(TAG, "Groq key present, length=$keyLen  model=$model")
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
                Keep replies CONCISE: 1 to 2 short punchy sentences max — your reply will be read aloud.
                Never use bullet points, markdown formatting, asterisks, emojis, or code blocks. Plain spoken English only.
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
                put("temperature", 0.7)
                put("max_tokens", 80)
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
                    try {
                        val root = JSONObject(responseText)
                        val choices = root.getJSONArray("choices")
                        if (choices.length() > 0) {
                            val msgObj = choices.getJSONObject(0).getJSONObject("message")
                            val rawContent = msgObj.optString("content", "").trim()
                            val cleanContent = rawContent.replace(Regex("[*#_`~]"), "").trim()

                            if (cleanContent.isNotBlank()) {
                                synchronized(history) {
                                    history.add(userUtterance to cleanContent)
                                    if (history.size > 10) history.removeAt(0)
                                }
                                SpikeLogger.log(TAG, "Groq reply: \"$cleanContent\"")
                                return@withContext cleanContent
                            }
                        }
                    } catch (parseEx: Exception) {
                        Log.e(TAG, "Groq JSON parse error: ${parseEx.message}")
                    }
                    return@withContext "Got a blank reply from the AI bro, try again."
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
                    "Bro, the AI model isn't available on this Groq account."
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
            "Something went wrong talking to the AI bro."
        }
    }

    suspend fun streamChat(userUtterance: String, onSentenceChunk: suspend (String) -> Unit): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            val msg = "Bro, the Groq API key isn't set yet. Add GROQ_API_KEY to local.properties."
            onSentenceChunk(msg)
            return@withContext msg
        }

        try {
            val t0 = System.currentTimeMillis()
            val url = URL("$baseUrl/chat/completions")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            conn.connectTimeout = 5000
            conn.readTimeout = 10_000
            conn.doOutput = true

            val systemPrompt = """
                You are Spidey, the user's best bro and personal voice assistant.
                Speak like a warm, funny, loyal, casual friend — you can call the user "bro" or "man".
                When the user is down or had a bad day, listen, sympathize, and cheer them up.
                Keep replies CONCISE: 1 to 2 short punchy sentences max — your reply will be read aloud.
                Never use bullet points, markdown formatting, asterisks, emojis, or code blocks. Plain spoken English only.
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
                put("temperature", 0.7)
                put("max_tokens", 80)
                put("stream", true)
            }

            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(requestJson.toString())
                writer.flush()
            }

            val responseCode = conn.responseCode
            val tConnect = System.currentTimeMillis() - t0
            SpikeLogger.log(TAG, "Groq stream HTTP $responseCode connect=${tConnect}ms")

            if (responseCode != HttpURLConnection.HTTP_OK) {
                val fallback = chat(userUtterance)
                onSentenceChunk(fallback)
                return@withContext fallback
            }

            val fullReply = StringBuilder()
            val currentChunk = StringBuilder()
            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            var line = reader.readLine()
            var firstTokenLogged = false

            while (line != null) {
                val trimmed = line.trim()
                if (trimmed.startsWith("data: ") && !trimmed.startsWith("data: [DONE]")) {
                    val jsonStr = trimmed.removePrefix("data: ").trim()
                    try {
                        val root = JSONObject(jsonStr)
                        val choices = root.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val delta = choices.getJSONObject(0).optJSONObject("delta")
                            val content = delta?.optString("content", "") ?: ""
                            if (content.isNotEmpty()) {
                                if (!firstTokenLogged) {
                                    firstTokenLogged = true
                                    val tFirst = System.currentTimeMillis() - t0
                                    SpikeLogger.log(TAG, "Groq stream time-to-first-token=${tFirst}ms")
                                }
                                fullReply.append(content)
                                currentChunk.append(content)

                                val str = currentChunk.toString()
                                val boundaryIdx = findSentenceBoundary(str)
                                if (boundaryIdx != -1) {
                                    val sentence = str.substring(0, boundaryIdx + 1).trim()
                                    val clean = sentence.replace(Regex("[*#_`~]"), "").trim()
                                    if (clean.isNotEmpty()) {
                                        onSentenceChunk(clean)
                                    }
                                    currentChunk.delete(0, boundaryIdx + 1)
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
                line = reader.readLine()
            }

            val remaining = currentChunk.toString().replace(Regex("[*#_`~]"), "").trim()
            if (remaining.isNotEmpty()) {
                onSentenceChunk(remaining)
            }

            val cleanFull = fullReply.toString().replace(Regex("[*#_`~]"), "").trim()
            if (cleanFull.isNotEmpty()) {
                synchronized(history) {
                    history.add(userUtterance to cleanFull)
                    if (history.size > 10) history.removeAt(0)
                }
                SpikeLogger.log(TAG, "Groq stream completed: \"$cleanFull\" (total=${System.currentTimeMillis() - t0}ms)")
                return@withContext cleanFull
            } else {
                val emptyFallback = "I'm right here with you bro."
                onSentenceChunk(emptyFallback)
                return@withContext emptyFallback
            }
        } catch (e: Exception) {
            SpikeLogger.log(TAG, "Groq stream exception: ${e.message}, fallback to sync chat")
            val fallback = chat(userUtterance)
            onSentenceChunk(fallback)
            return@withContext fallback
        }
    }

    private fun findSentenceBoundary(text: String): Int {
        for (i in text.indices) {
            val c = text[i]
            if (c == '.' || c == '!' || c == '?' || c == '\n') {
                if (c == '.' && i + 1 < text.length && text[i + 1].isDigit()) {
                    continue
                }
                if (i + 1 == text.length || text[i + 1].isWhitespace()) {
                    return i
                }
            }
        }
        return -1
    }

    fun clearHistory() {
        synchronized(history) { history.clear() }
    }

    companion object {
        private const val TAG = "GroqChatService"
    }
}
