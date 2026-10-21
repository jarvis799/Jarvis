package com.jarvis.assistant.security.cyberlab

import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import javax.net.ssl.HttpsURLConnection

/**
 * MODULE 1 + 3 — Cybersecurity reasoning + the Authorized Security Lab.
 * Learning Mode: curated defensive lessons.
 * Local Audit Mode: offline static checks on pasted manifest/source text.
 * Authorized Assessment Mode: ONE passive HTTPS request against a single
 * explicitly authorized host, gated by the creator master key. No scanning,
 * no crawling, no exploitation, no credential handling, no host expansion.
 */
object CyberLabEngine {

    const val CONFIRMED = "CONFIRMED"
    const val THEORETICAL = "THEORETICAL"

    // ---------- MODULE 3A: LEARNING MODE ----------

    val learningTopics: Map<String, String> = linkedMapOf(
        "Linux fundamentals" to (
            "Everything is a file: devices, sockets, pipes. The shell just runs small programs that read and write them. " +
            "Key ideas: paths (/etc, /home), users and permissions (rwx for user/group/other), processes (ps, top), " +
            "and package managers (apt/apk). Defensive practice: know which files are world-readable on your own system " +
            "and why 'least privilege' beats convenience. Safe lab example: ls -la /sdcard to inspect your own files."
            ),
        "Networking: TCP/IP & ports" to (
            "Data travels in packets. TCP is ordered and reliable, UDP is fast but unordered. An IP address finds a machine, " +
            "a port finds a service (80/443 web, 22 ssh, 53 dns). Tools: ping (reachable?), nslookup (name resolution). " +
            "Defensive practice: close or firewall every port you do not actually use. Never probe a host you do not own."
            ),
        "HTTP & HTTPS" to (
            "HTTP is a request/response protocol: methods GET/POST, status codes (200 ok, 301 redirect, 403 forbidden, 500 error). " +
            "Headers carry metadata; cookies carry session state. HTTPS is HTTP inside TLS, which encrypts and authenticates. " +
            "Defensive practice: force HTTPS, send HSTS, validate input on the server even when the client validates too."
            ),
        "DNS" to (
            "DNS turns names into IPs through a hierarchy of resolvers. Records: A (address), AAAA (IPv6), MX (mail), TXT (policy). " +
            "DNS was not encrypted originally - DoH/DoT fix that. Attacks like spoofing/poisoning are prevented by DNSSEC " +
            "and by using resolvers you trust."
            ),
        "TLS & certificates" to (
            "TLS does a handshake: agree on a cipher, verify the server certificate chain against trusted roots, then encrypt. " +
            "Weak ciphers (RC4, 3DES, NULL) or self-signed/unvalidated certificates break the guarantee. " +
            "Defensive practice: keep TLS library updated, disable old protocol versions, never ship a 'trust all' TrustManager."
            ),
        "OWASP: Injection" to (
            "Injection happens when untrusted input is interpreted as code or query (SQL, shell, LDAP, template). " +
            "Defense: parameterized queries / prepared statements, argument arrays instead of shell strings, " +
            "strict input validation, least-privilege DB users. Never build commands by concatenating user input."
            ),
        "OWASP: Broken Access Control" to (
            "Users reaching data or actions they should not (IDOR: changing /order/1234 to /order/1235). " +
            "Defense: deny by default, check authorization on the server for every object, use random non-guessable IDs, " +
            "log access to sensitive objects."
            ),
        "OWASP: Security Misconfiguration" to (
            "Default credentials, verbose errors, directory listing, allowBackup=true, debuggable=true, cleartext traffic, " +
            "missing security headers. Defense: harden by checklist, disable debug flags in release builds, " +
            "send CSP + X-Content-Type-Options + X-Frame-Options + HSTS."
            ),
        "Android permission model" to (
            "Apps declare permissions in the manifest; users grant runtime ones. Every declared permission widens the attack " +
            "surface, so request the minimum. Check: allowBackup, debuggable, cleartext, and exported components - " +
            "each exported component without a permission is an entry point other apps can call."
            ),
        "Secure coding checklist" to (
            "1. Validate all input at the trust boundary. 2. Use parameterized queries and argument arrays. " +
            "3. No secrets in code or logs. 4. Strong crypto only (AES/GCM, SHA-256+, real KDFs for passwords). " +
            "5. Deny by default on access checks. 6. Keep dependencies updated and audit advisories. " +
            "7. Log actions, redact secrets. 8. Add a verification step after every fix."
            )
    )

    // ---------- MODULE 3B: LOCAL AUDIT (static, offline, paste-based) ----------

    fun auditManifest(xml: String): List<FindingsStore.RawFinding> {
        val out = mutableListOf<FindingsStore.RawFinding>()
        fun add(title: String, severity: String, confidence: String, impact: String, fix: String, evidence: String) {
            out.add(
                FindingsStore.RawFinding(
                    component = "AndroidManifest.xml", category = "Android config", title = title,
                    evidence = redactLine(evidence.take(200)), severity = severity, confidence = confidence,
                    impact = impact, remediation = fix,
                    verification = "Re-run the audit after the change; this pattern must be gone."
                )
            )
        }
        if (Regex("android:debuggable\\s*=\\s*\"true\"").containsMatchIn(xml))
            add("debuggable=true (release build)", "CRITICAL", CONFIRMED,
                "Attackers can attach a debugger, dump memory and bypass hardening.",
                "Remove android:debuggable or ensure the release build type sets it to false.",
                "android:debuggable=\"true\"")
        if (Regex("android:allowBackup\\s*=\\s*\"true\"").containsMatchIn(xml))
            add("allowBackup=true", "MEDIUM", CONFIRMED,
                "App private data can be extracted via adb backup on older devices.",
                "Set android:allowBackup=\"false\" or restrict with backup rules.",
                "android:allowBackup=\"true\"")
        if (Regex("android:usesCleartextTraffic\\s*=\\s*\"true\"").containsMatchIn(xml))
            add("Cleartext traffic allowed", "MEDIUM", CONFIRMED,
                "Unencrypted HTTP traffic can be read and modified on the network.",
                "Set usesCleartextTraffic=false and allow cleartext only per-domain if truly required.",
                "android:usesCleartextTraffic=\"true\"")
        if (xml.contains("android.permission.INTERNET") && !xml.contains("networkSecurityConfig"))
            add("No networkSecurityConfig with INTERNET permission", "LOW", THEORETICAL,
                "Default trust anchors and cleartext policy are not explicitly pinned down.",
                "Add a network security config that blocks cleartext and pins user CAs appropriately.",
                "INTERNET permission without networkSecurityConfig")
        val tagRx = Regex("<(activity|service|receiver|provider)\\b[^>]*>", RegexOption.DOT_MATCHES_ALL)
        tagRx.findAll(xml).forEach { m ->
            val tag = m.value
            val name = Regex("android:name\\s*=\\s*\"([^\"]+)\"").find(tag)?.groupValues?.get(1) ?: "unknown"
            if (Regex("android:exported\\s*=\\s*\"true\"").containsMatchIn(tag) &&
                !tag.contains("android:permission") &&
                tag.contains("intent-filter") &&
                (m.groupValues[1] == "service" || m.groupValues[1] == "receiver" || m.groupValues[1] == "provider")
            ) {
                add("Exported " + m.groupValues[1] + " without permission: " + name, "MEDIUM", THEORETICAL,
                    "Any other app can start this component; verify it validates its callers and inputs.",
                    "Add android:permission, use signature-level permissions, or set exported=false if not needed.",
                    tag.take(200))
            }
        }
        return out
    }

    fun auditSource(path: String, code: String): List<FindingsStore.RawFinding> {
        val out = mutableListOf<FindingsStore.RawFinding>()
        fun add(title: String, severity: String, confidence: String, impact: String, fix: String, evidence: String) {
            out.add(
                FindingsStore.RawFinding(
                    component = path, category = "Source code", title = title,
                    evidence = redactLine(evidence.trim().take(200)), severity = severity, confidence = confidence,
                    impact = impact, remediation = fix,
                    verification = "Re-run the audit after the change; this pattern must be gone."
                )
            )
        }
        Regex("(?i)(password|passwd|secret|api[_-]?key|access[_-]?token)\\s*=\\s*\"[A-Za-z0-9_./+-]{8,}\"").findAll(code).forEach { m ->
            add("Hardcoded credential in source", "HIGH", CONFIRMED,
                "Anyone with the code or the APK can extract this secret.",
                "Move secrets to local.properties / injected BuildConfig fields or a backend; never commit them.",
                m.value)
        }
        Regex("(?i)\"http://(?!localhost|127\\.0\\.0\\.1)[^\"]+\"").findAll(code).forEach { m ->
            add("Cleartext HTTP URL in code", "LOW", CONFIRMED,
                "Traffic to this endpoint can be read or modified in transit.",
                "Use HTTPS; consider certificate pinning for your own endpoints.",
                m.value)
        }
        Regex("(?i)MessageDigest\\.getInstance\\(\\s*\"(MD5|SHA-?1)\"").findAll(code).forEach { m ->
            add("Weak hash (MD5/SHA-1)", "HIGH", CONFIRMED,
                "These hashes are broken for security purposes.",
                "Use SHA-256 or stronger; for passwords use PBKDF2/Argon2/bcrypt.",
                m.value)
        }
        Regex("(?i)Cipher\\.getInstance\\(\\s*\"(DES|DESede|AES/ECB)[^\"]*\"").findAll(code).forEach { m ->
            add("Weak/insecure cipher mode", "HIGH", CONFIRMED,
                "ECB and DES leak structure; not semantically secure.",
                "Use AES/GCM with a random IV and store the IV alongside the ciphertext.",
                m.value)
        }
        if (Regex("(?i)X509TrustManager|TrustAllCerts").containsMatchIn(code))
            add("Custom trust manager present", "MEDIUM", THEORETICAL,
                "If it does not validate the certificate chain, TLS becomes unauthenticated.",
                "Ensure the TrustManager actually validates chains; never ship a trust-all implementation.",
                "X509TrustManager / TrustAllCerts pattern")
        if (Regex("(?i)AllowAllHostnameVerifier|NoopHostnameVerifier").containsMatchIn(code))
            add("Hostname verification disabled", "CRITICAL", CONFIRMED,
                "Certificate name checking is skipped; any certificate is accepted (MITM risk).",
                "Remove the permissive hostname verifier and use default verification.",
                "AllowAllHostnameVerifier / NoopHostnameVerifier")
        Regex("(?i)Runtime\\.getRuntime\\(\\)\\.exec\\([^\\n]*\\+").findAll(code).forEach { m ->
            add("Shell command built by string concatenation", "MEDIUM", CONFIRMED,
                "Concatenated arguments allow argument or command injection.",
                "Use ProcessBuilder with an argument list, never concatenated strings.",
                m.value)
        }
        Regex("(?i)Log\\.[dvie]\\([^\\n]*(password|token|secret)").findAll(code).forEach { m ->
            add("Sensitive value logged", "MEDIUM", CONFIRMED,
                "Secrets can leak into system logs readable by debugging tools.",
                "Remove sensitive values from logs; redact before logging.",
                m.value)
        }
        return out
    }

    // ---------- MODULE 3C: AUTHORIZED ASSESSMENT (passive, single host) ----------

    data class AssessmentResult(val ok: Boolean, val message: String, val findings: List<FindingsStore.RawFinding>)

    fun isCreatorKey(input: String): Boolean =
        input.trim().uppercase(Locale.ROOT).contains("AHONA-I-LOVE-YOU")

    /**
     * Exactly one passive GET against the authorized host. No redirects are
     * followed, no other host is touched, nothing is written. Missing headers
     * and weak cipher suites are reported as findings; failures are reported
     * honestly instead of inventing results.
     */
    fun authorizedHeaderCheck(rawHost: String, masterKeyInput: String, timeoutMs: Int = 8000): AssessmentResult {
        val findings = mutableListOf<FindingsStore.RawFinding>()
        if (!isCreatorKey(masterKeyInput)) {
            return AssessmentResult(false, "Creator verification required - master key did not match. Nothing was tested.", findings)
        }
        val host = rawHost.trim()
            .removePrefix("https://").removePrefix("http://")
            .trimEnd('/').substringBefore('/').lowercase(Locale.ROOT)
        if (host.isBlank() || !host.contains('.')) {
            return AssessmentResult(false, "Invalid target - give a hostname you own or are authorized to test.", findings)
        }
        val forbidden = Regex("^(127\\.|10\\.|192\\.168\\.|172\\.(1[6-9]|2\\d|3[01])\\.|localhost|169\\.254\\.0\\.|0\\.0\\.0\\.0)", RegexOption.IGNORE_CASE)
        if (forbidden.containsMatchIn(host)) {
            return AssessmentResult(false, "Internal/loopback ranges are blocked - only your own public authorized asset.", findings)
        }
        return try {
            val conn = URL("https://" + host + "/").openConnection() as HttpsURLConnection
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.requestMethod = "GET"
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", "JarvisApexPassiveCheck/1.0")
            conn.connect()
            val code = conn.responseCode
            val headers = conn.headerFields ?: emptyMap()
            fun header(name: String): String? =
                headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.joinToString("; ")
            val checks = listOf(
                Triple("Strict-Transport-Security", "MEDIUM", "HSTS missing - browsers can be downgraded to plain HTTP."),
                Triple("Content-Security-Policy", "LOW", "No Content-Security-Policy - raises XSS impact if any injection exists."),
                Triple("X-Content-Type-Options", "LOW", "X-Content-Type-Options missing - MIME sniffing risk."),
                Triple("X-Frame-Options", "LOW", "X-Frame-Options missing - clickjacking exposure.")
            )
            checks.forEach { (name, sev, why) ->
                val v = header(name)
                if (v.isNullOrBlank()) {
                    findings.add(
                        FindingsStore.RawFinding(
                            component = host, category = "HTTP headers", title = name + " missing",
                            evidence = "Passive GET https://" + host + "/ (status " + code + ") returned no " + name + " header.",
                            severity = sev, confidence = CONFIRMED, impact = why,
                            remediation = "Send the " + name + " header from your server configuration.",
                            verification = "Re-run this passive check; the header must appear."
                        )
                    )
                }
            }
            val cipher = runCatching { conn.cipherSuite ?: "" }.getOrDefault("")
            if (cipher.contains("NULL") || cipher.contains("RC4") || cipher.contains("3DES")) {
                findings.add(
                    FindingsStore.RawFinding(
                        component = host, category = "TLS", title = "Weak cipher suite negotiated (" + cipher + ")",
                        evidence = "The TLS handshake used cipher " + cipher + ".",
                        severity = "HIGH", confidence = CONFIRMED,
                        impact = "Weak ciphers allow decryption or downgrade attacks.",
                        remediation = "Disable RC4/3DES/NULL suites; use modern AEAD ciphers (AES-GCM, ChaCha20).",
                        verification = "Re-run this passive check; a modern cipher must be negotiated."
                    )
                )
            }
            conn.disconnect()
            AssessmentResult(true, "Passive check done: 1 request to " + host + ", status " + code + ", cipher " + cipher + ".", findings)
        } catch (e: Exception) {
            AssessmentResult(false, "Could not reach " + host + " over HTTPS (" + e.javaClass.simpleName + "). Nothing was tested - no results fabricated.", findings)
        }
    }

    fun redactLine(text: String): String = text
        .replace(Regex("(?i)(password|passwd|secret|token|api[_-]?key|authorization)\\s*[:=]\\s*\\S+"), "$1=***")
        .replace(Regex("Bearer\\s+\\S+"), "Bearer ***")
        .replace(Regex("(?i)\\b(ghp|gho|ghs|xoxb|xoxp|sk)-[A-Za-z0-9_-]{10,}"), "***")
}
