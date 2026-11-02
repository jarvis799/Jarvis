package com.jarvis.assistant.voice

/**
 * Gates PCM before it is uploaded to the live model.
 *
 * Background noise must never reach the model: a fan, a TV or street sound used to cross a fixed
 * threshold, start a turn, and make Jarvis answer the same canned line again and again. The floor
 * here follows the *quietest* level of the last ~800 ms, so steady ambient sound raises the bar
 * while the natural dips between words keep real speech well above it.
 */
class LiveInputGate {
    data class Output(val chunks: List<ByteArray>, val started: Boolean = false, val ended: Boolean = false)

    private val onset = java.util.ArrayDeque<ByteArray>()
    private val recentLevels = java.util.ArrayDeque<Float>()
    private var noise = 0.02f
    private var voicedMs = 0f
    private var silentMs = 0f
    private var open = false

    fun accept(pcm: ByteArray, rms: Float): Output {
        val ms = pcm.size * 1000f / 32000f
        val level = if (rms.isFinite()) rms.coerceIn(0f, 1f) else 0f
        recentLevels.addLast(level)
        while (recentLevels.size > WINDOW_FRAMES) recentLevels.pollFirst()
        val quietest = recentLevels.minOrNull() ?: level
        val target = (quietest * 1.15f).coerceIn(0.008f, MAX_FLOOR)
        noise = noise * 0.8f + target * 0.2f
        val threshold = maxOf(MIN_VOICED, noise * NOISE_MULTIPLIER)
        val voiced = level > threshold
        if (!open) {
            onset.addLast(pcm.copyOf())
            while (onset.sumOf { it.size } > 24000) onset.pollFirst()
            // A short click decays instead of accumulating, so it can never add up to an utterance.
            voicedMs = if (voiced) voicedMs + ms else (voicedMs - ms).coerceAtLeast(0f)
            if (voicedMs < ONSET_MS) return Output(emptyList())
            open = true
            silentMs = 0f
            val chunks = onset.toList()
            onset.clear()
            return Output(chunks, started = true)
        }
        silentMs = if (voiced) 0f else silentMs + ms
        if (silentMs < TAIL_MS) return Output(listOf(pcm))
        reset()
        return Output(emptyList(), ended = true)
    }

    fun reset() {
        onset.clear()
        recentLevels.clear()
        voicedMs = 0f
        silentMs = 0f
        open = false
    }

    private companion object {
        const val MIN_VOICED = 0.032f
        const val NOISE_MULTIPLIER = 1.6f
        const val MAX_FLOOR = 0.06f
        const val WINDOW_FRAMES = 40
        const val ONSET_MS = 170f
        const val TAIL_MS = 600f
    }
}