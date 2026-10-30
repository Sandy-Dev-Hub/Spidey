package com.spidey.assistant.actions

import com.spidey.assistant.core.ActionHandler
import com.spidey.assistant.core.ActionResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Action representing a request to speak the current time or date.
 */
class TimeAction(val isDateOnly: Boolean = false) : AssistantAction {
    override fun equals(other: Any?): Boolean = other is TimeAction && other.isDateOnly == isDateOnly
    override fun hashCode(): Int = isDateOnly.hashCode()
    override fun toString(): String = "TimeAction(isDateOnly=$isDateOnly)"
}

/**
 * Handles [TimeAction] by formatting the local time or date.
 */
class TimeHandler : ActionHandler<TimeAction> {
    override val actionType: Class<TimeAction> = TimeAction::class.java

    override suspend fun handle(action: TimeAction): ActionResult {
        val now = Date()
        val message = if (action.isDateOnly) {
            val sdf = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
            "Today is ${sdf.format(now)}."
        } else {
            val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
            "The time is ${sdf.format(now)}."
        }
        return ActionResult.Success(spokenMessage = message)
    }
}
