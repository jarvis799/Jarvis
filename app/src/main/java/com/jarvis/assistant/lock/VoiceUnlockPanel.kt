package com.jarvis.assistant.lock

import android.content.Intent
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.jarvis.assistant.background.JarvisConversationService
import com.jarvis.assistant.security.UnlockManager

class VoiceUnlockPanel(private val activity: android.app.Activity, column: LinearLayout,
    withMic: (() -> Unit) -> Unit) {
    private val status: TextView = MonoUi.text(activity, "Settings and samples stay on this phone.")
    private var refreshing = false
    private lateinit var enabled: android.widget.Switch
    val editor: VoicePhraseEditor
    init {
        column.addView(MonoUi.text(activity, "Your phrase opens Android authentication. It does not verify who is speaking and cannot bypass PIN, pattern, password or biometrics."))
        enabled = MonoUi.toggle(activity, "Enable Voice Unlock", UnlockManager.isEnabled(activity)) { on ->
            if (!refreshing && on) withMic {
                UnlockManager.setEnabled(activity, true)
                ContextCompat.startForegroundService(activity, Intent(activity, JarvisConversationService::class.java).setAction(JarvisConversationService.ACTION_ENTER_STANDBY))
                status.text = "Background listener enabled. Keep Jarvis's microphone notification active."
            } else if (!refreshing) { UnlockManager.setEnabled(activity, false); status.text = "Voice Unlock disabled." }
        }
        column.addView(enabled); column.addView(status)
        column.addView(MonoUi.button(activity, "Microphone and notification permissions") { withMic { status.text = "Permissions granted." } })
        column.addView(MonoUi.button(activity, "Android app permissions") {
            activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${activity.packageName}")))
        })
        column.addView(MonoUi.text(activity, "Background phrases use Jarvis's existing on-device English recognizer; use words in its installed vocabulary. The foreground test uses your Android speech service and language packs. Android may pause background microphones; re-enable from this page if stopped."))
        editor = VoicePhraseEditor(activity, column, withMic) { status.text = it }
        column.addView(MonoUi.text(activity, "Backup authentication: always available through Android's original lock screen."))
        column.addView(MonoUi.button(activity, "Open Android security settings") { activity.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) })
        column.addView(MonoUi.button(activity, "Liquid Glass Lock Screen") {
            activity.startActivity(Intent(activity, GlassLockSettingsActivity::class.java))
        })
    }
    fun refresh() { refreshing = true; enabled.isChecked = UnlockManager.isEnabled(activity); refreshing = false }
    fun close() = editor.close()
}