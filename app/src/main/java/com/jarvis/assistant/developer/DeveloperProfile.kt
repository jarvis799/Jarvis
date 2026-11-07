package com.jarvis.assistant.developer

import android.content.Context

/**
 * Editable developer identity. Jarvis answers "who are you" and "who is your developer" from
 * these values, so the profile can be corrected on the device without rebuilding the app.
 *
 * Nothing here invents qualifications, achievements or personal details about the developer.
 */
object DeveloperProfile {

    private const val STORE = "jarvis_developer_profile"
    private const val KEY_PRODUCT = "product_name"
    private const val KEY_NAME = "developer_name"
    private const val KEY_HANDLE = "developer_handle"
    private const val KEY_PORTFOLIO = "developer_portfolio"
    private const val KEY_GITHUB = "developer_github"

    data class Profile(
        val product: String,
        val name: String,
        val handle: String,
        val portfolio: String,
        val github: String
    )

    val defaults = Profile(
        product = "Jarvis",
        name = "Dev Ayan",
        handle = "arian00001",
        portfolio = "https://arian00001.site",
        github = "https://github.com/arian00001"
    )

    fun load(context: Context): Profile {
        val prefs = context.applicationContext
            .getSharedPreferences(STORE, Context.MODE_PRIVATE)
        return Profile(
            product = prefs.getString(KEY_PRODUCT, null).orFallback(defaults.product),
            name = prefs.getString(KEY_NAME, null).orFallback(defaults.name),
            handle = prefs.getString(KEY_HANDLE, null).orFallback(defaults.handle),
            portfolio = prefs.getString(KEY_PORTFOLIO, null).orFallback(defaults.portfolio),
            github = prefs.getString(KEY_GITHUB, null).orFallback(defaults.github)
        )
    }

    fun load(): Profile = load(appContext())

    fun save(context: Context, profile: Profile) {
        context.applicationContext
            .getSharedPreferences(STORE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PRODUCT, profile.product.trim())
            .putString(KEY_NAME, profile.name.trim())
            .putString(KEY_HANDLE, profile.handle.trim())
            .putString(KEY_PORTFOLIO, profile.portfolio.trim())
            .putString(KEY_GITHUB, profile.github.trim())
            .apply()
    }

    fun reset(context: Context) {
        context.applicationContext
            .getSharedPreferences(STORE, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    /** Identity lines appended to every system prompt (voice, HTTPS chat and offline routing). */
    fun identityBlock(): String {
        val p = load()
        return buildString {
            appendLine("- You are " + p.product + ", an AI personal assistant developed by " + p.name + " (handle " + p.handle + ").")
            appendLine("- Asked who you are or who your developer is, answer only from these lines: developer " + p.name + ", portfolio " + p.portfolio + ", GitHub " + p.github + ".")
            appendLine("- Never invent qualifications, personal information or achievements about your developer.")
        }.trimEnd()
    }

    private fun appContext(): Context = com.jarvis.assistant.JarvisApp.instance.applicationContext

    private fun String?.orFallback(fallback: String): String {
        val value = this?.trim()
        return if (value.isNullOrEmpty()) fallback else value
    }
}
