package com.jarvis.assistant.email

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * DEV STYLE: Gmail IMAP credential vault.
 * The address and the 16-character Google App Password are stored ONLY in an
 * Android Keystore-backed EncryptedSharedPreferences file. Nothing is hardcoded,
 * nothing is uploaded. Saving credentials instantly arms the 24/7 auto-reply
 * worker - exactly like the GitHub/Vercel token save flow.
 */
object GmailStore {

    private const val FILE = "jarvis_gmail_vault"
    private const val KEY_EMAIL = "email"
    private const val KEY_APP_PASSWORD = "app_password"
    private const val KEY_AUTO_REPLY_ENABLED = "auto_reply_enabled"
    private const val KEY_REPLY_TEMPLATE = "reply_template"
    private const val KEY_REPLY_KEYWORDS = "reply_keywords"
    private const val KEY_HANDLED_IDS = "handled_message_ids"
    private const val KEY_LAST_REPLY_AT = "last_reply_at"
    private const val KEY_HOUR_BUCKET = "hour_bucket"
    private const val KEY_HOUR_COUNT = "hour_count"
    private const val KEY_DAY_BUCKET = "day_bucket"
    private const val KEY_DAY_COUNT = "day_count"

    const val DEFAULT_TEMPLATE = "Hello {name},\n\nThank you for your email. I have received it and will get back to you soon.\n\nBest regards,\nAyan"

    // Respect Gmail: max 10 auto-replies per hour, 90 seconds apart, 60 per day.
    const val MAX_PER_HOUR = 10
    const val MAX_PER_DAY = 60
    const val MIN_INTERVAL_MS = 90_000L
    private const val MAX_HANDLED_IDS = 300

    private fun prefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(context, FILE, masterKey, EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    }

    fun isConnected(context: Context): Boolean {
        return runCatching { val p = prefs(context); !p.getString(KEY_EMAIL, "").isNullOrBlank() && !p.getString(KEY_APP_PASSWORD, "").isNullOrBlank() }.getOrDefault(false)
    }

    /** Saves the credentials and instantly switches the 24/7 auto reply ON. */
    fun saveCredentials(context: Context, email: String, appPassword: String) {
        val normalizedEmail = email.trim()
        val normalizedPassword = appPassword.replace(" ", "").trim()
        require(normalizedEmail.contains("@")) { "Email address is not valid." }
        require(normalizedPassword.length == 16) { "Google App Password must be exactly 16 characters (without spaces)." }
        prefs(context).edit()
            .putString(KEY_EMAIL, normalizedEmail)
            .putString(KEY_APP_PASSWORD, normalizedPassword)
            .putBoolean(KEY_AUTO_REPLY_ENABLED, true)
            .putLong(KEY_LAST_REPLY_AT, 0L)
            .apply()
    }

    fun clear(context: Context) {
        runCatching { prefs(context).edit().clear().apply() }
    }

    fun email(context: Context): String = runCatching { prefs(context).getString(KEY_EMAIL, "") ?: "" }.getOrDefault("")
    fun appPassword(context: Context): String = runCatching { prefs(context).getString(KEY_APP_PASSWORD, "") ?: "" }.getOrDefault("")

    fun autoReplyEnabled(context: Context): Boolean = runCatching { prefs(context).getBoolean(KEY_AUTO_REPLY_ENABLED, false) }.getOrDefault(false)

    fun setAutoReplyEnabled(context: Context, enabled: Boolean) {
        runCatching { prefs(context).edit().putBoolean(KEY_AUTO_REPLY_ENABLED, enabled).apply() }
    }

    fun template(context: Context): String = runCatching { prefs(context).getString(KEY_REPLY_TEMPLATE, "") ?: "" }.getOrDefault("").ifBlank { DEFAULT_TEMPLATE }

    fun setTemplate(context: Context, template: String) {
        runCatching { prefs(context).edit().putString(KEY_REPLY_TEMPLATE, template.trim()).apply() }
    }

    /** Optional comma separated keywords - when set, only matching emails get a reply. */
    fun keywords(context: Context): List<String> = runCatching { (prefs(context).getString(KEY_REPLY_KEYWORDS, "") ?: "").split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() } }.getOrDefault(emptyList())

    fun setKeywords(context: Context, keywords: String) {
        runCatching { prefs(context).edit().putString(KEY_REPLY_KEYWORDS, keywords.trim()).apply() }
    }

    fun isHandled(context: Context, messageId: String): Boolean = runCatching { prefs(context).getStringSet(KEY_HANDLED_IDS, emptySet())?.contains(messageId) ?: false }.getOrDefault(false)

    fun markHandled(context: Context, messageId: String) {
        runCatching {
            val p = prefs(context)
            val ids = (p.getStringSet(KEY_HANDLED_IDS, emptySet()) ?: emptySet()).toMutableSet()
            ids.add(messageId)
            while (ids.size > MAX_HANDLED_IDS) {
                val oldest = ids.firstOrNull() ?: break
                ids.remove(oldest)
            }
            p.edit().putStringSet(KEY_HANDLED_IDS, ids).apply()
        }
    }

    /** Returns false when a send would break the hourly / daily / interval limits. */
    fun rateLimitAllows(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        return runCatching {
            val p = prefs(context)
            val hourStart = now - (now % 3_600_000L)
            val dayStart = now - (now % 86_400_000L)
            var hourCount = p.getInt(KEY_HOUR_COUNT, 0)
            var dayCount = p.getInt(KEY_DAY_COUNT, 0)
            if (p.getLong(KEY_HOUR_BUCKET, 0L) != hourStart) hourCount = 0
            if (p.getLong(KEY_DAY_BUCKET, 0L) != dayStart) dayCount = 0
            if (hourCount >= MAX_PER_HOUR) return false
            if (dayCount >= MAX_PER_DAY) return false
            if (now - p.getLong(KEY_LAST_REPLY_AT, 0L) < MIN_INTERVAL_MS) return false
            true
        }.getOrDefault(false)
    }

    fun recordAutoReply(context: Context, now: Long = System.currentTimeMillis()) {
        runCatching {
            val p = prefs(context)
            val hourStart = now - (now % 3_600_000L)
            val dayStart = now - (now % 86_400_000L)
            var hourCount = p.getInt(KEY_HOUR_COUNT, 0)
            var dayCount = p.getInt(KEY_DAY_COUNT, 0)
            if (p.getLong(KEY_HOUR_BUCKET, 0L) != hourStart) hourCount = 0
            if (p.getLong(KEY_DAY_BUCKET, 0L) != dayStart) dayCount = 0
            p.edit()
                .putLong(KEY_HOUR_BUCKET, hourStart).putInt(KEY_HOUR_COUNT, hourCount + 1)
                .putLong(KEY_DAY_BUCKET, dayStart).putInt(KEY_DAY_COUNT, dayCount + 1)
                .putLong(KEY_LAST_REPLY_AT, now)
                .apply()
        }
    }
}