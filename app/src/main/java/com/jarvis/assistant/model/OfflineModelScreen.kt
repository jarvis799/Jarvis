package com.jarvis.assistant.model

import android.content.Context
import android.content.res.ColorStateList
import android.widget.EditText
import android.widget.ScrollView
import androidx.appcompat.widget.SwitchCompat

class OfflineModelScreen(context: Context, back: () -> Unit) : ScrollView(context) {
    val content = OfflineModelStyle.column(context)
    val status = OfflineModelStyle.text(context, "", 13f, true)
    val recommended = OfflineModelStyle.column(context)
    val installed = OfflineModelStyle.column(context)
    val results = OfflineModelStyle.column(context)
    val archive = ModelArchiveControls(context)
    val searchField = EditText(context).apply {
        hint = "Search other GGUF models"; setTextColor(OfflineModelStyle.ink); setHintTextColor(OfflineModelStyle.mint)
        isSingleLine = true; textSize = 14f
    }
    val searchButton = OfflineModelStyle.button(context, "Search catalogue") {}
    val fallback = SwitchCompat(context).apply {
        text = "Offline fallback"; setTextColor(OfflineModelStyle.ink)
        thumbTintList = ColorStateList.valueOf(OfflineModelStyle.lime)
        setPadding(0, OfflineModelStyle.dp(context, 12), 0, OfflineModelStyle.dp(context, 12))
    }
    init {
        setBackgroundColor(OfflineModelStyle.background); isFillViewport = true
        content.setPadding(OfflineModelStyle.dp(context, 20), OfflineModelStyle.dp(context, 24), OfflineModelStyle.dp(context, 20), OfflineModelStyle.dp(context, 32))
        addView(content); content.addView(OfflineModelHeader(context, back))
        content.addView(status); content.addView(fallback)
        content.addView(OfflineModelStyle.text(context, "Gemini is used first whenever a key is set and the phone is online. With no key, or no internet, a ready local model answers by itself.", 12f))
        content.addView(LocalWebSettings(context))
        content.addView(archive)
        content.addView(OfflineModelStyle.text(context, "Recommended models", 20f, true)); content.addView(recommended)
        content.addView(OfflineModelStyle.text(context, "Downloaded on this phone", 20f, true)); content.addView(installed)
        content.addView(OfflineModelStyle.text(context, "Advanced catalogue", 20f, true))
        content.addView(OfflineModelStyle.text(context, "Other models may not fit this phone or engine. Setup errors appear here; they are never marked ready.", 12f))
        content.addView(searchField); content.addView(searchButton); content.addView(results)
        content.addView(com.jarvis.assistant.battery.ChargerVoiceStatus(context))
    }
}