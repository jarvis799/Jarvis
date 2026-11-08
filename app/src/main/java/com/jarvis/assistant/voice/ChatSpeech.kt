package com.jarvis.assistant.voice

import android.content.Context
import java.util.Locale

/**
 * Speaks a short reply with the phone's own voice.
 *
 * This used to own its own TextToSpeech engine and call stop() before every line, which is why a
 * typed reply could cut off a live one. It now only forwards to [VoiceSession], the single owner of
 * the engine, so nothing in the app can talk over anything else.
 */
object ChatSpeech {

    /** True while the shared voice lane is audible, so the microphone can ignore its own voice. */
    val isSpeaking: Boolean get() = VoiceSession.isSpeaking

    fun speak(context: Context, text: String) {
        VoiceSession.speak(context, text, localeFor(text), VoiceSession.OWNER_ASSISTANT)
    }

    fun stop() {
        VoiceSession.interrupt("chat-stop")
    }

    fun localeFor(text: String): Locale {
        val hasBangla = text.any { it in '\u0980'..'\u09FF' }
        if (hasBangla) return Locale.forLanguageTag("bn-IN")
        val hasDevanagari = text.any { it in '\u0900'..'\u097F' }
        if (hasDevanagari) return Locale.forLanguageTag("hi-IN")
        return Locale.US
    }
}
