package com.jarvis.assistant.lock

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent

/**
 * Recovery uses Android's own device credential. Jarvis never stores a PIN, password
 * or recovery answer, and cannot reset the phone's screen lock.
 */
object JarvisRecovery {
    fun isDeviceSecure(context: Context): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    fun confirmIntent(context: Context, title: String, subtitle: String): Intent? =
        context.getSystemService(KeyguardManager::class.java)
            ?.createConfirmDeviceCredentialIntent(title, subtitle)
}