package com.jarvis.assistant.lock

import android.app.Activity
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import com.jarvis.assistant.background.JarvisConversationService

/** App-private reference audio only; never used as a biometric credential. */
class VoiceSampleRecorder(private val activity: Activity) {
    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var cancelPending: (() -> Unit)? = null
    fun record(phrase: String, status: (String) -> Unit) {
        close()
        val target = VoiceUnlockStore(activity).sample(phrase)
        val temporary = java.io.File(target.parentFile, "capture.m4a")
        JarvisConversationService.pauseForLocalCapture {
            if (activity.isFinishing || activity.isDestroyed) { close(); return@pauseForLocalCapture }
            try {
                @Suppress("DEPRECATION")
                val audio = MediaRecorder()
                recorder = audio
                audio.setAudioSource(MediaRecorder.AudioSource.MIC); audio.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                audio.setAudioEncoder(MediaRecorder.AudioEncoder.AAC); audio.setOutputFile(temporary.absolutePath)
                audio.prepare(); audio.start(); status("Recording for 5 seconds… Say: $phrase")
                cancelPending = { temporary.delete() }
                handler.postDelayed({
                    try {
                        audio.stop(); audio.release(); recorder = null
                        check(temporary.length() > 0 && temporary.renameTo(target)) { "Could not save sample." }
                        cancelPending = null; status("Voice sample saved privately. This is not biometric verification.")
                    } catch (error: Exception) { status(error.message ?: "Recording failed.") }
                    finally { close() }
                }, 5000)
            } catch (error: Exception) { status(error.message ?: "Cannot record audio."); close() }
        }
    }
    fun play(phrase: String, status: (String) -> Unit) {
        close(); val file = VoiceUnlockStore(activity).sample(phrase)
        if (!file.isFile) { status("Record a voice sample first."); return }
        try { player = MediaPlayer().apply { setDataSource(file.absolutePath); prepare(); setOnCompletionListener { close() }; start() } }
        catch (error: Exception) { status(error.message ?: "Cannot play sample."); close() }
    }
    fun close() {
        handler.removeCallbacksAndMessages(null); runCatching { recorder?.stop() }; recorder?.release(); recorder = null
        player?.release(); player = null; cancelPending?.invoke(); cancelPending = null
        JarvisConversationService.resumeAfterLocalCapture()
    }
}