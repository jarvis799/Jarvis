package com.jarvis.assistant.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

/** Registered for the app process; the existing assistant foreground service keeps it alive. */
object ChargerMonitor {
    private var cache: ChargerSpeechCache? = null
    private val transitions = ChargerTransition()
    private val handler = Handler(Looper.getMainLooper())
    private val languageListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "key_language" || key == "selected_voice_key") handler.post { cache?.prepare() }
    }
    fun start(context: Context) {
        if (cache != null) return
        val app = context.applicationContext
        cache = ChargerSpeechCache(app)
        app.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(languageListener)
        app.getSharedPreferences("jarvis_tts_voice_v1", Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(languageListener)
        ContextCompat.registerReceiver(app, object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val full = status == BatteryManager.BATTERY_STATUS_FULL || (scale > 0 && level >= scale)
                transitions.accept(plugged, full, status == BatteryManager.BATTERY_STATUS_CHARGING)?.let { cache?.play(it) }
            }
        }, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
    }
}