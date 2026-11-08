package com.jarvis.assistant.voice.hotword

/** Monotonic, generation-scoped health of the actual audio worker, not the standby request. */
class WakeListenerHealth {
    private var generation = 0L
    private var running = false
    private var confirmed = false
    private var lastCallbackMs = 0L
    val isListening: Boolean get() = running && confirmed

    fun begin(nowMs: Long): Long {
        generation += 1
        running = true
        confirmed = false
        lastCallbackMs = nowMs
        return generation
    }

    fun isCurrent(token: Long): Boolean = running && token == generation

    fun onAudioCallback(token: Long, nowMs: Long): Boolean {
        if (!isCurrent(token)) return false
        lastCallbackMs = nowMs
        confirmed = true
        return true
    }

    fun isStalled(nowMs: Long, timeoutMs: Long = 12_000L): Boolean =
        running && nowMs - lastCallbackMs >= timeoutMs

    fun stop() {
        running = false
        confirmed = false
        generation += 1
    }

    companion object {
        fun retryDelay(attempt: Int): Long =
            (1_500L * (1L shl attempt.coerceIn(0, 5))).coerceAtMost(30_000L)
    }
}
