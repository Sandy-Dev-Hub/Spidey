package com.spidey.assistant.actions

import android.app.ActivityOptions
import android.app.PendingIntent
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.spidey.spikes.util.SpikeLogger

/**
 * Handles launching apps from the background (e.g. while YouTube is in front)
 * and verifying whether the target app actually reached the foreground.
 */
object BackgroundAppLauncher {

    private const val TAG = "SPIDEY"

    /**
     * Attempts to launch an activity from a background service using Android 15-compliant BAL routes.
     */
    fun launch(context: Context, intent: Intent): Boolean {
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        )

        val optionsBundle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ActivityOptions.makeBasic().apply {
                setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            }.toBundle()
        } else {
            null
        }

        // Route A: PendingIntent with MODE_BACKGROUND_ACTIVITY_START_ALLOWED
        try {
            val pendingIntent = PendingIntent.getActivity(
                context,
                (System.currentTimeMillis() % 10000).toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                optionsBundle
            )
            pendingIntent.send(context, 0, null, null, null, null, optionsBundle)
            SpikeLogger.log(TAG, "Sent launch via PendingIntent with MODE_BACKGROUND_ACTIVITY_START_ALLOWED")
        } catch (e: Exception) {
            Log.w(TAG, "PendingIntent send failed: ${e.message}")
        }

        // Route B: Direct startActivity with options
        return try {
            if (optionsBundle != null) {
                context.startActivity(intent, optionsBundle)
            } else {
                context.startActivity(intent)
            }
            SpikeLogger.log(TAG, "Called startActivity for ${intent.component?.packageName ?: intent.getPackage()}")
            true
        } catch (e: Exception) {
            SpikeLogger.log(TAG, "startActivity failed: ${e.message}")
            false
        }
    }

    /**
     * Confirms whether the target package is currently in the foreground using UsageStatsManager.
     */
    fun isAppInForeground(context: Context, targetPackage: String): Boolean {
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return false
            val now = System.currentTimeMillis()
            val events = usm.queryEvents(now - 3500L, now)
            val event = UsageEvents.Event()
            var lastResumedPkg: String? = null
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    lastResumedPkg = event.packageName
                }
            }
            SpikeLogger.log(TAG, "Foreground verification: target=$targetPackage, activeResumed=$lastResumedPkg")
            lastResumedPkg == targetPackage
        } catch (e: Exception) {
            Log.w(TAG, "UsageStats query failed: ${e.message}")
            false
        }
    }

    /**
     * Checks if the app has overlay permission to draw over other apps.
     */
    fun canDrawOverlays(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }
}

