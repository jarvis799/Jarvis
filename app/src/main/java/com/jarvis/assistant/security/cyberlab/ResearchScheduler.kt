package com.jarvis.assistant.security.cyberlab

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Self-evolving research scheduler.
 * Turns findings into a prioritised research queue, backs off on failures, and
 * keeps a ledger of what was actually learned and promoted.
 */
class ResearchScheduler(context: Context) {

    data class Task(
        val id: String,
        val topic: String,
        val trigger: String,
        val priority: Int,
        val status: String,
        val attempts: Int,
        val createdAt: Long,
        val lastAttemptAt: Long,
        val nextEligibleAt: Long,
        val result: String
    )

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enqueue(topic: String, trigger: String, priority: Int = 3): Task? {
        val clean = topic.trim()
        if (clean.isEmpty()) return null
        val list = read()
        if (list.any { it.topic.equals(clean, ignoreCase = true) && it.status == "QUEUED" }) return null
        val task = Task(
            id = java.util.UUID.nameUUIDFromBytes(("research-task|" + trigger + "|" + clean).toByteArray()).toString(),
            topic = clean,
            trigger = trigger,
            priority = priority.coerceIn(1, 5),
            status = "QUEUED",
            attempts = 0,
            createdAt = System.currentTimeMillis(),
            lastAttemptAt = 0L,
            nextEligibleAt = 0L,
            result = ""
        )
        write(list + task)
        return task
    }

    fun enqueueFromFindings(findings: List<FindingsStore.Finding>): Int {
        var queued = 0
        findings.filter { it.status == "OPEN" }.forEach { finding ->
            val priority = when (finding.severity) {
                "CRITICAL" -> 1
                "HIGH" -> 2
                "MEDIUM" -> 3
                else -> 4
            }
            if (enqueue("Investigate: " + finding.title + " (" + finding.component + ")", "finding", priority) != null) queued += 1
        }
        return queued
    }

    fun due(now: Long = System.currentTimeMillis()): List<Task> =
        read().filter { it.status == "QUEUED" && it.nextEligibleAt <= now }
            .sortedWith(compareBy({ it.priority }, { it.createdAt }))

    fun next(): Task? = due().firstOrNull()

    fun begin(id: String): Task? {
        val list = read().toMutableList()
        val index = list.indexOfFirst { it.id == id }
        if (index < 0) return null
        val updated = list[index].copy(status = "RUNNING", attempts = list[index].attempts + 1, lastAttemptAt = System.currentTimeMillis())
        list[index] = updated
        write(list)
        return updated
    }

    fun succeed(id: String, result: String) {
        val list = read().toMutableList()
        val index = list.indexOfFirst { it.id == id }
        if (index < 0) return
        val done = list[index].copy(status = "DONE", result = result)
        list[index] = done
        write(list)
        promote(done.topic + " -> " + result)
    }

    fun fail(id: String, reason: String) {
        val list = read().toMutableList()
        val index = list.indexOfFirst { it.id == id }
        if (index < 0) return
        val task = list[index]
        if (task.attempts >= MAX_ATTEMPTS) {
            list[index] = task.copy(status = "RETIRED", result = "stopped after " + task.attempts + " attempts: " + reason)
        } else {
            val waitMillis = 60000L * (1L shl task.attempts)
            list[index] = task.copy(
                status = "QUEUED",
                nextEligibleAt = System.currentTimeMillis() + waitMillis,
                result = "retry in " + (waitMillis / 1000L) + "s: " + reason
            )
        }
        write(list)
    }

    fun ledger(): List<String> {
        val raw = prefs.getString(LEDGER_KEY, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            val list = ArrayList<String>()
            for (index in 0 until array.length()) list.add(array.optString(index))
            list
        } catch (error: Throwable) {
            emptyList()
        }
    }

    fun summary(): String {
        val tasks = read()
        val queued = tasks.count { it.status == "QUEUED" }
        val running = tasks.count { it.status == "RUNNING" }
        val done = tasks.count { it.status == "DONE" }
        val retired = tasks.count { it.status == "RETIRED" }
        return "queued: " + queued + " | running: " + running + " | done: " + done + " | retired: " + retired + " | learned: " + ledger().size
    }

    fun reset() {
        write(emptyList())
        prefs.edit().remove(LEDGER_KEY).apply()
    }

    private fun promote(entry: String) {
        val list = ledger().toMutableList()
        list.add(System.currentTimeMillis().toString() + " | " + entry)
        while (list.size > MAX_LEDGER) list.removeAt(0)
        val array = JSONArray()
        list.forEach { array.put(it) }
        prefs.edit().putString(LEDGER_KEY, array.toString()).apply()
    }

    private fun read(): List<Task> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            val list = ArrayList<Task>()
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                list.add(
                    Task(
                        id = item.optString("id"),
                        topic = item.optString("topic"),
                        trigger = item.optString("trigger"),
                        priority = item.optInt("priority", 3),
                        status = item.optString("status", "QUEUED"),
                        attempts = item.optInt("attempts", 0),
                        createdAt = item.optLong("createdAt"),
                        lastAttemptAt = item.optLong("lastAttemptAt"),
                        nextEligibleAt = item.optLong("nextEligibleAt"),
                        result = item.optString("result")
                    )
                )
            }
            list
        } catch (error: Throwable) {
            emptyList()
        }
    }

    private fun write(list: List<Task>) {
        val array = JSONArray()
        list.forEach { task ->
            val item = JSONObject()
            item.put("id", task.id)
            item.put("topic", task.topic)
            item.put("trigger", task.trigger)
            item.put("priority", task.priority)
            item.put("status", task.status)
            item.put("attempts", task.attempts)
            item.put("createdAt", task.createdAt)
            item.put("lastAttemptAt", task.lastAttemptAt)
            item.put("nextEligibleAt", task.nextEligibleAt)
            item.put("result", task.result)
            array.put(item)
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    companion object {
        private const val PREFS = "jarvis_cyberlab"
        private const val KEY = "research_tasks"
        private const val LEDGER_KEY = "research_ledger"
        private const val MAX_ATTEMPTS = 4
        private const val MAX_LEDGER = 60
    }
}
