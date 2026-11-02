package com.jarvis.assistant.lock

import android.Manifest
import android.app.KeyguardManager
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.jarvis.assistant.security.UnlockManager
import com.jarvis.assistant.ui.settings.VoiceUnlockActivity
import com.jarvis.assistant.util.LocationWeatherHelper
import kotlinx.coroutines.launch

/**
 * Jarvis lock surface. It sits above Android's secure keyguard and never replaces it:
 * every unlock ends in Android's own authentication (biometrics or device credential).
 */
class GlassLockActivity : AppCompatActivity() {

    private lateinit var screen: GlassLockLayout
    private lateinit var speech: PhraseRecognition
    private lateinit var shortcuts: GlassLockShortcuts
    private var biometric: JarvisBiometric? = null
    private var listening = false
    private var autoListened = false

    private val recovery = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            startActivity(Intent(this, VoiceUnlockActivity::class.java))
            finishSmoothly()
        } else {
            screen.status.text = "Cancelled. Android security is unchanged."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.BLACK

        screen = GlassLockLayout(this) { action ->
            when (action) {
                "voice" -> listen()
                "unlock" -> unlockWithAndroid()
                "recovery" -> recoverPhrase()
                else -> shortcuts.use(action)
            }
        }
        speech = PhraseRecognition(this)
        shortcuts = GlassLockShortcuts(this) { message -> screen.status.text = message }

        setContentView(screen)
        screen.alpha = 0f
        screen.animate().alpha(1f).setDuration(320).start()
        updateBattery()
        loadWeather()
    }

    override fun onResume() {
        super.onResume()
        updateBattery()
        if (!autoListened) {
            autoListened = true
            autoListen()
        }
    }

    private fun autoListen() {
        val store = VoiceUnlockStore(this)
        if (!store.lockEnabled()) {
            screen.status.text = "Jarvis lock screen is off. Turn it on in Jarvis settings."
            return
        }
        if (store.phrases().isEmpty()) {
            screen.status.text = "No voice phrase saved. Unlock with your PIN, pattern, password or biometrics."
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            screen.status.text = "Microphone is off. Unlock with your PIN, pattern, password or biometrics."
            return
        }
        if (!UnlockManager.isEnabled(this)) {
            screen.status.text = "Voice unlock is off in Jarvis settings."
            return
        }
        listen()
    }

    private fun listen() {
        if (listening) return
        if (!UnlockManager.isEnabled(this)) {
            screen.status.text = "Enable Voice Unlock in Jarvis Settings first."
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 805)
            return
        }
        val store = VoiceUnlockStore(this)
        val phrase = store.phrases().firstOrNull()
        if (phrase == null) {
            screen.status.text = "Add a custom phrase in Jarvis Settings first."
            return
        }
        listening = true
        screen.pulse.listening(true)
        speech.start(phrase, { heard ->
            listening = false
            screen.pulse.listening(false)
            if (store.matches(heard)) {
                screen.status.text = "Phrase matched. Confirm with Android security."
                dismissKeyguard()
            } else {
                screen.status.text = "No phrase matched. Try again or unlock with Android."
            }
        }, { message ->
            screen.status.text = message
            if (!message.startsWith("Listening")) {
                listening = false
                screen.pulse.listening(false)
            }
        }, { level -> screen.pulse.level = level })
    }

    private fun unlockWithAndroid() {
        speech.close()
        listening = false
        screen.pulse.listening(false)
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (!keyguard.isKeyguardLocked) {
            finishSmoothly()
            return
        }
        if (!keyguard.isDeviceSecure) {
            finishSmoothly()
            return
        }
        biometric = JarvisBiometric(this) { confirmed, message ->
            if (confirmed) dismissKeyguard() else screen.status.text = message
        }.also { it.authenticate() }
    }

    private fun recoverPhrase() {
        if (!JarvisRecovery.isDeviceSecure(this)) {
            screen.status.text = "Set an Android screen lock first, then recovery can confirm it is you."
            return
        }
        val intent = JarvisRecovery.confirmIntent(
            this,
            "Jarvis recovery",
            "Confirm your Android screen lock to set a new voice phrase"
        )
        if (intent == null) {
            screen.status.text = "Android did not offer its own lock screen. Open Jarvis settings to change the phrase."
        } else {
            recovery.launch(intent)
        }
    }

    private fun dismissKeyguard() {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (!keyguard.isKeyguardLocked) {
            finishSmoothly()
            return
        }
        keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() = finishSmoothly()
            override fun onDismissCancelled() {
                screen.status.text = "Cancelled. Your Android lock is still active."
            }
            override fun onDismissError() {
                screen.status.text = "Android needs its own lock screen here. Unlock there to continue."
            }
        })
    }

    private fun finishSmoothly() {
        screen.animate().alpha(0f).setDuration(240).withEndAction { finish() }.start()
    }

    private fun updateBattery() {
        @Suppress("DEPRECATION")
        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return
        val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        if (level < 0 || scale <= 0) return
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        screen.setBattery(level * 100 / scale, charging)
    }

    private fun loadWeather() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) return
        lifecycleScope.launch {
            val weather = runCatching { LocationWeatherHelper.fetchWeather(this@GlassLockActivity) }.getOrNull()
            if (weather != null && !isFinishing) screen.setWeather(weather)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        when (requestCode) {
            804 -> shortcuts.permission(granted)
            805 -> if (granted) listen() else screen.status.text = "Microphone denied. Unlock with Android."
        }
    }

    @Deprecated("Android credential confirmation returns here")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == JarvisBiometric.REQUEST_CREDENTIAL) {
            if (resultCode == RESULT_OK) dismissKeyguard()
            else screen.status.text = "Cancelled. Android lock is still active."
        }
    }

    override fun onPause() {
        speech.close()
        listening = false
        shortcuts.close()
        screen.pulse.listening(false)
        super.onPause()
    }
}