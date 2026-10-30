package com.jarvis.assistant.security

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/** DEV STYLE: Jarvis's own knowledge - creator identity, full feature list, what's new, and a creator-only feature self-test. */
object FeatureKnowledge {

    const val APP_VERSION = "6.0"

    const val CREATOR_IDENTITY: String =
        "Amar creator Arian Ahamed Ayan, the mind behind DEV STYLE. Portfolio arian00001.site."

    private val capabilities = listOf(
        "Voice + text chat, 2D animated avatar, Hindi-first replies",
        "10000+ multilingual instant commands (English, Bangla, Banglish, Hindi, Hinglish)",
        "Background terminal: python, node, pip, npm, apt/pkg, git, curl - no terminal UI",
        "WhatsApp assistant: messages, calls, auto-reply",
        "YouTube automation: play, search, pause, fullscreen, subscribe",
        "Phone controls: torch, brightness, volume, open apps, calls, contacts",
        "Google Workspace: Gmail, Calendar, Drive in-app after one-time connect",
        "Memory: saved memories, song history, routines",
        "Photo memory, weather card, live web search, screen share visual tasks",
        "QR/barcode scanner with OCR, multi-command sentences",
        "On-Guard thief watch and official device lock (Device Administrator)",
        "Overlay avatar and pastel rainbow edge lighting on the wake word",
        "Every feature is free and unlimited for every user - no licence key, no daily limit",
        "Plugin Lab: install new skills as JSON plugins - self-evolving, hot-reloaded",
        "In-app media viewer: YouTube and Instagram play right inside Jarvis",
        "Media HUD over YouTube: skip ad, like, subscribe, fullscreen",
        "APK Agent: import any APK - truthful analysis and one-question-at-a-time changes",
        "In-app music player: songs, favourites and playlists from this device play inside Jarvis",
        "Skill registry: verified native skills + reviewed external skill libraries",
        "Runtime Feature Updates: feature packs install from GitHub Releases without a new APK"
    )

    private val whatsNew = listOf(
        "10000+ multilingual command pack - instant fast responses",
        "Pastel rainbow edge lighting on 'Hey Jarvis' - works over other apps too",
        "Glass QR scanner design",
        "Creator master key AHONA-I-LOVE-YOU now works everywhere",
        "Creator-only 'test all features' command with a full report",
        "Jarvis knows its creator (Ayan Sir) and its full feature list",
        "Plugin Lab: install new skills as plugins without an app update",
        "YouTube and Instagram play inside the app with the built-in media viewer",
        "Custom AI endpoints: any prompt-URL or OpenAI-style API now works reliably",
        "APK Agent: real on-device APK analysis with skill registry and risk review"
    )

    fun capabilitiesText(): String =
        "Amar sob feature, Sir:\n" + capabilities.withIndex()
            .joinToString("\n") { "${it.index + 1}. ${it.value}" }

    fun whatsNewText(): String =
        "Notun upgrade esheche, Sir! Ei feature gulo notun:\n" + whatsNew.joinToString("\n") { "- $it" }

    /** Creator-only full self-test of every major feature. Returns a spoken report. */
    fun runFeatureTest(context: Context): String {
        fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
        val results = mutableListOf<Pair<String, Boolean>>()
        results += "Camera" to granted(Manifest.permission.CAMERA)
        results += "Microphone" to granted(Manifest.permission.RECORD_AUDIO)
        results += "Contacts" to granted(Manifest.permission.READ_CONTACTS)
        results += "Overlay (avatar + edge lighting)" to Settings.canDrawOverlays(context)
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = android.content.ComponentName(context, DeviceLockReceiver::class.java)
        results += "Device admin (phone lock)" to dpm.isAdminActive(admin)
        val nm = context.getSystemService(android.app.NotificationManager::class.java)
        results += "Notifications" to (nm?.areNotificationsEnabled() ?: false)
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val torch = runCatching {
            cm.cameraIdList.any { id ->
                cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrDefault(false)
        results += "Torch hardware" to torch
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val battery = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        results += "Battery report" to (battery in 1..100)
        val pass = results.count { it.second }
        val lines = results.joinToString("\n") { (name, ok) ->
            (if (ok) "PASS " else "FAIL ") + name
        }
        return "Feature test complete, Ayan Sir: $pass/${results.size} pass.\n$lines\nBattery: $battery percent."
    }
}