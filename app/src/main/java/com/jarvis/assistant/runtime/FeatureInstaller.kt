package com.jarvis.assistant.runtime

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Downloads, verifies and installs runtime feature packages. Installation is
 * atomic with rollback: the previous version survives until the new version
 * activates, and a package that fails any check is never activated.
 */
object FeatureInstaller {

    private const val MAX_PACKAGE_BYTES = 50L * 1024 * 1024
    private const val MAX_FILES = 300
    private val BLOCKED_EXTENSIONS = setOf("dex", "jar", "apk", "so", "class", "kt", "bin", "exe")

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    class InstallException(message: String) : Exception(message)

    fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(65536)
            var n = input.read(buffer)
            while (n != -1) {
                digest.update(buffer, 0, n)
                n = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    /** Streaming download with progress; the SHA-256 is verified against the release manifest. */
    fun download(context: Context, feature: ReleaseFeature, onProgress: (Int) -> Unit): File {
        val tmp = File(context.cacheDir, "feature-" + feature.id + ".zip.part")
        if (tmp.exists()) tmp.delete()
        val request = Request.Builder().url(feature.downloadUrl)
            .header("User-Agent", "Jarvis-RuntimeUpdates/1.0").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw InstallException("Download failed: HTTP " + response.code)
            val body = response.body ?: throw InstallException("Empty download response.")
            val total = body.contentLength()
            if (total > MAX_PACKAGE_BYTES) throw InstallException("Package is larger than the 50 MB limit.")
            var downloaded = 0L
            body.byteStream().use { input ->
                tmp.outputStream().use { output ->
                    val buffer = ByteArray(16384)
                    var read = input.read(buffer)
                    while (read != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (downloaded > MAX_PACKAGE_BYTES) throw InstallException("Package exceeded the 50 MB limit.")
                        if (total > 0) onProgress(((downloaded * 100) / total).toInt().coerceIn(0, 100))
                        read = input.read(buffer)
                    }
                }
            }
        }
        val actual = sha256Of(tmp)
        if (actual != feature.sha256) {
            tmp.delete()
            throw InstallException("Package verification failed - the download did not match its digest.")
        }
        val done = File(context.cacheDir, "feature-" + feature.id + ".zip")
        if (done.exists()) done.delete()
        if (!tmp.renameTo(done)) throw InstallException("Could not stage the downloaded package.")
        return done
    }

    /** Unpacks, validates and installs a verified package, keeping the old version for rollback. */
    fun install(context: Context, zipFile: File, feature: ReleaseFeature): InstalledFeature {
        val dir = FeatureRegistry.featureDir(context, feature.id)
        val staging = File(dir, ".staging").apply { deleteRecursively(); mkdirs() }
        try {
            unpack(zipFile, staging)
            validatePackage(staging, feature)
            val previous = FeatureRegistry.get(context, feature.id)
            val active = File(dir, feature.version)
            if (active.exists()) active.deleteRecursively()
            val backup = File(dir, ".backup")
            backup.deleteRecursively()
            if (previous != null && previous.version != feature.version) {
                File(dir, previous.version).takeIf { it.exists() }?.renameTo(backup)
            }
            if (!staging.renameTo(active)) throw InstallException("Could not finalize the installation.")
            val installed = InstalledFeature(
                id = feature.id, name = feature.name, version = feature.version,
                featureType = feature.featureType, description = feature.description,
                entry = feature.entry, enabled = previous?.enabled ?: true,
                installedAt = System.currentTimeMillis(), previousVersion = previous?.version
            )
            FeatureRegistry.upsert(context, installed)
            return installed
        } catch (e: Exception) {
            staging.deleteRecursively()
            throw e
        }
    }

    /** Restores the previous version after a failed activation. */
    fun rollback(context: Context, id: String): InstalledFeature? {
        val current = FeatureRegistry.get(context, id) ?: return null
        val previous = current.previousVersion ?: return current
        val dir = FeatureRegistry.featureDir(context, id)
        val backup = File(dir, ".backup")
        if (!backup.exists()) return current
        File(dir, current.version).deleteRecursively()
        backup.renameTo(File(dir, previous))
        val restored = current.copy(version = previous, previousVersion = null)
        FeatureRegistry.upsert(context, restored)
        return restored
    }

    /** Deletes the kept backup once the new version is confirmed working. */
    fun confirmActivation(context: Context, id: String) {
        val current = FeatureRegistry.get(context, id) ?: return
        if (current.previousVersion != null) FeatureRegistry.upsert(context, current.copy(previousVersion = null))
        File(FeatureRegistry.featureDir(context, id), ".backup").deleteRecursively()
    }

    fun uninstall(context: Context, id: String) {
        FeatureRegistry.featureDir(context, id).deleteRecursively()
        FeatureRegistry.remove(context, id)
    }

    private fun unpack(zipFile: File, target: File) {
        val canonicalTarget = target.canonicalPath + File.separator
        var count = 0
        ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
            while (true) {
                val entry: ZipEntry = zip.nextEntry ?: break
                try {
                    if (entry.isDirectory) continue
                    val name = entry.name
                    if (name.contains("..")) throw InstallException("Rejected package: unsafe path '" + name + "'.")
                    val out = File(target, name)
                    if (!out.canonicalPath.startsWith(canonicalTarget))
                        throw InstallException("Rejected package: path escapes the sandbox '" + name + "'.")
                    val lower = name.substringAfterLast('.', "").lowercase()
                    if (lower in BLOCKED_EXTENSIONS)
                        throw InstallException("Rejected package: executable content is not allowed ('" + name + "').")
                    if (++count > MAX_FILES) throw InstallException("Rejected package: too many files.")
                    out.parentFile?.mkdirs()
                    out.outputStream().use { output -> zip.copyTo(output, 65536) }
                } finally {
                    zip.closeEntry()
                }
            }
        }
        if (count == 0) throw InstallException("The package is empty.")
    }

    private fun validatePackage(dir: File, feature: ReleaseFeature) {
        val manifest = File(dir, "feature.json")
        if (!manifest.isFile) throw InstallException("Package is missing feature.json.")
        val json = runCatching { JSONObject(manifest.readText()) }
            .getOrElse { throw InstallException("feature.json is not valid JSON.") }
        if (json.optInt("format_version", -1) != FeatureCodec.PACKAGE_FORMAT_VERSION)
            throw InstallException("Unsupported package format version " + json.optInt("format_version", -1) + ".")
        if (json.optString("feature_id") != feature.id)
            throw InstallException("Package id does not match the release manifest.")
        if (json.optString("feature_type") != feature.featureType)
            throw InstallException("Package type does not match the release manifest.")
        when (feature.featureType) {
            "commands" -> {
                val pluginFile = File(dir, "plugin.json")
                if (!pluginFile.isFile) throw InstallException("A commands feature needs plugin.json.")
                val plugin = runCatching { com.jarvis.assistant.plugins.PluginCodec.parse(pluginFile.readText()) }
                    .getOrElse { throw InstallException("plugin.json is not a valid plugin manifest.") }
                if (plugin.id != feature.id) throw InstallException("plugin.json id must match the feature id.")
                com.jarvis.assistant.plugins.PluginManager.validate(plugin)
                    ?.let { throw InstallException("Plugin validation failed: " + it) }
            }
            "web_tool" -> {
                val entry = json.optString("entry").ifBlank { feature.entry }
                if (entry.isBlank()) throw InstallException("A web_tool feature needs an entry page.")
                if (!File(dir, entry).isFile) throw InstallException("Entry page '" + entry + "' is missing from the package.")
            }
            "prompt_pack" -> {
                val prompts = File(dir, "prompts.json")
                if (!prompts.isFile) throw InstallException("A prompt_pack feature needs prompts.json.")
                val arr = runCatching { JSONArray(prompts.readText()) }
                    .getOrElse { throw InstallException("prompts.json is not valid JSON.") }
                if (arr.length() == 0) throw InstallException("prompts.json is empty.")
                for (i in 0 until arr.length()) {
                    val p = arr.optJSONObject(i) ?: throw InstallException("prompts.json entries must be objects.")
                    if (p.optString("name").isBlank() || p.optString("content").isBlank())
                        throw InstallException("Every prompt needs a name and content.")
                }
            }
        }
    }
}
