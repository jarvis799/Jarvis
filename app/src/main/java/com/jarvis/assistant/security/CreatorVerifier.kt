package com.jarvis.assistant.security

import com.jarvis.assistant.JarvisApp

/**
 * Local creator-greeting shortcut, not authentication or a privilege grant.
 *
 * The phrase is compared against the build-injected creator key
 * (BuildConfig.CREATOR_KEY) or the key the owner set on this device.
 * There is deliberately no master key in this file.
 */
object CreatorVerifier {

    private val prefs get() = JarvisApp.instance.preferences

    fun isConfigured(): Boolean = CreatorAuthenticationService.isConfigured()

    fun isCreatorPhrase(text: String): Boolean =
        CreatorAuthenticationService.matchesConfiguredKey(text)

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