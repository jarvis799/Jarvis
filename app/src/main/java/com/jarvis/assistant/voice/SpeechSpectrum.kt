package com.jarvis.assistant.voice

import kotlin.math.sqrt

/**
 * Reads the shape of one 20 ms PCM16 frame to tell a human voice apart from background audio.
 *
 * A voice keeps its energy in the formant band: little below ~220 Hz, and it crosses zero at a
 * speech-like rate. Bass-heavy music and drums push most energy below 220 Hz, while hiss, fans
 * and other steady noise cross zero far faster than any voice does. A frame that carries no
 * signal is reported as usable so callers never block on silence.
 */
object SpeechSpectrum {

    fun isSpeechLike(pcm: ByteArray, sampleRateHz: Int = 16000): Boolean {
        val samples = pcm.size / 2
        if (samples < 64) return true

        val lowSpan = (sampleRateHz / 220).coerceIn(8, 96)
        val ring = DoubleArray(lowSpan)
        var position = 0
        var windowSum = 0.0
        var filled = 0
        var total = 0.0
        var low = 0.0
        var crossings = 0
        var previous = 0.0

        for (i in 0 until samples) {
            val raw = (((pcm[i * 2 + 1].toInt() shl 8) or (pcm[i * 2].toInt() and 0xFF)).toShort()).toDouble() / 32768.0
            windowSum += raw - ring[position]
            ring[position] = raw
            position = (position + 1) % lowSpan
            if (filled < lowSpan) filled += 1
            val lowSample = windowSum / filled
            total += raw * raw
            low += lowSample * lowSample
            if ((raw >= 0.0) != (previous >= 0.0)) crossings += 1
            previous = raw
        }

        val count = samples.toDouble()
        val totalRms = sqrt(total / count)
        if (totalRms < 0.004) return true
        val lowRms = sqrt(low / count)
        val lowShare = lowRms / totalRms
        val zeroCrossRate = crossings / count

        // Bass-dominant audio (music, drums) and hiss are not a person speaking.
        return lowShare <= 0.62 && zeroCrossRate <= 0.30
    }
}
