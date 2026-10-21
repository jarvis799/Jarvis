package com.jarvis.assistant.vision.ui

import android.graphics.Rect
import com.jarvis.assistant.vision.UiElementInfo
import com.jarvis.assistant.vision.UiGroundingEngine
import java.util.Locale

/**
 * Resolves a spoken target against grounded screen evidence: exact accessible text,
 * content description, resource id, token match, editable/focused fields, scroll
 * containers and (as a last resort) on-device OCR lines.
 *
 * It never invents an element: no grounded candidate means no target. Two equally
 * strong candidates are reported as ambiguous instead of guessing.
 */
class UiTargetResolver(private val engine: UiGroundingEngine = UiGroundingEngine()) {

    companion object {
        private const val MIN_SCORE = 55
        const val OCR_VIEW_ID = "ocr/screen_text"
        const val OCR_CLASS = "android.widget.TextView"
    }

    fun resolve(spec: UiTargetSpec, elements: List<UiElementInfo>): UiResolution {
        return when (spec.source) {
            UiTargetSource.SELECTED_FIELD -> resolveField(spec, elements)
            UiTargetSource.SCROLL_CONTAINER -> resolveScroller(spec, elements)
            else -> resolveByLabel(spec, elements)
        }
    }

    /** Field target: focused editable first, then the only editable on screen. */
    private fun resolveField(spec: UiTargetSpec, elements: List<UiElementInfo>): UiResolution {
        val editables = elements.filter { it.isEditable }
        if (editables.isEmpty()) return UiResolution(spec, null, emptyList())
        if (editables.size == 1) {
            return UiResolution(spec, editables[0], listOf(UiCandidate(editables[0], 95, "only editable field")))
        }
        val query = normalize(spec.query)
        if (query.isNotBlank()) {
            val ranked = rank(spec, editables)
            if (ranked.isNotEmpty()) return UiResolution(spec, ranked[0].element, ranked, false)
        }
        val byHint = editables.firstOrNull { it.primaryLabel.contains("search", true) }
        if (byHint != null) {
            return UiResolution(spec, byHint, listOf(UiCandidate(byHint, 70, "search field")))
        }
        return UiResolution(
            spec, null, editables.map { UiCandidate(it, 60, "editable field") }, ambiguous = true,
            note = "several editable fields are visible"
        )
    }

    /** Scroll container: the largest scrollable area on screen. */
    private fun resolveScroller(spec: UiTargetSpec, elements: List<UiElementInfo>): UiResolution {
        val scrollables = elements.filter { it.isScrollable }
        if (scrollables.isEmpty()) return UiResolution(spec, null, emptyList())
        val ranked = scrollables.sortedByDescending { it.bounds.width().toLong() * it.bounds.height() }
        return UiResolution(spec, ranked[0], ranked.take(3).map { UiCandidate(it, 80, "scrollable container") })
    }

    private fun resolveByLabel(spec: UiTargetSpec, elements: List<UiElementInfo>): UiResolution {
        val ranked = rank(spec, elements)
        if (ranked.isEmpty()) {
            if (spec.ordinal != null) return resolveByPosition(spec, elements)
            return UiResolution(spec, null, emptyList())
        }
        val ordinal = spec.ordinal
        if (ordinal != null) {
            val ordered = ranked.sortedWith(compareBy({ it.element.bounds.top }, { it.element.bounds.left }))
            val index = if (ordinal < 0) ordered.size - 1 else (ordinal - 1).coerceIn(0, ordered.size - 1)
            val pick = ordered[index]
            return UiResolution(spec, pick.element, ordered, ambiguous = false, note = "ordinal match")
        }
        val top = ranked[0]
        val second = ranked.getOrNull(1)
        val ambiguous = second != null && second.score == top.score && !sameRow(top.element, second.element)
        return UiResolution(spec, if (ambiguous) null else top.element, ranked, ambiguous)
    }

    /** "the first option" / "the next one": a positional choice among real, labelled clickable elements. */
    private fun resolveByPosition(spec: UiTargetSpec, elements: List<UiElementInfo>): UiResolution {
        val ordered = elements
            .filter { it.hasTextOrDesc && (it.isClickable || !it.isGenericContainer) }
            .sortedWith(compareBy({ it.bounds.top }, { it.bounds.left }))
        if (ordered.isEmpty()) return UiResolution(spec, null, emptyList())
        val wanted = spec.ordinal ?: 1
        val index = if (wanted < 0) ordered.size - 1 else (wanted - 1).coerceIn(0, ordered.size - 1)
        return UiResolution(
            spec,
            ordered[index],
            ordered.take(6).map { UiCandidate(it, 60, "positional match") },
            ambiguous = false,
            usedOcr = false,
            note = "chosen by position on screen"
        )
    }

    /** Grounds OCR text lines so a screenshot can still resolve a target accessibility cannot. */
    fun resolveFromOcr(spec: UiTargetSpec, ocrLines: List<UiScreenOcr.OcrLine>, screenWidth: Int, screenHeight: Int, bitmapWidth: Int, bitmapHeight: Int): UiResolution {
        if (spec.query.isBlank()) return UiResolution(spec, null, emptyList(), usedOcr = true)
        val query = normalize(spec.query)
        val tokens = query.split(" ").filter { it.length > 1 }
        val candidates = ArrayList<UiCandidate>()
        for (line in ocrLines) {
            val text = normalize(line.text)
            if (text.isBlank()) continue
            val score = when {
                text == query -> 95
                text.contains(query) -> 80
                tokens.isNotEmpty() && tokens.all { text.contains(it) } -> 70
                else -> 0
            }
            if (score == 0) continue
            val bounds = scaleToScreen(line.bounds, screenWidth, screenHeight, bitmapWidth, bitmapHeight)
            candidates += UiCandidate(syntheticElement(line.text, bounds), score, "screen text (OCR)")
        }
        if (candidates.isEmpty()) return UiResolution(spec, null, emptyList(), usedOcr = true)
        val ranked = candidates.sortedWith(compareByDescending<UiCandidate> { it.score }.thenBy { it.element.bounds.top })
        val top = ranked[0]
        val second = ranked.getOrNull(1)
        val ambiguous = second != null && second.score == top.score && !sameRow(top.element, second.element)
        return UiResolution(
            spec,
            if (ambiguous) null else top.element,
            ranked,
            ambiguous = ambiguous,
            usedOcr = true,
            note = "matched from screen text (OCR)"
        )
    }

    fun rank(spec: UiTargetSpec, elements: List<UiElementInfo>): List<UiCandidate> {
        val query = normalize(spec.query)
        if (query.isBlank()) return emptyList()
        val idQuery = query.replace(" ", "").replace("_", "")
        val tokens = query.split(" ").filter { it.length > 1 }
        val out = ArrayList<UiCandidate>()
        for (element in elements) {
            val text = normalize(element.text)
            val desc = normalize(element.contentDescription)
            val viewId = element.viewId.substringAfterLast("/").lowercase(Locale.ROOT)
            val idFlat = viewId.replace("_", "")
            var score = 0
            var reason = ""
            if (text.isNotEmpty() && text == query) {
                score = 100; reason = "exact text"
            } else if (idQuery.length > 2 && (idFlat == idQuery || (spec.source == UiTargetSource.RESOURCE_ID && viewId == query))) {
                score = 96; reason = "resource id"
            } else if (desc.isNotEmpty() && desc == query) {
                score = 92; reason = "exact content description"
            } else if (desc.isNotEmpty() && desc.contains(query)) {
                score = 80; reason = "content description contains"
            } else if (text.isNotEmpty() && text.contains(query)) {
                score = 78; reason = "text contains"
            } else if (tokens.isNotEmpty() && tokens.all { text.contains(it) || desc.contains(it) }) {
                score = 74; reason = "all words present"
            } else if (idQuery.length > 2 && idFlat.contains(idQuery)) {
                score = 68; reason = "resource id contains"
            } else if (tokens.isNotEmpty() && tokens.any { it.length > 2 && (text.contains(it) || desc.contains(it)) }) {
                score = 58; reason = "partial word match"
            }
            if (score == 0) continue
            if (element.hasTextOrDesc) score += 4
            if (spec.prefersEditable && element.isEditable) score += 16
            if (element.isClickable) score += 2
            if (element.isGenericContainer) score -= 12
            if (score < MIN_SCORE) continue
            out += UiCandidate(element, score, reason)
        }
        return out.sortedWith(compareByDescending<UiCandidate> { it.score }.thenBy { it.element.bounds.top }.thenBy { it.element.bounds.left })
    }

    private fun syntheticElement(text: String, bounds: Rect): UiElementInfo = UiElementInfo(
        id = -1,
        text = text,
        contentDescription = "",
        className = OCR_CLASS,
        viewId = OCR_VIEW_ID,
        bounds = bounds,
        isClickable = false,
        isCheckable = false,
        isChecked = false,
        isEditable = false,
        isScrollable = false
    )

    private fun scaleToScreen(bounds: Rect, screenWidth: Int, screenHeight: Int, bitmapWidth: Int, bitmapHeight: Int): Rect {
        if (bitmapWidth <= 0 || bitmapHeight <= 0 || screenWidth <= 0 || screenHeight <= 0) return Rect(bounds)
        val sx = screenWidth.toFloat() / bitmapWidth.toFloat()
        val sy = screenHeight.toFloat() / bitmapHeight.toFloat()
        return Rect(
            (bounds.left * sx).toInt(),
            (bounds.top * sy).toInt(),
            (bounds.right * sx).toInt(),
            (bounds.bottom * sy).toInt()
        )
    }

    private fun sameRow(a: UiElementInfo, b: UiElementInfo): Boolean {
        val tolerance = maxOf(24, a.bounds.height() / 2)
        return kotlin.math.abs(a.bounds.centerY() - b.bounds.centerY()) <= tolerance
    }

    private fun normalize(value: String): String = value
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
