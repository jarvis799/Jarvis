package com.jarvis.assistant.voice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One spoken exchange: what was heard, what was answered, and how long the answer took. */
data class VoiceTurn(
    val heard: String = "",
    val reply: String = "",
    val millis: Long = 0L,
    val finished: Boolean = false
)

/**
 * The last thing the boss said and what Jarvis answered, with the answer time.
 * Written by the live conversation service, read by the home screen.
 */
object VoiceTurnLog {

    private val _turn = MutableStateFlow(VoiceTurn())
    val turn: StateFlow<VoiceTurn> = _turn.asStateFlow()

    @Volatile private var startedAt = 0L

    fun begin(heard: String) {
        startedAt = System.currentTimeMillis()
        _turn.value = VoiceTurn(heard = heard.trim(), reply = "", millis = 0L, finished = false)
    }

    fun heard(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        if (startedAt == 0L) startedAt = System.currentTimeMillis()
        _turn.value = _turn.value.copy(heard = clean)
    }

    fun complete(reply: String) {
        val clean = reply.trim()
        if (clean.isEmpty()) return
        val elapsed = if (startedAt > 0L) (System.currentTimeMillis() - startedAt).coerceAtLeast(1L) else 0L
        _turn.value = _turn.value.copy(reply = clean, millis = elapsed, finished = true)
        startedAt = 0L
    }

    fun reset() {
        startedAt = 0L
        _turn.value = VoiceTurn()
    }
}
