package com.jarvis.assistant.ui.settings

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.jarvis.assistant.background.JarvisConversationService
import com.jarvis.assistant.data.preferences.AppPreferences
import com.jarvis.assistant.databinding.ActivityVoiceActivationBinding
import com.jarvis.assistant.util.ThemeManager
import com.jarvis.assistant.voice.hotword.HotwordCapabilities
import com.jarvis.assistant.voice.hotword.HotwordStats

/**
 * Voice Activation: the honest control panel for the wake word.
 * Every status line comes from a real probe of this device.
 */
class VoiceActivationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVoiceActivationBinding
    private lateinit var preferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityVoiceActivationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ThemeManager.applyDynamicBackground(binding.root)
        preferences = AppPreferences(this)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnMicPermission.setOnClickListener {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), MIC_REQUEST)
        }
        binding.btnAssistant.setOnClickListener {
            runCatching { startActivity(HotwordCapabilities.assistantSettingsIntent()) }
                .onFailure { toast("This Android build has no digital assistant settings screen") }
        }
        binding.btnWakePhrase.setOnClickListener { editWakePhrase() }
        binding.btnIntelligence.setOnClickListener {
            startActivity(Intent(this, VoiceIntelligenceActivity::class.java))
        }
        binding.btnTestWake.setOnClickListener {
            startConversation(JarvisConversationService.ACTION_START_CONVERSATION)
            toast("Listening — say your wake word or speak your command")
        }
        binding.btnEnable.setOnClickListener { enableWakeWord() }
        binding.btnDisable.setOnClickListener { disableWakeWord() }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val standby = JarvisConversationService.isStandby.value
        val status = HotwordCapabilities.probe(this, standby)

        binding.tvEngineBadge.text = status.detectionLabel
        binding.tvEngineDetail.text = status.soundTrigger.detail +
            "\n\nEngine: " + status.engine +
            "\nOn-device recognition: " + (if (status.onDeviceRecognition) "yes" else "no (cloud recognition only)") +
            "\nScreen-off listening: " + if (standby) "running while the phone is locked" else "not running"

        binding.tvMicStatus.text = if (status.micPermission)
            "Microphone permission granted"
        else
            "Permission required — the wake word cannot work without it"
        binding.btnMicPermission.text = if (status.micPermission) "Permission granted" else "Grant microphone permission"

        binding.tvAssistantStatus.text = status.assistantLabel +
            (if (status.defaultAssistant) "" else " — open the picker below and choose JARVIS")

        val custom = preferences.customWakeWord.trim()
        binding.tvWakePhrase.text = if (custom.isEmpty()) "Hey JARVIS" else "Hey JARVIS • custom: " + custom

        binding.tvStats.text = "Wake activations: " + HotwordStats.activations(this) +
            "\nStandby uptime: " + HotwordStats.standbyUptime(this) +
            "\nStandby sessions: " + HotwordStats.standbySessions(this) +
            "\nFalse activations: " + HotwordStats.falseActivations(this)

        binding.tvBatteryStatus.text = if (status.batteryUnrestricted)
            "Battery optimisation is off for JARVIS — standby listening survives Doze."
        else
            "Battery optimisation is on: Android may stop standby listening. Allow JARVIS to run without restrictions."
    }

    private fun enableWakeWord() {
        JarvisConversationService.setPoweredOff(this, false)
        startConversation(JarvisConversationService.ACTION_ENTER_STANDBY)
        toast("Wake word armed — standby listening starts in a moment")
        binding.root.postDelayed({ if (!isFinishing) render() }, 1200)
    }

    private fun disableWakeWord() {
        startConversation(JarvisConversationService.ACTION_STOP_ALL)
        JarvisConversationService.setPoweredOff(this, true)
        toast("Wake word disabled — JARVIS stops listening in the background")
        binding.root.postDelayed({ if (!isFinishing) render() }, 800)
    }

    private fun startConversation(action: String) {
        val intent = Intent(this, JarvisConversationService::class.java).setAction(action)
        runCatching { ContextCompat.startForegroundService(this, intent) }
            .onFailure { toast("JARVIS service could not start: " + (it.message ?: "unknown")) }
    }

    private fun editWakePhrase() {
        val input = EditText(this).apply {
            setText(preferences.customWakeWord)
            setHint("hey jarvis")
            setPadding(48, 24, 48, 24)
        }
        AlertDialog.Builder(this)
            .setTitle("Custom wake phrase")
            .setMessage("The word jarvis always works. A custom phrase is added on top of it.")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                preferences.customWakeWord = input.text.toString().trim()
                JarvisConversationService.refreshVoiceUnlockGrammar()
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == MIC_REQUEST) render()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val MIC_REQUEST = 4711
    }
}
