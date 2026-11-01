package com.spidey.assistant.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.spidey.assistant.apps.model.AppRecord

/**
 * Discovers all installed applications that declare a launcher activity
 * (MAIN intent + CATEGORY_LAUNCHER).
 */
class AppLauncherRepository(private val context: Context) {

    fun getInstalledApps(): List<AppRecord> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        val ownPackage = context.packageName

        return resolveInfos
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
    }
}

