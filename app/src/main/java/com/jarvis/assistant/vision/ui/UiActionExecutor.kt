package com.jarvis.assistant.vision.ui

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.jarvis.assistant.automation.accessibility.JarvisAccessibilityService
import com.jarvis.assistant.vision.UiElementInfo
import java.util.Locale

/**
 * Executes one UI action through authorized Android APIs only, and always prefers a
 * semantic accessibility action over a coordinate gesture. A coordinate tap is used
 * only when the node itself cannot perform the action.
 */
object UiActionExecutor {

    private val sensitiveHints = listOf(
        "password", "passcode", "pass word", "pin", "cvv", "card", "otp", "upi",
        "netbank", "secret", "payment", "wallet"
    )

    enum class Method { SEMANTIC_NODE, COORDINATE_TAP, GLOBAL_ACTION, SCROLL_NODE, SET_TEXT, NONE }

    data class Outcome(val performed: Boolean, val method: Method = Method.NONE, val detail: String? = null) {
        val sensitive: Boolean get() = detail == SENSITIVE_FIELD
    }

    const val SENSITIVE_FIELD = "sensitive_field"

    fun isServiceReady(): Boolean = JarvisAccessibilityService.isEnabled()

    fun activeRoot(): AccessibilityNodeInfo? = JarvisAccessibilityService.instance?.getActiveRootNode()

    fun click(element: UiElementInfo?): Outcome {
        val service = JarvisAccessibilityService.instance
            ?: return Outcome(false, Method.NONE, "the Jarvis accessibility service is off")
        val node = liveNode(element)
        if (node != null && service.clickNodeAndTap(node)) {
            return Outcome(true, Method.SEMANTIC_NODE, "accessibility click")
        }
        if (element != null && element.bounds.width() > 0 && element.bounds.height() > 0) {
            val dispatched = service.clickAt(element.center.x, element.center.y)
            return Outcome(dispatched, Method.COORDINATE_TAP, if (dispatched) "tapped at the element position" else "Android refused the tap gesture")
        }
        return Outcome(false, Method.NONE, "the target is not on screen")
    }

    fun longClick(element: UiElementInfo?): Outcome {
        val node = liveNode(element) ?: return Outcome(false, Method.NONE, "the target is not on screen")
        if (node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) {
            return Outcome(true, Method.SEMANTIC_NODE, "accessibility long press")
        }
        return Outcome(false, Method.NONE, "this element does not support a long press through accessibility")
    }

    fun setText(element: UiElementInfo?, text: String): Outcome {
        val service = JarvisAccessibilityService.instance ?: return Outcome(false, Method.NONE, "the Jarvis accessibility service is off")
        val node = editableNode(element) ?: return Outcome(false, Method.NONE, "no editable field was found")
        if (isSensitive(node)) return Outcome(false, Method.NONE, SENSITIVE_FIELD)
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        var focused = node.isFocused
        if (!focused) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (rect.width() > 0 && rect.height() > 0) {
                service.clickAt(rect.centerX().toFloat(), rect.centerY().toFloat())
                focused = editableNode(element)?.isFocused == true
            }
        }
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val written = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        val detail = when {
            written && focused -> "text set in the focused field"
            written -> "text set in the field"
            else -> "the field refused the text action"
        }
        return Outcome(written, if (written) Method.SET_TEXT else Method.NONE, detail)
    }

    fun scroll(forward: Boolean, container: UiElementInfo?): Outcome {
        val node = scrollableNode(container)
            ?: return Outcome(false, Method.NONE, "there is no scrollable area on this screen")
        val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        val scrolled = node.performAction(action)
        val detail = when {
            scrolled && forward -> "scrolled forward"
            scrolled -> "scrolled back"
            else -> "the scroll action was refused here"
        }
        return Outcome(scrolled, if (scrolled) Method.SCROLL_NODE else Method.NONE, detail)
    }

    fun global(action: UiActionKind): Outcome {
        val service = JarvisAccessibilityService.instance ?: return Outcome(false, Method.NONE, "the Jarvis accessibility service is off")
        val globalAction = when (action) {
            UiActionKind.BACK -> AccessibilityService.GLOBAL_ACTION_BACK
            UiActionKind.HOME -> AccessibilityService.GLOBAL_ACTION_HOME
            UiActionKind.RECENTS -> AccessibilityService.GLOBAL_ACTION_RECENTS
            else -> return Outcome(false, Method.NONE, "unsupported system action")
        }
        val sent = service.performGlobalAction(globalAction)
        return Outcome(sent, if (sent) Method.GLOBAL_ACTION else Method.NONE, if (sent) "system navigation sent" else "Android refused that navigation action")
    }

    fun focusedEditable(): AccessibilityNodeInfo? {
        val root = activeRoot() ?: return null
        return findNode(root) { it.isEditable && it.isFocused } ?: findNode(root) { it.isEditable }
    }

    fun liveNode(element: UiElementInfo?): AccessibilityNodeInfo? {
        if (element == null) return null
        val root = activeRoot() ?: return null
        if (element.viewId.isNotBlank()) {
            runCatching { root.findAccessibilityNodeInfosByViewId(element.viewId) }
                .getOrNull()
                ?.firstOrNull()
                ?.let { return it }
        }
        val label = if (element.text.isNotBlank()) element.text else element.contentDescription
        if (label.isNotBlank()) {
            val matches = runCatching { root.findAccessibilityNodeInfosByText(label) }.getOrNull().orEmpty()
            matches.firstOrNull { sameBounds(it, element.bounds) }?.let { return it }
            matches.firstOrNull()?.let { return it }
        }
        return findNode(root) { sameBounds(it, element.bounds) }
    }

    private fun editableNode(element: UiElementInfo?): AccessibilityNodeInfo? {
        val node = liveNode(element)
        if (node == null) return focusedEditable()
        if (node.isEditable) return node
        return findNode(node) { it.isEditable } ?: ascend(node) { it.isEditable } ?: focusedEditable()
    }

    private fun scrollableNode(container: UiElementInfo?): AccessibilityNodeInfo? {
        val node = liveNode(container)
        if (node != null) {
            if (node.isScrollable) return node
            findNode(node) { it.isScrollable }?.let { return it }
            ascend(node) { it.isScrollable }?.let { return it }
        }
        val root = activeRoot() ?: return null
        var best: AccessibilityNodeInfo? = null
        var bestArea = 0
        walk(root, 0) { candidate ->
            if (candidate.isScrollable) {
                val rect = Rect()
                candidate.getBoundsInScreen(rect)
                val area = rect.width() * rect.height()
                if (area > bestArea) {
                    bestArea = area
                    best = candidate
                }
            }
        }
        return best
    }

    private fun findNode(root: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        var found: AccessibilityNodeInfo? = null
        walk(root, 0) { node ->
            if (found == null && predicate(node)) found = node
        }
        return found
    }

    private fun ascend(node: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        var parent = node.parent
        var depth = 0
        while (parent != null && depth < 4) {
            if (predicate(parent)) return parent
            parent = parent.parent
            depth++
        }
        return null
    }

    private fun walk(node: AccessibilityNodeInfo, depth: Int, visit: (AccessibilityNodeInfo) -> Unit) {
        if (depth > 14) return
        visit(node)
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            walk(child, depth + 1, visit)
        }
    }

    private fun sameBounds(node: AccessibilityNodeInfo, bounds: Rect): Boolean {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        return rect == bounds
    }

    private fun isSensitive(node: AccessibilityNodeInfo): Boolean {
        if (node.isPassword) return true
        val haystack = (
            node.hintText?.toString().orEmpty() + " " +
                node.text?.toString().orEmpty() + " " +
                node.viewIdResourceName.orEmpty() + " " +
                node.contentDescription?.toString().orEmpty()
            ).lowercase(Locale.ROOT)
        return sensitiveHints.any { haystack.contains(it) }
    }
}
