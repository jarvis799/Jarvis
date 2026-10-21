package com.jarvis.assistant.devstudio

import android.content.Context
import android.net.Uri
import com.jarvis.assistant.JarvisApp
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * App-managed project workspace: JarvisProjects/<project>/ plus exported ZIP files.
 * Generated code is never allowed to contain a stored credential.
 */
class ProjectWorkspace(context: Context = JarvisApp.instance) {

    private val appContext = context.applicationContext

    fun root(): File = File(appContext.filesDir, "JarvisProjects").apply { mkdirs() }

    fun exportsDir(): File = File(root(), "_exports").apply { mkdirs() }

    fun projectDir(name: String): File = File(root(), DevStudioRules.sanitizeProjectName(name)).apply { mkdirs() }

    fun projectExists(name: String): Boolean {
        val dir = File(root(), DevStudioRules.sanitizeProjectName(name))
        return dir.isDirectory && (dir.listFiles()?.isNotEmpty() ?: false)
    }

    fun uniqueProjectName(base: String): String {
        val clean = DevStudioRules.sanitizeProjectName(base)
        if (!projectExists(clean)) return clean
        var i = 2
        while (projectExists(clean + "-" + i)) i++
        return clean + "-" + i
    }

    fun listProjects(): List<File> = (root().listFiles() ?: emptyArray())
        .filter { it.isDirectory && !it.name.startsWith("_") }
        .sortedByDescending { it.lastModified() }

    fun relativeFiles(projectName: String, includeBuildOutput: Boolean = false): List<String> {
        val base = projectDir(projectName)
        val out = mutableListOf<String>()
        base.walkTopDown().filter { it.isFile }.forEach { file ->
            val rel = file.relativeTo(base).path.replace('\\', '/')
            if (!DevStudioRules.isExcludedPath(rel, includeBuildOutput)) out.add(rel)
        }
        return out.sorted()
    }

    /** Writes generated files safely. Returns written paths plus skipped ones (unsafe path or credential leak). */
    fun writeFiles(projectName: String, files: List<ProjectFile>, secrets: List<String>): Pair<List<String>, List<String>> {
        val base = projectDir(projectName)
        val written = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        files.forEach { file ->
            val path = file.path.replace('\\', '/').trim().trimStart('/')
            if (!DevStudioRules.isSafeRelativePath(path) || DevStudioRules.isExcludedPath(path)) {
                skipped.add(path.ifBlank { "(unnamed)" })
                return@forEach
            }
            if (DevStudioRules.containsSecret(file.content, secrets)) {
                skipped.add(path + " (would expose a stored credential)")
                return@forEach
            }
            val target = File(base, path)
            target.parentFile?.mkdirs()
            if (target.isFile && target.readText() == file.content) return@forEach
            target.writeText(file.content)
            written.add(path)
        }
        return written to skipped
    }

    fun readFile(projectName: String, path: String): String? {
        if (!DevStudioRules.isSafeRelativePath(path)) return null
        val file = File(projectDir(projectName), path)
        return if (file.isFile) runCatching { file.readText() }.getOrNull() else null
    }

    /** Builds the source ZIP. Existing archives are never overwritten. */
    fun buildZip(projectName: String, includeBuildOutput: Boolean = false): File {
        val clean = DevStudioRules.sanitizeProjectName(projectName)
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        var zip = File(exportsDir(), DevStudioRules.zipFileName(clean, date))
        var i = 2
        while (zip.exists()) {
            zip = File(exportsDir(), "jarvis-" + clean + "-" + date + "-" + i + ".zip")
            i++
        }
        val base = projectDir(projectName)
        ZipOutputStream(zip.outputStream().buffered()).use { out ->
            val files = base.walkTopDown().filter { it.isFile }.toList()
            files.forEach { file ->
                val rel = file.relativeTo(base).path.replace('\\', '/')
                if (DevStudioRules.isExcludedPath(rel, includeBuildOutput)) return@forEach
                out.putNextEntry(ZipEntry(rel))
                file.inputStream().use { it.copyTo(out) }
                out.closeEntry()
            }
        }
        return zip
    }

    fun zipEntryNames(zip: File): List<String> {
        val names = mutableListOf<String>()
        runCatching {
            ZipInputStream(zip.inputStream().buffered()).use { input ->
                var entry = input.nextEntry
                while (entry != null) {
                    names.add(entry.name)
                    input.closeEntry()
                    entry = input.nextEntry
                }
            }
        }
        return names
    }

    fun exportZip(zip: File, destination: Uri): Boolean = runCatching {
        appContext.contentResolver.openOutputStream(destination)?.use { out ->
            zip.inputStream().use { it.copyTo(out) }
        } != null
    }.getOrDefault(false)

    fun deleteZip(zip: File): Boolean = zip.exists() && zip.delete()
}
