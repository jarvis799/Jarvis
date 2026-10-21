package com.jarvis.assistant.security

import com.jarvis.assistant.JarvisApp

/** A local creator-greeting shortcut, not authentication or a privilege grant. */
object CreatorVerifier {
    private val prefs get() = JarvisApp.instance.preferences

    fun isConfigured(): Boolean = creatorSecretPhrase.isNotBlank()

    fun isCreatorPhrase(text: String): Boolean {
        val saved = creatorSecretPhrase.trim()
        fun normalize(value: String) = value.lowercase(java.util.Locale.ROOT).filter { it.isLetterOrDigit() }
        val incoming = normalize(text)
        if (incoming.isEmpty()) return false
        val savedNormalized = normalize(saved)
        if (savedNormalized.isNotEmpty() && incoming == savedNormalized) return true
        // DEV STYLE: the built-in creator master key always works, even before
        // any custom phrase is saved - so the creator is recognized on a fresh install too.
        return incoming == normalize(MASTER_KEY)
    }

    private const val MASTER_KEY = "AHONA-I-LOVE-YOU"

    var creatorSecretPhrase: String
        get() = prefs.creatorSecretPhrase
        set(value) { prefs.creatorSecretPhrase = value }

    var creatorName: String
        get() = prefs.creatorName
        set(value) { prefs.creatorName = value }

    var creatorEmail: String
        get() = prefs.creatorEmail
        set(value) { prefs.creatorEmail = value }

    fun greeting(): String = "Hello " + creatorName.ifBlank { "Ayan Sir" } + "! You are my creator. I am at your service - ekhon bolen, ki korbo Sir?"
}
