package com.jarvis.assistant.vision.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UiCommandPlannerTest {

    @Test
    fun tapsExactButton() {
        val plan = UiCommandPlanner.plan("tap the Search button")
        assertNotNull(plan)
        assertEquals(UiActionKind.CLICK, plan!!.action)
        assertEquals("search", plan.target?.query)
        assertTrue(plan.target!!.hints.contains("button"))
    }

    @Test
    fun understandsVerbLastBanglish() {
        val plan = UiCommandPlanner.plan("search button e tap koro")
        assertNotNull(plan)
        assertEquals(UiActionKind.CLICK, plan!!.action)
        assertEquals("search", plan.target?.query)
    }

    @Test
    fun findAndOpenKeepsTheSeekHint() {
        val plan = UiCommandPlanner.plan("find the Create button and open it")
        assertNotNull(plan)
        assertEquals(UiActionKind.CLICK, plan!!.action)
        assertEquals("create", plan.target?.query)
        assertTrue(plan.target!!.hints.contains("seek"))
    }

    @Test
    fun scrollsDown() {
        assertEquals(UiActionKind.SCROLL_FORWARD, UiCommandPlanner.plan("scroll down")?.action)
        assertEquals(UiActionKind.SCROLL_BACKWARD, UiCommandPlanner.plan("scroll up")?.action)
    }

    @Test
    fun scrollAndFindNextOptionBecomesPositional() {
        val plan = UiCommandPlanner.plan("scroll down and find the next option")
        assertNotNull(plan)
        assertEquals(UiActionKind.FIND_AND_ACT, plan!!.action)
        assertEquals(1, plan.target?.ordinal)
        assertEquals("", plan.target?.query)
        assertTrue(plan.target!!.hints.contains("scroll_forward"))
    }

    @Test
    fun typesIntoNamedField() {
        val plan = UiCommandPlanner.plan("type hello into the search field")
        assertNotNull(plan)
        assertEquals(UiActionKind.SET_TEXT, plan!!.action)
        assertEquals("hello", plan.text)
        assertEquals("search", plan.target?.query)
    }

    @Test
    fun describesScreenInBothLanguages() {
        assertEquals(UiActionKind.DESCRIBE, UiCommandPlanner.plan("what's on the screen")?.action)
        assertEquals(UiActionKind.DESCRIBE, UiCommandPlanner.plan("screen e ki ache")?.action)
    }

    @Test
    fun navigatesBack() {
        assertEquals(UiActionKind.BACK, UiCommandPlanner.plan("go back")?.action)
    }

    @Test
    fun riskyTargetNeedsConfirmation() {
        val plan = UiCommandPlanner.plan("tap the delete account button")
        assertNotNull(plan)
        assertTrue(plan!!.needsConfirmation)
    }

    @Test
    fun neverHijacksDeviceOrAppCommands() {
        assertNull(UiCommandPlanner.plan("open settings"))
        assertNull(UiCommandPlanner.plan("open whatsapp"))
        assertNull(UiCommandPlanner.plan("play a song on youtube"))
        assertNull(UiCommandPlanner.plan("what is the battery level"))
        assertNull(UiCommandPlanner.plan("call mom"))
        assertNull(UiCommandPlanner.plan("enter"))
    }

    @Test
    fun deicticDictationStaysWithTheSmartPath() {
        assertNull(UiCommandPlanner.plan("type this text"))
        assertNull(UiCommandPlanner.plan("enter this text in the selected field"))
    }
}
