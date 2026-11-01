package com.jarvis.assistant.model

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.widget.LinearLayout
import android.widget.Switch

class LocalWebSettings(context: Context) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        val store = com.jarvis.assistant.lock.VoiceUnlockStore(context)
        addView(Switch(context).apply {
            text = "Use internet with local AI"; setTextColor(Color.WHITE); isChecked = store.webEnabled()
            thumbTintList = ColorStateList.valueOf(Color.WHITE)
            trackTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(Color.GRAY, Color.DKGRAY))
            setOnCheckedChangeListener { _, on -> store.setWebEnabled(on) }
        })
        addView(OfflineModelStyle.text(context, "When enabled and online, your question is sent to the existing web-search provider (or DuckDuckGo) for reference material. Generation stays on this phone; no Gemini key is required. Search may fail or be unavailable.", 12f))
    }
}