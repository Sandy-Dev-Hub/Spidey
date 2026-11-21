package com.spidey.assistant.actions

import android.content.Context
import com.spidey.assistant.core.ActionHandler
import com.spidey.assistant.core.ActionResult
import com.spidey.assistant.skills.SpotifySkill

/**
 * Handles [PlaySpotifyAction] by delegating to [SpotifySkill].
 */
class PlaySpotifyHandler(
    private val context: Context,
    private val skill: SpotifySkill = SpotifySkill()
) : ActionHandler<PlaySpotifyAction> {
    override val actionType: Class<PlaySpotifyAction> = PlaySpotifyAction::class.java

    override suspend fun handle(action: PlaySpotifyAction): ActionResult {
        return skill.execute(action, context)
    }
}
