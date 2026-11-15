package com.spidey.assistant.engine

import android.util.Log
import com.spidey.spikes.util.SpikeLogger
import java.util.concurrent.CopyOnWriteArrayList

/**
 * J0: Lightweight latency tracer.
 *
 * Records timestamps at the five key pipeline milestones for one interaction:
 *   1. SPEECH_END      – last audio frame received
 *   2. TEXT_READY      – STT result delivered to ConversationManager
 *   3. INTENT_DECIDED  – IntentEngine.parse() returned
 *   4. ACTION_STARTED  – ActionRouter began executing
 *   5. FIRST_AUDIO     – TtsEngine.speak() was called with the reply
 *
 * At session end, computes and logs p50/p95 for each gap.
 * All logging goes to logcat tag SPIDEY so it appears in the SPIDEY filter.
 *
 * Usage:
 *   LatencyTracer.mark(LatencyTracer.Phase.SPEECH_END)
 *   LatencyTracer.mark(LatencyTracer.Phase.TEXT_READY)
 *   ...
 *   LatencyTracer.finishRound()   // logs this round; resets for next
 *   LatencyTracer.printSummary()  // prints aggregate p50/p95
 */
object LatencyTracer {

    enum class Phase { WAKE_DETECTED, SPEECH_END, TEXT_READY, INTENT_DECIDED, ACTION_STARTED, FIRST_AUDIO }

    private const val TAG = "SPIDEY"
    private val marks = mutableMapOf<Phase, Long>()

    // Stores per-round gap lists: "SPEECH_END→TEXT_READY" → [ms, ms, ...]
    private val history = mutableMapOf<String, CopyOnWriteArrayList<Long>>()

    var enabled: Boolean = true

    /** Record the current wall-clock time for [phase]. */
    fun mark(phase: Phase) {
        if (!enabled) return
        marks[phase] = System.currentTimeMillis()
    }

    /** Log this round's gaps; store in history; reset marks. */
    fun finishRound() {
        if (!enabled) return
        val ordered = listOf(
            Phase.WAKE_DETECTED to Phase.FIRST_AUDIO,
            Phase.SPEECH_END to Phase.TEXT_READY,
            Phase.TEXT_READY to Phase.INTENT_DECIDED,
            Phase.INTENT_DECIDED to Phase.ACTION_STARTED,
            Phase.ACTION_STARTED to Phase.FIRST_AUDIO,
            Phase.SPEECH_END to Phase.ACTION_STARTED,
            Phase.SPEECH_END to Phase.FIRST_AUDIO
        )
        val sb = StringBuilder("SPIDEY latency:")
        for ((from, to) in ordered) {
            val t0 = marks[from] ?: continue
            val t1 = marks[to] ?: continue
            val gap = t1 - t0
            val label = "${from.name}→${to.name}"
            sb.append(" $label=${gap}ms")
            history.getOrPut(label) { CopyOnWriteArrayList() }.add(gap)
        }
        SpikeLogger.log(TAG, sb.toString())
        marks.clear()
    }

    /** Print aggregate p50/p95 for all rounds so far. */
    fun printSummary() {
        if (!enabled || history.isEmpty()) return
        val sb = StringBuilder("SPIDEY latency summary (${history.values.firstOrNull()?.size ?: 0} rounds):")
        for ((label, samples) in history) {
            if (samples.isEmpty()) continue
            val sorted = samples.sorted()
            val p50 = sorted[sorted.size / 2]
            val p95 = sorted[(sorted.size * 0.95).toInt().coerceAtMost(sorted.size - 1)]
            sb.append(" $label p50=${p50}ms p95=${p95}ms")
        }
        SpikeLogger.log(TAG, sb.toString())
    }

    /** Reset everything — call at app start. */
    fun reset() {
        marks.clear()
        history.clear()
    }
}

