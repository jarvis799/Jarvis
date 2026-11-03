package com.jarvis.assistant.audio

import android.content.Context

/** What Android is really using right now for sound in and sound out. */
data class AudioRouteSnapshot(
    val bluetoothConnected: Boolean,
    val bluetoothName: String,
    val outputName: String,
    val outputKind: String,
    val inputName: String,
    val inputKind: String,
    val bluetoothMic: Boolean,
    val micAvailable: Boolean
) {
    val connectionLabel: String
        get() = if (bluetoothConnected) "BLUETOOTH CONNECTED" else "NO BLUETOOTH AUDIO"
}

/** Owner choices for routing, shared by the audio screen and the router. */
object AudioRoutePrefs {

    private const val PREFS = "jarvis_audio_routing"
    private const val KEY_AUTO = "auto_routing"
    private const val KEY_PREFER_OUTPUT = "prefer_bluetooth_output"
    private const val KEY_PREFER_FALLBACK = "prefer_phone_mic_fallback"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun autoRouting(context: Context): Boolean = prefs(context).getBoolean(KEY_AUTO, true)

    fun setAutoRouting(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO, enabled).apply()
    }

    fun preferBluetoothOutput(context: Context): Boolean = prefs(context).getBoolean(KEY_PREFER_OUTPUT, true)

    fun setPreferBluetoothOutput(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_PREFER_OUTPUT, enabled).apply()
    }

    fun preferPhoneMicFallback(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PREFER_FALLBACK, true)

    fun setPreferPhoneMicFallback(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_PREFER_FALLBACK, enabled).apply()
    }
}
