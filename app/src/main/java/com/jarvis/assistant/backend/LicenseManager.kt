package com.jarvis.assistant.backend

import android.content.Context

/**
 * DEV STYLE: the licence system is removed. Every feature is free and unlimited
 * for all users, so no key, no activation and no heartbeat is ever required.
 * The public API is kept unchanged so existing callers keep compiling.
 */
object LicenseManager {

    data class ActivationOutcome(val success: Boolean, val message: String, val status: String = "")

    fun registerOnLaunch(context: Context, ownerName: String = "DEV STYLE"): Boolean = true

    fun checkKey(licenseKey: String): com.jarvis.assistant.network.AhonaApi.KeyCheckResult = com.jarvis.assistant.network.AhonaApi.checkKey(licenseKey)

    fun activate(context: Context, licenseKey: String): ActivationOutcome =
        ActivationOutcome(true, "Everything is already unlocked for free.", "active")

    fun verify(context: Context): Boolean = true

    fun heartbeatOnce(context: Context, errors: List<com.jarvis.assistant.network.AhonaApi.ClientError> = emptyList()): com.jarvis.assistant.network.AhonaApi.HeartbeatResult? = null

    suspend fun runHeartbeatLoop(context: Context) {
        // Licence heartbeat removed - nothing to keep alive.
    }

    fun isPremium(context: Context): Boolean = true

}
