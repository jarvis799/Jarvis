package com.jarvis.assistant.vision

import android.os.SystemClock
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.vision.ui.UiActionExecutor

/**
 * DEV STYLE: always-on screen awareness.
 *
 * The user does NOT have to start Screen Share for Jarvis to know what is on screen. Android's
 * accessibility tree already exposes the foreground app's visible text, buttons and fields, so
 * Jarvis can read the screen, name the app and act on it at any moment.
 */
object ScreenAwareness {

    private const val CACHE_MS = 1200L
    private val engine = UiGroundingEngine()

    data class ScreenSnapshot(
        val available: Boolean,
        val packageName: String,
        val appLabel: String,
        val capturedAt: Long,
        val labels: List<String>
    ) {
        companion object {
            val EMPTY = ScreenSnapshot(false, "", "", 0L, emptyList())
        }
    }

    @Volatile
    private var cached: ScreenSnapshot = ScreenSnapshot.EMPTY

    @Volatile
    private var cachedAt = 0L

    fun isAvailable(): Boolean = UiActionExecutor.isServiceReady()

    /** Fresh read of the foreground screen; cached briefly so prompt building stays cheap. */
    fun snapshot(force: Boolean = false): ScreenSnapshot {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - cachedAt < CACHE_MS) return cached
        cachedAt = now
        val root = runCatching { UiActionExecutor.activeRoot() }.getOrNull()
            ?: return ScreenSnapshot(false, "", "", now, emptyList()).also { cached = it }
        val packageName = runCatching { root.packageName?.toString().orEmpty() }.getOrDefault("")
        val elements = runCatching { engine.scanScreen(root) }.getOrDefault(emptyList())
        val labels = elements.asSequence()
            .map { it.primaryLabel.trim() }
            .filter { it.length in 2..90 }
            .distinct()
            .take(40)
            .toList()
        val snapshot = ScreenSnapshot(true, packageName, appLabel(packageName), now, labels)
        cached = snapshot
        return snapshot
    }

    /** One spoken-style line about where the user is and what is on the screen. */
    fun describe(): String {
        val snapshot = snapshot()
        if (!snapshot.available) {
            return "Accessibility access is off, so I cannot read the screen right now. Turn on JARVIS accessibility in Settings and I can analyse it without Screen Share."
        }
        if (snapshot.labels.isEmpty()) {
            return "Foreground app: ${snapshot.appLabel.ifBlank { "unknown" }}. No text is exposed on this screen yet."
        }
        return "Foreground app: ${snapshot.appLabel.ifBlank { "unknown" }}. On screen: " +
            snapshot.labels.take(18).joinToString(", ")
    }

    /** Compact context line injected into the system prompt before every turn. */
    fun promptLine(): String {
        if (!isAvailable()) return "Accessibility is off - screen text is unavailable until the user enables it."
        val snapshot = snapshot()
        if (!snapshot.available) return "Screen text is unavailable right now."
        val shown = snapshot.labels.take(20).joinToString(" | ")
        return "App: ${snapshot.appLabel.ifBlank { snapshot.packageName }}. Visible text: " +
            shown.ifBlank { "(no readable text)" } +
            ". Use this instead of asking the user what is on screen."
    }

    private fun appLabel(packageName: String): String {
        if (packageName.isBlank()) return ""
        return runCatching {
            val pm = JarvisApp.instance.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName)
    }
}