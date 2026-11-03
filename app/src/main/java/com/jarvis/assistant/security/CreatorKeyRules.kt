package com.jarvis.assistant.security

/**
 * Pure comparison and lockout rules for the creator key.
 *
 * The owner's own phrase always works: it ships with the app as BUILT_IN_KEY, so
 * creator verification never depends on the build machine, on CI variables or on a
 * network call. A key injected at build time (JARVIS_CREATOR_KEY) or one set on the
 * device is accepted as well - any one of them is enough.
 */
object CreatorKeyRules {

    /** The owner's phrase. Always accepted, so the creator can never be locked out. */
    const val BUILT_IN_KEY = "AHONA-I-LOVE-YOU"

    const val MIN_KEY_LENGTH = 8
    const val MAX_FAILURES = 5
    const val LOCK_MILLIS = 5 * 60 * 1000L

    fun normalize(value: String?): String =
        (value ?: "").trim().lowercase(java.util.Locale.ROOT).filter { it.isLetterOrDigit() }

    fun usable(normalizedKey: String): Boolean = normalizedKey.length >= MIN_KEY_LENGTH

    /** Constant-work equality: same length, every character compared. */
    fun matches(expected: String?, candidate: String?): Boolean {
        val a = normalize(expected)
        val b = normalize(candidate)
        if (!usable(a) || !usable(b) || a.length != b.length) return false
        var differences = 0
        for (index in a.indices) {
            if (a[index] != b[index]) differences++
        }
        return differences == 0
    }

    fun shouldLock(failures: Int): Boolean = failures >= MAX_FAILURES

    fun lockUntil(now: Long): Long = now + LOCK_MILLIS
}