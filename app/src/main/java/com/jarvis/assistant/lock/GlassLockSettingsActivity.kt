package com.jarvis.assistant.lock

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*

class GlassLockSettingsActivity : AppCompatActivity() {
    private lateinit var status: android.widget.TextView
    private val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) lifecycleScope.launch {
            status.text = "Preparing monochrome wallpaper…"
            try { withContext(Dispatchers.IO) { GlassWallpaper.save(this@GlassLockSettingsActivity, uri) }; status.text = "Wallpaper saved privately in grayscale." }
            catch (error: Exception) { status.text = error.message ?: "Cannot save wallpaper." }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val column = MonoUi.page(this, "Liquid Glass Lock Screen")
        val store = VoiceUnlockStore(this)
        column.addView(MonoUi.text(this, "A companion above Android's secure lock screen, never a replacement. Android may require tapping the Jarvis lock-screen notification to open it."))
        column.addView(MonoUi.toggle(this, "Enable Jarvis lock screen", store.lockEnabled()) { on ->
            store.setLockEnabled(on)
            if (on) GlassLockNotifications.show(this) else GlassLockNotifications.cancel(this)
            status.text = if (on && !getSystemService(android.app.NotificationManager::class.java).areNotificationsEnabled()) "Allow Jarvis notifications to open the lock screen from the notification." else if (on) "Enabled. Open it from the Jarvis notification." else "Disabled."
        })
        status = MonoUi.text(this, "Wallpaper stays on this phone. All wallpaper colors are converted to neutral grayscale.")
        column.addView(status)
        column.addView(MonoUi.button(this, "Manage lock-screen notifications") { startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)) })
        column.addView(MonoUi.button(this, "Choose wallpaper from Gallery") { picker.launch("image/*") })
        column.addView(MonoUi.button(this, "Remove custom wallpaper") { GlassWallpaper.clear(this); status.text = "Default monochrome wallpaper restored." })
        column.addView(MonoUi.button(this, "Preview lock screen") { startActivity(Intent(this, GlassLockActivity::class.java)) })
        column.addView(MonoUi.text(this, "Use Voice Unlock settings to enable the existing background microphone listener. Flashlight and camera request permission only when used. The secure camera does not expose your Gallery while locked."))
    }
}