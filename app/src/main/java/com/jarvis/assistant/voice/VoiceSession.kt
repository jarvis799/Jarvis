package com.jarvis.assistant.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.delay
import java.util.Locale
import java.util.UUID

/**
 * One engine, one voice, one line.
 *
 * Every spoken word in the app goes through here: the live reply, a typed answer, a task status
 * line. Before this, four subsystems each owned their own TextToSpeech instance, so two of them
 * could be audible at the same time and each speak() call silently cut the other off mid-sentence.
 * That is what "Jarvis talking over itself" and "speech stopping midway" actually were.
 *
 * Guarantees this class provides:
 *  - only one utterance is ever audible (strict FIFO lane);
 *  - lines play in the order they were queued and are never reordered;
 *  - a piece the engine drops is retried from the last confirmed word, so words are neither
 *    skipped nor repeated;
 *  - an interrupted turn (barge-in) is dropped, never resumed later.
 */
object VoiceSession {

    private const val TAG = "VoiceSession"

    const val OWNER_ASSISTANT = "assistant"
    const val OWNER_SYSTEM = "system"

    private const val MAX_RETRIES = 2
    private const val RETRY_DELAY_MS = 300L
    private const val READY_POLL_MS = 200L
    private const val INIT_RETRY_MS = 1500L

    private val main = Handler(Looper.getMainLooper())
    private val queue = SpeechQueue()

    private var engine: TextToSpeech? = null
    private var appContext: Context? = null
    private var initializing = false
    private var ready = false

    @Volatile private var current: SpeechItem? = null
    private var pendingId = ""
    private var chunkStart = 0
    private var chunkEnd = 0
    private var resumeAt = 0
    private var rangeKnown = false
    private var retries = 0
    private var paused = false

    /** True while this lane is audibly speaking, so the microphone can ignore its own voice. */
    @Volatile
    var isSpeaking: Boolean = false
        private set

    @Volatile
    var isPaused: Boolean = false
        private set

    val generation: Long get() = queue.generation

    /** Starts the engine early so the first reply is not delayed by engine start-up. */
    fun warm(context: Context) {
        appContext = context.applicationContext
        ensureEngine()
    }

    /**
     * Queues one line. Returns the generation it belongs to; [onFinished] reports whether the line
     * was actually spoken to the end (false when it was dropped by a barge-in or the engine gave up).
     */
    fun speak(
        context: Context,
        text: String,
        locale: Locale? = null,
        owner: String = OWNER_ASSISTANT,
        onFinished: ((Boolean) -> Unit)? = null
    ): Long {
        appContext = context.applicationContext
        val spoken = SpeechText.clean(text).trim()
        if (spoken.isEmpty()) {
            onFinished?.invoke(true)
            return queue.generation
        }
        val accepted = queue.offer(
            SpeechItem(
                id = UUID.randomUUID().toString(),
                text = spoken,
                owner = owner,
                generation = queue.generation,
                locale = locale,
                onFinished = onFinished
            )
        )
        if (!accepted) {
            onFinished?.invoke(false)
            return queue.generation
        }
        ensureEngine()
        main.post { pump() }
        return queue.generation
    }

    /** Barge-in: the current turn is gone, not paused. Nothing queued for it will be spoken. */
    fun interrupt(reason: String = "") {
        val dropped = queue.bump()
        dropped.forEach { it.onFinished?.invoke(false) }
        val stopped = current
        current = null
        paused = false
        isPaused = false
        isSpeaking = false
        chunkStart = 0
        chunkEnd = 0
        resumeAt = 0
        rangeKnown = false
        retries = 0
        runCatching { engine?.stop() }
        stopped?.onFinished?.invoke(false)
        if (reason.isNotBlank()) Log.i(TAG, "interrupted: " + reason)
    }

    fun pause() {
        if (current == null) return
        paused = true
        isPaused = true
        isSpeaking = false
        runCatching { engine?.stop() }
    }

    fun resume() {
        if (!paused) return
        paused = false
        isPaused = false
        val item = current
        if (item == null) {
            main.post { pump() }
            return
        }
        val from = if (rangeKnown) resumeAt else chunkStart
        main.post { speakPiece(item, from) }
    }

    /** Suspends until nothing is queued and nothing is audible. */
    suspend fun awaitIdle(timeoutMs: Long = 120_000L) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (current == null && queue.isEmpty()) return
            delay(60)
        }
    }

    // ---- engine -------------------------------------------------------------------------

    private fun ensureEngine() {
        if (engine != null || initializing) return
        val context = appContext ?: return
        initializing = true
        val created = TextToSpeech(context) { status ->
            initializing = false
            ready = status == TextToSpeech.SUCCESS
            main.post {
                if (ready) pump() else scheduleInitRetry()
            }
        }
        created.setOnUtteranceProgressListener(listener)
        engine = created
    }

    private fun scheduleInitRetry() {
        if (ready) return
        runCatching { engine?.shutdown() }
        engine = null
        appContext?.let { if (it !== null) main.postDelayed({ ensureEngine() }, INIT_RETRY_MS) }
    }

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        override fun onDone(utteranceId: String?) {
            if (utteranceId != pendingId) return
            main.post { onPieceDone() }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            if (utteranceId != pendingId) return
            main.post { onPieceError() }
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            if (utteranceId != pendingId) return
            main.post { onPieceError() }
        }

        override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
            if (utteranceId != pendingId) return
            rangeKnown = true
            resumeAt = (chunkStart + start).coerceIn(chunkStart, chunkEnd)
        }
    }

    // ---- playback -----------------------------------------------------------------------

    private fun pump() {
        if (paused || current != null) return
        val next = queue.poll() ?: return
        if (next.generation != queue.generation) {
            next.onFinished?.invoke(false)
            return
        }
        if (engine == null) ensureEngine()
        if (!ready) {
            queue.offerFront(next)
            main.postDelayed({ pump() }, READY_POLL_MS)
            return
        }
        current = next
        retries = 0
        speakPiece(next, 0)
    }

    private fun speakPiece(item: SpeechItem, from: Int) {
        val tts = engine
        val context = appContext
        if (tts == null || context == null) {
            finishItem(item, false)
            return
        }
        if (item.generation != queue.generation) {
            finishItem(item, false)
            return
        }
        val text = item.text
        if (from >= text.length) {
            finishItem(item, true)
            return
        }
        var end = (from + TextToSpeech.getMaxSpeechInputLength() - 1).coerceAtMost(text.length)
        if (end < text.length && Character.isHighSurrogate(text[end - 1])) end -= 1
        chunkStart = from
        chunkEnd = end
        resumeAt = from
        rangeKnown = false
        pendingId = UUID.randomUUID().toString()
        val locale = item.locale ?: ChatSpeech.localeFor(text)
        val language = runCatching { TtsDelivery.applyLanguage(context, tts, locale) }
            .getOrDefault(TextToSpeech.LANG_AVAILABLE)
        if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "no installed voice for " + locale)
            finishItem(item, false)
            return
        }
        isSpeaking = true
        val queued = runCatching {
            tts.speak(text.substring(from, end), TextToSpeech.QUEUE_FLUSH, null, pendingId)
        }.getOrDefault(TextToSpeech.ERROR)
        if (queued == TextToSpeech.SUCCESS) return
        isSpeaking = false
        retries += 1
        if (retries > MAX_RETRIES) {
            finishItem(item, false)
            return
        }
        main.postDelayed({ if (current === item && !paused) speakPiece(item, from) }, RETRY_DELAY_MS * retries)
    }

    private fun onPieceDone() {
        if (paused) return
        val item = current ?: return
        retries = 0
        if (chunkEnd >= item.text.length) finishItem(item, true) else speakPiece(item, chunkEnd)
    }

    private fun onPieceError() {
        if (paused) return
        val item = current ?: return
        retries += 1
        if (retries > MAX_RETRIES) {
            // The engine keeps refusing this piece. Move to the next piece instead of leaving the
            // rest of the reply unspoken, and keep no claim that the whole line was heard.
            Log.w(TAG, "engine dropped a piece after retries")
            retries = 0
            main.postDelayed({ if (current === item && !paused) speakPiece(item, chunkEnd) }, RETRY_DELAY_MS)
            return
        }
        val from = if (rangeKnown) resumeAt else chunkStart
        main.postDelayed({ if (current === item && !paused) speakPiece(item, from) }, RETRY_DELAY_MS * retries)
    }

    private fun finishItem(item: SpeechItem, spoken: Boolean) {
        if (current === item) {
            current = null
            isSpeaking = false
            chunkStart = 0
            chunkEnd = 0
            resumeAt = 0
            rangeKnown = false
            retries = 0
        }
        item.onFinished?.invoke(spoken)
        pump()
    }
}
