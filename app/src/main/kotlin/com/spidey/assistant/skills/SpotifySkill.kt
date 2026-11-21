package com.spidey.assistant.skills

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.view.KeyEvent
import com.spidey.assistant.actions.AssistantAction
import com.spidey.assistant.actions.BackgroundAppLauncher
import com.spidey.assistant.actions.OpenAppAction
import com.spidey.assistant.actions.PlaySpotifyAction
import com.spidey.assistant.core.ActionResult

/**
 * AppSkill for Spotify.
 * Handles opening Spotify, searching/playing songs/artists/playlists via MEDIA_PLAY_FROM_SEARCH,
 * and media playback controls (next, resume, pause).
 */
class SpotifySkill : AppSkill {
    override val packageName: String = "com.spotify.music"
    override val supportedActions: List<String> = listOf("play", "search", "open", "next", "change", "resume")

    override suspend fun execute(action: AssistantAction, context: Context): ActionResult {
        return when (action) {
            is OpenAppAction -> {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                if (launchIntent != null) {
                    launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    val launched = BackgroundAppLauncher.launch(context, launchIntent)
                    if (launched) {
                        ActionResult.Success(spokenMessage = "Opening Spotify.")
                    } else {
                        ActionResult.Failure("Failed to open Spotify.", "Launch failed")
                    }
                } else {
                    ActionResult.Failure("Spotify is not installed on this device.", "Package not found")
                }
            }
            is PlaySpotifyAction -> {
                val query = action.query.trim()
                if (query.equals("next", ignoreCase = true) || query.equals("change", ignoreCase = true)) {
                    sendMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_NEXT)
                    ActionResult.Success(spokenMessage = "Skipping to the next song boss.")
                } else if (query.isBlank() || query.equals("resume", ignoreCase = true) || query.equals("play", ignoreCase = true)) {
                    sendMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_PLAY)
                    ActionResult.Success(spokenMessage = "Resuming Spotify playback boss.")
                } else if (query.equals("pause", ignoreCase = true) || query.equals("stop", ignoreCase = true)) {
                    sendMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_PAUSE)
                    ActionResult.Success(spokenMessage = "Spotify playback paused boss.")
                } else {
                    val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                        setPackage(packageName)
                        putExtra(SearchManager.QUERY, query)
                        putExtra(MediaStore.EXTRA_MEDIA_TITLE, query)
                        putExtra(MediaStore.EXTRA_MEDIA_FOCUS, MediaStore.Audio.Media.ENTRY_CONTENT_TYPE)
                        putExtra("android.intent.extra.focus", "vnd.android.cursor.item/audio")
                        putExtra("android.intent.extra.title", query)
                        putExtra("autolaunch", true)
                        putExtra("play", true)
                        putExtra("android.intent.extra.START_PLAYBACK", true)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    val launched = BackgroundAppLauncher.launch(context, intent)
                    if (launched) {
                        ActionResult.Success(spokenMessage = "Opening $query on Spotify boss.")
                    } else {
                        val uriIntent = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:${Uri.encode(query)}")).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        if (BackgroundAppLauncher.launch(context, uriIntent)) {
                            ActionResult.Success(spokenMessage = "Opening $query on Spotify boss.")
                        } else {
                            ActionResult.Failure("Could not open Spotify for $query boss.", "Failed to launch Spotify intent")
                        }
                    }
                }
            }
            else -> ActionResult.Failure("Spotify cannot perform this action.", "Unsupported action for SpotifySkill")
        }
    }

    private fun sendMediaKeyEvent(context: Context, keyCode: Int) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        } catch (_: Exception) {}

        try {
            val down = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                setPackage(packageName)
                putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            }
            val up = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                setPackage(packageName)
                putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_UP, keyCode))
            }
            context.sendOrderedBroadcast(down, null)
            context.sendOrderedBroadcast(up, null)
        } catch (_: Exception) {}
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
