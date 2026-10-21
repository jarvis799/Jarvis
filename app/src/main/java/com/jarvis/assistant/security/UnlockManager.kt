package com.jarvis.assistant.security

import android.app.Activity
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Locale
import com.jarvis.assistant.JarvisApp

/**
 * Voice-driven unlock requests. Jarvis only asks Android to show its own unlock
 * screen: a PIN, password or fingerprint is still required on a secured phone,
 * and no lock credential is ever stored.
 */
object UnlockManager {

    private const val PREFS = "jarvis_voice_unlock"
    private const val KEY_ENABLED = "enabled"
    private const val CHANNEL_ID = "jarvis_unlock_voice"

    /** Phrases Jarvis listens for while the phone is locked. */
    val SPOKEN_PHRASES = listOf(
        "Jarvis, unlock my phone",
        "Jarvis, phone unlock koro",
        "Jarvis, amar phone unlock koro",
        "Jarvis, unlock karo",
        "Jarvis, ফোন আনলক করো",
        "Jarvis, আনলক করো"
    )

    private val UNLOCK_PATTERNS = listOf(
        "unlock phone", "unlock my phone", "unlock the phone", "phone unlock",
        "unlock koro", "unlock karo", "unlock please",
        "ফোন আনলক", "আনলক করো", "লক খোলো", "আনলক"
    )

    private val FILLERS = setOf(
        "jarvis", "hey", "hi", "hello", "ok", "okay", "please", "now", "just", "boss",
        "amar", "amake", "ami", "e", "ta", "ekhon", "tui",
        "জারভিস", "দয়া", "করে", "এখন", "আমার", "একটু"
    )

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    private fun normalize(text: String): String {
        val builder = StringBuilder()
        for (ch in text.lowercase(Locale.ROOT)) {
            builder.append(if (ch.isLetterOrDigit() || ch.isWhitespace()) ch else ' ')
        }
        return builder.toString().split(' ').filter { it.isNotEmpty() }.joinToString(" ")
    }

    /** True when the spoken text is a request to unlock the phone. */
    fun isUnlockCommand(text: String): Boolean {
        val spoken = normalize(text)
        if (spoken.isEmpty()) return false
        return UNLOCK_PATTERNS.any { phrase ->
            val at = spoken.indexOf(phrase)
            if (at < 0) return@any false
            val rest = (spoken.substring(0, at) + " " + spoken.substring(at + phrase.length))
                .trim().split(' ').filter { it.isNotEmpty() }
            rest.isEmpty() || rest.all { it in FILLERS }
        }
    }

    fun requestUnlock(context: Context): String {
        JarvisApp.instance.preferences.clearLegacyUnlockCredential()
        if (!isEnabled(context)) return "Voice unlock is off. Turn it on in Settings, Voice Unlock."
        val keyguard = context.getSystemService(KeyguardManager::class.java)
            ?: return "Android unlock is unavailable. Use your phone's lock screen."
        if (!keyguard.isKeyguardLocked) return "The phone is already unlocked."
        val intent = Intent(context, UnlockActivity::class.java)
        if (context is Activity) {
            context.startActivity(intent)
            return "Android unlock opened. Authenticate with your PIN, password or fingerprint."
        }
        // Background activity launches are restricted, so the unlock screen is offered over
        // the lock screen through a high-priority notification with a full-screen intent.
        val notifications = context.getSystemService(NotificationManager::class.java)
            ?: return "Open Jarvis to use Android unlock."
        if (!notifications.areNotificationsEnabled()) {
            return "Allow Jarvis notifications, then ask again. Your PIN or fingerprint is still required."
        }
        return try {
            notifications.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Voice unlock", NotificationManager.IMPORTANCE_HIGH)
            )
            val open = PendingIntent.getActivity(
                context, 803, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            notifications.notify(803, Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentTitle("Voice unlock")
                .setContentText("Jarvis heard your unlock command. Confirm with Android to open the phone.")
                .setCategory(Notification.CATEGORY_CALL)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(open)
                .setFullScreenIntent(open, true)
                .setAutoCancel(true)
                .build())
            "Android unlock opened on your lock screen. Authenticate with your PIN, password or fingerprint."
        } catch (e: SecurityException) {
            "Android blocked the unlock screen. Allow Jarvis notifications, then ask again."
        }
    }
}
