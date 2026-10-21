package com.jarvis.assistant.devstudio

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Keystore-backed encrypted storage for GitHub and Vercel credentials.
 * Tokens live only here: never in generated code, ZIP files, logs or AI prompts.
 */
class DevStudioPreferences(context: Context) {

    companion object {
        private const val FILE = "jarvis_devstudio_secure"
        private const val K_GH_USER = "gh_user"
        private const val K_GH_TOKEN = "gh_token"
        private const val K_GH_VISIBILITY = "gh_visibility"
        private const val K_GH_REPO = "gh_repo"
        private const val K_VERCEL_TOKEN = "vercel_token"
        private const val K_VERCEL_PROJECT = "vercel_project"
        private const val K_BROWSER_ENABLED = "browser_automation_enabled"
    }

    private val secure: SharedPreferences? = try {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        null
    }

    val storageAvailable: Boolean get() = secure != null

    private fun write(block: (SharedPreferences.Editor) -> Unit) {
        val s = secure ?: throw IllegalStateException("Encrypted storage is unavailable on this device, so credentials cannot be saved.")
        check(s.edit().also(block).commit()) { "Could not save the credential to encrypted storage." }
    }

    var githubUsername: String
        get() = secure?.getString(K_GH_USER, "").orEmpty()
        set(value) = write { it.putString(K_GH_USER, value.trim()) }

    var githubToken: String
        get() = secure?.getString(K_GH_TOKEN, "").orEmpty()
        set(value) = write { it.putString(K_GH_TOKEN, value.trim()) }

    var githubVisibility: String
        get() = secure?.getString(K_GH_VISIBILITY, "private").orEmpty().ifBlank { "private" }
        set(value) = write { it.putString(K_GH_VISIBILITY, if (value == "public") "public" else "private") }

    var githubRepo: String
        get() = secure?.getString(K_GH_REPO, "").orEmpty()
        set(value) = write { it.putString(K_GH_REPO, value.trim()) }

    var vercelToken: String
        get() = secure?.getString(K_VERCEL_TOKEN, "").orEmpty()
        set(value) = write { it.putString(K_VERCEL_TOKEN, value.trim()) }

    var vercelProject: String
        get() = secure?.getString(K_VERCEL_PROJECT, "").orEmpty()
        set(value) = write { it.putString(K_VERCEL_PROJECT, value.trim()) }

    var browserAutomationEnabled: Boolean
        get() = secure?.getBoolean(K_BROWSER_ENABLED, false) ?: false
        set(value) = write { it.putBoolean(K_BROWSER_ENABLED, value) }

    val hasGithub: Boolean get() = githubToken.isNotBlank() && githubUsername.isNotBlank()
    val hasVercel: Boolean get() = vercelToken.isNotBlank()

    /** Values that must never appear inside generated source code. */
    val secrets: List<String> get() = listOf(githubToken, vercelToken).filter { it.isNotBlank() }

    fun disconnectGithub() = write { it.remove(K_GH_USER); it.remove(K_GH_TOKEN); it.remove(K_GH_REPO) }

    fun disconnectVercel() = write { it.remove(K_VERCEL_TOKEN); it.remove(K_VERCEL_PROJECT) }
}
