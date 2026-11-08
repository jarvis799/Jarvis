package com.jarvis.assistant.voice.hotword

import org.junit.Assert.*
import org.junit.Test

class WakePcmMonitorTest {
    @Test fun bufferHonorsThePhoneMinimum() {
        assertEquals(16000, WakePcmMonitor.bufferBytes(6400))
        assertEquals(32768, WakePcmMonitor.bufferBytes(16384))
    }
    @Test(expected = IllegalArgumentException::class) fun unsupportedInputDoesNotOpenARecorder() {
        WakePcmMonitor.bufferBytes(-2)
    }
    @Test fun realSamplesProduceLevel() {
        assertEquals(0.5f, WakePcmMonitor().amplitude(shortArrayOf(16384, -16384), 2), 0.0001f)
    }
    @Test(expected = IllegalStateException::class) fun digitalMuteIsNotHealthyListening() {
        val monitor = WakePcmMonitor()
        repeat(50) { monitor.amplitude(ShortArray(3200), 3200) }
    }
    @Test fun genuineQuietSoundResetsDigitalMuteCounter() {
        val monitor = WakePcmMonitor()
        repeat(49) { monitor.amplitude(ShortArray(3200), 3200) }
        assertTrue(monitor.amplitude(shortArrayOf(1), 1) > 0f)
        repeat(49) { monitor.amplitude(ShortArray(3200), 3200) }
    }
    @Test(expected = IllegalArgumentException::class) fun emptyReadIsNotAHeartbeat() {
        WakePcmMonitor().amplitude(ShortArray(3200), 0)
    }
}
