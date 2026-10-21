package com.jarvis.assistant.macro

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.automation.accessibility.JarvisAccessibilityService
import com.jarvis.assistant.network.EndpointConfig
import com.jarvis.assistant.network.HttpChatClient
import com.jarvis.assistant.vision.VisualTaskOrchestrator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * DEV STYLE - Macro Center engine.
 * Watches notifications, matches them against the user's macros and runs the
 * actions in the background. The Brain Reply action asks Jarvis's chat brain
 * to think about the message and answers through the app's own quick-reply
 * action, so WhatsApp never even has to open.
 */
class MacroEngine private constructor(private val context: Context) {

    companion object {
        private const val COOLDOWN_MS = 15_000L
        private const val CHANNEL_ID = "macro_center"
        @Volatile private var instance: MacroEngine? = null
        fun get(context: Context): MacroEngine = instance ?: synchronized(this) {
            instance ?: MacroEngine(context.applicationContext).also { instance = it }
        }
    }

    data class NotificationContext(
        val packageName: String,
        val title: String,
        val text: String,
        val replyAction: Notification.Action?
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cooldowns = mutableMapOf<String, Long>()
    private val lastReplies = mutableMapOf<String, String>()

    /**
     * Entry point for the notification listener. Runs synchronously so the
     * caller knows a macro claimed this notification. Returns true when a
     * macro matched and took over.
     */
    fun onNotification(packageName: String, title: String, text: String, replyAction: Notification.Action?): Boolean {
        val msg = text.trim()
        if (msg.isEmpty()) return false
        val now = System.currentTimeMillis()
        val candidates = MacroStore.list(context).filter { m ->
            m.enabled && m.trigger is MacroTrigger.NotificationReceived &&
                (m.trigger.packages.isEmpty() || m.trigger.packages.contains(packageName)) &&
                (m.trigger.keyword.isBlank() || (title + " " + msg).contains(m.trigger.keyword, ignoreCase = true))
        }
        val macro = candidates.firstOrNull { m -> (cooldowns[m.id + ":" + packageName] ?: 0L) + COOLDOWN_MS <= now }
            ?: return false
        cooldowns[macro.id + ":" + packageName] = now
        scope.launch { execute(macro, NotificationContext(packageName, title.trim(), msg, replyAction)) }
        return true
    }

    /** Manual "Run now" from the Macro Center screen. */
    fun runByName(name: String): Boolean {
        val macro = MacroStore.list(context).firstOrNull { it.name.contains(name, ignoreCase = true) } ?: return false
        scope.launch { execute(macro, null) }
        return true
    }

    private suspend fun execute(macro: Macro, event: NotificationContext?) {
        var status = "OK"
        for (action in macro.actions) {
            val result = runCatching { runAction(action, event) }
                .getOrElse { "Error: " + (it.message ?: "unknown") }
            if (result != "OK" && status == "OK") status = result
        }
        MacroStore.updateResult(context, macro.id, status)
        MacroStore.addEvent(context, MacroEvent(
            macro.name, System.currentTimeMillis(), status,
            event?.let { it.title + " - " + it.text } ?: ""
        ))
    }

    private suspend fun runAction(action: MacroAction, event: NotificationContext?): String = when (action) {
        is MacroAction.BrainReply -> event?.let { brainReply(action, it) } ?: "manual run: no notification to reply to"
        is MacroAction.OpenApp -> openApp(action.packageName)
        is MacroAction.UiTask -> runUiTask(action)
        is MacroAction.Notify -> showNotify(action.text)
    }

    /**
     * The signature Macro Center feature - Jarvis thinks about the message
     * and answers through the notification's own reply action.
     */
    private suspend fun brainReply(action: MacroAction.BrainReply, event: NotificationContext): String {
        val ra = event.replyAction ?: return "this notification has no quick-reply action"
        if (ra.remoteInputs.isNullOrEmpty()) return "this notification has no reply input"
        val prefs = JarvisApp.instance.preferences
        val endpoint = EndpointConfig.httpEndpoint(prefs.apiKey, prefs.customEndpoint)
            ?: return "no AI endpoint configured - Settings > API Connection e endpoint din"
        val deep = if (action.style == "deep") "Think carefully about the intent, tone and context of the message. " else ""
        val prompt = deep +
            "You are Jarvis, the personal AI assistant of the phone's owner, replying to this chat message for him. " +
            "Sender: \"" + event.title + "\". Message: \"" + event.text + "\". " +
            "Write ONE natural reply (max 40 words) in the same language as the message (Bangla/Banglish/Hindi/English). " +
            "Sound human, warm and specific. Never mention you are an AI or an automation. Output only the reply text."
        val reply = HttpChatClient().ask(endpoint, prompt, prefs.apiKey, prefs.aiModel)
            .trim().removeSurrounding("\"").take(500)
        if (reply.isBlank()) return "brain returned an empty reply"
        val sender = event.title.lowercase().trim()
        if (lastReplies[sender] == reply) return "loop guard - same reply as last time for " + event.title
        val intent = Intent()
        val results = Bundle()
        results.putCharSequence(ra.remoteInputs[0].resultKey, reply)
        RemoteInput.addResultsToIntent(ra.remoteInputs, intent, results)
        ra.actionIntent?.send(context, 0, intent)
        lastReplies[sender] = reply
        return "OK"
    }

    /**
     * Runs a UI task against any app - the brain's tap/type/enter/back/home/
     * wait steps, executed through the Accessibility tree. This is what makes
     * automation work for every app, not only Spotify.
     */
    private suspend fun runUiTask(action: MacroAction.UiTask): String {
        if (action.steps.isEmpty() && action.goal.startsWith("spotify play ", ignoreCase = true)) {
            val song = action.goal.substringAfter("play").trim()
            val failed = openApp("com.spotify.music")
            if (failed != "OK") return failed
            delay(4000)
            val result = VisualTaskOrchestrator.getInstance().runSpotifySmartSearch(song)
            return if (result.success) "OK" else result.message
        }
        if (action.steps.isEmpty()) return "no UI steps for goal: " + action.goal
        val service = JarvisAccessibilityService.instance
            ?: return "Accessibility Service is off - Settings > Accessibility e Jarvis on korun"
        for (step in action.steps) {
            when (step.type) {
                "tap" -> {
                    val root = service.getActiveRootNode() ?: return "screen unavailable for tap: " + step.value
                    val node = findNodeByText(root, step.value) ?: return "not found on screen: " + step.value
                    clickNode(node)
                }
                "type" -> {
                    // DEV STYLE - strict typing anywhere: focus, SET_TEXT, paste, tap-and-retry.
                    val status = service.typeTextRobust(step.value, false)
                    if (status != "OK") return status
                }
                "enter" -> {
                    val root = service.getActiveRootNode() ?: return "no screen for enter"
                    val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: findEditableNode(root)
                        ?: return "no text field for enter"
                    val ok = if (Build.VERSION.SDK_INT >= 30)
                        focused.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
                    else false
                    if (!ok && !clickNode(focused)) return "could not press enter"
                }
                "back" -> service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                "home" -> service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
                "wait" -> delay(step.value.toLongOrNull() ?: 1500L)
            }
            delay(500)
        }
        return "OK"
    }

    /** Finds the first node whose text or content description contains [value]. */
    private fun findNodeByText(root: AccessibilityNodeInfo, value: String): AccessibilityNodeInfo? {
        val want = value.trim().lowercase()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val texts = listOf(node.text?.toString(), node.contentDescription?.toString())
            if (texts.any { it != null && it.lowercase().contains(want) }) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
        }
        return null
    }

    /** Clicks the node, walking up to a clickable ancestor when needed. */
    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 6) {
            if (current.isClickable && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            current = current.parent
            depth++
        }
        return false
    }

    private fun findEditableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.isEditable) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
        }
        return null
    }

    private fun openApp(packageName: String): String {
        val launch = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return "app not installed: " + packageName
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launch)
        return "OK"
    }

    private fun showNotify(text: String): String {
        if (Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            return "notification permission missing"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Macro Center", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, CHANNEL_ID)
        else @Suppress("DEPRECATION") Notification.Builder(context)
        val note = builder
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Jarvis Automation")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        nm.notify(text.hashCode(), note)
        return "OK"
    }
}
