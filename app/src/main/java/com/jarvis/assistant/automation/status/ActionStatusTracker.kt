package com.jarvis.assistant.automation.status

import android.content.Context
import android.util.Log
import com.jarvis.assistant.automation.accessibility.JarvisAccessibilityService
import com.jarvis.assistant.voice.ChatSpeech
import com.jarvis.assistant.voice.VoiceSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Turns "we asked Android to open something" into a state that is actually known.
 *
 * Every launch now ends in COMPLETED, FAILED or UNVERIFIED within a bounded time, from a single
 * source: the same report feeds the spoken line and the state the UI and the model read. A stuck
 * "Loading" line is no longer representable, because loading is not a terminal state.
 */
object ActionStatusTracker {

    private const val TAG = "ActionStatusTracker"

    private val _state = MutableStateFlow<ActionReport?>(null)

    /** The live status of the most recent action, shared by voice output and the UI. */
    val state: StateFlow<ActionReport?> = _state.asStateFlow()

    /**
     * Waits until the launched app or website is really in the foreground, then reports it.
     * [expectedPackages] are matched case-insensitively against the foreground package, which is
     * what "actually opened" means here; nothing is announced from the launch Intent alone.
     */
    suspend fun awaitLaunch(
        context: Context,
        label: String,
        host: String,
        expectedPackages: List<String>,
        launched: Boolean
    ): ActionReport {
        if (!launched) {
            val report = ActionReport(label, host, ActionState.FAILED, false, "the launch was rejected")
            _state.value = report
            return announce(context, report)
        }
        val started = System.currentTimeMillis()
        _state.value = ActionReport(label, host, ActionState.LOADING, false, "waiting for the screen")
        while (true) {
            val elapsed = System.currentTimeMillis() - started
            val foreground = foregroundPackage()
            val verified = matchesForeground(foreground, expectedPackages)
            val report = ActionReport(
                label = label,
                host = host,
                state = ActionStatusPolicy.settle(true, verified, elapsed),
                verified = verified,
                detail = if (verified) "foreground: " + foreground else "foreground: " + foreground.ifBlank { "unknown" }
            )
            _state.value = report
            if (report.terminal) return announce(context, report)
            delay(ActionStatusPolicy.POLL_MS)
        }
    }

    /** Marks the most recent action as finished without launching anything. */
    fun clear() {
        _state.value = null
    }

    private fun announce(context: Context, report: ActionReport): ActionReport {
        val line = if (report.state == ActionState.UNVERIFIED && !accessibilityAvailable()) {
            "Sir, " + report.label + " kholne ki koshish ki, par verify nahi kar paya. Accessibility service on karein."
        } else {
            ActionStatusPolicy.spokenLine(report.label, report.state)
        }
        if (line.isNotBlank()) {
            VoiceSession.speak(
                context = context.applicationContext,
                text = line,
                locale = ChatSpeech.localeFor(line),
                owner = VoiceSession.OWNER_SYSTEM
            )
            Log.i(TAG, "announced " + report.state.name + ": " + line)
        }
        return report
    }

    private fun foregroundPackage(): String =
        JarvisAccessibilityService.instance?.getActiveRootNode()?.packageName?.toString().orEmpty()

    private fun accessibilityAvailable(): Boolean = JarvisAccessibilityService.instance != null

    private fun matchesForeground(foreground: String, expectedPackages: List<String>): Boolean {
        if (foreground.isBlank()) return false
        if (expectedPackages.isEmpty()) return true
        return expectedPackages.any { it.isNotBlank() && foreground.contains(it, ignoreCase = true) }
    }
}
