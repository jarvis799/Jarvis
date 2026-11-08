package com.jarvis.assistant.automation.status

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionStatusPolicyTest {

    @Test
    fun aLaunchThatWasRejectedFailsImmediately() {
        assertEquals(ActionState.FAILED, ActionStatusPolicy.afterLaunch(false))
        assertEquals(ActionState.LOADING, ActionStatusPolicy.afterLaunch(true))
    }

    @Test
    fun aVerifiedForegroundScreenCompletesTheAction() {
        assertEquals(ActionState.COMPLETED, ActionStatusPolicy.settle(true, true, 0L))
        assertEquals(ActionState.COMPLETED, ActionStatusPolicy.settle(true, true, 8_000L))
    }

    @Test
    fun anUnconfirmedLaunchNeverStaysLoadingForever() {
        assertEquals(ActionState.LOADING, ActionStatusPolicy.settle(true, false, 1_000L))
        assertEquals(
            ActionState.UNVERIFIED,
            ActionStatusPolicy.settle(true, false, ActionStatusPolicy.VERIFY_TIMEOUT_MS)
        )
    }

    @Test
    fun successIsSpokenOnlyForAVerifiedAction() {
        assertEquals("Ji sir, Gemini open ho gaya.", ActionStatusPolicy.spokenLine("Gemini", ActionState.COMPLETED))
        assertTrue(ActionStatusPolicy.spokenLine("Gemini", ActionState.UNVERIFIED).contains("confirm"))
        assertTrue(ActionStatusPolicy.spokenLine("Gemini", ActionState.FAILED).contains("nahi hua"))
        assertEquals("", ActionStatusPolicy.spokenLine("Gemini", ActionState.LOADING))
    }

    @Test
    fun theModelIsToldNotToRepeatLoadingOnceTheStateIsTerminal() {
        val done = ActionStatusPolicy.toolMessage("Gemini", "gemini.google.com", ActionState.COMPLETED)
        assertTrue(done.contains("COMPLETED"))
        assertTrue(done.contains("Never say loading"))
        val loose = ActionStatusPolicy.toolMessage("Gemini", "gemini.google.com", ActionState.UNVERIFIED)
        assertTrue(loose.contains("UNVERIFIED"))
        assertFalse(loose.contains("COMPLETED"))
    }

    @Test
    fun aUrlBecomesASpokenName() {
        assertEquals("Gemini", ActionStatusPolicy.labelFor("https://gemini.google.com/app"))
        assertEquals("gemini.google.com", ActionStatusPolicy.hostOf("https://www.gemini.google.com/app?x=1"))
        assertEquals("Youtube", ActionStatusPolicy.labelFor("https://www.youtube.com"))
        assertEquals("website", ActionStatusPolicy.labelFor(""))
    }
}
