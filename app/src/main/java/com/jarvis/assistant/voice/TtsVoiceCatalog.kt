package com.jarvis.assistant.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

/** One real voice exposed by the device's TTS engine. */
data class VoiceOption(
    val key: String,
    val name: String,
    val locale: Locale,
    val languageLabel: String,
    val accent: String,
    val provider: String,
    val requiresNetwork: Boolean,
    val installed: Boolean,
    val featuredRank: Int
) {
    val availabilityLabel: String
        get() = if (requiresNetwork) "Online (internet required)" else "Offline-capable"
}

/**
 * Reads the ACTUAL voice list from the configured Android TTS engine.
 * Voices the engine reports as not installed are filtered out, and nothing is invented:
 * if the engine cannot synthesise a language, no voice for it is listed.
 */
object TtsVoiceCatalog {

    private val FEATURED_LANGUAGES = listOf("bn", "hi", "en")

    fun options(tts: TextToSpeech?): List<VoiceOption> {
        val voices = tts?.voices ?: return emptyList()
        val engine = engineLabel(tts)
        return voices
            .filter { it.locale != null }
            .filterNot { it.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) == true }
            .map { voice ->
                VoiceOption(
                    key = keyOf(voice),
                    name = voice.name,
                    locale = voice.locale,
                    languageLabel = languageLabel(voice.locale),
                    accent = accentLabel(voice.locale),
                    provider = engine,
                    requiresNetwork = voice.isNetworkConnectionRequired,
                    installed = true,
                    featuredRank = featuredRank(voice.locale)
                )
            }
            .sortedWith(compareBy<VoiceOption>({ it.featuredRank }, { it.languageLabel }, { it.name }))
    }

    fun engineLabel(tts: TextToSpeech?): String {
        val engine = tts?.defaultEngine ?: return "Android Text-to-Speech"
        return engine.substringAfterLast('.').replaceFirstChar { it.uppercase(Locale.ENGLISH) } + " (Android TTS)"
    }

    fun keyOf(voice: Voice): String = voice.name + "|" + voice.locale.toLanguageTag()

    fun keyOf(option: VoiceOption): String = option.key

    fun languageLabel(locale: Locale): String {
        val base = locale.getDisplayLanguage(Locale.ENGLISH).ifBlank { locale.language }
        return base.replaceFirstChar { it.uppercase(Locale.ENGLISH) }
    }

    private fun accentLabel(locale: Locale): String {
        val country = locale.getDisplayCountry(Locale.ENGLISH)
        return country.ifBlank { "Generic" }
    }

    private fun featuredRank(locale: Locale): Int {
        val index = FEATURED_LANGUAGES.indexOf(locale.language.lowercase(Locale.ENGLISH))
        return if (index >= 0) index else FEATURED_LANGUAGES.size
    }

    /** Language-appropriate sample line used by the preview button. */
    fun previewText(locale: Locale): String = when (locale.language.lowercase(Locale.ENGLISH)) {
        "bn" -> "হ্যালো Boss, আমি জার্ভিস। আজ আপনাকে কীভাবে সাহায্য করতে পারি?"
        "hi" -> "नमस्ते Boss, मैं जार्विस हूँ। आज मैं आपकी क्या मदद कर सकता हूँ?"
        else -> "Hello Boss, I am Jarvis. How can I help you today?"
    }

    fun find(tts: TextToSpeech?, key: String): Voice? =
        tts?.voices?.firstOrNull { keyOf(it) == key }
}

/** Persists the voice the boss picked in Voice Studio. */
object TtsVoiceSelector {

    private const val PREFS = "jarvis_tts_voice_v1"
    private const val KEY_SELECTED = "selected_voice_key"

    fun save(context: Context, option: VoiceOption) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_SELECTED, option.key).apply()
    }

    fun savedKey(context: Context): String =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SELECTED, "").orEmpty()

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_SELECTED).apply()
    }

    /**
     * Applies the saved voice to a live engine. Returns the applied voice name, or null
     * when nothing is saved or the saved voice no longer exists on this device.
     */
    fun applyPreferred(context: Context, tts: TextToSpeech): String? {
        val key = savedKey(context)
        if (key.isBlank()) return null
        val voice = TtsVoiceCatalog.find(tts, key) ?: return null
        return try {
            tts.voice = voice
            voice.name
        } catch (_: Exception) {
            null
        }
    }
}