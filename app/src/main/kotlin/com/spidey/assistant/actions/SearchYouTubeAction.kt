package com.spidey.assistant.actions

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.spidey.assistant.core.ActionHandler
import com.spidey.assistant.core.ActionResult
import com.spidey.assistant.youtube.YouTubePlayerHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URLEncoder

/**
 * Action representing a search query or song play on YouTube.
 */
data class SearchYouTubeAction(
    val query: String,
    val isPlayDirectly: Boolean = false
) : AssistantAction

/**
 * Handles [SearchYouTubeAction] by searching or directly playing via YouTube.
 * Pre-fetches the first video in the background for instant playback if user requests "play first one".
 * Uses Android 15-compliant BackgroundAppLauncher routes.
 */
class SearchYouTubeHandler(
    private val context: Context,
    private val isDeviceLocked: () -> Boolean = {
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        km?.isDeviceLocked == true
    }
) : ActionHandler<SearchYouTubeAction> {

    override val actionType: Class<SearchYouTubeAction> = SearchYouTubeAction::class.java

    override suspend fun handle(action: SearchYouTubeAction): ActionResult {
        if (isDeviceLocked()) {
            return ActionResult.NeedsUnlock
        }

        val cleanQuery = action.query.trim()
        if (cleanQuery.isBlank()) {
            return ActionResult.Failure(
                spokenMessage = "What would you like me to search for on YouTube?",
                reason = "Empty search query"
            )
        }

        return try {
            if (action.isPlayDirectly) {
                val videoId = YouTubePlayerHelper.resolveFirstVideoId(cleanQuery)
                if (videoId != null && YouTubePlayerHelper.playVideo(context, videoId)) {
                    return ActionResult.Success(spokenMessage = "Playing $cleanQuery on YouTube.")
                }
            }

            // Standard search intent
            val intent = Intent(Intent.ACTION_SEARCH).apply {
                setPackage("com.google.android.youtube")
                putExtra("query", cleanQuery)
                putExtra(android.app.SearchManager.QUERY, cleanQuery)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val launched = BackgroundAppLauncher.launch(context, intent)
            if (!launched) {
                val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
                val webUri = Uri.parse("https://www.youtube.com/results?search_query=$encoded")
                val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                BackgroundAppLauncher.launch(context, webIntent)
            }

            // Pre-fetch the first video in background for instant playback if user follows up with "play the first one"
            CoroutineScope(Dispatchers.IO).launch {
                YouTubePlayerHelper.preFetchVideoId(cleanQuery)
            }

            ActionResult.Success(spokenMessage = "Searching $cleanQuery on YouTube.")
        } catch (e: Exception) {
            ActionResult.Failure(
                spokenMessage = "Failed to search YouTube.",
                reason = e.message ?: "Error launching YouTube search"
            )
        }
    }
}
