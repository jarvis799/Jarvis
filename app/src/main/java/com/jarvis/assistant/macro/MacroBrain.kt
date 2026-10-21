package com.jarvis.assistant.macro

import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.network.EndpointConfig
import com.jarvis.assistant.network.HttpChatClient
import org.json.JSONObject
import java.util.UUID

/**
 * DEV STYLE - the Brain of the Macro Center.
 * Takes a natural-language request ("whatsapp e message asle reply diye de")
 * and turns it into a runnable macro - through the user's AI brain when an
 * endpoint is configured, and through a built-in rule parser otherwise.
 */
object MacroBrain {

    private val APP_PACKAGES = linkedMapOf(
        "whatsapp" to listOf("com.whatsapp", "com.whatsapp.w4b"),
        "spotify" to listOf("com.spotify.music"),
        "youtube" to listOf("com.google.android.youtube"),
        "instagram" to listOf("com.instagram.android"),
        "telegram" to listOf("org.telegram.messenger"),
        "messenger" to listOf("com.facebook.orca"),
        "facebook" to listOf("com.facebook.katana"),
        "gmail" to listOf("com.google.android.gm")
    )

    /** "automation list dekhao" / "macro center open koro" -> open the Macro Center screen. */
    fun isListRequest(clean: String): Boolean {
        val c = clean.trim().lowercase()
        if (c.contains("macro center") || c.contains("automation center")) return true
        val words = listOf("list", "dekhao", "dekhai", "kholo", "open", "show", "dikhao", "gulo")
        return ((c.contains("automation") || c.contains("macro")) && words.any { c.contains(it) } && !isCreateIntent(c))
    }

    /** Returns the automation request when the sentence asks Jarvis to automate something. */
    fun extractCreationPrompt(clean: String): String? {
        val c = clean.trim().lowercase()
        val mentions = c.contains("automation") || c.contains("automate") || c.contains("macro") || c.contains("automatic")
        if (mentions && isCreateIntent(c)) return clean.trim()
        val eventHappens = listOf("asle", "ashle", "ele", "ashen", "ashche", "jokhon", "jakhon").any { c.contains(it) } ||
            c.contains("when a message") || c.contains("when i get a message") || c.contains("message arrives")
        val hasAction = listOf("reply", "uttor", "notify", "pathabe", "pathiye", "bajabe", "play", "khule").any { c.contains(it) }
        val mentionsApp = c.contains("whatsapp") || c.contains("message") || c.contains("msg") ||
            c.contains("notification") || c.contains("noti") || APP_PACKAGES.keys.any { c.contains(it) }
        return if (eventHappens && hasAction && mentionsApp) clean.trim() else null
    }

    private fun isCreateIntent(c: String): Boolean =
        listOf("banaw", "banao", "bana ", "banate", "banabo", "banaben", "create", "set koro", "set korun", "kore dao", "kore de", "chiye").any { c.contains(it) }

    /** Builds the macro for a natural-language request and saves it. Returns a user-facing summary. */
    suspend fun createFromPrompt(prompt: String): String {
        val app = JarvisApp.instance
        val existing = MacroStore.list(app).firstOrNull { it.prompt.equals(prompt.trim(), ignoreCase = true) }
        if (existing != null)
            return "Eta already banano ache, Sir - " + existing.name + ". Macro Center e on/off korte parben."
        val macro = buildViaBrain(app, prompt) ?: buildViaRules(prompt)
        if (macro == null) {
            return "Ektu clearly bolen, Sir - kon app e kiko hobar por Jarvis ki korbe? " +
                "Example: Jarvis, WhatsApp e message asle deep-think kore reply diye de - " +
                "ba: Jarvis, ekta automation banao - Spotify khulle amar playlist cholabe."
        }
        MacroStore.save(app, macro)
        return "Automation banano hoyeche, Sir!\n\n" + macro.name + "\n" +
            "Trigger: " + macro.trigger.describe() + "\n" +
            macro.actions.joinToString("\n") { "- " + it.describe() } + "\n\n" +
            "Ekhon theke background e cholbe. Settings > Macro Center theke on/off, run now ar history control korte parben."
    }

    /** Asks the configured AI endpoint to design the macro as strict JSON. */
    private suspend fun buildViaBrain(app: JarvisApp, prompt: String): Macro? {
        val prefs = app.preferences
        val endpoint = EndpointConfig.httpEndpoint(prefs.apiKey, prefs.customEndpoint) ?: return null
        val schema =
            "You convert automation requests into strict JSON for an Android macro engine. Allowed values:\n" +
            "trigger: {\"type\":\"notification\",\"packages\":[\"com.whatsapp\"],\"keyword\":\"optional text\"} or {\"type\":\"manual\"}\n" +
            "actions (array, at least one):\n" +
            "{\"type\":\"brain_reply\",\"style\":\"deep\"} - AI thinks about the message and replies via the notification quick-reply\n" +
            "{\"type\":\"ui_task\",\"goal\":\"short description\",\"steps\":[{\"type\":\"tap|type|enter|back|home|wait\",\"value\":\"label to tap, text to type, or milliseconds\"}]}\n" +
            "{\"type\":\"open_app\",\"package\":\"com.spotify.music\"}\n" +
            "{\"type\":\"notify\",\"text\":\"message to show the user\"}\n" +
            "Known packages: WhatsApp com.whatsapp, Spotify com.spotify.music, YouTube com.google.android.youtube, Instagram com.instagram.android, Telegram org.telegram.messenger, Messenger com.facebook.orca, Gmail com.google.android.gm, Facebook com.facebook.katana.\n" +
            "Steps run in order on the target app screen - tap opens buttons/menus, type fills the app text field, enter submits.\n" +
            "Reply ONLY with the JSON object, no other text.\n" +
            "Request: " + prompt.take(1200)
        val raw = runCatching { HttpChatClient().ask(endpoint, schema, prefs.apiKey, prefs.aiModel) }.getOrNull() ?: return null
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val json = runCatching { JSONObject(raw.substring(start, end + 1)) }.getOrNull() ?: return null
        if (json.optString("id").isBlank()) json.put("id", UUID.randomUUID().toString())
        if (json.optString("name").isBlank()) json.put("name", prompt.take(40))
        json.put("prompt", prompt)
        return runCatching { MacroCodec.fromJson(json) }.getOrNull()
    }

    /** Offline fallback: built-in rules for the most common automation shapes. */
    private fun buildViaRules(prompt: String): Macro? {
        val c = prompt.lowercase()
        val appEntry = APP_PACKAGES.entries.firstOrNull { c.contains(it.key) }
        val eventHappens = listOf("asle", "ashle", "ele", "ashen", "ashche", "jokhon", "jakhon").any { c.contains(it) } ||
            c.contains("when a message") || c.contains("message arrives")
        val wantsReply = c.contains("reply") || c.contains("uttor")

        if (wantsReply && (eventHappens || appEntry?.key == "whatsapp" || c.contains("whatsapp"))) {
            val packages = (appEntry?.value ?: APP_PACKAGES["whatsapp"]!!).toSet()
            return Macro(
                id = UUID.randomUUID().toString(),
                name = (appEntry?.key ?: "whatsapp").replaceFirstChar { it.uppercase() } + " Smart Auto-Reply",
                prompt = prompt,
                trigger = MacroTrigger.NotificationReceived(packages),
                actions = listOf(MacroAction.BrainReply("deep"))
            )
        }

        if (appEntry?.key == "spotify" && listOf("play", "bajao", "bajabe", "chalu", "gan").any { c.contains(it) }) {
            val song = Regex("(?:play|bajao|bajabe|chalu koro|chalu korun)\\s+(.+)").find(c)?.groupValues?.get(1)?.trim() ?: ""
            return Macro(
                id = UUID.randomUUID().toString(),
                name = "Spotify Auto Play",
                prompt = prompt,
                trigger = MacroTrigger.Manual,
                actions = listOf(MacroAction.UiTask("spotify play " + song))
            )
        }

        if (eventHappens && listOf("notify", "bujaben", "bolben", "alert", "janiye").any { c.contains(it) }) {
            val packages = appEntry?.value?.toSet() ?: emptySet()
            return Macro(
                id = UUID.randomUUID().toString(),
                name = (appEntry?.key ?: "Notification") + " Alert",
                prompt = prompt,
                trigger = MacroTrigger.NotificationReceived(packages),
                actions = listOf(MacroAction.Notify("Jarvis automation fired - notification received."))
            )
        }
        return null
    }
}
