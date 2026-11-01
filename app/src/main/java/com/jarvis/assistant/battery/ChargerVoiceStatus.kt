package com.jarvis.assistant.battery

import android.content.Context
import android.content.SharedPreferences
import android.widget.LinearLayout
import com.jarvis.assistant.model.OfflineModelStyle

class ChargerVoiceStatus(context: Context) : LinearLayout(context) {
    private val prefs = context.getSharedPreferences("charger_voice", Context.MODE_PRIVATE)
    private val status = OfflineModelStyle.text(context, "", 12f)
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> post { refresh() } }
    init {
        orientation = VERTICAL
        addView(OfflineModelStyle.text(context, "Charger voice", 20f, true))
        addView(OfflineModelStyle.text(context,
            "Connected, full and disconnected announcements use Settings → Conversation Language. Auto learns your first identifiable spoken language. Android TTS saves the phrases once, without recording your microphone, then replays local audio. Install that language's TTS voice if requested. Jarvis must remain running; Android force-stop prevents background announcements.", 12f))
        addView(status); refresh()
    }
    private fun refresh() { status.text = prefs.getString("status", "Waiting for Android TTS…") }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow(); prefs.registerOnSharedPreferenceChangeListener(listener); refresh()
    }
    override fun onDetachedFromWindow() {
        prefs.unregisterOnSharedPreferenceChangeListener(listener); super.onDetachedFromWindow()
    }
}