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
import java.util.concurrent.ConcurrentHashMap

/**
 * Helper to resolve YouTube video IDs from search queries and directly initiate playback.
 * Includes in-memory caching and pre-fetching for instant playback.
 * Supports ordinal indexing (1st, 2nd, 3rd, etc.).
 */
object YouTubePlayerHelper {

    private const val TAG = "SPIDEY"
    private val VIDEO_ID_REGEX = Regex("\"videoId\":\"([a-zA-Z0-9_-]{11})\"")
    private val queryCache = ConcurrentHashMap<String, List<String>>()

    /**
     * Resolves multiple distinct video IDs for a query by fetching YouTube search results (cached when available).
     */
    suspend fun resolveVideoIds(query: String, maxCount: Int = 10): List<String> = withContext(Dispatchers.IO) {
        val norm = query.trim().lowercase()
        val cached = queryCache[norm]
        if (!cached.isNullOrEmpty() && cached.size >= maxCount) {
            SpikeLogger.log(TAG, "YouTubePlayerHelper cache hit for '$query': $cached")
            return@withContext cached
        }

        var conn: HttpURLConnection? = null
        var reader: BufferedReader? = null
        val distinctVideoIds = mutableListOf<String>()
        val seen = mutableSetOf<String>()

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

            while (line != null) {
                val matches = VIDEO_ID_REGEX.findAll(line)
                for (match in matches) {
                    val id = match.groupValues[1]
                    if (seen.add(id)) {
                        distinctVideoIds.add(id)
                        if (distinctVideoIds.size >= maxOf(maxCount, 5)) {
                            break
                        }
                    }
                }
                if (distinctVideoIds.size >= maxOf(maxCount, 5)) {
                    break
                }
                line = reader.readLine()
            }

            if (distinctVideoIds.isNotEmpty()) {
                queryCache[norm] = distinctVideoIds
            }

            SpikeLogger.log(TAG, "YouTubePlayerHelper resolved ${distinctVideoIds.size} videoIds for '$query': $distinctVideoIds")
            distinctVideoIds
        } catch (e: Exception) {
            SpikeLogger.log(TAG, "YouTubePlayerHelper error for '$query': ${e.message}")
            distinctVideoIds
        } finally {
            try {
                reader?.close()
                conn?.disconnect()
            } catch (_: Exception) {}
        }
    }

    /**
     * Resolves the video ID at a 1-based index (1 = first, 2 = second, 3 = third, etc.).
     */
    suspend fun resolveVideoId(query: String, itemIndex: Int = 1): String? {
        val list = resolveVideoIds(query, maxCount = maxOf(itemIndex, 5))
        val targetIdx = maxOf(0, itemIndex - 1)
        val selected = list.getOrNull(targetIdx) ?: list.firstOrNull()
        SpikeLogger.log(TAG, "YouTubePlayerHelper resolveVideoId query='$query', index=$itemIndex -> $selected (total found=${list.size})")
        return selected
    }

    /**
     * Resolves the first video ID for a query.
     */
    suspend fun resolveFirstVideoId(query: String): String? = resolveVideoId(query, 1)

    /**
     * Pre-fetches video IDs in the background while the user looks at search results.
     */
    suspend fun preFetchVideoId(query: String) = withContext(Dispatchers.IO) {
        val norm = query.trim().lowercase()
        if (!queryCache.containsKey(norm)) {
            resolveVideoIds(query, 5)
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
