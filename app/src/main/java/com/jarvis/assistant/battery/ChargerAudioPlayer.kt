package com.jarvis.assistant.battery

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import java.io.File

object ChargerAudioPlayer {
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    @Volatile var isPlaying = false
        private set
    fun play(context: Context, file: File) {
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        stop(manager)
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes).setOnAudioFocusChangeListener { change ->
                if (change < 0) stop(manager)
            }.build()
        if (manager.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        focus = request
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(attributes); setDataSource(file.path)
                setOnCompletionListener { stop(manager) }
                setOnErrorListener { _, _, _ -> stop(manager); true }
                setOnPreparedListener { isPlaying = true; it.start() }
                prepareAsync()
            }
        } catch (_: Exception) { stop(manager) }
    }
    private fun stop(manager: AudioManager) {
        player?.release(); player = null; isPlaying = false
        focus?.let { manager.abandonAudioFocusRequest(it) }; focus = null
    }
}