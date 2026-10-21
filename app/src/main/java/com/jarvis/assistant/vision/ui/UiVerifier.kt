package com.jarvis.assistant.vision.ui

import com.jarvis.assistant.automation.agent.ScreenSignature
import com.jarvis.assistant.vision.UiElementInfo
import java.util.Locale

/**
 * Closed-loop verification for a single UI action: the result is judged only on
 * re-inspected screen evidence, never on the fact that a tap was dispatched.
 */
object UiVerifier {

    enum class Evidence { SCREEN_CHANGED, FIELD_TEXT_MATCHED, FIELD_TEXT_MISMATCH, NO_PROGRESS, TARGET_GONE, NO_EVIDENCE }

    data class Verification(val verified: Boolean, val evidence: Evidence, val detail: String)

    fun verifyScreenChanged(before: ScreenSignature?, after: ScreenSignature?, what: String): Verification {
        if (before == null || after == null) {
            return Verification(false, Evidence.NO_EVIDENCE, "the screen could not be inspected before and after the action")
        }
        return if (after.isEquivalentTo(before)) {
            Verification(false, Evidence.NO_PROGRESS, "the screen looks the same after " + what)
        } else {
            Verification(true, Evidence.SCREEN_CHANGED, "the screen changed after " + what)
        }
    }

    fun verifyScrolled(before: ScreenSignature?, after: ScreenSignature?): Verification {
        if (before == null || after == null) {
            return Verification(false, Evidence.NO_EVIDENCE, "the screen could not be inspected before and after the scroll")
        }
        return if (after.isEquivalentTo(before)) {
            Verification(false, Evidence.NO_PROGRESS, "nothing new appeared - the list is probably at its end")
        } else {
            Verification(true, Evidence.SCREEN_CHANGED, "new content appeared after scrolling")
        }
    }

    fun verifyText(expected: String, elements: List<UiElementInfo>): Verification {
        val want = normalize(expected)
        if (want.isBlank()) return Verification(false, Evidence.NO_EVIDENCE, "no text was sent")
        val editable = elements.filter { it.isEditable }
        val matched = editable.firstOrNull { normalize(it.text).contains(want) }
        if (matched != null) return Verification(true, Evidence.FIELD_TEXT_MATCHED, "the field now contains the text")
        val anyText = editable.firstOrNull { it.text.isNotBlank() }
        if (anyText != null) {
            return Verification(false, Evidence.FIELD_TEXT_MISMATCH, "the field shows \"" + anyText.text.take(40) + "\" instead of the requested text")
        }
        return Verification(false, Evidence.NO_EVIDENCE, "no editable field shows the text yet")
    }

    fun verifyTargetPresent(label: String, elements: List<UiElementInfo>): Boolean {
        val want = normalize(label)
        if (want.isBlank()) return false
        val tokens = want.split(" ").filter { it.length > 1 }
        return elements.any { element ->
            val text = normalize(element.text)
            val desc = normalize(element.contentDescription)
            (text.isNotEmpty() && text.contains(want)) ||
                (desc.isNotEmpty() && desc.contains(want)) ||
                (tokens.isNotEmpty() && tokens.all { text.contains(it) || desc.contains(it) })
        }
    }

    private fun normalize(value: String): String = value
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
