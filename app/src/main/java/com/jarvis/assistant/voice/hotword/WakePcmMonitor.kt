package com.jarvis.assistant.voice.hotword

import kotlin.math.sqrt

/** Examines actual PCM, not decoder callbacks. Digital zero is distinct from quiet sound. */
class WakePcmMonitor(private val sampleRate: Int = 16000) {
    private var zeroSamples = 0L
    fun amplitude(samples: ShortArray, count: Int): Float {
        require(count in 1..samples.size) { "Microphone returned no readable PCM samples" }
        var sum = 0.0
        for (i in 0 until count) { val value = samples[i].toDouble(); sum += value * value }
        zeroSamples = if (sum == 0.0) zeroSamples + count else 0L
        check(zeroSamples < sampleRate * 10L) {
            "No microphone signal: Android supplied only digital silence for 10 seconds. Check Android Microphone access, mute, and other recording apps."
        }
        return (sqrt(sum / count) / 32768.0).toFloat().coerceIn(0f, 1f)
    }
    companion object {
        fun bufferBytes(minimum: Int): Int {
            require(minimum > 0) { "This phone rejected 16 kHz microphone capture (buffer=$minimum)" }
            return maxOf(minimum * 2, 16000)
        }
    }
}
