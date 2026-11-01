package com.jarvis.assistant.security

import android.content.Context
import com.jarvis.assistant.BuildConfig
import com.jarvis.assistant.JarvisApp

/**
 * Creator authentication.
 *
 * The expected key is never hardcoded in the APK source. It comes from
 * BuildConfig.CREATOR_KEY, injected at build time from the JARVIS_CREATOR_KEY
 * Gradle property / CI secret, or from the key the owner sets on this device.
 * Wrong entries are rate limited; successful verification persists the Creator role.
 *
 * Creator verification does not bypass normal authorization checks or grant
 * unrelated system permissions, and it does not turn any feature on or off.
 */
object CreatorAuthenticationService {

    private const val PREFS = "jarvis_creator_auth"
    private const val KEY_IS_CREATOR = "is_creator_verified"
    private const val KEY_VERIFIED_AT = "creator_verified_at"
    private const val KEY_FAILURES = "creator_failures"
    private const val KEY_LOCK_UNTIL = "creator_lock_until"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Key injected at build time. Empty when the build did not supply one. */
    private fun buildKey(): String = runCatching {
        CreatorKeyRules.normalize(BuildConfig.CREATOR_KEY)
    }.getOrDefault("")

    /** Key the owner set on this device in Settings. */
    private fun deviceKey(): String = runCatching {
        CreatorKeyRules.normalize(JarvisApp.instance.preferences.creatorSecretPhrase)
    }.getOrDefault("")

    fun isConfigured(): Boolean =
        CreatorKeyRules.usable(buildKey()) || CreatorKeyRules.usable(deviceKey())

    fun lockRemainingMillis(ctx: Context): Long {
        val until = prefs(ctx).getLong(KEY_LOCK_UNTIL, 0L)
        return (until - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    /** Rate-limited check used by the creator key dialog. */
    fun verify(ctx: Context, key: String): Boolean {
        if (lockRemainingMillis(ctx) > 0L) return false
        val candidate = CreatorKeyRules.normalize(key)
        val accepted = CreatorKeyRules.matches(buildKey(), candidate) ||
            CreatorKeyRules.matches(deviceKey(), candidate)
        if (accepted) {
            clearFailures(ctx)
            markCreatorVerified(ctx)
        } else {
            registerFailure(ctx)
        }
        return accepted
    }

    /**
     * Passive match used by the chat and voice paths. A wrong attempt produces no
     * distinct feedback, so this cannot be used as an oracle; the counter is only
     * driven by [verify].
     */
    fun matchesConfiguredKey(text: String): Boolean =
        CreatorKeyRules.matches(buildKey(), text) || CreatorKeyRules.matches(deviceKey(), text)

    fun markCreatorVerified(ctx: Context) {
        prefs(ctx).edit()
            .putBoolean(KEY_IS_CREATOR, true)
            .putLong(KEY_VERIFIED_AT, System.currentTimeMillis())
            .apply()
    }

    private fun registerFailure(ctx: Context) {
        val store = prefs(ctx)
        val failures = store.getInt(KEY_FAILURES, 0) + 1
        val editor = store.edit().putInt(KEY_FAILURES, failures)
        if (CreatorKeyRules.shouldLock(failures)) {
            editor.putLong(KEY_LOCK_UNTIL, CreatorKeyRules.lockUntil(System.currentTimeMillis()))
        }
        editor.apply()
    }

    private fun clearFailures(ctx: Context) {
        prefs(ctx).edit().remove(KEY_FAILURES).remove(KEY_LOCK_UNTIL).apply()
    }

    fun isCreator(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_IS_CREATOR, false)

    fun verifiedAt(ctx: Context): Long =
        prefs(ctx).getLong(KEY_VERIFIED_AT, 0L)

    fun revoke(ctx: Context) {
        prefs(ctx).edit()
            .putBoolean(KEY_IS_CREATOR, false)
            .putLong(KEY_VERIFIED_AT, 0L)
            .apply()
        clearFailures(ctx)
    }
}