package com.jarvis.assistant.background

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import java.util.Locale

/**
 * Phrase-level playback streaming for the HTTPS voice path.
 *
 * The phrase splitting still lives upstream with [com.jarvis.assistant.voice.SpeechChunker]; this
 * class now feeds every phrase into [com.jarvis.assistant.voice.VoiceSession] instead of owning its
 * own engine. That is what stops two replies being audible at once: there is exactly one queue and
 * one engine in the whole app, and it plays strictly in the order phrases arrive.
 *
 * Turn tokens are still enforced, so a phrase from an interrupted turn is dropped rather than
 * spoken later.
 */
class StreamingSpeechOutput(
    context: Context,
    @Suppress("UNUSED_PARAMETER") scope: CoroutineScope,
    private val onError: (String) -> Unit
) {

    private val appContext = context.applicationContext

    @Volatile
    private var turnToken: Long = 0L

    val isPaused: Boolean get() = com.jarvis.assistant.voice.VoiceSession.isPaused

    val isActive: Boolean get() = com.jarvis.assistant.voice.VoiceSession.isSpeaking

    /** Starts a new turn; phrases carrying an older token become unplayable. */
    fun begin(token: Long) {
        turnToken = token
    }

    /** Queues one phrase for the shared lane. Order is preserved by that lane. */
    suspend fun enqueue(text: String, locale: Locale?, token: Long) {
        if (token != turnToken) return
        com.jarvis.assistant.voice.VoiceSession.speak(
            context = appContext,
            text = text,
            locale = locale,
            owner = com.jarvis.assistant.voice.VoiceSession.OWNER_ASSISTANT
        )
    }

    /** Returns when everything queued for [token] has actually been spoken. */
    suspend fun finish(token: Long) {
        if (token != turnToken) return
        com.jarvis.assistant.voice.VoiceSession.awaitIdle()
    }

    fun interrupt() {
        turnToken = 0L
        com.jarvis.assistant.voice.VoiceSession.interrupt("stream-turn")
    }

    fun pause() {
        com.jarvis.assistant.voice.VoiceSession.pause()
    }

    fun resume() {
        com.jarvis.assistant.voice.VoiceSession.resume()
    }

    fun close() {
        interrupt()
    }

    private fun report(message: String) {
        onError(message)
    }
}
