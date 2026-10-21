package com.jarvis.assistant.runtime.update

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Applies a verified runtime update manifest: downloads each module to a
 *  staging file, verifies its SHA-256, atomically moves it into place, marks
 *  it active, and bumps the runtime version. If a module fails after others
 *  have activated, the updater rolls the runtime version back and re-activates
 *  the prior module versions, so the device never runs a half-applied runtime.
 *  Cancellation is cooperative via [cancelled]. */
class RuntimeUpdater(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS).build()

    val cancelled = AtomicBoolean(false)

    sealed class Outcome {
        data class Applied(val runtimeVersion: String, val modules: List<String>) : Outcome()
        data class Failed(val message: String, val rolledBack: Boolean) : Outcome()
    }

    fun apply(
        manifest: UpdateManifest,
        onProgress: (moduleIndex: Int, moduleCount: Int, percent: Int, moduleName: String) -> Unit
    ): Outcome {
        cancelled.set(false)
        val previousRuntime = ModuleStore.activeRuntimeVersion(context)
        val modulesDir = File(context.filesDir, "runtime-modules").apply { mkdirs() }
        val stagingDir = File(modulesDir, ".staging").apply { mkdirs() }
        val applied = mutableListOf<RuntimeModule>()
        for ((index, module) in manifest.modules.withIndex()) {
            if (cancelled.get()) return Outcome.Failed("Cancelled.", rolledBack(applied, previousRuntime))
            onProgress(index, manifest.modules.size, 0, module.name)
            val staging = File(stagingDir, module.id + "-" + module.version + ".bin")
            try {
                downloadTo(module, staging) { p -> onProgress(index, manifest.modules.size, p, module.name) }
                if (cancelled.get()) { staging.delete(); return Outcome.Failed("Cancelled.", rolledBack(applied, previousRuntime)) }
                val sha = sha256(staging)
                if (!sha.equals(module.sha256, ignoreCase = true)) {
                    staging.delete()
                    return Outcome.Failed("Checksum mismatch for " + module.name + ".", rolledBack(applied, previousRuntime))
                }
                val moduleDir = File(modulesDir, module.id).apply { mkdirs() }
                val finalFile = File(moduleDir, module.version + ".bin")
                if (finalFile.exists()) finalFile.delete()
                if (!staging.renameTo(finalFile))
                    throw IOException("Could not move " + module.name + " into place.")
                ModuleStore.upsert(context, ModuleStore.InstalledModule(
                    module.id, module.name, module.version, module.type, module.sha256,
                    finalFile.absolutePath, false
                ))
                ModuleStore.setActive(context, module.id, module.version)
                applied += module
            } catch (e: Exception) {
                return Outcome.Failed(e.message ?: ("Failed to apply " + module.name + "."), rolledBack(applied, previousRuntime))
            }
        }
        ModuleStore.setRuntimeVersion(context, manifest.runtimeVersion)
        return Outcome.Applied(manifest.runtimeVersion, applied.map { it.name })
    }

    private fun rolledBack(applied: MutableList<RuntimeModule>, previousRuntime: String): Boolean {
        if (applied.isEmpty()) return false
        var anyRolled = false
        for (module in applied) {
            val failed = ModuleStore.active(context, module.id)
            val prev = ModuleStore.previousVersion(context, module.id)
            if (prev != null) {
                ModuleStore.setActive(context, module.id, prev.version)
                if (failed != null) { File(failed.filePath).delete(); ModuleStore.remove(context, module.id, failed.version) }
                anyRolled = true
            } else if (failed != null) {
                File(failed.filePath).delete()
                ModuleStore.remove(context, module.id, failed.version)
                anyRolled = true
            }
        }
        if (anyRolled) ModuleStore.setRuntimeVersion(context, previousRuntime)
        return anyRolled
    }

    /** Reverts [id] to its retained previous version. Returns false if there
     *  is nothing to roll back to. */
    fun rollback(id: String): Boolean {
        val prev = ModuleStore.previousVersion(context, id) ?: return false
        val current = ModuleStore.active(context, id)
        ModuleStore.setActive(context, id, prev.version)
        if (current != null && current.version != prev.version) {
            File(current.filePath).delete()
            ModuleStore.remove(context, id, current.version)
        }
        return true
    }

    private fun downloadTo(module: RuntimeModule, dest: File, onPercent: (Int) -> Unit) {
        client.newCall(Request.Builder().url(module.downloadUrl).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP " + resp.code)
            val body = resp.body ?: throw IOException("Empty body")
            val total = body.contentLength()
            dest.outputStream().use { out ->
                body.byteStream().use { input ->
                    val buf = ByteArray(16 * 1024)
                    var read = 0; var done = 0L
                    while (input.read(buf).also { read = it } != -1) {
                        if (cancelled.get()) throw IOException("Cancelled")
                        out.write(buf, 0, read)
                        done += read
                        if (total > 0) onPercent(((done * 100) / total).toInt().coerceIn(0, 100))
                    }
                }
            }
        }
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(16 * 1024); var read = 0
            while (input.read(buf).also { read = it } != -1) md.update(buf, 0, read)
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
