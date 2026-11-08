package com.jarvis.assistant.voice.hotword

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/** One PCM owner. Worker failures are reported; the worker alone closes its native decoder. */
class WakeAudioCapture(
    context: Context,
    private val recognizer: Recognizer,
    private val listener: RecognitionListener,
    private val preferMic: Boolean = false,
    private val onInput: (String) -> Unit = {},
    private val onAudio: (Float) -> Unit
) {
    private val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val main = Handler(Looper.getMainLooper())
    private val closed = AtomicBoolean(false)
    private var recorder: AudioRecord? = null
    private var worker: Thread? = null

    @SuppressLint("MissingPermission")
    fun start() {
        check(!manager.isMicrophoneMute) { "Android microphone is muted. Enable Microphone access and unmute it." }
        val minimum = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bytes = WakePcmMonitor.bufferBytes(minimum)
        val sources = if (preferMic) listOf(MediaRecorder.AudioSource.MIC, MediaRecorder.AudioSource.VOICE_RECOGNITION)
            else listOf(MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC)
        var lastFailure: Exception? = null
        for (source in sources) {
            var candidate: AudioRecord? = null
            try {
                candidate = AudioRecord(source, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bytes)
                check(candidate.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord initialization failed (source=$source, buffer=$bytes)" }
                candidate.startRecording()
                check(candidate.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Android refused microphone recording (source=$source)" }
                recorder = candidate
                break
            } catch (e: Exception) {
                lastFailure = e
                runCatching { candidate?.stop() }
                runCatching { candidate?.release() }
            }
        }
        val input = recorder ?: throw IOException("Microphone could not start: " + lastFailure?.message, lastFailure)
        worker = Thread({ readAudio(input) }, "jarvis-wake-pcm").apply { start() }
    }

    private fun deliver(action: () -> Unit) { main.post { if (!closed.get()) action() } }

    private fun readAudio(input: AudioRecord) {
        val monitor = WakePcmMonitor()
        val samples = ShortArray(3200)
        var inputReported = false
        try {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
            while (!closed.get()) {
                val count = input.read(samples, 0, samples.size, AudioRecord.READ_BLOCKING)
                if (closed.get()) break
                check(count > 0) { "Android microphone read failed (code=$count)" }
                val level = monitor.amplitude(samples, count)
                if (!inputReported) {
                    inputReported = true
                    val device = input.routedDevice?.productName?.toString()?.take(40) ?: "Android microphone"
                    val source = if (input.audioSource == MediaRecorder.AudioSource.MIC) "MIC" else "VOICE"
                    deliver { onInput("$device / $source") }
                }
                deliver { onAudio(level) }
                val complete = recognizer.acceptWaveForm(samples, count)
                val hypothesis = if (complete) recognizer.result else recognizer.partialResult
                deliver { if (complete) listener.onResult(hypothesis) else listener.onPartialResult(hypothesis) }
            }
        } catch (e: Exception) {
            if (!closed.get()) deliver { listener.onError(IOException("Wake audio: " + e.message, e)) }
        } finally {
            runCatching { input.stop() }
            runCatching { input.release() }
            runCatching { recognizer.close() }
        }
    }

    fun close() {
        if (closed.getAndSet(true)) return
        // stop() unblocks a blocking read; unlike SpeechService.cancel(), joining is bounded.
        runCatching { recorder?.stop() }
        val thread = worker
        thread?.interrupt()
        if (thread != null && thread !== Thread.currentThread()) {
            try { thread.join(500L) } catch (e: InterruptedException) { Thread.currentThread().interrupt() }
        } else if (thread == null) {
            runCatching { recorder?.release() }
            runCatching { recognizer.close() }
        }
    }
}
