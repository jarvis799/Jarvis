package com.jarvis.assistant.lock

import android.os.Handler
import android.os.Looper

/** A short lease over the existing foreground service's mic, not another background service. */
object SharedMicrophone {
    private val handler = Handler(Looper.getMainLooper())
    private var restore: (() -> Unit)? = null
    private var generation = 0
    fun pause(active: Boolean, standby: Boolean, stop: () -> Unit, restart: (String) -> Unit, ready: () -> Unit) {
        handler.post {
            if (restore == null) {
                val mode = if (active) "active" else if (standby) "standby" else "idle"
                restore = { restart(mode) }; stop()
            }
            val token = ++generation
            handler.postDelayed({ if (token == generation && restore != null) ready() }, 350)
        }
    }
    fun resume() {
        handler.post {
            val callback = restore ?: return@post
            restore = null; generation++; callback()
        }
    }
}