package com.jarvis.assistant.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Keeps the "Hey Jarvis" wake word alive after the phone restarts or the app is
 * updated. Without this the conversation service was never running again, so the
 * wake word could not work at all until the app was opened by hand.
 */
class WakeBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON" &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return
        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, JarvisConversationService::class.java)
                    .setAction(JarvisConversationService.ACTION_ENTER_STANDBY)
            )
        } catch (_: Exception) {
            // Microphone start may be refused while the device is booting; the next
            // app launch brings the listener back.
        }
    }
}