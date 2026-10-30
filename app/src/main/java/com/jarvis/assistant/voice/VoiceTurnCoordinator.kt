package com.jarvis.assistant.voice

import java.util.concurrent.atomic.AtomicLong

/**
 * Generation authority for one voice session.
 *
 * Every asynchronous step that can reach the user (LLM deltas, TTS phrases, queued PCM,
 * UI state) is tagged with the token of the turn that started it. [invalidate] makes all
 * outstanding tokens stale at once, which is how "an interrupted answer never resumes" is
 * enforced structurally instead of by timing guesses.
 *
 * Thread-safe: callbacks arrive on OkHttp, TTS and coroutine threads.
 */
class VoiceTurnCoordinator {

    private val generation = AtomicLong(0L)

    fun current(): Long = generation.get()

    /** Starts a new turn; everything tagged with an older token becomes stale. */
    fun begin(): Long = generation.incrementAndGet()

    /** Interruption / cancellation; everything tagged with an older token becomes stale. */
    fun invalidate(): Long = generation.incrementAndGet()

    fun isCurrent(token: Long): Boolean = token == generation.get()
}