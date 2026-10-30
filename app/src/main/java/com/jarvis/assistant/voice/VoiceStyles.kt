package com.jarvis.assistant.voice

import android.content.Context

/**
 * A prosody preset applied ON TOP of a real device voice.
 *
 * An Android TTS voice itself cannot be created by an app — the engine supplies the voices.
 * What an app can change honestly is how that voice is delivered: pitch and pace. These
 * presets give the same engine voice a warmer, deeper, brighter or calmer character without
 * pretending a new speaker exists.
 */
data class VoiceStyle(
    val key: String,
    val label: String,
    val description: String,
    val pitch: Float,
    val rate: Float
)

object VoiceStyles {

    private const val PREFS = "jarvis_voice_style_v1"
    private const val KEY_SELECTED = "style_key"

    val ALL: List<VoiceStyle> = listOf(
        VoiceStyle("natural", "Natural", "Balanced pitch and pace — everyday speech.", 1.0f, 1.0f),
        VoiceStyle("warm", "Warm", "A little lower and slower — personal and calm.", 0.94f, 0.96f),
        VoiceStyle("deep", "Deep", "Lower and steadier — a heavier, cinematic tone.", 0.86f, 0.92f),
        VoiceStyle("bright", "Bright", "Slightly higher and quicker — friendly and upbeat.", 1.08f, 1.06f),
        VoiceStyle("calm", "Calm", "Unhurried — best for long answers and night use.", 0.97f, 0.88f),
        VoiceStyle("energetic", "Energetic", "Faster and brighter — short answers on the move.", 1.05f, 1.14f)
    )

    fun byKey(key: String?): VoiceStyle = ALL.firstOrNull { it.key == key } ?: ALL.first()

    /** The saved style, or null when the user never picked one (engine defaults then apply). */
    fun current(context: Context): VoiceStyle? {
        val key = savedKey(context)
        if (key.isBlank()) return null
        return ALL.firstOrNull { it.key == key }
    }

    fun savedKey(context: Context): String =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SELECTED, "").orEmpty()

    fun save(context: Context, style: VoiceStyle) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_SELECTED, style.key).apply()
    }

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_SELECTED).apply()
    }
}