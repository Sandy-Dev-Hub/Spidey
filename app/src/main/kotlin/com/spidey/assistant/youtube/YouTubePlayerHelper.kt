package com.spidey.assistant.youtube

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.spidey.assistant.actions.BackgroundAppLauncher
import com.spidey.spikes.util.SpikeLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Helper to resolve YouTube video IDs from search queries and directly initiate playback.
 */
object YouTubePlayerHelper {

    private const val TAG = "SPIDEY"
    private val VIDEO_ID_REGEX = Regex("\"videoId\":\"([a-zA-Z0-9_-]{11})\"")

    /**
     * Resolves the first video ID for a query by fetching YouTube search results.
     */
    suspend fun resolveFirstVideoId(query: String): String? = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        var reader: BufferedReader? = null
        try {
            val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
            val searchUrl = "https://www.youtube.com/results?search_query=$encodedQuery"
            val url = URL(searchUrl)
            conn = (url.openConnection() as HttpURLConnection).apply {
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                connectTimeout = 4000
                readTimeout = 4000
            }

            reader = BufferedReader(InputStreamReader(conn.inputStream))
            var line: String? = reader.readLine()
            var foundId: String? = null

            while (line != null) {
                val match = VIDEO_ID_REGEX.find(line)
                if (match != null) {
                    foundId = match.groupValues[1]
                    break
                }
                line = reader.readLine()
            }

            SpikeLogger.log(TAG, "YouTubePlayerHelper resolved videoId for '$query': $foundId")
            foundId
        } catch (e: Exception) {
            SpikeLogger.log(TAG, "YouTubePlayerHelper error for '$query': ${e.message}")
            null
        } finally {
            try {
                reader?.close()
                conn?.disconnect()
            } catch (_: Exception) {}
        }
    }

    /**
     * Directly launches playback of the specified video in the YouTube app.
     */
    fun playVideo(context: Context, videoId: String): Boolean {
        return try {
            val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$videoId")).apply {
                setPackage("com.google.android.youtube")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            var launched = BackgroundAppLauncher.launch(context, appIntent)
            if (!launched) {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$videoId")).apply {
                    setPackage("com.google.android.youtube")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                launched = BackgroundAppLauncher.launch(context, webIntent)
            }
            SpikeLogger.log(TAG, "YouTubePlayerHelper playVideo videoId=$videoId, launched=$launched")
            launched
        } catch (e: Exception) {
            SpikeLogger.log(TAG, "YouTubePlayerHelper playVideo failed: ${e.message}")
            false
        }
    }
}

