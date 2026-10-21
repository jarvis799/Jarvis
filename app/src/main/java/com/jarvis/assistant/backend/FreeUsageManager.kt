package com.jarvis.assistant.backend

import android.content.Context

/**
 * DEV STYLE: every feature is free and unlimited for every user.
 * The old 30-minutes-a-day licence meter is gone; this API stays so
 * existing callers keep working without limits.
 */
object FreeUsageManager {
    const val DAILY_FREE_MS = Long.MAX_VALUE

    fun remainingMs(context: Context): Long = Long.MAX_VALUE

    fun canStartSession(context: Context): Boolean = true

    fun addUsage(context: Context, ms: Long): Boolean = true
}
