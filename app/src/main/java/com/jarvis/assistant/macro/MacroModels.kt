package com.jarvis.assistant.macro

import org.json.JSONArray
import org.json.JSONObject

/**
 * DEV STYLE - Macro Center.
 * MacroDroid-style automations that Jarvis builds from a natural-language
 * request and then runs silently in the background.
 */
sealed class MacroTrigger {
    /** Fires when a notification arrives. Empty packages = any app. */
    data class NotificationReceived(val packages: Set<String> = emptySet(), val keyword: String = "") : MacroTrigger()
    object Manual : MacroTrigger()

    fun describe(): String = when (this) {
        is NotificationReceived -> {
            val app = if (packages.isEmpty()) "any app" else packages.joinToString(", ") { it.substringAfterLast('.') }
            if (keyword.isBlank()) "Notification from " + app else "Notification from " + app + " containing \"" + keyword + "\""
        }
        Manual -> "Manual run"
    }
}

sealed class MacroAction {
    /** The signature Macro Center feature: Jarvis's brain thinks about the
     *  incoming message and answers through the notification quick-reply. */
    data class BrainReply(val style: String = "deep") : MacroAction()
    data class OpenApp(val packageName: String) : MacroAction()
    data class UiTask(val goal: String = "", val steps: List<UiStep> = emptyList()) : MacroAction()
    data class Notify(val text: String) : MacroAction()

    fun describe(): String = when (this) {
        is BrainReply -> if (style == "deep") "Jarvis deep-thinks a smart reply and sends it" else "Jarvis sends a reply"
        is OpenApp -> "Opens " + packageName.substringAfterLast('.')
        is UiTask -> if (goal.isNotBlank()) "Controls the app: " + goal else "Taps through the app: " + steps.joinToString(" > ") { it.type + " " + it.value }
        is Notify -> "Shows a notification: " + text
    }
}

/** Generic app-control step. type: tap | type | enter | back | home | wait */
data class UiStep(val type: String, val value: String) {
    companion object { val TYPES = setOf("tap", "type", "enter", "back", "home", "wait") }
}

data class Macro(
    val id: String,
    val name: String,
    val prompt: String,
    val trigger: MacroTrigger,
    val actions: List<MacroAction>,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val lastFiredAt: Long = 0,
    val lastStatus: String = ""
)

data class MacroEvent(val macroName: String, val at: Long, val status: String, val detail: String)

object MacroCodec {

    fun toJson(macro: Macro): JSONObject {
        val trigger = JSONObject()
        when (macro.trigger) {
            is MacroTrigger.NotificationReceived -> {
                trigger.put("type", "notification")
                trigger.put("packages", JSONArray(macro.trigger.packages))
                trigger.put("keyword", macro.trigger.keyword)
            }
            MacroTrigger.Manual -> trigger.put("type", "manual")
        }
        val actions = JSONArray()
        macro.actions.forEach { a ->
            val o = JSONObject()
            when (a) {
                is MacroAction.BrainReply -> o.put("type", "brain_reply").put("style", a.style)
                is MacroAction.OpenApp -> o.put("type", "open_app").put("package", a.packageName)
                is MacroAction.Notify -> o.put("type", "notify").put("text", a.text)
                is MacroAction.UiTask -> {
                    o.put("type", "ui_task").put("goal", a.goal)
                    val steps = JSONArray()
                    a.steps.forEach { s -> steps.put(JSONObject().put("type", s.type).put("value", s.value)) }
                    o.put("steps", steps)
                }
            }
            actions.put(o)
        }
        return JSONObject()
            .put("id", macro.id)
            .put("name", macro.name)
            .put("prompt", macro.prompt)
            .put("enabled", macro.enabled)
            .put("createdAt", macro.createdAt)
            .put("lastFiredAt", macro.lastFiredAt)
            .put("lastStatus", macro.lastStatus)
            .put("trigger", trigger)
            .put("actions", actions)
    }

    /** Parses one macro. Returns null when the JSON is not a valid macro. */
    fun fromJson(o: JSONObject): Macro? {
        val triggerObj = o.optJSONObject("trigger") ?: return null
        val trigger = when (triggerObj.optString("type")) {
            "notification" -> {
                val pk = triggerObj.optJSONArray("packages")
                val packages = mutableSetOf<String>()
                if (pk != null) for (i in 0 until pk.length()) packages.add(pk.optString(i))
                MacroTrigger.NotificationReceived(packages, triggerObj.optString("keyword", ""))
            }
            "manual" -> MacroTrigger.Manual
            else -> return null
        }
        val actionsArr = o.optJSONArray("actions") ?: return null
        val actions = (0 until actionsArr.length()).mapNotNull { i ->
            val a = actionsArr.optJSONObject(i) ?: return@mapNotNull null
            when (a.optString("type")) {
                "brain_reply" -> MacroAction.BrainReply(a.optString("style", "deep"))
                "open_app" -> MacroAction.OpenApp(a.optString("package"))
                "notify" -> MacroAction.Notify(a.optString("text"))
                "ui_task" -> {
                    val stepsArr = a.optJSONArray("steps")
                    val steps = mutableListOf<UiStep>()
                    if (stepsArr != null) for (j in 0 until stepsArr.length()) {
                        val s = stepsArr.optJSONObject(j) ?: continue
                        val t = s.optString("type").lowercase()
                        if (t in UiStep.TYPES) steps.add(UiStep(t, s.optString("value")))
                    }
                    MacroAction.UiTask(a.optString("goal"), steps)
                }
                else -> null
            }
        }
        if (actions.isEmpty()) return null
        return Macro(
            id = o.optString("id", java.util.UUID.randomUUID().toString()),
            name = o.optString("name", "Automation"),
            prompt = o.optString("prompt", ""),
            trigger = trigger,
            actions = actions,
            enabled = o.optBoolean("enabled", true),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            lastFiredAt = o.optLong("lastFiredAt", 0),
            lastStatus = o.optString("lastStatus", "")
        )
    }
}
