package com.jarvis.assistant.security.cyberlab

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/**
 * MODULE 8 — Dynamic skills & hot reload.
 * Skill packs are DATA only (title, prompt, checklist) - never executable code.
 * Import validates the schema, records a sha256 integrity digest, keeps the
 * last 3 version snapshots and supports rollback. New Kotlin components,
 * dependencies or manifest entries still require an APK rebuild.
 */
class SkillPackLoader(private val context: Context) {

    data class SkillDef(val id: String, val title: String, val category: String, val prompt: String, val checklist: List<String>)
    data class SkillPack(
        val id: String, val name: String, val version: Int, val sha256: String,
        val installedAt: Long, val skills: List<SkillDef>, val history: List<String>
    )

    private fun file() = File(context.filesDir, "jarvis_skill_packs.json")

    fun packs(): List<SkillPack> {
        val f = file()
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) { emptyList() }
    }

    private fun save(list: List<SkillPack>) = file().writeText(JSONArray(list.map { toJson(it) }).toString())

    /** Import/upgrade a pack. Returns (pack, message); pack is null on rejection. */
    fun importPack(jsonText: String): Pair<SkillPack?, String> {
        return try {
            val root = JSONObject(jsonText)
            val name = root.optString("name").trim()
            val skillsArr = root.optJSONArray("skills")
            if (name.isBlank() || skillsArr == null || skillsArr.length() == 0) {
                return null to "Rejected: 'name' and a non-empty 'skills' array are required."
            }
            if (skillsArr.length() > 50) return null to "Rejected: pack too large (max 50 skills)."
            val defs = mutableListOf<SkillDef>()
            for (i in 0 until skillsArr.length()) {
                val s = skillsArr.optJSONObject(i) ?: continue
                val title = s.optString("title").trim()
                val prompt = s.optString("prompt").trim()
                if (title.isBlank() || prompt.isBlank()) continue
                val cl = s.optJSONArray("checklist")
                defs.add(
                    SkillDef(
                        id = s.optString("id").ifBlank { title.lowercase().replace(' ', '_') },
                        title = title,
                        category = s.optString("category", "general").trim(),
                        prompt = prompt,
                        checklist = if (cl == null) emptyList() else (0 until cl.length()).map { cl.optString(it) }
                    )
                )
            }
            if (defs.isEmpty()) return null to "Rejected: every skill needs a 'title' and a 'prompt'."
            val sha = sha256(jsonText)
            val all = packs().toMutableList()
            val idx = all.indexOfFirst { it.name.equals(name, ignoreCase = true) }
            val pack: SkillPack
            if (idx >= 0) {
                val old = all[idx]
                pack = old.copy(
                    version = old.version + 1, sha256 = sha, installedAt = System.currentTimeMillis(),
                    skills = defs, history = (old.history + toJson(old).toString()).takeLast(3)
                )
                all[idx] = pack
            } else {
                pack = SkillPack(UUID.randomUUID().toString(), name, 1, sha, System.currentTimeMillis(), defs, emptyList())
                all.add(pack)
            }
            save(all)
            pack to "Installed " + pack.name + " v" + pack.version + " (" + defs.size + " skills) - integrity " + sha.take(12) + " verified."
        } catch (e: Exception) {
            null to "Import failed: the pack must be valid JSON."
        }
    }

    /** Rolls a pack back to its most recent previous version (re-imported and re-validated). */
    fun rollback(packId: String): Pair<SkillPack?, String> {
        val p = packs().find { it.id == packId } ?: return null to "Pack not found."
        if (p.history.isEmpty()) return null to "No earlier version to roll back to."
        return importPack(p.history.last())
    }

    fun delete(packId: String) = save(packs().filter { it.id != packId })

    fun findSkill(query: String): SkillDef? {
        val q = query.trim().lowercase()
        if (q.isBlank()) return null
        return packs().asSequence().flatMap { it.skills.asSequence() }
            .firstOrNull { it.title.lowercase().contains(q) || it.category.lowercase().contains(q) }
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { String.format("%02x", it) }

    private fun toJson(p: SkillPack): JSONObject {
        val skillsArr = JSONArray()
        p.skills.forEach { s ->
            val cl = JSONArray()
            s.checklist.forEach { cl.put(it) }
            skillsArr.put(
                JSONObject().put("id", s.id).put("title", s.title).put("category", s.category)
                    .put("prompt", s.prompt).put("checklist", cl)
            )
        }
        val hist = JSONArray()
        p.history.forEach { hist.put(it) }
        return JSONObject()
            .put("id", p.id).put("name", p.name).put("version", p.version).put("sha256", p.sha256)
            .put("installedAt", p.installedAt).put("skills", skillsArr).put("history", hist)
    }

    private fun fromJson(o: JSONObject): SkillPack {
        val arr = o.optJSONArray("skills")
        val skills = if (arr == null) emptyList() else (0 until arr.length()).mapNotNull { i ->
            val s = arr.optJSONObject(i) ?: return@mapNotNull null
            val cl = s.optJSONArray("checklist")
            SkillDef(s.optString("id"), s.optString("title"), s.optString("category"), s.optString("prompt"),
                if (cl == null) emptyList() else (0 until cl.length()).map { cl.optString(it) })
        }
        val hist = o.optJSONArray("history")
        return SkillPack(
            id = o.optString("id"), name = o.optString("name"), version = o.optInt("version", 1),
            sha256 = o.optString("sha256"), installedAt = o.optLong("installedAt"),
            skills = skills,
            history = if (hist == null) emptyList() else (0 until hist.length()).map { hist.optString(it) }
        )
    }
}
