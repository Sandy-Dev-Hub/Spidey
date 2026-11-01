package com.spidey.spikes.util

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lightweight spike logger.
 *
 * Writes timestamped entries to:
 *   – Android Logcat (tag SPIDEY_SPIKE)
 *   – In-memory list (read by UI)
 *   – getExternalFilesDir(null)/spidey_spike_log.txt  (survives app being backgrounded;
 *     readable via:  adb pull /sdcard/Android/data/com.spidey.spikes.debug/files/spidey_spike_log.txt)
 *
 * Phase 0 only. No sensitive data is ever passed into this logger.
 */
object SpikeLogger {

    private const val TAG = "SPIDEY_SPIKE"
    private const val LOG_FILE = "spidey_spike_log.txt"
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    /** In-memory ring buffer – last 500 entries. */
    private val buffer = ArrayDeque<String>(500)

    @Volatile
    private var logFile: File? = null

    /**
     * Call once from SpikeApp.onCreate or the first Activity.
     */
    fun init(context: Context) {
        logFile = File(context.applicationContext.getExternalFilesDir(null), LOG_FILE)
        log("LOGGER", "=== Session started ===")
    }

    fun log(tag: String, message: String) {
        val entry = "${fmt.format(Date())} [$tag] $message"
        Log.d(TAG, entry)
        synchronized(buffer) {
            if (buffer.size >= 500) buffer.removeFirst()
            buffer.addLast(entry)
        }
        writeToFile(entry)
    }

    /** Returns a snapshot of the in-memory buffer. */
    fun snapshot(): List<String> = synchronized(buffer) { buffer.toList() }

    /** Clears in-memory buffer (does not touch the file). */
    fun clear() = synchronized(buffer) { buffer.clear() }

    fun logPath(): String = logFile?.absolutePath ?: "not initialised"

    // ── private ──────────────────────────────────────────────────────────

    private fun writeToFile(entry: String) {
        try {
            logFile?.appendText(entry + "\n")
        } catch (e: Exception) {
            Log.e(TAG, "File write failed: ${e.message}")
        }
    }
}
