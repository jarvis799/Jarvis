package com.jarvis.assistant.ui.voice

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.materialswitch.MaterialSwitch
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.R
import com.jarvis.assistant.data.preferences.AppPreferences
import com.jarvis.assistant.util.PermissionHelper
import com.jarvis.assistant.util.ThemeManager

/**
 * DEV STYLE: Voice Experience settings - four independent switches that decide what the
 * Hey Jarvis experience does. Preferences live in AppPreferences, so they survive restarts.
 */
class VoiceExperienceActivity : AppCompatActivity() {

    private lateinit var preferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_experience)
        ThemeManager.applyDynamicBackground(findViewById(R.id.voiceExperienceRoot))
        preferences = JarvisApp.instance.preferences

        bindSwitch(R.id.switchVoiceOverlay, preferences.isVoiceOverlayEnabled) { preferences.isVoiceOverlayEnabled = it }
        bindSwitch(R.id.switchEdgeLighting, preferences.isEdgeLightingEnabled) { preferences.isEdgeLightingEnabled = it }
        bindSwitch(R.id.switchKeyboardInput, preferences.isVoiceOverlayKeyboardEnabled) { preferences.isVoiceOverlayKeyboardEnabled = it }
        bindSwitch(R.id.switchVoiceAutoListen, preferences.isVoiceOverlayAutoListen) { preferences.isVoiceOverlayAutoListen = it }

        findViewById<View>(R.id.btnGrantOverlayAccess).setOnClickListener {
            PermissionHelper.requestOverlayPermission(this)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionCard()
    }

    private fun bindSwitch(id: Int, checked: Boolean, apply: (Boolean) -> Unit) {
        val switch = findViewById<MaterialSwitch>(id)
        switch.isChecked = checked
        switch.setOnCheckedChangeListener { _, value -> apply(value) }
    }

    private fun refreshPermissionCard() {
        val granted = PermissionHelper.hasOverlayPermission(this)
        findViewById<View>(R.id.cardOverlayPermission).visibility = if (granted) View.GONE else View.VISIBLE
    }
}
