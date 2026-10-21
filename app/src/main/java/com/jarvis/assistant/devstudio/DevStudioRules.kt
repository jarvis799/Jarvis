package com.jarvis.assistant.devstudio

/**
 * DEV STYLE: pure helpers for the AI Developer Studio. No Android calls here so the
 * rules stay unit-testable on the JVM.
 */
object DevStudioRules {

    private val excludedDirs = setOf(
        "node_modules", ".git", ".gradle", ".idea", ".vscode", "__pycache__", ".venv", "venv",
        ".next", ".nuxt", ".cache", ".parcel-cache", ".turbo", "coverage", ".dart_tool", "Pods"
    )
    private val buildDirs = setOf("build", "dist", "out", ".output")
    private val excludedNames = setOf(
        ".env", "local.properties", "secrets.json", "credentials.json",
        "id_rsa", "id_ed25519", ".npmrc", ".netrc", ".git-credentials", ".DS_Store"
    )
    private val excludedExtensions = setOf("keystore", "jks", "p12", "pfx", "pem", "key", "log", "apk", "aab")

    /** True when a generated source package must NOT contain this path. */
    fun isExcludedPath(relativePath: String, includeBuildOutput: Boolean = false): Boolean {
        val parts = relativePath.replace('\\', '/').trim().trimStart('/').split('/').filter { it.isNotBlank() }
        if (parts.isEmpty()) return true
        if (parts.any { it.lowercase() in excludedDirs }) return true
        if (!includeBuildOutput && parts.any { it.lowercase() in buildDirs }) return true
        val name = parts.last().lowercase()
        if (name in excludedNames || name.startsWith(".env")) return true
        if (name.contains("secret") || name.contains("credential")) return true
        return excludedExtensions.contains(name.substringAfterLast('.', ""))
    }

    fun isSafeRelativePath(path: String): Boolean {
        val cleaned = path.replace('\\', '/').trim()
        if (cleaned.isBlank() || cleaned.startsWith("/") || cleaned.contains(":")) return false
        return cleaned.split('/').none { it.isBlank() || it == ".." || it == "." }
    }

    fun sanitizeProjectName(raw: String): String {
        val cleaned = raw.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        return cleaned.ifBlank { "jarvis-project" }.take(40).trim('-').ifBlank { "jarvis-project" }
    }

    fun zipFileName(projectName: String, date: String): String =
        "jarvis-" + sanitizeProjectName(projectName) + "-" + date + ".zip"

    fun extractJson(raw: String): String {
        var body = raw.replace("```json", "```")
        val fence = body.indexOf("```")
        if (fence >= 0) {
            val open = body.indexOf('\n', fence)
            val close = body.indexOf("```", fence + 3)
            body = if (open > fence && close > open) body.substring(open + 1, close) else body.replace("```", " ")
        }
        val start = body.indexOf('{')
        val end = body.lastIndexOf('}')
        return if (start >= 0 && end > start) body.substring(start, end + 1) else body.trim()
    }

    fun containsSecret(text: String, secrets: List<String>): Boolean =
        secrets.filter { it.trim().length >= 8 }.any { text.contains(it.trim()) }

    // ---------------- Browser automation safety ----------------

    enum class BrowserActionKind { READ_ONLY, FORM_SUBMIT, MESSAGE_SEND, PUBLISH, ACCOUNT_CHANGE, DESTRUCTIVE }

    private val destructiveWords = listOf("delete", "remove all", "wipe", "cancel order", "withdraw", "transfer fund", "close account")
    private val accountWords = listOf("password", "account setting", "unsubscribe", "billing", "subscription", "profile update", "change setting")
    private val publishWords = listOf("publish", "deploy", "purchase", "buy now", "checkout", "listing", "upload file")
    private val messageWords = listOf("send", "message", "comment", "reply", " dm ", "email", "post", "tweet", "share", "pathan", "pathao")
    private val submitWords = listOf("submit", "form", "apply", "application", "sign up", "signup", "register", "order", "book")

    fun classifyBrowserAction(command: String): BrowserActionKind {
        val t = " " + command.lowercase() + " "
        fun hit(words: List<String>) = words.any { t.contains(it) }
        return when {
            hit(destructiveWords) -> BrowserActionKind.DESTRUCTIVE
            hit(accountWords) -> BrowserActionKind.ACCOUNT_CHANGE
            hit(publishWords) -> BrowserActionKind.PUBLISH
            hit(messageWords) -> BrowserActionKind.MESSAGE_SEND
            hit(submitWords) -> BrowserActionKind.FORM_SUBMIT
            else -> BrowserActionKind.READ_ONLY
        }
    }

    fun requiresApproval(kind: BrowserActionKind): Boolean = kind != BrowserActionKind.READ_ONLY

    fun approvalPrompt(kind: BrowserActionKind, detail: String): String = when (kind) {
        BrowserActionKind.FORM_SUBMIT -> "This submits a form on " + detail + ". Allow it?"
        BrowserActionKind.MESSAGE_SEND -> "This sends a message on " + detail + ". Allow it?"
        BrowserActionKind.PUBLISH -> "This publishes or purchases on " + detail + ". Allow it?"
        BrowserActionKind.ACCOUNT_CHANGE -> "This changes account settings on " + detail + ". Allow it?"
        BrowserActionKind.DESTRUCTIVE -> "This is destructive on " + detail + ". Allow it?"
        BrowserActionKind.READ_ONLY -> "Reading " + detail + " only."
    }

    /** Refuses CAPTCHA / anti-bot circumvention instead of attempting it. */
    fun bypassRefusal(command: String): String? {
        val t = command.lowercase()
        if (t.contains("brute force") || t.contains("bruteforce")) {
            return "I will not run brute-force attempts against a website."
        }
        val mentionsProtection = t.contains("captcha") || t.contains("anti-bot") || t.contains("antibot") || t.contains("bot protection")
        val asksToBreak = t.contains("bypass") || t.contains("break") || t.contains("solve") || t.contains("crack") || t.contains("evade")
        if (mentionsProtection && asksToBreak) {
            return "I will not bypass CAPTCHAs or anti-bot protections. Please complete that step yourself."
        }
        return null
    }
}
