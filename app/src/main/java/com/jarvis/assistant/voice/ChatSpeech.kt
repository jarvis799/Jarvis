package com.jarvis.assistant.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

/**
 * Speaks a short reply with the phone's own TTS engine.
 *
 * Used wherever the live audio stream cannot answer: a typed command handled on the device,
 * a task summary, or the REST fallback when the live socket stayed silent. The language is
 * picked from the text itself so Bangla, Hindi and English replies are read in their own
 * voice instead of an English engine guessing.
 *
 * Two failure modes are handled here because both used to end in silence:
 *  - the engine reports ready before [engine] is assigned (the reply is spoken from one
 *    posted retry instead of being dropped);
 *  - the engine is already busy (the previous reply is stopped, never queued behind it).
 */
object ChatSpeech {
    private const val TAG = "ChatSpeech"
    private const val UTTERANCE_ID = "jarvis-chat"

    private val main = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null
    private var initializing = false
    private var pendingText = ""

    /** True while the engine is audibly reading, so the microphone can ignore its own voice. */
    @Volatile
    var isSpeaking: Boolean = false
        private set

    fun speak(context: Context, text: String) {
        val spoken = SpeechText.clean(text).take(900).trim()
        if (spoken.isEmpty()) return
        val app = context.applicationContext
        pendingText = spoken
        val ready = engine
        if (ready != null) {
            say(app, ready, spoken)
            return
        }
        if (initializing) return
        initializing = true
        engine = TextToSpeech(app) { status ->
            initializing = false
            val created = engine
            if (status != TextToSpeech.SUCCESS || created == null) {
                main.post {
                    val late = engine
                    if (late != null) say(app, late, pendingText) else Log.w(TAG, "TTS engine unavailable")
                }
                return@TextToSpeech
            }
            attach(created)
            say(app, created, pendingText)
        }
    }

    fun stop() {
        runCatching { engine?.stop() }
        isSpeaking = false
    }

    fun localeFor(text: String): Locale {
        val hasBangla = text.any { it in '\u0980'..'\u09FF' }
        if (hasBangla) return Locale.forLanguageTag("bn-IN")
        val hasDevanagari = text.any { it in '\u0900'..'\u097F' }
        if (hasDevanagari) return Locale.forLanguageTag("hi-IN")
        return Locale.US
    }

    private fun say(context: Context, target: TextToSpeech, spoken: String) {
        if (spoken.isBlank()) return
        runCatching { target.stop() }
        applyLanguage(context, target, spoken)
        isSpeaking = true
        if (target.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID) == TextToSpeech.ERROR) {
            isSpeaking = false
            Log.w(TAG, "TTS rejected the reply")
        }
    }

    private fun attach(target: TextToSpeech) {
        runCatching {
            target.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    if (utteranceId == UTTERANCE_ID) isSpeaking = true
                }

                override fun onDone(utteranceId: String?) {
                    if (utteranceId == UTTERANCE_ID) isSpeaking = false
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    isSpeaking = false
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    isSpeaking = false
                }
            })
        }
    }

    private fun applyLanguage(context: Context, target: TextToSpeech, spoken: String) {
        val app = context.applicationContext
        val wanted = localeFor(spoken)
        val result = runCatching { TtsDelivery.applyLanguage(app, target, wanted) }.getOrDefault(-2)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            runCatching { TtsDelivery.applyLanguage(app, target, Locale.US) }
        }
    }
}
