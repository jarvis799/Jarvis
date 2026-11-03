package com.jarvis.assistant.voice.hotword

import android.content.Context

/**
 * What this device actually exposes of Android's SoundTrigger (hardware hotword) stack.
 *
 * SoundTrigger is a system API: a normal APK cannot open a hardware keyphrase session,
 * even when the classes are present in the framework. The probe reports exactly that
 * instead of pretending the DSP is available.
 */
data class SoundTriggerReport(
    val soundTriggerClass: Boolean,
    val managerClass: Boolean,
    val modulesReachable: Boolean,
    val featureFlag: Boolean,
    val exposedToThirdParty: Boolean,
    val detail: String
)

object SoundTriggerProbe {

    fun probe(context: Context): SoundTriggerReport {
        val soundTrigger = runCatching { Class.forName("android.hardware.soundtrigger.SoundTrigger") }.isSuccess
        val manager = runCatching { Class.forName("android.hardware.soundtrigger.SoundTriggerManager") }.isSuccess
        val modules = if (soundTrigger) runCatching {
            Class.forName("android.hardware.soundtrigger.SoundTrigger").getDeclaredMethod("listModules")
            true
        }.getOrDefault(false) else false
        val feature = runCatching {
            context.packageManager.hasSystemFeature("android.hardware.soundtrigger")
        }.getOrDefault(false)

        // Reserved for a system-signed package carrying the privileged SoundTrigger
        // permission. Nothing a sideloaded APK can obtain, so this stays false.
        val exposed = false

        val detail = when {
            !soundTrigger ->
                "This Android build does not include the SoundTrigger framework, so hardware keyphrase detection cannot exist on it."
            !modules ->
                "SoundTrigger is present but closed to apps on this Android version."
            else ->
                "SoundTrigger classes exist, but keyphrase sessions require a system-signed package with the privileged SoundTrigger permission."
        }

        return SoundTriggerReport(soundTrigger, manager, modules, feature, exposed, detail)
    }
}
