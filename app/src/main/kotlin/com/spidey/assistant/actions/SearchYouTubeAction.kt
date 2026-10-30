package com.spidey.assistant.actions

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.spidey.assistant.core.ActionHandler
import com.spidey.assistant.core.ActionResult
import java.net.URLEncoder

/**
 * Action representing a search query or song play on YouTube.
 */
data class SearchYouTubeAction(val query: String) : AssistantAction

/**
 * Handles [SearchYouTubeAction] by launching YouTube directly with search results or playback.
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
            val intent = Intent(Intent.ACTION_SEARCH).apply {
                setPackage("com.google.android.youtube")
                putExtra("query", cleanQuery)
                putExtra(android.app.SearchManager.QUERY, cleanQuery)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            try {
                context.startActivity(intent)
            } catch (_: Exception) {
                // If native YouTube app package is not available, open in default browser
                val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
                val webUri = Uri.parse("https://www.youtube.com/results?search_query=$encoded")
                val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(webIntent)
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
