package com.jarvis.assistant.runtime.update

import org.json.JSONException
import org.json.JSONObject

/**
 * Versioned runtime update manifest (schema v2). Carries declarative runtime
 * payloads (prompts, command aliases, remote config, allowlisted workflows) -
 * never executable Kotlin/DEX/native code. Native changes ship as an APK update.
 *
 * A subset of fields forms a canonical string signed (SHA256withRSA) by the
 * release process; the app verifies it against a trusted public key bundled in
 * the build before trusting any checksum or payload. See ManifestSignatureVerifier.
 */
data class RuntimeModule(
    val id: String,
    val name: String,
    val version: String,
    val type: String,
    val downloadUrl: String,
    val size: Long,
    val sha256: String,
    val entry: String,
    val dependsOn: List<String>
)

data class RuntimeCompatibility(
    val minAppVersion: String,
    val maxAppVersion: String,
    val minRuntimeVersion: String,
    val sdkMin: Int
)

data class UpdateManifest(
    val schemaVersion: Int,
    val runtimeVersion: String,
    val minimumSupportedAppVersion: String,
    val releaseNotes: String,
    val publishedAt: String,
    val expiresAt: String,
    val updateType: String,
    val modules: List<RuntimeModule>,
    val compatibility: RuntimeCompatibility,
    val signingKeyId: String,
    val signature: String
) {
    companion object {
        const val SUPPORTED_SCHEMA = 2
        val MODULE_TYPES = setOf("prompt_pack", "commands", "remote_config", "workflow", "web_tool")
        val UPDATE_TYPES = setOf("runtime", "apk")
        private val ID = Regex("[a-z0-9][a-z0-9-]{1,39}")
        private val SHA = Regex("[a-fA-F0-9]{64}")
        const val MAX_MODULE_BYTES = 50L * 1024 * 1024

        @Throws(RuntimeUpdateException::class)
        fun parse(json: String): UpdateManifest {
            val o = try { JSONObject(json) } catch (e: JSONException) {
                throw RuntimeUpdateException("Malformed manifest JSON", e)
            }
            val schema = o.optInt("schema_version", -1)
            if (schema != SUPPORTED_SCHEMA)
                throw RuntimeUpdateException("Unsupported manifest schema_version=" + schema)
            val type = o.optString("update_type").ifBlank { "runtime" }
            if (type !in UPDATE_TYPES) throw RuntimeUpdateException("Invalid update_type=" + type)
            val modulesArr = o.optJSONArray("modules")
                ?: throw RuntimeUpdateException("Missing modules array")
            if (modulesArr.length() == 0) throw RuntimeUpdateException("Manifest has no modules")
            val modules = mutableListOf<RuntimeModule>()
            val seenIds = mutableSetOf<String>()
            for (i in 0 until modulesArr.length()) {
                val m = modulesArr.optJSONObject(i)
                    ?: throw RuntimeUpdateException("module[" + i + "] not an object")
                val id = m.optString("id").trim()
                if (!ID.matches(id)) throw RuntimeUpdateException("module[" + i + "] bad id")
                if (!seenIds.add(id)) throw RuntimeUpdateException("module[" + i + "] duplicate id=" + id)
                val mtype = m.optString("type").trim().lowercase()
                if (mtype !in MODULE_TYPES) throw RuntimeUpdateException("module[" + i + "] invalid type=" + mtype)
                val url = m.optString("download_url").trim()
                if (!url.startsWith("https://")) throw RuntimeUpdateException("module[" + i + "] download_url must be https")
                val sha = m.optString("sha256").trim().lowercase()
                if (!SHA.matches(sha)) throw RuntimeUpdateException("module[" + i + "] sha256 not 64 hex")
                val size = m.optLong("size", 0L)
                if (size <= 0L || size > MAX_MODULE_BYTES) throw RuntimeUpdateException("module[" + i + "] size out of range")
                modules += RuntimeModule(
                    id, m.optString("name").trim(), m.optString("version").trim().ifBlank { "1.0.0" },
                    mtype, url, size, sha, m.optString("entry").trim(),
                    jsonArrToStringList(m.optJSONArray("depends_on"))
                )
            }
            val compat = o.optJSONObject("compatibility") ?: JSONObject()
            val compatObj = RuntimeCompatibility(
                compat.optString("min_app_version").ifBlank { "1.0.0" },
                compat.optString("max_app_version").ifBlank { "" },
                compat.optString("min_runtime_version").ifBlank { "" },
                compat.optInt("sdk_min", 0)
            )
            val pubAt = o.optString("published_at").trim()
            if (!pubAt.startsWith("20")) throw RuntimeUpdateException("published_at missing/invalid")
            return UpdateManifest(
                schema, o.optString("runtime_version").trim(), o.optString("minimum_supported_app_version").ifBlank { "1.0.0" },
                o.optString("release_notes").trim(), pubAt, o.optString("expires_at").trim(),
                type, modules, compatObj, o.optString("signing_key_id").trim(), o.optString("signature").trim()
            )
        }

        private fun jsonArrToStringList(a: org.json.JSONArray?): List<String> {
            if (a == null) return emptyList()
            val out = mutableListOf<String>()
            for (i in 0 until a.length()) out += a.optString(i)
            return out
        }

        /** Semver-ish compare: negative if a<b, 0 equal, positive if a>b. */
        fun compareVersions(a: String, b: String): Int {
            val pa = a.split(".").map { it.toIntOrNull() ?: 0 }
            val pb = b.split(".").map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(pa.size, pb.size)) {
                val va = pa.getOrNull(i) ?: 0
                val vb = pb.getOrNull(i) ?: 0
                if (va != vb) return va - vb
            }
            return 0
        }
    }
}
