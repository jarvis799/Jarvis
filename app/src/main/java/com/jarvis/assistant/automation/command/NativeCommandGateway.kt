package com.jarvis.assistant.automation.command

import android.content.Context
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.agent.CapabilityRouter
import com.jarvis.assistant.email.GmailStore
import com.jarvis.assistant.email.GmailVoiceCommands
import com.jarvis.assistant.subagent.SubAgentBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One execution path shared by typed chat, HTTPS voice and Live tool calls. */
object NativeCommandGateway {
    fun clean(text: String): String = text.trim().replace(
        Regex("^(?:(?:hey|hello|hi|ok|okay)\\s+)?(?:jarvis|জার্ভিস|जार्विस)[,،:!\\s]+", RegexOption.IGNORE_CASE), ""
    ).trim()

    fun canHandle(context: Context, input: String): Boolean {
        val text = clean(input)
        val parsed = CommandParser.parse(text, isAwaitingConfirmation = GmailVoiceCommands.draftPending())
        return com.jarvis.assistant.automation.flows.NativeExtras.matches(text) ||
            NativeScheduleRouter.matches(text) || SubAgentBuilder.wantsCreation(text) ||
            Regex("(?i)^(?:time (?:bolo|koto)|(?:ekhon )?(?:koyta|koita) baje|সময় বলো|কয়টা বাজে)\\??$").matches(text) ||
            Regex("(?i)^(?:reject|decline|end|hang up|cut) (?:the )?call$|^call (?:reject|decline|cut)(?: koro)?$").matches(text) ||
            com.jarvis.assistant.website.WebsiteControl.matches(text) ||
            (parsed.command != null && parsed.confidence >= .94f) || FastCommandRouter.lookup(context, text) != null ||
            Regex("(?i)^(?:gmail|email|mail) (?:connection )?status\\??$").matches(text)
    }

    suspend fun execute(context: Context, input: String): String? = withContext(Dispatchers.Main) {
        val text = clean(input)
        if (text.isBlank()) return@withContext null
        com.jarvis.assistant.automation.flows.NativeExtras.route(context, text)?.let { return@withContext it }
        NativeScheduleRouter.route(context, text)?.let { return@withContext it }
        NativeCallControl.route(context, text)?.let { return@withContext it }
        com.jarvis.assistant.website.WebsiteControl.route(context, text)?.let { return@withContext it }
        if (Regex("(?i)^(?:what(?:'s| is) the time|what time is it|time (?:bolo|koto)|(?:ekhon )?(?:koyta|koita) baje|সময় বলো|কয়টা বাজে)\\??$").matches(text)) {
            return@withContext FastCommandRouter.executeAction(JarvisApp.instance, "READ_TIME", text)
        }
        if (SubAgentBuilder.wantsCreation(text)) return@withContext SubAgentBuilder.create(context, text)
        if (Regex("(?i)^(?:(?:my|amar)\\s+)?(?:gmail|email|mail)\\s+(?:connection\\s+)?(?:status|connected|connect ache|connect hoyeche|connect hoyeche kina)\\??$").matches(text)) {
            return@withContext when {
                !GmailStore.isConnected(context) -> GmailVoiceCommands.notConnectedHint()
                GmailStore.connectionVerified(context) -> "Gmail account saved and connection verified. Auto reply: " + if (GmailStore.autoReplyEnabled(context)) "ON." else "OFF."
                else -> "Gmail credentials are saved, but the connection is not verified yet. Use Test Gmail; no need to enter the password again."
            }
        }
        val parsed = CommandParser.parse(text, isAwaitingConfirmation = GmailVoiceCommands.draftPending())
        val command = parsed.command
        if (command != null && parsed.confidence >= 0.94f && command !is JarvisCommand.DisambiguationChoice) {
            return@withContext JarvisApp.instance.commandExecutor.execute(command)
        }
        FastCommandRouter.lookup(context, text)?.let {
            return@withContext FastCommandRouter.executeAction(JarvisApp.instance, it, text)
        }
        CapabilityRouter.route(context, text)
    }
}