package com.spidey.assistant.engine

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * J0: Feature flags backed by SharedPreferences.
 *
 * Safe mode = true  → use the proven Legacy engines (default after J0).
 * Safe mode = false → use whatever new engine is configured.
 *
 * To reset to safe mode: Settings → Developer → Safe Mode toggle.
 */
object FeatureFlags {

    private const val PREFS_NAME = "spidey_feature_flags"
    private const val KEY_SAFE_MODE = "safe_mode"

    // Latency tracer on/off
    private const val KEY_LATENCY_TRACING = "latency_tracing"

    // Active session timeout in milliseconds (default 30 s)
    private const val KEY_SESSION_TIMEOUT_MS = "session_timeout_ms"
    private const val DEFAULT_SESSION_TIMEOUT_MS = 30_000L

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /** When true: Legacy STT/TTS/Brain are used; no new engines active. Default ON. */
    var safeMode: Boolean
        get() = prefs?.getBoolean(KEY_SAFE_MODE, true) ?: true
        set(value) = prefs?.edit { putBoolean(KEY_SAFE_MODE, value) } ?: Unit

    /** When true: LatencyTracer logs p50/p95 to logcat tag SPIDEY. */
    var latencyTracingEnabled: Boolean
        get() = prefs?.getBoolean(KEY_LATENCY_TRACING, true) ?: true
        set(value) = prefs?.edit { putBoolean(KEY_LATENCY_TRACING, value) } ?: Unit

    /** Active-session follow-up timeout in ms. User-configurable. */
    var sessionTimeoutMs: Long
        get() = prefs?.getLong(KEY_SESSION_TIMEOUT_MS, DEFAULT_SESSION_TIMEOUT_MS) ?: DEFAULT_SESSION_TIMEOUT_MS
        set(value) = prefs?.edit { putLong(KEY_SESSION_TIMEOUT_MS, value) } ?: Unit
}

