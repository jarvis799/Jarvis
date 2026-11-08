package com.jarvis.assistant.background

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID

class HttpSpeechOutput(context: Context, private val onError: (String) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private var ready = CompletableDeferred<Boolean>()
    private var initAttempts = 0
    private var speakAttempts = 0
    private var completion: CompletableDeferred<Unit>? = null
    private var text = ""
    private var offset = 0
    private var chunkEnd = 0
    private var utteranceBase = 0
    private var utteranceId = ""
    private var interrupted = false
    private var closed = false
    private var retries = 0
    private val appContext = context.applicationContext
    var isPaused = false
        private set
    private var engine: TextToSpeech? = null
    init {
        startEngine()
    }

    /** (Re)creates the engine; a failed start is retried instead of muting the session. */
    private fun startEngine() {
        if (closed) return
        val tts = TextToSpeech(appContext) { status -> if (!closed) ready.complete(status == TextToSpeech.SUCCESS) }
        tts.setOnUtteranceProgressListener(listener)
        engine = tts
    }

    private val listener = object : UtteranceProgressListener() {
            override fun onStart(id: String?) = Unit
            override fun onDone(id: String?) { main.post { if (id == utteranceId && !isPaused) { retries = 0; completion?.complete(Unit) } } }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                main.post {
                    if (id == utteranceId && !isPaused) {
                        // A single engine hiccup used to end the whole reply and lose the rest of it.
                        // Retry the same chunk from the last confirmed position before giving up.
                        if (retries < MAX_RETRIES) {
                            retries += 1
                            val wait = 300L * retries
                            main.postDelayed({ playRemaining() }, wait)
                        } else {
                            failSpeech()
                        }
                    }
                }
            }
            override fun onError(id: String?, errorCode: Int) = onError(id)
            override fun onRangeStart(id: String?, start: Int, end: Int, frame: Int) {
                main.post { if (id == utteranceId && !isPaused) offset = (utteranceBase + start).coerceAtMost(chunkEnd) }
            }
    }
    suspend fun say(value: String, locale: Locale) {
        val spoken = com.jarvis.assistant.voice.SpeechText.clean(value)
        if (closed || spoken.isBlank()) return
        if (!ensureReady()) { onError("Android TTS is unavailable; read the reply in chat."); return }
        val result = engine?.let { com.jarvis.assistant.voice.TtsDelivery.applyLanguage(appContext, it, locale) }
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) { onError("Install a TTS voice for " + locale.displayLanguage); return }
        text = spoken
        offset = 0
        interrupted = false
        isPaused = false
        while (offset < text.length && !interrupted && !closed) {
            chunkEnd = (offset + TextToSpeech.getMaxSpeechInputLength() - 1).coerceAtMost(text.length)
            if (chunkEnd < text.length && Character.isHighSurrogate(text[chunkEnd - 1])) chunkEnd--
            val done = CompletableDeferred<Unit>()
            completion = done
            playRemaining()
            done.await()
            if (!interrupted) offset = chunkEnd
        }
        completion = null
    }
    private fun playRemaining() {
        if (offset >= chunkEnd) { completion?.complete(Unit); return }
        val piece = text.substring(offset, chunkEnd)
        utteranceId = UUID.randomUUID().toString()
        utteranceBase = offset
        // A missing engine or a rejected piece used to leave the reply waiting forever, or end
        // it on the first hiccup. Recreate a dead engine and speak the same words again.
        val spoke = engine?.speak(piece, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (spoke == TextToSpeech.SUCCESS) { speakAttempts = 0; return }
        speakAttempts += 1
        if (speakAttempts > MAX_SPEAK_ATTEMPTS || closed || interrupted) { failSpeech(); return }
        if (engine == null) { ready = CompletableDeferred(); startEngine() }
        main.postDelayed({ if (!closed && !interrupted && !isPaused) playRemaining() }, 300L * speakAttempts)
    }
    private fun failSpeech() { interrupted = true; completion?.complete(Unit); onError("Android could not speak; read the reply in chat.") }
    fun pause() { if (completion == null || closed) return; isPaused = true; utteranceId = ""; engine?.stop() }
    fun resume() { if (!isPaused || closed) return; isPaused = false; playRemaining() }
    fun interrupt() { interrupted = true; isPaused = false; utteranceId = ""; engine?.stop(); completion?.complete(Unit) }
    fun close() { closed = true; interrupt(); ready.complete(false); engine?.shutdown(); engine = null }

    /** Waits for the engine, re-creating it once when the first start failed. */
    private suspend fun ensureReady(): Boolean {
        if (withTimeoutOrNull(READY_TIMEOUT_MS) { ready.await() } == true) return true
        while (initAttempts < MAX_INIT_ATTEMPTS) {
            initAttempts += 1
            try { engine?.shutdown() } catch (_: Exception) {}
            engine = null
            ready = CompletableDeferred()
            startEngine()
            if (withTimeoutOrNull(READY_TIMEOUT_MS) { ready.await() } == true) return true
        }
        return false
    }

    private companion object {
        const val MAX_RETRIES = 2
        const val MAX_SPEAK_ATTEMPTS = 2
        const val MAX_INIT_ATTEMPTS = 1
        const val READY_TIMEOUT_MS = 8000L
    }
}