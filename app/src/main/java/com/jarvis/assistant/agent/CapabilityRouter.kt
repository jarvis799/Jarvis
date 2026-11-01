package com.jarvis.assistant.agent

import android.content.Context
import android.content.Intent
import com.jarvis.assistant.agent.tools.ToolOrchestrator

/**
 * DEV STYLE 2.0.5 — local capability router.
 *
 * Jarvis used to answer "amar sub agent bananor tool nai" because a request only
 * reached a tool when its exact name appeared in the text. This router resolves a
 * plain request to the real subsystem that performs it, so a capability the app
 * owns is never reported as missing.
 */
object CapabilityRouter {

    private val openVerb = Regex(
        "\\b(open|kholo|khulo|khulun|launch|start|show|dekhao|dekhun|chalu|chalao|niye jao|jao)\\b",
        RegexOption.IGNORE_CASE
    )
    private val questionOnly = Regex(
        "\\b(kivabe|kemon kore|how|what|why|keno|ki ki|koto|kothay)\\b",
        RegexOption.IGNORE_CASE
    )
    private val toolsQuestion = Regex(
        "\\b(kon kon tool|koto gulo tool|tool list|tomar tool|your tools|which tool)\\b|\\btools?\\b.*\\b(nai|nei|ache|have|use)\\b",
        RegexOption.IGNORE_CASE
    )
    private val pluginIntent = Regex("\\b(plugin|skill)\\b", RegexOption.IGNORE_CASE)
    private val pluginAction = Regex("\\b(install|add|new|lab|store|banao)\\b", RegexOption.IGNORE_CASE)
    private val modelIntent = Regex("\\b(local model|offline model|gguf|hugging ?face)\\b", RegexOption.IGNORE_CASE)
    private val modelAction = Regex("\\b(download|install|search|add|nao|koro)\\b", RegexOption.IGNORE_CASE)
    private val backupIntent = Regex("\\b(backup|restore)\\b", RegexOption.IGNORE_CASE)
    private val backupAction = Regex("\\b(nao|naw|koro|create|save|file|korbo)\\b", RegexOption.IGNORE_CASE)

    private data class Screen(val key: String, val pattern: Regex, val target: Class<*>)

    private val screens: List<Screen> = listOf(
        Screen("scanner", Regex("\\b(scanner|scan|qr|barcode)\\b", RegexOption.IGNORE_CASE), com.jarvis.assistant.scanner.ScannerActivity::class.java),
        Screen("sub agents", Regex("\\b(sub[ -]?agent|subagent|agent list|agent showcase)\\b", RegexOption.IGNORE_CASE), com.jarvis.assistant.subagent.SubAgentActivity::class.java),
        Screen("memories", Regex("\\b(memory|memories)\\b", RegexOption.IGNORE_CASE), com.jarvis.assistant.ui.memory.MemoryActivity::class.java),
        Screen("photo memory", Regex("\\b(photo memory|photo memories)\\b", RegexOption.IGNORE_CASE), com.jarvis.assistant.photomemory.PhotoMemoryActivity::class.java),
        Screen("plugin lab", pluginIntent, com.jarvis.assistant.plugins.PluginStoreActivity::class.java),
        Screen("terminal", Regex("\\b(terminal|console|shell)\\b", RegexOption.IGNORE_CASE), com.jarvis.assistant.terminal.TerminalActivity::class.java),
        Screen("local models", modelIntent, com.jarvis.assistant.model.ModelCatalogueActivity::class.java),
        Screen("backup", backupIntent, com.jarvis.assistant.backup.BackupActivity::class.java),
        Screen("notification briefing", Regex("\\b(notification briefing|briefing|notification summary)\\b", RegexOption.IGNORE_CASE), com.jarvis.assistant.notifications.NotificationBriefingActivity::class.java),
        Screen("cyber lab", Regex("\\b(cyber ?lab|security lab)\\b", RegexOption.IGNORE_CASE), com.jarvis.assistant.security.cyberlab.CyberLabActivity::class.java),
        Screen("browser agent", Regex("\\b(browser agent)\\b", RegexOption.IGNORE_CASE), com.jarvis.assistant.browser.BrowserAgentActivity::class.java),
        Screen("time machine", Regex("\\b(time ?machine)\\b", RegexOption.IGNORE_CASE), com.jarvis.assistant.timemachine.TimeMachineActivity::class.java),
        Screen("debate", Regex("\\bdebate\\b", RegexOption.IGNORE_CASE), com.jarvis.assistant.debate.DebateActivity::class.java),
        Screen("prompt lab", Regex("\\bprompt lab\\b", RegexOption.IGNORE_CASE), com.jarvis.assistant.promptlab.PromptLabActivity::class.java),
        Screen("apk agent", Regex("\\bapk agent\\b", RegexOption.IGNORE_CASE), com.jarvis.assistant.apkagent.ApkAgentActivity::class.java),
        Screen("background remover", Regex("\\b(background remover|bg remover)\\b", RegexOption.IGNORE_CASE), com.jarvis.assistant.bgremove.BackgroundRemovalActivity::class.java),
        Screen("music player", Regex("\\b(music player|media library|my music)\\b", RegexOption.IGNORE_CASE), com.jarvis.assistant.media.MusicPlayerActivity::class.java),
        Screen("screen tools", Regex("\\b(screen tools|screen share setup)\\b", RegexOption.IGNORE_CASE), com.jarvis.assistant.vision.ScreenToolsActivity::class.java),
        Screen("dev studio", Regex("\\b(dev studio|developer studio|project studio)\\b", RegexOption.IGNORE_CASE), com.jarvis.assistant.ui.devstudio.DevStudioActivity::class.java),
        Screen("voice studio", Regex("\\b(voice studio|voice settings|tts)\\b", RegexOption.IGNORE_CASE), com.jarvis.assistant.ui.settings.VoiceStudioActivity::class.java),
        Screen("persona", Regex("\\b(persona|personality)\\b", RegexOption.IGNORE_CASE), com.jarvis.assistant.personality.PersonalitySettingsActivity::class.java),
        Screen("privacy shield", Regex("\\bprivacy shield\\b", RegexOption.IGNORE_CASE), com.jarvis.assistant.ui.settings.PrivacyShieldActivity::class.java),
        Screen("hands free", Regex("\\b(hands free|handsfree)\\b", RegexOption.IGNORE_CASE), com.jarvis.assistant.ui.settings.HandsFreeActivity::class.java),
        Screen("settings", Regex("\\b(settings|control centre|control center)\\b", RegexOption.IGNORE_CASE), com.jarvis.assistant.ui.settings.SettingsActivity::class.java)
    )

    private sealed class Plan {
        object CreateAgent : Plan()
        object Tools : Plan()
        data class Open(val screen: Screen) : Plan()
    }

    private fun resolve(request: String): Plan? {
        if (request.isBlank()) return null
        val text = request.lowercase()

        if (com.jarvis.assistant.subagent.SubAgentBuilder.wantsCreation(request)) return Plan.CreateAgent
        if (toolsQuestion.containsMatchIn(text)) return Plan.Tools

        // Screens asked for by intent, without an "open" verb.
        if (pluginIntent.containsMatchIn(text) && pluginAction.containsMatchIn(text)) {
            return Plan.Open(screens.first { it.key == "plugin lab" })
        }
        if (modelIntent.containsMatchIn(text) && modelAction.containsMatchIn(text)) {
            return Plan.Open(screens.first { it.key == "local models" })
        }
        if (backupIntent.containsMatchIn(text) && backupAction.containsMatchIn(text)) {
            return Plan.Open(screens.first { it.key == "backup" })
        }

        if (!openVerb.containsMatchIn(text) || questionOnly.containsMatchIn(text)) return null
        val screen = screens.firstOrNull { it.pattern.containsMatchIn(text) } ?: return null
        return Plan.Open(screen)
    }

    /** True when this request can be performed locally - used by the instant command pack. */
    fun matches(request: String): Boolean = resolve(request) != null

    /** Returns a reply when the request was handled locally, or null to continue to the model. */
    fun route(context: Context, request: String): String? = when (val plan = resolve(request)) {
        is Plan.CreateAgent -> com.jarvis.assistant.subagent.SubAgentBuilder.create(context, request)
        is Plan.Tools -> toolsAnswer()
        is Plan.Open -> open(context, plan.screen)
        null -> null
    }

    private fun open(context: Context, screen: Screen): String {
        val opened = runCatching {
            context.startActivity(
                Intent(context, screen.target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        }.getOrDefault(false)
        return if (opened) screen.key + " kholche, Sir." else screen.key + " khola gelo na, Sir."
    }

    private fun toolsAnswer(): String = buildString {
        val tools = ToolOrchestrator.availableTools()
        append("Amar sob tool, Sir (").append(tools.size).append("):\n")
        tools.forEach { append("\u2022 ").append(it.name.replace('_', ' ')).append(" \u2014 ").append(it.description).append("\n") }
        append("Echara local action: sub agent banano, plugin install, local model download, backup, memory, scanner ar app er protita screen kholar kaj ami nijei kori.")
    }
}