package com.jarvis.assistant.model

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.jarvis.assistant.util.ThemeManager

/** Premium offline model page shared by Settings, the control centre and voice shortcuts. */
class ModelCatalogueActivity : AppCompatActivity() {
    private val setup: ModelSetupViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        ModelCatalogueController(this, setup).bind()
    }
}