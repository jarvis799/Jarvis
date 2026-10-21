package com.jarvis.assistant.terminal

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.util.Locale
import java.util.zip.ZipInputStream

/**
 * DEV STYLE: "run <file name>" - the creator names a file or project, Jarvis
 * finds it on storage (storage permission is already granted), runs it with
 * npm inside its own built-in terminal and opens the localhost preview in
 * Chrome. Plain .js files run with node and the output is reported back.
 */
object DevProjectRunner {

    data class RunResult(val success: Boolean, val message: String)

    private val SKIP = setOf(
        "Android", "DCIM", "Pictures", "Movies", "Music", ".thumbnails",
        ".Trash", "LOST.DIR", "LOST.Dir", "lost.dir", "WhatsApp"
    )

    /** Pull the file / project name out of a spoken run command. */
    fun extractName(spokenLower: String): String {
        val noise = setOf(
            "jarvis", "run", "running", "chalao", "chalu", "koro", "kore", "dao", "do", "de",
            "diye", "npm", "node", "localhost", "local", "host", "preview", "chrome", "browser",
            "open", "khule", "khul", "file", "project", "folder", "ta", "tai", "amar", "my",
            "this", "the", "please", "plz", "start", "started", "server", "a", "an", "to", "and",
            "install", "background", "website", "web", "site", "korei", "koroa"
        )
        return spokenLower.split(Regex("[^a-z0-9_-]+"))
            .filter { it.isNotBlank() && it !in noise }
            .joinToString(" ")
    }

    suspend fun runByName(context: Context, name: String): RunResult = withContext(Dispatchers.IO) {
        try {
            val check = TerminalManager.runSuspend("command -v npm", 10000)
            if (check.exitCode != 0)
                return@withContext RunResult(false, "Boss, terminal e npm nei - Developer Studio theke Node.js install kore abar bolen.")

            val found = search(name)
                ?: return@withContext RunResult(false, "Boss, " + name + " namer kono file ba project storage e khuje pelam na. Download ba Documents e ache kina dekhe abar bolen.")

            var target = found
            if (found.name.lowercase(Locale.ROOT).endsWith(".zip")) {
                val dir = unzip(found)
                    ?: return@withContext RunResult(false, "Boss, " + found.name + " unzip korte parlam na - file ta nosto moto lagche.")
                target = findPackageDir(dir) ?: dir
            }
            val packageDir = if (target.isDirectory) findPackageDir(target) else null

            if (packageDir == null) {
                if (target.isFile && (target.name.endsWith(".js") || target.name.endsWith(".mjs"))) {
                    val run = TerminalManager.runSuspend("node '" + target.absolutePath + "'", 180000)
                    return@withContext if (run.exitCode == 0)
                        RunResult(true, "Hoise Boss! " + target.name + " chalu hoyechhe. Output: " + run.output.take(1500))
                    else
                        RunResult(false, "Boss, " + target.name + " run korar somoy error elo: " + run.output.take(600))
                }
                return@withContext RunResult(false, "Boss, " + target.name + " ta runnable project na - ei folder e package.json nei.")
            }

            val npmDir = terminalPath(packageDir)
                ?: return@withContext RunResult(false, "Boss, terminal ei path ta dekhche na: " + packageDir.absolutePath)

            if (!File(packageDir, "node_modules").exists()) {
                val ins = TerminalManager.runSuspend(
                    "cd '" + npmDir + "' && npm install --no-audit --no-fund --loglevel=error 2>&1 | tail -n 4",
                    600000
                )
                if (ins.exitCode != 0)
                    return@withContext RunResult(false, "Boss, ei project er npm install fail korlo: " + ins.output.take(400))
            }

            val script = pickScript(File(packageDir, "package.json"))
                ?: return@withContext RunResult(false, "Boss, ei project er package.json e kono run script nei - tai chalu korte parlam na.")

            TerminalManager.runSuspend(
                "cd '" + npmDir + "' && rm -f .jarvis-server.log && (nohup npm run " + script + " > .jarvis-server.log 2>&1 &) && echo DETACHED",
                15000
            )

            var port = ""
            for (i in 1..40) {
                delay(3000)
                val log = TerminalManager.runSuspend(
                    "cd '" + npmDir + "' && tail -c 4000 .jarvis-server.log 2>/dev/null",
                    8000
                )
                val m = Regex("(?:localhost|127\\.0\\.0\\.1|0\\.0\\.0\\.0):(\\d{2,5})").find(log.output)
                if (m != null) { port = m.groupValues[1]; break }
            }
            if (port.isBlank())
                return@withContext RunResult(false, "Boss, server background e start holo kintu 2 minute odike localhost URL print holo na - package.json er script ta thik moto kaj korche kina dekhen.")

            val url = "http://localhost:" + port
            openInChrome(context, url)
            RunResult(true, "Hoise Boss! " + name + " chalu hoye geche - Chrome e " + url + " preview khule dilam. Server background e cholche, jar muloto kaj cholbe.")
        } catch (e: Exception) {
            RunResult(false, "Boss, kichu ekta problem holo: " + (e.message ?: "unknown error") + " - bug log e save korechi.")
        }
    }

    /** Bounded search over shared storage for the spoken name. */
    private fun search(name: String): File? {
        val n = name.trim().lowercase(Locale.ROOT)
        if (n.isBlank()) return null
        val deadline = System.currentTimeMillis() + 20000
        val queue = ArrayDeque<Pair<File, Int>>()
        queue.add(Environment.getExternalStorageDirectory() to 0)
        var best: Pair<File, Int>? = null
        while (queue.isNotEmpty()) {
            if (System.currentTimeMillis() > deadline) break
            val (dir, depth) = queue.removeFirst()
            val children = dir.listFiles() ?: continue
            for (child in children) {
                if (depth < 4 && child.isDirectory && child.name !in SKIP) queue.add(child to depth + 1)
                val s = score(child, n) ?: continue
                if (best == null || s > best.second) best = child to s
            }
        }
        return best?.first
    }

    private fun score(f: File, n: String): Int? {
        val base = f.name.substringBeforeLast(".").lowercase(Locale.ROOT)
        val full = f.name.lowercase(Locale.ROOT)
        return when {
            f.isDirectory && base == n -> 100
            f.isDirectory && n.length >= 3 && base.contains(n) -> 55
            f.isFile && (full.endsWith(".zip") || full.endsWith(".tgz")) && base.contains(n) -> 80
            f.isFile && (full.endsWith(".js") || full.endsWith(".mjs")) && base.contains(n) -> 45
            else -> null
        }
    }

    /** The project root: package.json in this folder, or one level down. */
    private fun findPackageDir(dir: File): File? {
        if (File(dir, "package.json").isFile) return dir
        val children = dir.listFiles() ?: return null
        return children.firstOrNull { it.isDirectory && File(it, "package.json").isFile }
    }

    /** The path as the built-in terminal itself sees shared storage. */
    private suspend fun terminalPath(dir: File): String? {
        val abs = dir.absolutePath
        val candidates = listOf(abs, abs.replace("/storage/emulated/0", "/sdcard"))
        for (c in candidates) {
            if (TerminalManager.runSuspend("test -d '" + c + "'", 8000).exitCode == 0) return c
        }
        return null
    }

    /** dev is preferred, then start, then the first script available. */
    private fun pickScript(packageJson: File): String? {
        val scripts = runCatching { JSONObject(packageJson.readText()).optJSONObject("scripts") }.getOrNull() ?: return null
        return when {
            scripts.has("dev") -> "dev"
            scripts.has("start") -> "start"
            scripts.length() > 0 -> scripts.keys().next()
            else -> null
        }
    }

    private fun unzip(zip: File): File? = runCatching {
        val outDir = File(zip.parentFile, zip.name.substringBeforeLast(".") + "_project")
        if (!File(outDir, "package.json").isFile) {
            outDir.mkdirs()
            ZipInputStream(BufferedInputStream(FileInputStream(zip))).use { zin ->
                var entry = zin.nextEntry
                while (entry != null) {
                    if (!entry.name.contains("..")) {
                        val out = File(outDir, entry.name)
                        if (entry.isDirectory) out.mkdirs() else {
                            out.parentFile?.mkdirs()
                            out.outputStream().use { zin.copyTo(it) }
                        }
                    }
                    entry = zin.nextEntry
                }
            }
        }
        outDir
    }.getOrNull()

    private fun openInChrome(context: Context, url: String) {
        // "package" is a reserved keyword in Kotlin, so setPackage() is used
        // instead of an apply { package = ... } block.
        val chrome = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        chrome.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        chrome.setPackage("com.android.chrome")
        runCatching { context.startActivity(chrome) }.recoverCatching {
            val anyBrowser = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            anyBrowser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(anyBrowser)
        }
    }
}
