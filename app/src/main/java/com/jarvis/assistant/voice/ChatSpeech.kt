package com.jarvis.assistant.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * Speaks a short reply with the phone's own TTS engine.
 *
 * Used where the live audio stream cannot answer: a typed command handled on the device
 * ("open YouTube", "call Rakib") used to return text only and stayed silent in chat.
 * The language is picked from the text itself so Bangla, Hindi and English replies
 * are read in their own voice instead of an English engine guessing.
 */
object ChatSpeech {
    private const val TAG = "ChatSpeech"
    private var engine: TextToSpeech? = null

    fun speak(context: Context, text: String) {
        val spoken = text.trim().take(600)
        if (spoken.isEmpty()) return
        val ready = engine
        if (ready != null) {
            ready.stop()
            applyLanguage(ready, spoken)
            ready.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, "jarvis-chat")
            return
        }
        val app = context.applicationContext
        engine = TextToSpeech(app) { status ->
            if (status != TextToSpeech.SUCCESS) {
                Log.w(TAG, "TTS engine unavailable")
                return@TextToSpeech
            }
            val created = engine ?: return@TextToSpeech
            applyLanguage(created, spoken)
            created.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, "jarvis-chat")
        }
    }

    fun localeFor(text: String): Locale {
        val hasBangla = text.any { it in '\u0980'..'\u09FF' }
        if (hasBangla) return Locale.forLanguageTag("bn-IN")
        val hasDevanagari = text.any { it in '\u0900'..'\u097F' }
        if (hasDevanagari) return Locale.forLanguageTag("hi-IN")
        return Locale.US
    }

    private fun applyLanguage(target: TextToSpeech, spoken: String) {
        val wanted = localeFor(spoken)
        val result = runCatching { target.setLanguage(wanted) }.getOrDefault(-2)
        // A phone without the Bangla/Hindi voice data falls back to English rather than silence.
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            runCatching { target.setLanguage(Locale.US) }
        }
    }
}
