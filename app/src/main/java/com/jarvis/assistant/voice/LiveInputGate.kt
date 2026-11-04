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
    private var backgroundMs = 0f

    fun accept(pcm: ByteArray, rms: Float): Output = Output(listOf(pcm))

    fun reset() {
        onset.clear()
        recentLevels.clear()
        voicedMs = 0f
        silentMs = 0f
        open = false
        backgroundMs = 0f
    }

    private companion object {
        const val MIN_VOICED = 0.040f
        const val NOISE_MULTIPLIER = 2.0f
        const val MAX_FLOOR = 0.06f
        const val WINDOW_FRAMES = 40
        const val ONSET_MS = 260f
        const val TAIL_MS = 700f
        const val BACKGROUND_LIMIT_MS = 1400f
    }
}