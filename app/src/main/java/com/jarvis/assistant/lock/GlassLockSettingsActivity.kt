package com.jarvis.assistant.lock

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Premium monochrome settings for the Jarvis lock surface. Every row performs a real action. */
class GlassLockSettingsActivity : AppCompatActivity() {

    private lateinit var status: TextView

    private val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            status.text = "Preparing monochrome wallpaper…"
            val outcome = withContext(Dispatchers.IO) {
                runCatching { GlassWallpaper.save(this@GlassLockSettingsActivity, uri) }
            }
            status.text = outcome.fold(
                onSuccess = { "Wallpaper saved privately on this phone." },
                onFailure = { it.message ?: "Cannot save this picture." }
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        build()
    }

    private fun build() {
        val column = MonoUi.page(
            this,
            "Lock screen",
            "Jarvis sits above Android's secure lock screen. Every unlock is confirmed by Android itself."
        )
        val store = VoiceUnlockStore(this)

        column.addView(MonoUi.toggle(this, "Enable Jarvis lock screen", store.lockEnabled()) { enabled ->
            store.setLockEnabled(enabled)
            if (enabled) GlassLockNotifications.show(this) else GlassLockNotifications.cancel(this)
            status.text = if (enabled) {
                "Enabled. Open it from Preview lock screen below; no notification is posted."
            } else {
                "Disabled. Android's own lock screen is untouched."
            }
        })

        status = MonoUi.text(this, "Your wallpaper stays on this phone.").apply { setTextColor(MonoUi.DIM) }
        column.addView(status)

        column.addView(MonoUi.label(this, "Appearance"))
        column.addView(MonoUi.actionRow(this, "Choose wallpaper", "Gallery photo, converted to monochrome") {
            picker.launch("image/*")
        })
        column.addView(MonoUi.actionRow(this, "Remove wallpaper", "Back to pure black") {
            GlassWallpaper.clear(this)
            status.text = "Wallpaper removed."
        })
        column.addView(MonoUi.actionRow(this, "Preview lock screen", "Open the full lock surface now") {
            startActivity(Intent(this, GlassLockActivity::class.java))
        })

        column.addView(MonoUi.label(this, "Security"))
        column.addView(MonoUi.actionRow(
            this,
            "Test Android authentication",
            "Real BiometricPrompt, or your PIN / pattern if no fingerprint"
        ) {
            JarvisBiometric(this) { confirmed, message ->
                status.text = if (confirmed) "Android confirmed you. Unlock works." else message
            }.authenticate()
        })
        column.addView(MonoUi.footer(
            this,
            "Android's secure keyguard always stays on. A matched phrase or biometric only continues to Android's own " +
                "authentication; nothing here can bypass your PIN, pattern, password or fingerprint. The camera " +
                "shortcut opens Android's secure camera and never exposes your Gallery while locked."
        ))
    }

}