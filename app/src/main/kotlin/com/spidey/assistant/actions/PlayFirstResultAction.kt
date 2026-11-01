package com.spidey.assistant.actions

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import com.spidey.assistant.core.ActionHandler
import com.spidey.assistant.core.ActionResult
import com.spidey.assistant.youtube.YouTubePlayerHelper

/**
 * Action representing an in-app command to play the first result (e.g. within YouTube).
 */
data class PlayFirstResultAction(
    val query: String? = null,
    val targetApp: String = "youtube",
    val itemIndex: Int = 1
) : AssistantAction

/**
 * Handles [PlayFirstResultAction] by resolving the first video ID for the query and launching playback.
 */
class PlayFirstResultHandler(
    private val context: Context,
    private val isDeviceLocked: () -> Boolean = {
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        km?.isDeviceLocked == true
    }
) : ActionHandler<PlayFirstResultAction> {

    override val actionType: Class<PlayFirstResultAction> = PlayFirstResultAction::class.java

    override suspend fun handle(action: PlayFirstResultAction): ActionResult {
        if (isDeviceLocked()) {
            return ActionResult.NeedsUnlock
        }

        val targetQuery = action.query?.trim()
        if (targetQuery.isNullOrBlank()) {
            return ActionResult.Failure(
                spokenMessage = "What would you like me to play?",
                reason = "No target query for play first"
            )
        }

        val videoId = YouTubePlayerHelper.resolveFirstVideoId(targetQuery)
        return if (videoId != null) {
            val played = YouTubePlayerHelper.playVideo(context, videoId)
            if (played) {
                ActionResult.Success(spokenMessage = "Playing the first result for $targetQuery.")
            } else {
                ActionResult.Failure(
                    spokenMessage = "I found the video but could not start playback.",
                    reason = "Failed to launch YouTube playback intent"
                )
            }
        } else {
            // Fallback: search on YouTube so user can view results
            val intent = Intent(Intent.ACTION_SEARCH).apply {
                setPackage("com.google.android.youtube")
                putExtra("query", targetQuery)
                putExtra(android.app.SearchManager.QUERY, targetQuery)
            }
            BackgroundAppLauncher.launch(context, intent)
            ActionResult.Success(spokenMessage = "I opened the search results for $targetQuery.")
        }
    }
}
