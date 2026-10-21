package com.jarvis.assistant.media

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.util.concurrent.CopyOnWriteArrayList

/**
 * In-app music playback. Songs keep playing while the user works elsewhere in
 * Jarvis because a small foreground service holds the playback.
 */
object JarvisMusicPlayer {

    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val queue = ArrayList<MusicTrack>()

    private var player: MediaPlayer? = null
    private var ticker: Runnable? = null
    private var index = -1

    var isPlaying: Boolean = false
        private set
    var positionMs: Long = 0L
        private set
    var durationMs: Long = 0L
        private set

    val current: MusicTrack? get() = if (index in queue.indices) queue[index] else null

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun publish() {
        main.post { for (listener in listeners) listener.invoke() }
    }

    fun play(context: Context, tracks: List<MusicTrack>, startIndex: Int) {
        if (tracks.isEmpty()) return
        queue.clear()
        queue.addAll(tracks)
        index = startIndex.coerceIn(0, tracks.size - 1)
        startCurrent(context)
    }

    fun toggle(context: Context) {
        val active = player
        if (active == null) {
            if (queue.isNotEmpty()) startCurrent(context)
            return
        }
        if (isPlaying) {
            active.pause()
            isPlaying = false
            stopTicker()
        } else {
            active.start()
            isPlaying = true
            startTicker()
        }
        notifyService(context)
        publish()
    }

    fun next(context: Context) {
        if (queue.isEmpty()) return
        index = if (index + 1 >= queue.size) 0 else index + 1
        startCurrent(context)
    }

    fun previous(context: Context) {
        if (queue.isEmpty()) return
        index = if (index <= 0) queue.size - 1 else index - 1
        startCurrent(context)
    }

    fun seekTo(ms: Int) {
        val active = player ?: return
        active.seekTo(ms)
        positionMs = ms.toLong()
        publish()
    }

    fun stopAll(context: Context) {
        stopTicker()
        try {
            player?.release()
        } catch (_: Exception) {
        }
        player = null
        queue.clear()
        index = -1
        isPlaying = false
        positionMs = 0L
        durationMs = 0L
        publish()
        try {
            context.applicationContext.stopService(Intent(context.applicationContext, MusicPlaybackService::class.java))
        } catch (_: Exception) {
        }
    }

    private fun startCurrent(context: Context) {
        val track = current ?: return
        stopTicker()
        try {
            player?.release()
        } catch (_: Exception) {
        }
        val active = MediaPlayer()
        player = active
        try {
            active.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            active.setDataSource(context.applicationContext, Uri.parse(track.uri))
            active.setOnPreparedListener { mp ->
                durationMs = mp.duration.toLong()
                positionMs = 0L
                mp.start()
                isPlaying = true
                startTicker()
                notifyService(context)
                publish()
            }
            active.setOnCompletionListener { next(context) }
            active.setOnErrorListener { _, _, _ ->
                isPlaying = false
                publish()
                true
            }
            active.prepareAsync()
        } catch (_: Exception) {
            isPlaying = false
            publish()
        }
    }

    private fun startTicker() {
        stopTicker()
        val runner = object : Runnable {
            override fun run() {
                val active = player
                if (active != null && isPlaying) {
                    positionMs = active.currentPosition.toLong()
                    publish()
                    main.postDelayed(this, 500L)
                }
            }
        }
        ticker = runner
        main.postDelayed(runner, 500L)
    }

    private fun stopTicker() {
        ticker?.let { main.removeCallbacks(it) }
        ticker = null
    }

    private fun notifyService(context: Context) {
        try {
            ContextCompat.startForegroundService(
                context.applicationContext,
                Intent(context.applicationContext, MusicPlaybackService::class.java)
            )
        } catch (_: Exception) {
        }
    }
}
