package com.jarvis.assistant.background

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID

/**
 * Phrase-level streaming speech output for the HTTPS voice path.
 *
 * Honest limitation: Android's [TextToSpeech] synthesises whole strings, so this is not
 * audio-level streaming. What it does provide is *playback* streaming — the first sentence
 * starts speaking while the LLM is still generating the rest, which is where most of the
 * perceived latency lives.
 *
 * Ordering and safety:
 *  - phrases are spoken strictly in the order they were enqueued;
 *  - every phrase carries the turn token it belongs to; a phrase whose token is no longer
 *    current is dropped instead of spoken (an interrupted answer never resumes);
 *  - the queue is bounded, so a fast LLM cannot buffer unbounded audio work;
 *  - [finish] resolves only after the last phrase has actually been spoken, which gives
 *    the session a real "speech ended" edge to return to listening.
 */
class StreamingSpeechOutput(
    context: Context,
    private val scope: CoroutineScope,
    private val onError: (String) -> Unit
) {

    private data class Phrase(
        val text: String,
        val locale: Locale?,
        val token: Long,
        val done: CompletableDeferred<Unit>? = null
    )

    private val queue = Channel<Phrase>(4)
    private val ready = CompletableDeferred<Boolean>()
    private var engine: TextToSpeech? = null

    @Volatile private var currentToken: Long = 0L
    @Volatile private var paused = false
    @Volatile private var closed = false
    @Volatile private var speakingNow = false

    private var pendingDone: CompletableDeferred<Unit>? = null
    private var pendingId: String = ""
    private var resumeOffset = 0
    private var pieceStart = 0

    val isPaused: Boolean get() = paused
    val isActive: Boolean get() = speakingNow

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            if (!closed) ready.complete(status == TextToSpeech.SUCCESS)
        }
        engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                if (utteranceId != pendingId) return
                pendingDone?.complete(Unit)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId != pendingId) return
                pendingDone?.complete(Unit)
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                if (utteranceId != pendingId) return
                pendingDone?.complete(Unit)
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                if (utteranceId != pendingId) return
                resumeOffset = pieceStart + end
            }
        })
        scope.launch { worker() }
    }

    /** Starts a new turn; phrases from older turns become unplayable. */
    fun begin(token: Long) {
        currentToken = token
        paused = false
        resumeOffset = 0
    }

    /** Queues one speakable phrase. Suspends while the bounded queue is full. */
    suspend fun enqueue(text: String, locale: Locale?, token: Long) {
        if (closed || text.isBlank() || token != currentToken) return
        queue.send(Phrase(text, locale, token))
    }

    /** Waits until everything queued for [token] has been spoken. */
    suspend fun finish(token: Long) {
        if (closed || token != currentToken) return
        val done = CompletableDeferred<Unit>()
        queue.send(Phrase("", null, token, done))
        done.await()
    }

    fun interrupt() {
        currentToken = -1L
        paused = false
        drain()
        pendingDone?.complete(Unit)
        pendingDone = null
        try { engine?.stop() } catch (_: Exception) {}
    }

    fun pause() {
        if (closed) return
        paused = true
        pendingDone?.complete(Unit)
        try { engine?.stop() } catch (_: Exception) {}
    }

    fun resume() {
        paused = false
    }

    fun close() {
        closed = true
        interrupt()
        queue.close()
        ready.complete(false)
        try { engine?.shutdown() } catch (_: Exception) {}
        engine = null
    }

    private fun drain() {
        while (true) {
            val remaining = queue.tryReceive().getOrNull() ?: break
            remaining.done?.complete(Unit)
        }
    }

    private suspend fun worker() {
        while (!closed) {
            val phrase = queue.receiveCatching().getOrNull() ?: return
            if (phrase.done != null) {
                phrase.done.complete(Unit)
                continue
            }
            if (phrase.token != currentToken) continue
            speakPhrase(phrase)
        }
    }

    private suspend fun speakPhrase(phrase: Phrase) {
        if (withTimeoutOrNull(8000) { ready.await() } != true) {
            onError("Android TTS is unavailable; read the reply in chat.")
            return
        }
        val result = phrase.locale?.let { engine?.setLanguage(it) }
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            onError("Install a TTS voice for " + (phrase.locale?.displayLanguage ?: "this language"))
            return
        }

        speakingNow = true
        var offset = 0
        val text = phrase.text
        while (offset < text.length) {
            if (closed || phrase.token != currentToken) break
            if (paused) { delay(60); continue }

            val end = (offset + TextToSpeech.getMaxSpeechInputLength() - 1).coerceAtMost(text.length)
            var safeEnd = end
            if (safeEnd < text.length && Character.isHighSurrogate(text[safeEnd - 1])) safeEnd--

            pieceStart = offset
            resumeOffset = offset
            pendingId = UUID.randomUUID().toString()
            val done = CompletableDeferred<Unit>()
            pendingDone = done

            if (engine?.speak(text.substring(offset, safeEnd), TextToSpeech.QUEUE_FLUSH, null, pendingId) == TextToSpeech.ERROR) {
                onError("Android could not speak; read the reply in chat.")
                pendingDone = null
                break
            }

            done.await()
            pendingDone = null

            offset = if (paused) resumeOffset.coerceIn(offset, safeEnd) else safeEnd
        }
        speakingNow = false
    }
}