package com.jarvis.assistant.personality

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.switchmaterial.SwitchMaterial
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.R
import com.jarvis.assistant.background.JarvisConversationService

/** One control and one saved setting, shared by the existing persona screens. */
class AdultEducationSettingsView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {
    private val preferences get() = JarvisApp.instance.preferences
    private val toggle: SwitchMaterial
    private val status: TextView
    private var syncing = false

    init {
        orientation = VERTICAL
        setPadding(0, 0, 0, (12 * resources.displayMetrics.density).toInt())
        inflate(context, R.layout.view_adult_education_settings, this)
        toggle = findViewById(R.id.switchAdultEducation)
        status = findViewById(R.id.tvAdultEducationStatus)
        toggle.setOnCheckedChangeListener { _, checked ->
            if (!syncing) {
                val saved = runCatching { preferences.adultEducationEnabled = checked }.isSuccess
                refresh()
                if (saved) JarvisConversationService.restartSession()
                else Toast.makeText(context, R.string.adult_education_save_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); refresh() }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) refresh()
    }
    private fun refresh() {
        syncing = true
        val enabled = preferences.adultEducationEnabled
        toggle.isChecked = enabled
        status.setText(if (enabled) R.string.adult_education_on else R.string.adult_education_off)
        syncing = false
    }
}