package com.jarvis.assistant.security.cyberlab

import java.util.Locale

/**
 * Authorized Assessment Mode.
 * Explicit scope, a recorded authorization attestation and hard limits:
 * passive HTTPS only, one target family, capped call counts and a rate limit.
 * Active probing, payload injection and destructive testing are not available.
 */
object AuthorizedAssessmentMode {

    data class Scope(
        val allowedHosts: List<String>,
        val blockedHosts: List<String>,
        val allowSubdomains: Boolean,
        val allowPrivateTargets: Boolean,
        val requireHttps: Boolean,
        val maxCallsPerHost: Int,
        val maxCallsPerSession: Int
    )

    data class Limits(
        val minSecondsBetweenCalls: Int,
        val connectTimeoutMillis: Int,
        val readTimeoutMillis: Int,
        val maxResponseBytes: Int,
        val allowActiveProbing: Boolean,
        val allowPayloadInjection: Boolean,
        val allowDestructiveTests: Boolean
    )

    data class Attestation(
        val operator: String,
        val authorizationReference: String,
        val statement: String,
        val acceptedAt: Long
    )

    data class Decision(val allowed: Boolean, val reason: String)

    data class Attempt(val host: String, val allowed: Boolean, val reason: String, val at: Long, val bytes: Int)

    private val callsPerHost = LinkedHashMap<String, Int>()
    private val attempts = ArrayList<Attempt>()
    private var sessionCalls = 0
    private var lastCallAt = 0L
    private var attestation: Attestation? = null
    private var scope = defaultScope()
    private var limits = defaultLimits()

    fun defaultScope(): Scope = Scope(
        allowedHosts = emptyList(),
        blockedHosts = emptyList(),
        allowSubdomains = false,
        allowPrivateTargets = false,
        requireHttps = true,
        maxCallsPerHost = 5,
        maxCallsPerSession = 5
    )

    fun defaultLimits(): Limits = Limits(
        minSecondsBetweenCalls = 20,
        connectTimeoutMillis = 8000,
        readTimeoutMillis = 8000,
        maxResponseBytes = 65536,
        allowActiveProbing = false,
        allowPayloadInjection = false,
        allowDestructiveTests = false
    )

    fun currentScope(): Scope = scope

    fun currentLimits(): Limits = limits

    fun currentAttestation(): Attestation? = attestation

    fun configure(newScope: Scope, newLimits: Limits) {
        scope = newScope
        limits = newLimits
    }

    /** The creator authorizes one host for this session, with a written reference. */
    fun authorize(host: String, declaration: Attestation): Decision {
        val normalized = normalizeHost(host)
        if (normalized.isEmpty()) return Decision(false, "Enter a host name, for example example.com")
        if (declaration.authorizationReference.trim().isEmpty()) {
            return Decision(false, "An authorization reference is required (ticket, email or contract id).")
        }
        if (declaration.statement.trim().isEmpty()) {
            return Decision(false, "The ownership statement is required.")
        }
        val target = targetCheck(normalized)
        if (!target.allowed) return Decision(false, target.reason)
        attestation = declaration
        if (!scope.allowedHosts.contains(normalized)) {
            scope = scope.copy(allowedHosts = scope.allowedHosts + normalized)
        }
        return Decision(true, normalized + " is now in scope for this session.")
    }

    fun gate(host: String): Decision {
        if (attestation == null) return Decision(false, "Assessment mode is locked: no creator attestation recorded.")
        if (limits.allowActiveProbing || limits.allowPayloadInjection || limits.allowDestructiveTests) {
            return Decision(false, "Active probing, payload injection and destructive tests are not available in this mode.")
        }
        val normalized = normalizeHost(host)
        val target = targetCheck(normalized)
        if (!target.allowed) return Decision(false, target.reason)
        if (!inScope(normalized)) return Decision(false, normalized + " is not in the declared scope.")
        if (!scope.requireHttps) return Decision(false, "Plain HTTP is not allowed; assessment is HTTPS only.")
        val used = callsPerHost[normalized] ?: 0
        if (used >= scope.maxCallsPerHost) {
            return Decision(false, "Per-host limit reached for " + normalized + " (" + scope.maxCallsPerHost + " calls).")
        }
        if (sessionCalls >= scope.maxCallsPerSession) {
            return Decision(false, "Session limit reached (" + scope.maxCallsPerSession + " calls). Restart the lab to reset.")
        }
        val sinceLast = System.currentTimeMillis() - lastCallAt
        val waitMillis = limits.minSecondsBetweenCalls * 1000L
        if (lastCallAt > 0L && sinceLast < waitMillis) {
            return Decision(false, "Rate limit: wait " + ((waitMillis - sinceLast) / 1000L) + "s before the next request.")
        }
        return Decision(true, "In scope, within limits, passive HTTPS only.")
    }

    fun record(host: String, allowed: Boolean, reason: String, bytes: Int) {
        val normalized = normalizeHost(host)
        if (allowed) {
            callsPerHost[normalized] = (callsPerHost[normalized] ?: 0) + 1
            sessionCalls += 1
            lastCallAt = System.currentTimeMillis()
        }
        attempts.add(Attempt(normalized, allowed, reason, System.currentTimeMillis(), bytes))
        while (attempts.size > 100) attempts.removeAt(0)
    }

    fun attempts(): List<Attempt> = ArrayList(attempts)

    fun statusLine(): String {
        val host = scope.allowedHosts.firstOrNull() ?: "none"
        return "scope: " + host +
            " | session: " + sessionCalls + "/" + scope.maxCallsPerSession +
            " | this host: " + (callsPerHost[host] ?: 0) + "/" + scope.maxCallsPerHost +
            " | min gap: " + limits.minSecondsBetweenCalls + "s"
    }

    fun reset() {
        callsPerHost.clear()
        attempts.clear()
        sessionCalls = 0
        lastCallAt = 0L
        attestation = null
    }

    fun normalizeHost(value: String): String {
        var host = value.trim().toLowerCase(Locale.ROOT)
        if (host.startsWith("https://")) host = host.substring(8)
        if (host.startsWith("http://")) host = host.substring(7)
        val slash = host.indexOf('/')
        if (slash >= 0) host = host.substring(0, slash)
        val colon = host.indexOf(':')
        if (colon >= 0) host = host.substring(0, colon)
        return host.trim()
    }

    private fun inScope(host: String): Boolean {
        if (scope.allowedHosts.contains(host)) return true
        if (!scope.allowSubdomains) return false
        return scope.allowedHosts.any { host.endsWith("." + it) }
    }

    private fun targetCheck(host: String): Decision {
        if (host.isEmpty()) return Decision(false, "Host is empty.")
        if (host.contains(" ")) return Decision(false, "Host contains a space; enter one single host name.")
        if (host.contains("*")) return Decision(false, "Wildcards are not allowed; enter one exact host.")
        if (scope.blockedHosts.contains(host)) return Decision(false, host + " is on the blocked list.")
        if (!host.contains(".")) return Decision(false, "Enter a fully qualified host name, for example example.com")
        if (!scope.allowPrivateTargets && isPrivateTarget(host)) {
            return Decision(false, host + " looks like a private, loopback or local address; that needs private targets enabled explicitly.")
        }
        return Decision(true, "Target accepted for passive checks.")
    }

    private fun isPrivateTarget(host: String): Boolean {
        if (host == "localhost" || host.endsWith(".local") || host.endsWith(".internal")) return true
        if (host == "::1" || host.startsWith("fe80:") || host.startsWith("fc") || host.startsWith("fd")) return true
        if (host.startsWith("127.") || host.startsWith("10.") || host.startsWith("0.") || host.startsWith("169.254.")) return true
        if (host.startsWith("192.168.")) return true
        if (host.startsWith("172.")) {
            val parts = host.split(".")
            val second = parts.getOrNull(1)?.toIntOrNull() ?: -1
            if (second in 16..31) return true
        }
        return false
    }
}
