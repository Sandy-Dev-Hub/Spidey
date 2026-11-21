package com.spidey.assistant.skills

import android.content.Context
import com.spidey.assistant.actions.AssistantAction
import com.spidey.assistant.core.ActionResult

/**
 * Interface representing an app-specific capability/skill.
 * Each supported application implements this contract in its own dedicated file.
 */
interface AppSkill {
    val packageName: String
    val supportedActions: List<String>
    suspend fun execute(action: AssistantAction, context: Context): ActionResult
    suspend fun verify(context: Context): Boolean
}
