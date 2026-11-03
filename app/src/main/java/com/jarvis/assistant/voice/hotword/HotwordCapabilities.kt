package com.jarvis.assistant.voice.hotword

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat

/** Verified, on-device facts behind the Voice Activation screen. */
data class HotwordStatus(
    val micPermission: Boolean,
    val soundTrigger: SoundTriggerReport,
    val onDeviceRecognition: Boolean,
    val defaultAssistant: Boolean,
    val batteryUnrestricted: Boolean,
    val listeningInStandby: Boolean,
    val engine: String
) {
    val detectionLabel: String
        get() = when {
            soundTrigger.exposedToThirdParty -> "HARDWARE DSP AVAILABLE"
            listeningInStandby -> "SOFTWARE DETECTION ACTIVE"
            else -> "DETECTION ENGINE IDLE"
        }

    val assistantLabel: String
        get() = if (defaultAssistant) "JARVIS IS DEFAULT ASSISTANT" else "DEFAULT ASSISTANT NOT SELECTED"
}

object HotwordCapabilities {

    fun probe(context: Context, standby: Boolean): HotwordStatus {
        val app = context.applicationContext
        val mic = ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        val soundTrigger = SoundTriggerProbe.probe(app)
        val onDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(app) }.getOrDefault(false)
        } else {
            false
        }
        val selected = runCatching {
            android.provider.Settings.Secure.getString(app.contentResolver, "voice_interaction_service")
        }.getOrNull().orEmpty()
        val assistant = selected.contains(app.packageName)
        val unrestricted = runCatching {
            val power = app.getSystemService(Context.POWER_SERVICE) as PowerManager
            power.isIgnoringBatteryOptimizations(app.packageName)
        }.getOrDefault(false)

        return HotwordStatus(
            micPermission = mic,
            soundTrigger = soundTrigger,
            onDeviceRecognition = onDevice,
            defaultAssistant = assistant,
            batteryUnrestricted = unrestricted,
            listeningInStandby = standby,
            engine = HotwordStats.engine(app)
        )
    }

    /** The only legitimate way to make JARVIS the default assistant: the system picker. */
    fun assistantSettingsIntent(): Intent =
        Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
