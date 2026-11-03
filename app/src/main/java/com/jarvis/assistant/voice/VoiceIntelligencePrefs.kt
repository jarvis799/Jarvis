package com.jarvis.assistant.voice

import android.content.Context
import android.content.SharedPreferences

/**
 * Persistence for the Voice Intelligence screen.
 *
 * A separate small file (not the main preference store) so nothing existing has to be
 * migrated, and so a corrupt entry here can never take the app's other settings with it.
 */
class VoiceIntelligencePrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var profile: ListeningProfile
        get() = ListeningProfile.from(prefs.getString(KEY_PROFILE, ListeningProfile.NORMAL.key))
        set(value) = prefs.edit().putString(KEY_PROFILE, value.key).apply()

    /** 0 = very strict, 100 = very sensitive. 50 keeps the historical thresholds. */
    var wakeSensitivity: Int
        get() = prefs.getInt(KEY_WAKE, 50)
        set(value) = prefs.edit().putInt(KEY_WAKE, value.coerceIn(0, 100)).apply()

    var noiseSuppression: Int
        get() = prefs.getInt(KEY_NOISE, 50)
        set(value) = prefs.edit().putInt(KEY_NOISE, value.coerceIn(0, 100)).apply()

    var interruptionSensitivity: Int
        get() = prefs.getInt(KEY_INTERRUPT, 50)
        set(value) = prefs.edit().putInt(KEY_INTERRUPT, value.coerceIn(0, 100)).apply()

    /**
     * Final multiplier for the wake-word confidence gates already used by the service.
     * 1.0 reproduces today's behaviour exactly.
     */
    fun wakeConfidenceFactor(): Float {
        val slider = 1.30f - (wakeSensitivity.coerceIn(0, 100) / 100f) * 0.60f
        return profile.wakeConfidenceFactor * slider
    }

    /** Minimum speech level that may interrupt Jarvis while it is speaking. */
    fun bargeInThreshold(): Float {
        val base = 0.045f
        val slider = 1.40f - (interruptionSensitivity.coerceIn(0, 100) / 100f) * 0.80f
        return base * slider
    }

    companion object {
        const val FILE = "jarvis_voice_intelligence"
        private const val KEY_PROFILE = "profile"
        private const val KEY_WAKE = "wake_sensitivity"
        private const val KEY_NOISE = "noise_suppression"
        private const val KEY_INTERRUPT = "interrupt_sensitivity"
    }
}
