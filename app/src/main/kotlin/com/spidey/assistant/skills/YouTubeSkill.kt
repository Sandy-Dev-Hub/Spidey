package com.spidey.assistant.skills

import android.content.Context
import android.content.Intent
import com.spidey.assistant.actions.AssistantAction
import com.spidey.assistant.actions.BackgroundAppLauncher
import com.spidey.assistant.actions.OpenAppAction
import com.spidey.assistant.actions.PlayFirstResultAction
import com.spidey.assistant.actions.PlayFirstResultHandler
import com.spidey.assistant.actions.SearchYouTubeAction
import com.spidey.assistant.actions.SearchYouTubeHandler
import com.spidey.assistant.core.ActionResult

/**
 * AppSkill for YouTube.
 * Handles opening YouTube, video search, and ordinal playback.
 */
class YouTubeSkill(
    private val searchHandler: SearchYouTubeHandler? = null,
    private val playFirstHandler: PlayFirstResultHandler? = null
) : AppSkill {
    override val packageName: String = "com.google.android.youtube"
    override val supportedActions: List<String> = listOf("search", "play", "play_ordinal", "open")

    override suspend fun execute(action: AssistantAction, context: Context): ActionResult {
        return when (action) {
            is SearchYouTubeAction -> {
                val handler = searchHandler ?: SearchYouTubeHandler(context)
                handler.handle(action)
            }
            is PlayFirstResultAction -> {
                val handler = playFirstHandler ?: PlayFirstResultHandler(context)
                handler.handle(action)
            }
            is OpenAppAction -> {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                if (launchIntent != null) {
                    launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    BackgroundAppLauncher.launch(context, launchIntent)
                    ActionResult.Success(spokenMessage = "Opening YouTube.")
                } else {
                    ActionResult.Failure("YouTube is not installed on this device.", "Package not found")
                }
            }
            else -> ActionResult.Failure("YouTube cannot perform this action.", "Unsupported action for YouTubeSkill")
        }
    }

    override suspend fun verify(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }
    }
}
