package com.jarvis.assistant.personality

import android.content.Context
import android.content.SharedPreferences

/**
 * Persistent personality / voice-mode preferences.
 *
 * Kept in its own SharedPreferences file so that app updates (which never clear
 * this file) cannot erase the boss's saved custom personality prompt.
 */
class PersonalityStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun load(): PersonalityProfile = PersonalityProfile(
        humor = HumorLevel.fromName(prefs.getString(KEY_HUMOR, null)),
        roasting = prefs.getBoolean(KEY_ROASTING, false),
        sarcasm = SarcasmLevel.fromName(prefs.getString(KEY_SARCASM, null)),
        emoji = prefs.getBoolean(KEY_EMOJI, true),
        style = ResponseStyle.fromName(prefs.getString(KEY_STYLE, null)),
        customPromptEnabled = prefs.getBoolean(KEY_CUSTOM_ENABLED, false),
        customPrompt = prefs.getString(KEY_CUSTOM_PROMPT, "").orEmpty(),
        voiceMode = VoiceMode.fromName(prefs.getString(KEY_VOICE_MODE, null)),
        addressName = prefs.getString(KEY_ADDRESS, "Boss").orEmpty().ifBlank { "Boss" }
    )

    fun save(profile: PersonalityProfile) {
        prefs.edit()
            .putString(KEY_HUMOR, profile.humor.name)
            .putBoolean(KEY_ROASTING, profile.roasting)
            .putString(KEY_SARCASM, profile.sarcasm.name)
            .putBoolean(KEY_EMOJI, profile.emoji)
            .putString(KEY_STYLE, profile.style.name)
            .putBoolean(KEY_CUSTOM_ENABLED, profile.customPromptEnabled)
            .putString(KEY_CUSTOM_PROMPT, PersonalityComposer.sanitizeCustomPrompt(profile.customPrompt))
            .putString(KEY_VOICE_MODE, profile.voiceMode.name)
            .putString(KEY_ADDRESS, profile.addressName.ifBlank { "Boss" })
            .putLong(KEY_SAVED_AT, System.currentTimeMillis())
            .apply()
        PersonalityProfileHolder.update(load())
    }

    fun saveCustomPrompt(text: String, enabled: Boolean) {
        prefs.edit()
            .putString(KEY_CUSTOM_PROMPT, PersonalityComposer.sanitizeCustomPrompt(text))
            .putBoolean(KEY_CUSTOM_ENABLED, enabled)
            .putLong(KEY_SAVED_AT, System.currentTimeMillis())
            .apply()
    }

    fun savedAt(): Long = prefs.getLong(KEY_SAVED_AT, 0L)

    companion object {
        private const val PREF_NAME = "jarvis_personality_v1"
        private const val KEY_HUMOR = "humor"
        private const val KEY_ROASTING = "roasting"
        private const val KEY_SARCASM = "sarcasm"
        private const val KEY_EMOJI = "emoji"
        private const val KEY_STYLE = "style"
        private const val KEY_CUSTOM_ENABLED = "custom_enabled"
        private const val KEY_CUSTOM_PROMPT = "custom_prompt"
        private const val KEY_VOICE_MODE = "voice_mode"
        private const val KEY_ADDRESS = "address_name"
        private const val KEY_SAVED_AT = "saved_at"
    }
}