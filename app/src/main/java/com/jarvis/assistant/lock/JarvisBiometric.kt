package com.jarvis.assistant.lock

import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat

/**
 * Real Android authentication only: BiometricPrompt with the device credential,
 * or Android's own confirm-credential screen when no biometric is enrolled.
 * Nothing here reports success unless Android itself confirmed the user.
 */
class JarvisBiometric(
    private val activity: AppCompatActivity,
    private val result: (Boolean, String) -> Unit
) {

    fun authenticate() {
        val manager = BiometricManager.from(activity)
        val allowed = if (Build.VERSION.SDK_INT >= 30) {
            BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else 0
        val ready = if (Build.VERSION.SDK_INT >= 30) {
            manager.canAuthenticate(allowed) == BiometricManager.BIOMETRIC_SUCCESS
        } else {
            manager.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
        }
        if (!ready) {
            deviceCredential()
            return
        }

        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(authResult: BiometricPrompt.AuthenticationResult) {
                    result(true, "Confirmed by Android.")
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    result(false, errString.toString())
                }

                override fun onAuthenticationFailed() {
                    result(false, "Not recognised. Try again or use your PIN.")
                }
            }
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Jarvis")
            .setSubtitle("Android security stays in control")
            .apply {
                if (Build.VERSION.SDK_INT >= 30) {
                    setAllowedAuthenticators(allowed)
                } else {
                    // BiometricPrompt rejects a negative button together with the device credential.
                    setDeviceCredentialAllowed(true)
                }
            }
            .build()

        try {
            prompt.authenticate(info)
        } catch (error: Exception) {
            deviceCredential()
        }
    }

    /** Android's own confirm-credential screen; the activity receives [REQUEST_CREDENTIAL]. */
    private fun deviceCredential() {
        val intent = JarvisRecovery.confirmIntent(activity, "Unlock Jarvis", "Confirm your Android screen lock")
        if (intent == null) {
            result(
                false,
                if (JarvisRecovery.isDeviceSecure(activity))
                    "Android did not open its lock screen. Unlock from the Jarvis notification."
                else "No Android screen lock is set. Add a PIN, pattern or password in Android Settings."
            )
            return
        }
        activity.startActivityForResult(intent, REQUEST_CREDENTIAL)
    }

    companion object {
        const val REQUEST_CREDENTIAL = 1805
    }
}