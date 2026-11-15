package com.spidey.assistant.engine

/**
 * J0: Engine abstraction interfaces.
 *
 * These thin interfaces let us swap STT, TTS, and Brain (AI) engines
 * independently — the existing implementations become the default "Legacy"
 * adapters, and new engines (Whisper, Piper, local LLM) are added behind
 * these interfaces without touching ConversationManager or the service.
 *
 * Safe mode (FeatureFlags.safeMode) always returns to the Legacy adapters.
 */

/** Speech-to-text engine contract. */
interface SttEngine {
    /** True when actively recording. */
    val isListening: Boolean

    /** Start a single recognition session. Result delivered via [onResult]. */
    fun startListening(onResult: (text: String) -> Unit, onError: (code: Int) -> Unit)

    /** Stop the current recognition session. */
    fun stopListening()

    /** Release all resources. */
    fun destroy()
}

/** Text-to-speech engine contract. */
interface TtsEngine {
    /** Speak [text]; call [onDone] when audio finishes (or immediately on error). */
    fun speak(text: String, onDone: () -> Unit)

    /** Speak chunk in streaming mode; queue subsequent chunks. */
    fun speakStream(chunk: String, isFirst: Boolean, isLast: Boolean, onDone: (() -> Unit)? = null) {
        if (chunk.isNotBlank()) {
            speak(chunk) { onDone?.invoke() }
        } else {
            onDone?.invoke()
        }
    }

    /** Stop speaking immediately. */
    fun stop()

    /** Release all resources. */
    fun destroy()
}

/**
 * Brain (AI / NLU) engine contract.
 *
 * Receives a raw user utterance and returns a spoken reply string.
 * Routing to system actions (call, open app, etc.) is still handled by
 * [IntentEngine] before the brain is consulted.
 */
interface BrainEngine {
    /**
     * Process a conversational [utterance] and return a reply to be spoken.
     * Must be called from a coroutine (may do I/O).
     */
    suspend fun reply(utterance: String): String

    /**
     * Stream conversational reply sentence by sentence via [onChunk].
     * Returns full concatenated reply.
     */
    suspend fun streamReply(utterance: String, onChunk: suspend (String) -> Unit): String {
        val full = reply(utterance)
        onChunk(full)
        return full
    }

    /** Clear conversation history. */
    fun clearHistory()
}

