package com.jarvis.assistant.memory

import android.content.Context
import com.jarvis.assistant.data.memory.MemoryDatabase

/**
 * Retention policy for Jarvis long-term memory.
 *
 * 30 days is the guaranteed minimum availability, never an automatic wipe: the owner can raise
 * it (or lower it) and nothing is deleted until this policy is asked to run.
 */
object MemoryRetention {

    const val DEFAULT_RETENTION_DAYS = 30
    private const val STORE = "jarvis_memory_retention"
    private const val KEY_DAYS = "retention_days"
    private const val DAY_MS = 86_400_000L

    data class Outcome(val deleted: Int, val kept: Int, val days: Int)

    fun retentionDays(context: Context): Int {
        val days = context.applicationContext
            .getSharedPreferences(STORE, Context.MODE_PRIVATE)
            .getInt(KEY_DAYS, DEFAULT_RETENTION_DAYS)
        return if (days < 1) DEFAULT_RETENTION_DAYS else days
    }

    fun setRetentionDays(context: Context, days: Int) {
        if (days < 1) return
        context.applicationContext
            .getSharedPreferences(STORE, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_DAYS, days)
            .apply()
    }

    /** Oldest timestamp still inside the window. Memories at or after this instant are kept. */
    fun cutoff(now: Long, days: Int = DEFAULT_RETENTION_DAYS): Long = now - days.toLong() * DAY_MS

    /** Deletes only memories older than the retention window. Returns what actually happened. */
    suspend fun prune(context: Context, now: Long = System.currentTimeMillis()): Outcome {
        val dao = MemoryDatabase.getInstance(context.applicationContext).memoryDao()
        val days = retentionDays(context)
        val limit = cutoff(now, days)
        val all = dao.getAllMemories()
        var deleted = 0
        for (memory in all) {
            if (memory.timestamp < limit) {
                dao.deleteMemoryById(memory.id)
                deleted++
            }
        }
        return Outcome(deleted = deleted, kept = all.size - deleted, days = days)
    }
}
