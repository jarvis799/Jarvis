package com.jarvis.assistant.subagent

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.jarvis.assistant.automation.whatsapp.WhatsAppAutoReplyManager
import com.jarvis.assistant.email.EmailWatchService
import com.jarvis.assistant.email.GmailStore

/**
 * DEV STYLE — reads the REAL state of every sub agent from its own store.
 * Nothing here reports a status that the underlying feature does not actually have.
 */
object SubAgentStateResolver {

    fun notificationAccessGranted(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun whatsappRules(context: Context) = WhatsAppAutoReplyManager.get(context).getRules()

    fun whatsappEnabled(context: Context): Boolean = WhatsAppAutoReplyManager.get(context).isEnabled()

    fun emailConnected(context: Context): Boolean = GmailStore.isConnected(context)

    fun emailEnabled(context: Context): Boolean = GmailStore.autoReplyEnabled(context)

    fun status(context: Context, agent: SubAgent): SubAgentStatus = when (agent.target) {
        SubAgentTarget.WHATSAPP_AUTO_REPLY -> {
            val manager = WhatsAppAutoReplyManager.get(context)
            val usableRules = manager.getRules().count { it.enabled && it.replyText.isNotBlank() }
            when {
                usableRules == 0 -> SubAgentStatus(SubAgentState.SETUP, "SETUP", "Add one reply rule")
                !notificationAccessGranted(context) ->
                    SubAgentStatus(SubAgentState.SETUP, "SETUP", "Notification access needed")
                manager.isEnabled() ->
                    SubAgentStatus(SubAgentState.RUNNING, "ACTIVE", "$usableRules rule(s) auto-replying")
                else -> SubAgentStatus(SubAgentState.PAUSED, "PAUSED", "Switch is off")
            }
        }

        SubAgentTarget.EMAIL_AUTO_REPLY -> {
            val connected = GmailStore.isConnected(context)
            when {
                !connected -> SubAgentStatus(SubAgentState.SETUP, "SETUP", "Add Gmail + App Password")
                GmailStore.autoReplyEnabled(context) ->
                    SubAgentStatus(
                        SubAgentState.RUNNING,
                        "ACTIVE",
                        GmailStore.email(context).ifBlank { "Auto replying" }
                    )
                else -> SubAgentStatus(SubAgentState.PAUSED, "PAUSED", "Switch is off")
            }
        }

        else -> SubAgentStatus.of(agent, autoEnabled = false, configured = true)
    }

    /** Flips the real switch behind an auto agent. Returns false when it is not configured yet. */
    fun setAutoRunning(context: Context, agent: SubAgent, running: Boolean): Boolean = when (agent.target) {
        SubAgentTarget.WHATSAPP_AUTO_REPLY -> {
            WhatsAppAutoReplyManager.get(context).setEnabled(running)
            true
        }

        SubAgentTarget.EMAIL_AUTO_REPLY -> {
            if (!emailConnected(context)) {
                false
            } else {
                GmailStore.setAutoReplyEnabled(context, running)
                if (running) EmailWatchService.start(context) else EmailWatchService.stop(context)
                true
            }
        }

        else -> false
    }
}