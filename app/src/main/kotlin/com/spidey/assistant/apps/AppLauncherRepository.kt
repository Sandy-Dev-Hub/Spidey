package com.spidey.assistant.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.spidey.assistant.apps.model.AppRecord

/**
 * Discovers all installed applications that declare a launcher activity
 * (MAIN intent + CATEGORY_LAUNCHER).
 * Uses in-memory caching for sub-millisecond retrieval on budget devices.
 */
class AppLauncherRepository(private val context: Context) {

    @Volatile
    private var cachedApps: List<AppRecord>? = null

    fun getInstalledApps(forceRefresh: Boolean = false): List<AppRecord> {
        if (!forceRefresh) {
            cachedApps?.let { return it }
        }

        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        val ownPackage = context.packageName

        val apps = resolveInfos
            .filter { it.activityInfo.packageName != ownPackage }
            .map { ri ->
                val label = ri.loadLabel(pm).toString().trim()
                AppRecord(
                    label = label,
                    packageName = ri.activityInfo.packageName
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }

        cachedApps = apps
        return apps
    }

    fun invalidateCache() {
        cachedApps = null
    }
}

