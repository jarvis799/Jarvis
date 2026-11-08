package com.jarvis.assistant.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class AudioRecorder(
    private val onAudioChunk: (chunk: ByteArray, amplitude: Float, frequencyData: AudioFrequencyData) -> Unit
) {
    companion object {
        private const val TAG = "AudioRecorder"
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        // Ultra-low latency 20ms chunk: 16000 samples/sec * 0.02s * 2 bytes/sample = 640 bytes
        const val CHUNK_SIZE_BYTES = 640
    }

    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var automaticGainControl: AutomaticGainControl? = null
    private var recordingJob: Job? = null
    private val isRecording = AtomicBoolean(false)
    val isMuted = AtomicBoolean(false)

    @SuppressLint("MissingPermission")
    fun start(scope: CoroutineScope): Boolean {
        if (isRecording.get()) return true

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "Unable to get valid min buffer size")
            return false
        }

        val bufferSize = maxOf(minBufferSize * 4, 32000)

        // The wake-word engine hands the microphone over right before this runs, and some
        // Android builds keep the input claimed for a few dozen milliseconds afterwards.
        // Without this short retry the session opened deaf and Android answered with
        // "microphone not available / another app is using the microphone".
        var record: AudioRecord? = null
        for (attempt in 1..4) {
            if (attempt > 1) Thread.sleep(120L)
            val candidate = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
            if (candidate.state == AudioRecord.STATE_INITIALIZED) {
                record = candidate
                break
            }
            Log.w(TAG, "Microphone not available yet (attempt " + attempt + "/4)")
            candidate.release()
        }
        val opened = record
        if (opened == null) {
            Log.e(TAG, "Microphone could not be opened: another app is holding it")
            return false
        }

        return try {

            val sessionId = opened.audioSessionId
            // AcousticEchoCanceler is intentionally disabled. With no active phone-call
            // reference signal, hardware AEC cancels the user's own speech — the primary cause
            // of Gemini receiving near-silent audio and replying "I didn't catch that".
            // Echo is already handled by AEDP (150ms discard after playback) + speaker gate.
            echoCanceler = null

            if (NoiseSuppressor.isAvailable()) {
                try {
                    noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply {
                        enabled = true
                    }
                    Log.i(TAG, "Hardware NoiseSuppressor enabled on session $sessionId")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to enable NoiseSuppressor: ${e.message}")
                }
            }

            // DEV STYLE v3.0.1: AutomaticGainControl is intentionally DISABLED.
            // AGC normalises mic gain dynamically, which flattens the level cues the recognition
            // model relies on and causes misrecognition. The VOICE_RECOGNITION AudioSource
            // already applies the correct gain path, so AGC only hurts accuracy here.
            automaticGainControl = null

            opened.startRecording()
            audioRecord = opened
            isRecording.set(true)

            recordingJob = scope.launch(Dispatchers.IO) {
                try {
                    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
                } catch (_: Exception) {}
                val buffer = ByteArray(CHUNK_SIZE_BYTES)
                var currentRecord: AudioRecord? = opened
                while (isActive && isRecording.get()) {
                    var consecutiveErrors = 0
                    while (isActive && isRecording.get() && currentRecord != null) {
                        try {
                            val readBytes = currentRecord.read(buffer, 0, buffer.size)
                            if (readBytes > 0) {
                                consecutiveErrors = 0
                                val freq = AudioUtils.analyzeFrequency(buffer, readBytes, SAMPLE_RATE)
                                if (!isMuted.get()) {
                                    val chunkCopy = buffer.copyOf(readBytes)
                                    onAudioChunk(chunkCopy, freq.amplitude, freq)
                                } else {
                                    onAudioChunk(ByteArray(0), 0f, AudioFrequencyData())
                                }
                            } else if (readBytes < 0) {
                                consecutiveErrors++
                                Log.w(TAG, "AudioRecord read error code: $readBytes (error count: $consecutiveErrors)")
                                delay(40)
                                if (consecutiveErrors > 10 && isRecording.get()) {
                                    Log.e(TAG, "Consecutive AudioRecord read errors exceeded limit. Attempting auto-recovery...")
                                    break
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Exception during AudioRecord read loop: ${e.message}", e)
                            delay(50)
                        }
                    }

                    // Auto-recovery if broken but still meant to be recording:
                    if (isActive && isRecording.get()) {
                        delay(400)
                        try {
                            currentRecord?.release()
                        } catch (_: Exception) {}
                        try {
                            val newRecord = AudioRecord(
                                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                                SAMPLE_RATE,
                                CHANNEL_CONFIG,
                                AUDIO_FORMAT,
                                bufferSize
                            )
                            if (newRecord.state == AudioRecord.STATE_INITIALIZED) {
                                newRecord.startRecording()
                                currentRecord = newRecord
                                audioRecord = newRecord
                                // Recreate audio effects on the NEW session id. The old NoiseSuppressor /
                                // AutomaticGainControl were bound to the previous (released) session and do
                                // not follow the new AudioRecord. Without this, a recovered recorder runs
                                // raw, letting background noise spike VAD and produce empty utterances.
                                // (AcousticEchoCanceler stays intentionally disabled - see start().)
                                val newSession = newRecord.audioSessionId
                                try { noiseSuppressor?.release() } catch (_: Exception) {}
                                try { automaticGainControl?.release() } catch (_: Exception) {}
                                noiseSuppressor = null
                                automaticGainControl = null
                                if (NoiseSuppressor.isAvailable()) {
                                    try {
                                        noiseSuppressor = NoiseSuppressor.create(newSession)?.apply { enabled = true }
                                        Log.i(TAG, "Re-enabled NoiseSuppressor on recovered session ${newSession}")
                                    } catch (e: Exception) { Log.w(TAG, "NoiseSuppressor recreate failed: ${e.message}") }
                                }
                                // AGC stays disabled for recognition accuracy (see start()).
                                Log.i(TAG, "AudioRecord successfully auto-recovered and restarted on session ${newSession}")
                            } else {
                                newRecord.release()
                                delay(1000)
                            }
                        } catch (reInitEx: Exception) {
                            Log.e(TAG, "AudioRecord auto-recovery failed: ${reInitEx.message}")
                            delay(1000)
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting AudioRecord: ${e.message}", e)
            false
        }
    }

    fun stop() {
        isRecording.set(false)
        recordingJob?.cancel()
        recordingJob = null

        try {
            echoCanceler?.release()
            noiseSuppressor?.release()
            automaticGainControl?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing audio effects: ${e.message}")
        } finally {
            echoCanceler = null
            noiseSuppressor = null
            automaticGainControl = null
        }

        try {
            audioRecord?.let {
                if (it.state == AudioRecord.STATE_INITIALIZED) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord: ${e.message}")
        } finally {
            audioRecord = null
        }
    }

    fun setMuted(muted: Boolean) {
        isMuted.set(muted)
    }
}
