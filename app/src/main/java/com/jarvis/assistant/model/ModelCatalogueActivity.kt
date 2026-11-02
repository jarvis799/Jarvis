package com.jarvis.assistant.model

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.jarvis.assistant.util.ThemeManager

/** Premium offline model page shared by Settings, the control centre and voice shortcuts. */
class ModelCatalogueActivity : AppCompatActivity() {
    private val setup: ModelSetupViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        // The offline model page is a clean white sheet, so the window must match it.
        window.setBackgroundDrawable(ColorDrawable(Color.WHITE))
        window.statusBarColor = Color.WHITE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
        ModelCatalogueController(this, setup).bind()
    }
}