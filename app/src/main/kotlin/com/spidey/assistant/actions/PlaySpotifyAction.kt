package com.spidey.assistant.actions

/**
 * Action representing an intent to play music, artist, playlist, or track on Spotify.
 */
data class PlaySpotifyAction(
    val query: String,
    val isPlayDirectly: Boolean = true
) : AssistantAction
