package com.jarvis.assistant.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jarvis.assistant.databinding.ActivityVoiceUnlockBinding
import com.jarvis.assistant.security.UnlockManager
import com.jarvis.assistant.util.ThemeManager

/** Voice unlock settings: phrases, lock-screen readiness and Android's security limits. */
class VoiceUnlockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVoiceUnlockBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityVoiceUnlockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvPhrases.text = UnlockManager.SPOKEN_PHRASES.joinToString("\n") { "\u2022  " + it }
        binding.switchVoiceUnlock.isChecked = UnlockManager.isEnabled(this)
        binding.switchVoiceUnlock.setOnCheckedChangeListener { _, checked ->
            UnlockManager.setEnabled(this, checked)
            Toast.makeText(this, if (checked) "Voice unlock on" else "Voice unlock off", Toast.LENGTH_SHORT).show()
        }
        binding.btnReady.setOnClickListener { openLockScreenAccess() }
    }

    override fun onResume() {
        super.onResume()
        binding.tvStatus.text = statusLines().joinToString("\n")
    }

    private fun statusLines(): List<String> {
        val micReady = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        return listOf(
            (if (micReady) "OK" else "NEEDED") + "  Microphone, so Jarvis can hear the unlock command"
        )
    }

    private fun openLockScreenAccess() {
        try {
            startActivity(Intent("android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT"))
        } catch (e: Exception) {
            Toast.makeText(this, "Open Settings, Apps, Jarvis and allow full-screen notifications.", Toast.LENGTH_LONG).show()
        }
    }

}
