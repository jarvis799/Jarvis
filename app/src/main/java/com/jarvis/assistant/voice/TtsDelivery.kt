package com.jarvis.assistant.voice

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * One place that decides HOW an engine voice is delivered, so the live voice reply, a typed
 * command reply and the Voice Studio preview all sound the same.
 *
 * Order matters: the language is set first (setting a language can put the engine back on its
 * default voice), then the voice the boss picked is applied when it fits that language, then
 * the chosen prosody preset.
 */
object TtsDelivery {

    /** Speech attributes, so replies are routed and ducked like an assistant, not like music. */
    fun prepare(context: Context, tts: TextToSpeech) {
        runCatching {
            tts.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
        }
        VoiceStyles.current(context)?.let { style ->
            runCatching {
                tts.setPitch(style.pitch)
                tts.setSpeechRate(style.rate)
            }
        }
    }

    /**
     * Applies language, chosen voice and prosody to [tts].
     * @return the [TextToSpeech.setLanguage] result, so callers keep their existing checks.
     */
    fun applyLanguage(context: Context, tts: TextToSpeech, locale: Locale?): Int {
        prepare(context, tts)
        val result = locale?.let { runCatching { tts.setLanguage(it) }.getOrDefault(TextToSpeech.LANG_AVAILABLE) }
            ?: TextToSpeech.LANG_AVAILABLE

        val savedKey = TtsVoiceSelector.savedKey(context)
        if (savedKey.isNotBlank()) {
            val voice = TtsVoiceCatalog.find(tts, savedKey)
            if (voice != null && (locale == null || voice.locale.language.equals(locale.language, ignoreCase = true))) {
                runCatching { tts.voice = voice }
            }
        }
        return result
    }
}
