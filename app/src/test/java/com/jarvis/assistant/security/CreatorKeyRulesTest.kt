package com.jarvis.assistant.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreatorKeyRulesTest {

    @Test
    fun normalizesCaseAndSeparators() {
        assertEquals("ahonailoveyou", CreatorKeyRules.normalize("  Ahona-I-Love-You  "))
        assertEquals("", CreatorKeyRules.normalize(null))
    }

    @Test
    fun rejectsShortKeys() {
        assertFalse(CreatorKeyRules.usable("abc123"))
        assertTrue(CreatorKeyRules.usable("abcdefgh"))
    }

    @Test
    fun matchesOnlyTheExactConfiguredKey() {
        assertTrue(CreatorKeyRules.matches("Ahona-I-Love-You", "ahona i love you"))
        assertFalse(CreatorKeyRules.matches("Ahona-I-Love-You", "ahona i love youu"))
        assertFalse(CreatorKeyRules.matches("Ahona-I-Love-You", "ahona love"))
    }

    @Test
    fun blankExpectedKeyNeverMatches() {
        assertFalse(CreatorKeyRules.matches("", ""))
        assertFalse(CreatorKeyRules.matches(null, "anything"))
    }

    @Test
    fun locksAfterFiveFailures() {
        assertFalse(CreatorKeyRules.shouldLock(4))
        assertTrue(CreatorKeyRules.shouldLock(5))
        assertEquals(1_000_000L + CreatorKeyRules.LOCK_MILLIS, CreatorKeyRules.lockUntil(1_000_000L))
    }
}