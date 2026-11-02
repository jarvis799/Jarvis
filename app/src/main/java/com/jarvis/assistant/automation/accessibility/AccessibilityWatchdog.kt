package com.jarvis.assistant.automation.accessibility

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Android silently disables an accessibility service after an app update, a force stop or a
 * crash of the service itself, and every automation that depends on it stops with it.
 * This checks the real system state and offers a one-tap repair instead of failing quietly.
 */
object AccessibilityWatchdog {
    private var prompted = false

    fun isEnabled(context: Context): Boolean = runCatching {
        val component = ComponentName(context, JarvisAccessibilityService::class.java)
        val raw = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        raw.split(':').any { entry ->
            entry.equals(component.flattenToString(), true) ||
                entry.endsWith("JarvisAccessibilityService", true)
        }
    }.getOrDefault(false)

    fun ensureUsable(activity: Activity) {
        if (isEnabled(activity) || prompted) return
        prompted = true
        runCatching {
            AlertDialog.Builder(activity)
                .setTitle("Accessibility is switched off")
                .setMessage("Android turned JARVIS accessibility off, so screen reading, WhatsApp control and on-screen actions stop working. Tap Repair and switch JARVIS back on.")
                .setPositiveButton("REPAIR") { _, _ -> open(activity) }
                .setNegativeButton("LATER", null)
                .show()
        }
    }

    fun open(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
