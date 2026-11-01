package com.spidey.assistant.apps

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages apps excluded from voice launch by user configuration
 * (e.g., banking apps, payment apps, private messengers).
 */
class AppExclusionRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getExcludedPackages(): Set<String> {
        return prefs.getStringSet(KEY_EXCLUDED, emptySet())?.toSet() ?: emptySet()
    }

    fun isAppExcluded(packageName: String): Boolean {
        return getExcludedPackages().contains(packageName)
    }

    fun setAppExcluded(packageName: String, excluded: Boolean) {
        val current = getExcludedPackages().toMutableSet()
        if (excluded) {
            current.add(packageName)
        } else {
            current.remove(packageName)
        }
        prefs.edit().putStringSet(KEY_EXCLUDED, current).apply()
    }

    companion object {
        private const val PREFS_NAME = "spidey_app_exclusions"
        private const val KEY_EXCLUDED = "excluded_package_names"
    }
}

