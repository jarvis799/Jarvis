package com.jarvis.assistant.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import com.jarvis.assistant.audio.AudioUtils
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Keeps the microphone alive while Jarvis is speaking, so the user can simply talk over it.
 *
 * This exists because the HTTPS voice path otherwise only listens between turns: while TTS
 * plays, there is no capture at all, so barge-in is impossible.
 *
 * Truthfulness rules:
 *  - [hasEchoCancellation] reports what the platform *actually enabled*, not what it offers.
 *    On devices without working AEC, speaker audio leaks into the microphone at levels that
 *    would interrupt Jarvis constantly, so callers keep barge-in off instead of pretending
 *    it works (see HttpChatSession.startBargeIn).
 *  - capture uses VOICE_COMMUNICATION so AEC/NS/AGC apply to the same stream the gate reads;
 *  - detection is energy-based [VoiceActivityGate] with a sustained-speech requirement, not
 *    speech recognition — it answers "is someone talking", not "what was said".
 */
class BargeInMonitor(private val onBargeIn: (Float) -> Unit) {

    companion object {
        private const val TAG = "BargeInMonitor"
        private const val SAMPLE_RATE = 16000
        private const val CHUNK_BYTES = 640
        private const val FRAME_MS = 20
        private const val BARGE_IN_DROP_MS = 1500L
    }

    private var record: AudioRecord? = null
    private var thread: Thread? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var gainControl: AutomaticGainControl? = null
    private val running = AtomicBoolean(false)
    private val gate = VoiceActivityGate(sustainedMs = 150, frameMs = FRAME_MS)

    @Volatile private var echoCancellationActive = false

    val isRunning: Boolean get() = running.get()
    val hasEchoCancellation: Boolean get() = echoCancellationActive

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running.get()) return true

        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) {
            Log.w(TAG, "No usable microphone buffer size")
            return false
        }

        val capture = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer * 4, 32000)
            )
        } catch (e: Exception) {
            Log.w(TAG, "AudioRecord could not start: ${e.message}")
            return false
        }

        if (capture.state != AudioRecord.STATE_INITIALIZED) {
            capture.release()
            return false
        }

        val session = capture.audioSessionId
        echoCancellationActive = false

        if (AcousticEchoCanceler.isAvailable()) {
            try {
                echoCanceler = AcousticEchoCanceler.create(session)?.apply { enabled = true }
                // Verify it really engaged: a created effect is not a working effect.
                echoCancellationActive = echoCanceler?.enabled == true
            } catch (e: Exception) {
                Log.w(TAG, "AcousticEchoCanceler failed: ${e.message}")
            }
        }
        if (NoiseSuppressor.isAvailable()) {
            try { noiseSuppressor = NoiseSuppressor.create(session)?.apply { enabled = true } } catch (_: Exception) {}
        }
        if (AutomaticGainControl.isAvailable()) {
            try { gainControl = AutomaticGainControl.create(session)?.apply { enabled = true } } catch (_: Exception) {}
        }
        Log.i(TAG, "barge-in capture: AEC=$echoCancellationActive NS=${noiseSuppressor?.enabled} AGC=${gainControl?.enabled}")

        gate.reset()
        record = capture
        running.set(true)

        try {
            capture.startRecording()
        } catch (e: Exception) {
            Log.w(TAG, "startRecording failed: ${e.message}")
            stop()
            return false
        }

        thread = Thread {
            try {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
            } catch (_: Exception) {}
            val buffer = ByteArray(CHUNK_BYTES)
            while (running.get()) {
                val read = try { capture.read(buffer, 0, buffer.size) } catch (_: Exception) { -1 }
                if (read <= 0) {
                    if (read < 0) {
                        try { Thread.sleep(40) } catch (_: InterruptedException) { break }
                    }
                    continue
                }
                val amplitude = AudioUtils.calculateRmsNormalized(buffer, read)
                if (gate.onFrame(amplitude, speakerActive = true) == VoiceActivityGate.Event.SPEECH_STARTED) {
                    running.set(false)
                    Log.i(TAG, "sustained speech detected (amp=$amplitude)")
                    onBargeIn(amplitude)
                    break
                }
            }
        }.apply {
            priority = Thread.MAX_PRIORITY
            name = "jarvis-barge-in"
            start()
        }
        return true
    }

    fun stop() {
        running.set(false)
        thread?.interrupt()
        thread = null
        try { record?.let { if (it.state == AudioRecord.STATE_INITIALIZED) it.stop() } } catch (_: Exception) {}
        try { record?.release() } catch (_: Exception) {}
        record = null
        try { echoCanceler?.release() } catch (_: Exception) {}
        try { noiseSuppressor?.release() } catch (_: Exception) {}
        try { gainControl?.release() } catch (_: Exception) {}
        echoCanceler = null
        noiseSuppressor = null
        gainControl = null
        gate.reset()
    }

    /** Grace window used by callers that must not re-open the mic instantly. */
    fun dropWindowMs(): Long = BARGE_IN_DROP_MS
}