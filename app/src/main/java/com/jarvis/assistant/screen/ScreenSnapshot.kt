package com.jarvis.assistant.screen

import android.view.accessibility.AccessibilityNodeInfo
import com.jarvis.assistant.automation.accessibility.JarvisAccessibilityService

data class ScreenItem(val label: String, val role: String, val node: AccessibilityNodeInfo)

data class ScreenSnapshot(val packageName: String, val items: List<ScreenItem>) {
    val spokenText: String
        get() = items.joinToString(". ") { it.label }
}

/** Reads what is actually on screen through the accessibility service. */
object ScreenSnapshotReader {

    fun capture(): ScreenSnapshot? {
        val root = JarvisAccessibilityService.instance?.rootInActiveWindow ?: return null
        val items = ArrayList<ScreenItem>()
        val seen = HashSet<String>()
        collect(root, items, seen, 0)
        return ScreenSnapshot(root.packageName?.toString().orEmpty(), items)
    }

    private fun collect(node: AccessibilityNodeInfo?, out: MutableList<ScreenItem>, seen: MutableSet<String>, depth: Int) {
        if (node == null || depth > 14 || out.size >= 120) return
        val label = labelOf(node)
        if (label.isNotBlank() && seen.add(label)) out.add(ScreenItem(label, roleOf(node), node))
        for (index in 0 until node.childCount) {
            collect(runCatching { node.getChild(index) }.getOrNull(), out, seen, depth + 1)
        }
    }

    private fun labelOf(node: AccessibilityNodeInfo): String {
        val text = node.text?.toString()?.trim().orEmpty()
        val description = node.contentDescription?.toString()?.trim().orEmpty()
        return when {
            text.isBlank() -> description
            description.isBlank() -> text
            description == text -> text
            else -> text + " (" + description + ")"
        }
    }

    private fun roleOf(node: AccessibilityNodeInfo): String {
        val name = node.className?.toString().orEmpty().substringAfterLast('.')
        return when {
            name.contains("Button", true) || node.isClickable -> "Tap"
            name.contains("EditText", true) -> "Field"
            name.contains("Image", true) -> "Image"
            else -> "Text"
        }
    }
}
