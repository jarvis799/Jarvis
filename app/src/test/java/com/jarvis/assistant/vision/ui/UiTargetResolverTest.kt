package com.jarvis.assistant.vision.ui

import android.graphics.Rect
import com.jarvis.assistant.vision.UiElementInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UiTargetResolverTest {

    private val resolver = UiTargetResolver()

    private fun element(
        id: Int,
        text: String,
        left: Int,
        top: Int,
        clickable: Boolean = true,
        editable: Boolean = false,
        viewId: String = "",
        description: String = ""
    ) = UiElementInfo(
        id = id,
        text = text,
        contentDescription = description,
        className = if (editable) "android.widget.EditText" else "android.widget.Button",
        viewId = viewId,
        bounds = Rect(left, top, left + 220, top + 64),
        isClickable = clickable,
        isCheckable = false,
        isChecked = false,
        isEditable = editable,
        isScrollable = false
    )

    @Test
    fun exactTextBeatsPartialMatch() {
        val elements = listOf(
            element(1, "Search history", 0, 0),
            element(2, "Search", 0, 200)
        )
        val resolution = resolver.resolve(UiTargetSpec(query = "search"), elements)
        assertNotNull(resolution.chosen)
        assertEquals("Search", resolution.chosen!!.text)
        assertFalse(resolution.ambiguous)
    }

    @Test
    fun twoIdenticalLabelsAreReportedAsAmbiguous() {
        val elements = listOf(
            element(1, "Share", 0, 0),
            element(2, "Share", 0, 400)
        )
        val resolution = resolver.resolve(UiTargetSpec(query = "share"), elements)
        assertTrue(resolution.ambiguous)
        assertNull(resolution.chosen)
        assertTrue(resolution.topLabels.size >= 2)
    }

    @Test
    fun resourceIdCanGroundAnIconOnlyButton() {
        val elements = listOf(
            element(1, "", 0, 0, viewId = "com.example.app:id/btn_create", description = ""),
            element(2, "Cancel", 0, 200)
        )
        val resolution = resolver.resolve(UiTargetSpec(query = "btn create", source = UiTargetSource.RESOURCE_ID), elements)
        assertNotNull(resolution.chosen)
        assertEquals("btn_create", resolution.chosen!!.viewId.substringAfterLast("/"))
    }

    @Test
    fun missingTargetIsNotFound() {
        val elements = listOf(element(1, "Cancel", 0, 0))
        val resolution = resolver.resolve(UiTargetSpec(query = "publish"), elements)
        assertNull(resolution.chosen)
        assertFalse(resolution.ambiguous)
    }

    @Test
    fun ordinalUsesScreenPosition() {
        val elements = listOf(
            element(1, "Alpha", 0, 30),
            element(2, "Beta", 0, 120, clickable = false),
            element(3, "Gamma", 0, 260)
        )
        val resolution = resolver.resolve(UiTargetSpec(query = "", ordinal = 1), elements)
        assertNotNull(resolution.chosen)
        assertEquals("Alpha", resolution.chosen!!.text)
    }

    @Test
    fun severalEditableFieldsAskWhichOne() {
        val elements = listOf(
            element(1, "", 0, 0, editable = true, viewId = "com.example.app:id/field_one"),
            element(2, "", 0, 100, editable = true, viewId = "com.example.app:id/field_two")
        )
        val resolution = resolver.resolve(UiTargetSpec(query = "", source = UiTargetSource.SELECTED_FIELD, prefersEditable = true), elements)
        assertTrue(resolution.ambiguous)
        assertNull(resolution.chosen)
    }

    @Test
    fun namedFieldIsResolvedForTyping() {
        val elements = listOf(
            element(1, "", 0, 0, editable = true),
            element(2, "Search", 0, 200, editable = true, viewId = "com.example.app:id/search_field"),
            element(3, "Send", 0, 400)
        )
        val resolution = resolver.resolve(UiTargetSpec(query = "search", source = UiTargetSource.SELECTED_FIELD, prefersEditable = true), elements)
        assertNotNull(resolution.chosen)
        assertTrue(resolution.chosen!!.isEditable)
        assertEquals(2, resolution.chosen!!.id)
    }

    @Test
    fun ocrLinesAreScaledBackToScreenCoordinates() {
        val ocr = listOf(UiScreenOcr.OcrLine("Create", Rect(10, 20, 60, 40)))
        val resolution = resolver.resolveFromOcr(
            UiTargetSpec(query = "create"),
            ocr,
            screenWidth = 1080,
            screenHeight = 1920,
            bitmapWidth = 540,
            bitmapHeight = 960
        )
        assertNotNull(resolution.chosen)
        assertTrue(resolution.usedOcr)
        assertEquals(20, resolution.chosen!!.bounds.left)
        assertEquals(40, resolution.chosen!!.bounds.top)
    }
}
