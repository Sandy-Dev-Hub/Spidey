package com.spidey.assistant.actions

import android.content.Context
import android.os.PowerManager
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
 * Wakes the screen so the user can also see the display if the device was locked/sleeping.
 */
class TimeHandler(private val context: Context? = null) : ActionHandler<TimeAction> {
    override val actionType: Class<TimeAction> = TimeAction::class.java

    override suspend fun handle(action: TimeAction): ActionResult {
        // Wake the screen if context is available
        context?.let { ctx ->
            try {
                val pm = ctx.getSystemService(PowerManager::class.java)
                @Suppress("DEPRECATION")
                val wl = pm?.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                    "spidey:time_wake_screen"
                )
                wl?.acquire(3000L)
            } catch (_: Exception) {}
        }

        val now = Date()
        val message = if (action.isDateOnly) {
            val sdf = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
            com.spidey.assistant.personality.SpideyPersona.dateResponse(sdf.format(now))
        } else {
            val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
            com.spidey.assistant.personality.SpideyPersona.timeResponse(sdf.format(now))
        }
        return ActionResult.Success(spokenMessage = message)
    }
}
