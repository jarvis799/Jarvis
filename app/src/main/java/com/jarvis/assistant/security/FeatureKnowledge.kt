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

    const val APP_VERSION = "2.1.2"

    const val CREATOR_IDENTITY: String =
        "Amar creator Arian Ahamed Ayan, the mind behind DEV STYLE. Portfolio arian00001.site."

    private val capabilities = listOf(
        "Voice + text chat, 2D animated avatar, Hindi-first replies",
        "Fast command router for device controls, apps, media and messaging in English, Bangla, Banglish, Hindi and Hinglish",
        "Background terminal: python, node, pip, npm, apt/pkg, git, curl - no terminal UI",
        "WhatsApp assistant: messages, calls, auto-reply",
        "YouTube automation: play, search, pause, fullscreen, subscribe",
        "Phone controls: torch, brightness, volume, open apps, calls, contacts",
        "Google Workspace: Gmail, Calendar, Drive in-app after one-time connect",
        "Memory: saved memories, song history, routines",
        "Photo memory, weather card, live web search, screen share visual tasks",
        "QR/barcode scanner with OCR, multi-command sentences",
        "On-Guard thief watch and official device lock (Device Administrator)",
        "Custom Voice Unlock phrases and private reference samples in Settings; Android PIN, pattern, password or biometrics remain required. Separate monochrome Liquid Glass lock-screen companion with Gallery wallpaper, camera and flashlight",
        "Every feature is free and unlimited for every user - no licence key, no daily limit",
        "Plugin Lab: install extra skills as JSON plugins",
        "In-app media viewer: YouTube and Instagram play right inside Jarvis",
        "Media HUD over YouTube: skip ad, like, subscribe, fullscreen",
        "APK Agent: import an APK and read its manifest, permissions and structure",
        "In-app music player: songs, favourites and playlists from this device play inside Jarvis",
        "Skill registry: verified native skills + reviewed external skill libraries",
        "Runtime Feature Updates: feature packs install from GitHub Releases without a new APK",
        "Sub Agents: WhatsApp auto reply agent and Gmail email auto reply agent, both Kotlin native - no Python needed",
        "Sub agent banano: ek sentence e bolo - 'sub agent banao, jokhon keu price bole reply: ...' - Jarvis real agent toiri kore rule store e rakhe; kono 'tool nai' na",
        "Settings > Offline AI Models: premium on-device setup page with TinyLlama 1.1B and Qwen2.5 1.5B downloads, pause/resume, SHA-256 verification and automatic activation only after real native test generation succeeds",
        "Offline knowledge: existing pretrained open-source model weights with concise Jarvis-style instructions, not a newly trained model and not one billion facts. Live information still needs internet. Runtime and model readiness are checked before claiming offline replies work",
        "Creator console: unlocked by the creator key configured at build time or set on this device - no key ships inside the app",
        "Scanner: camera QR/barcode scan plus gallery import, torch and WiFi/Link handling",
        "Home header greets you with your display name, live weather and connection status",
        "18+ Adult Education Mode: a separate saved switch in Settings > Persona & Voice for non-graphic health, consent and relationship education; not erotic roleplay or a Girlfriend Persona",
        "Profile photo: tap the avatar on the Settings profile card to choose your own picture or remove it - it stays on this device",
        "Backup & Restore in Settings: save your settings and personalisation to a file and restore them later; API keys and passwords are never included"
    )

    private val whatsNew = listOf(
        "Background noise is now ignored: a fan, TV or street sound no longer opens a turn or gets answered",
        "The same creator line is no longer repeated - Jarvis answers the actual question",
        "Answers start much faster: thinking is at its lightest for every Live model",
        "Email auto reply sends a redesigned HTML card with no double greeting",
        "Offline AI replies are much faster: the model now stays loaded instead of reloading every message",
        "Offline AI Models: 1.1B / 1.5B verified downloads, automatic setup and a bundled llama.cpp CPU inference engine",
        "Offline readiness is reported only after this phone actually loads the model and generates a test reply; setup errors appear directly on the download page",
        "Sub agent banano ekhon kaj kore: bolo ar sathe sathe real WhatsApp/Email agent toiri hoy",
        "Jarvis ar kono kaj e 'amar tool nai' bole na - sub agent, plugin, local model, backup, memory ar scanner sob local action diye kore",
        "Creator key is no longer inside the app - the build secret or the key set on this device is what verifies the creator",
        "Offline reply switch now sits directly under the local model downloader",
        "Local model downloader restored: search Hugging Face, download with pause and resume, set active",
        "Scanner bottom bar now matches the main dock and the torch and gallery buttons are full size",
        "Sub agents answer with their real state and explain their own setup",
        "Jarvis now explains every feature and how to use it, instead of asking the user to do it",
        "Chat border beam now travels around the conversation area",
        "Tap your profile photo in Settings to set your own picture, or remove it",
        "Backup & Restore in Settings saves your settings without keys",
        "Every feature stays free and unlimited for every user"
    )

    /**
     * Compact, truthful capability block injected into the model's system prompt so Jarvis
     * always knows what the app can actually do and how each feature is turned on.
     */
    fun promptBlock(): String = buildString {
        appendLine("[WHAT THIS APP CAN ACTUALLY DO - v$APP_VERSION]:")
        capabilities.forEachIndexed { index, item -> appendLine("${index + 1}. $item") }
        appendLine("[LOCAL MODEL STATUS]: " + com.jarvis.assistant.model.OfflineModelStatus.describe(com.jarvis.assistant.JarvisApp.instance))
        appendLine("[SUB AGENTS - WHAT THEY DO AND HOW THEY WORK]:")
        appendLine("- WhatsApp sub agent: watches incoming WhatsApp notifications, matches your reply rules and answers for you in the background. Setup on its screen: notification access plus at least one enabled rule.")
        appendLine("- Email sub agent: connects to Gmail with a 16-character App Password, reads new unread mail and replies using your saved HTML design. Runs about every 15 minutes with hourly and daily send caps.")
        appendLine("- Sub agents are pure Kotlin and need no Python. Python only exists for the background terminal when the user asks for shell work.")
        appendLine("- If the user asks what a sub agent does, say it in one short line and name the single switch that turns it on. Never claim an agent is ON unless its screen shows it ON.")
        appendLine("[CREATING A NEW SUB AGENT - THE APP CAN DO THIS]:")
        appendLine("- The user may say: \"ekta sub agent banao\", \"create a sub agent\", \"sub agent lagao je price bolle reply de\". Jarvis builds it at once with the create_sub_agent tool (or locally): the trigger keyword and the reply line are stored in the WhatsApp auto-reply rule store, or in the Email agent, so a real agent runs afterwards.")
        appendLine("- Reply with what was stored (trigger + reply + ON/OFF state) and name the one remaining step if a permission is missing (notification access for WhatsApp, App Password for Gmail).")
        appendLine("- NEVER say \"amar sub agent bananor tool nai\", \"I have no tool for this\" or any equivalent. If a specific detail is missing, ask one short question (which trigger, which reply) and create it immediately after the answer.")
    }

    fun capabilitiesText(): String =
        "Amar sob feature, Sir:\n" + capabilities.withIndex()
            .joinToString("\n") { "${it.index + 1}. ${it.value}" } + "\n" +
            com.jarvis.assistant.personality.AdultEducationPolicy.statusText(
                com.jarvis.assistant.JarvisApp.instance.preferences.adultEducationEnabled
            )

    fun whatsNewText(): String =
        "Notun upgrade esheche, Sir! Ei feature gulo notun:\n" + whatsNew.joinToString("\n") { "- $it" }

    /** Creator-only full self-test of every major feature. Returns a spoken report. */
    fun runFeatureTest(context: Context): String {
        fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
        val results = mutableListOf<Pair<String, Boolean>>()
        results += "Camera" to granted(Manifest.permission.CAMERA)
        results += "Microphone" to granted(Manifest.permission.RECORD_AUDIO)
        results += "Contacts" to granted(Manifest.permission.READ_CONTACTS)
        results += "Display over other apps (edge lighting)" to Settings.canDrawOverlays(context)
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