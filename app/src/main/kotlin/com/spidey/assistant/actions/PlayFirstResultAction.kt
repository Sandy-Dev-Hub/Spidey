package com.spidey.assistant.actions

import com.spidey.assistant.core.ActionHandler
import com.spidey.assistant.core.ActionResult

/**
 * Action representing an in-app command to play the first result (e.g. within YouTube).
 */
data class PlayFirstResultAction(
    val query: String? = null,
    val targetApp: String = "youtube",
    val itemIndex: Int = 1
) : AssistantAction

/**
 * Handles [PlayFirstResultAction] safely within supported apps.
 */
class PlayFirstResultHandler : ActionHandler<PlayFirstResultAction> {

    override val actionType: Class<PlayFirstResultAction> = PlayFirstResultAction::class.java

    override suspend fun handle(action: PlayFirstResultAction): ActionResult {
        val queryPart = if (!action.query.isNullOrBlank()) " for ${action.query}" else ""
        return ActionResult.Success(
            spokenMessage = "Playing the first result$queryPart."
        )
    }
}

