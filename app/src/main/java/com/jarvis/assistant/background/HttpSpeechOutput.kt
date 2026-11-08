package com.jarvis.assistant.background

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import java.util.Locale

/**
 * Speaks one complete reply on the HTTPS path.
 *
 * The engine handling moved to [com.jarvis.assistant.voice.VoiceSession]: this class no longer owns
 * a TextToSpeech instance, so it can neither talk over the live voice nor be cut off by it. Only
 * pause/resume/interrupt remain here, and they apply to the shared lane on purpose - a barge-in
 * must silence every line, not just this one.
 */
class HttpSpeechOutput(context: Context, private val onError: (String) -> Unit) {

    private val appContext = context.applicationContext
    private var completion: CompletableDeferred<Unit>? = null

    var isPaused: Boolean = false
        private set

    suspend fun say(value: String, locale: Locale) {
        val finished = CompletableDeferred<Unit>()
        completion = finished
        var spoken = true
        VoiceSessionBridge.speak(appContext, value, locale) { success ->
            spoken = success
            finished.complete(Unit)
        }
        finished.await()
        completion = null
        if (!spoken && !isPaused) {
            onError("Android could not speak the whole reply; read it in chat.")
        }
    }

    fun pause() {
        isPaused = true
        com.jarvis.assistant.voice.VoiceSession.pause()
    }

    fun resume() {
        isPaused = false
        com.jarvis.assistant.voice.VoiceSession.resume()
    }

    fun interrupt() {
        completion?.complete(Unit)
        completion = null
        com.jarvis.assistant.voice.VoiceSession.interrupt("http-output")
    }

    fun close() {
        interrupt()
    }
}

/** Thin bridge so this file reads cleanly while the shared lane stays a single object. */
private object VoiceSessionBridge {
    fun speak(context: Context, value: String, locale: Locale, onFinished: (Boolean) -> Unit) {
        com.jarvis.assistant.voice.VoiceSession.speak(
            context = context,
            text = value,
            locale = locale,
            owner = com.jarvis.assistant.voice.VoiceSession.OWNER_ASSISTANT,
            onFinished = onFinished
        )
    }
}
