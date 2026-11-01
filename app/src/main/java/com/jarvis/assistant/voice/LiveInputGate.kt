package com.jarvis.assistant.voice

/** Gates actual PCM before upload, preserving onset and short inter-word pauses. */
class LiveInputGate {
    data class Output(val chunks: List<ByteArray>, val ended: Boolean = false)
    private val onset = java.util.ArrayDeque<ByteArray>()
    private var noise = 0.025f
    private var voicedMs = 0f
    private var silentMs = 0f
    private var open = false

    fun accept(pcm: ByteArray, rms: Float): Output {
        val ms = pcm.size * 1000f / 32000f
        if (!open && rms < noise * 1.4f) noise = (noise * .98f + rms * .02f).coerceIn(.008f, .08f)
        val voiced = rms.isFinite() && rms > maxOf(.035f, noise * 2.4f)
        if (!open) {
            onset.addLast(pcm.copyOf())
            while (onset.sumOf { it.size } > 16000) onset.pollFirst()
            voicedMs = if (voiced) voicedMs + ms else 0f
            if (voicedMs < 180f) return Output(emptyList())
            open = true
            silentMs = 0f
            val chunks = onset.toList()
            onset.clear()
            return Output(chunks)
        }
        silentMs = if (voiced) 0f else silentMs + ms
        if (silentMs < 600f) return Output(listOf(pcm))
        reset()
        return Output(emptyList(), ended = true)
    }

    fun reset() {
        onset.clear()
        voicedMs = 0f
        silentMs = 0f
        open = false
    }
}