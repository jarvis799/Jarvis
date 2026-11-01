package com.jarvis.assistant.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.jarvis.assistant.lock.MonoUi
import com.jarvis.assistant.lock.VoiceUnlockPanel

class VoiceUnlockActivity : AppCompatActivity() {
    private lateinit var panel: VoiceUnlockPanel
    private var pending: (() -> Unit)? = null
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val action = pending; pending = null
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
            (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)) action?.invoke()
        else android.widget.Toast.makeText(this, "Allow microphone and notifications in Android app permissions to enable background Voice Unlock.", android.widget.Toast.LENGTH_LONG).show()
        panel.refresh()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        panel = VoiceUnlockPanel(this, MonoUi.page(this, "Voice Unlock"), ::withMicrophone)
    }
    private fun withMicrophone(action: () -> Unit) {
        val needed = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) needed.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = needed.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) action() else { pending = action; permissions.launch(missing.toTypedArray()) }
    }
    override fun onPause() { panel.close(); super.onPause() }
}