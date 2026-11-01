package com.jarvis.assistant.lock

import android.Manifest
import android.app.KeyguardManager
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class GlassLockActivity : AppCompatActivity() {
    private lateinit var screen: GlassLockLayout
    private lateinit var speech: PhraseRecognition
    private lateinit var shortcuts: GlassLockShortcuts
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        else { @Suppress("DEPRECATION") window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON) }
        window.statusBarColor = android.graphics.Color.BLACK; window.navigationBarColor = android.graphics.Color.BLACK; window.decorView.systemUiVisibility = 0
        screen = GlassLockLayout(this) { action -> when (action) { "voice" -> listen(); "unlock" -> unlock(); else -> shortcuts.use(action) } }
        speech = PhraseRecognition(this); shortcuts = GlassLockShortcuts(this) { screen.status.text = it }
        setContentView(screen); screen.alpha = 0f; screen.animate().alpha(1f).setDuration(300).start()
    }
    private fun listen() {
        if (!com.jarvis.assistant.security.UnlockManager.isEnabled(this)) { screen.status.text = "Enable Voice Unlock in Jarvis Settings first."; return }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 805); return
        }
        val store = VoiceUnlockStore(this)
        if (store.phrases().isEmpty()) { screen.status.text = "Add a custom phrase in Jarvis Settings first."; return }
        screen.pulse.listening(true)
        speech.start(store.phrases().first(), { heard ->
            screen.pulse.listening(false)
            if (store.matches(heard)) unlock() else screen.status.text = "No phrase matched. Try again or use Android authentication."
        }, { message -> screen.status.text = message; if (!message.startsWith("Listening")) screen.pulse.listening(false) }, { screen.pulse.level = it })
    }
    private fun unlock() {
        speech.close(); screen.pulse.listening(false)
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (!keyguard.isKeyguardLocked) { finishSmoothly(); return }
        keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() { finishSmoothly() }
            override fun onDismissCancelled() { screen.status.text = "Cancelled. Your Android lock is still active." }
            override fun onDismissError() { screen.status.text = "Use Android's original lock screen to authenticate." }
        })
    }
    private fun finishSmoothly() { screen.animate().alpha(0f).setDuration(250).withEndAction { finish() }.start() }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        if (requestCode == 804) shortcuts.permission(granted) else if (requestCode == 805) { if (granted) listen() else screen.status.text = "Microphone denied. Use Android authentication." }
    }
    override fun onPause() { speech.close(); shortcuts.close(); screen.pulse.listening(false); super.onPause() }
}