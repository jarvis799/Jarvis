package com.jarvis.assistant.apkagent

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import java.io.File
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.util.zip.ZipFile

/**
 * DEV STYLE: real on-device APK inspection - package metadata, components,
 * permissions, signing info and archive structure, with honest limitations.
 */
object ApkMetadataAnalyzer {

    data class ComponentSummary(
        val activities: List<String>,
        val services: List<String>,
        val receivers: List<String>,
        val providers: List<String>
    )

    data class ApkReport(
        val fileName: String,
        val sha256: String,
        val sizeBytes: Long,
        val packageName: String?,
        val versionName: String?,
        val versionCode: Long?,
        val minSdk: Int?,
        val targetSdk: Int?,
        val permissions: List<String>,
        val components: ComponentSummary,
        val nativeAbis: List<String>,
        val dexCount: Int,
        val assetCount: Int,
        val hasResources: Boolean,
        val signerFingerprints: List<String>,
        val certSubjects: List<String>
    )

    fun analyze(context: Context, fileName: String, sha256: String, sizeBytes: Long, apkFile: File): ApkReport {
        val pm = context.packageManager
        val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or
            PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS or
            PackageManager.GET_PERMISSIONS or PackageManager.GET_SIGNATURES
        val info: PackageInfo? = pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
        val perms = info?.requestedPermissions?.toList().orEmpty()
        val components = ComponentSummary(
            activities = info?.activities?.mapNotNull { it.name }.orEmpty(),
            services = info?.services?.mapNotNull { it.name }.orEmpty(),
            receivers = info?.receivers?.mapNotNull { it.name }.orEmpty(),
            providers = info?.providers?.mapNotNull { it.name }.orEmpty()
        )
        var dexCount = 0
        val abis = LinkedHashSet<String>()
        var assetCount = 0
        var hasResources = false
        runCatching {
            ZipFile(apkFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val n = entry.name
                    if (n.startsWith("classes") && n.endsWith(".dex")) dexCount++
                    else if (n.startsWith("lib/")) {
                        val abi = n.split('/').getOrNull(2)
                        if (!abi.isNullOrBlank()) abis.add(abi)
                    } else if (n.startsWith("assets/")) assetCount++
                    else if (n == "resources.arsc") hasResources = true
                }
            }
        }
        val fingerprints = mutableListOf<String>()
        val subjects = mutableListOf<String>()
        info?.signatures?.forEach { sig ->
            runCatching {
                val md = MessageDigest.getInstance("SHA-256")
                fingerprints.add(md.digest(sig.toByteArray()).joinToString("") { "%02x".format(it) })
                val cert = CertificateFactory.getInstance("X.509")
                    .generateCertificate(sig.toByteArray().inputStream())
                subjects.add((cert as java.security.cert.X509Certificate).subjectX500Principal.name)
            }
        }
        return ApkReport(
            fileName = fileName, sha256 = sha256, sizeBytes = sizeBytes,
            packageName = info?.packageName,
            versionName = info?.versionName,
            versionCode = info?.let {
                if (android.os.Build.VERSION.SDK_INT >= 28) it.longVersionCode else it.versionCode.toLong()
            },
            minSdk = info?.applicationInfo?.minSdkVersion?.takeIf { v -> v > 0 },
            targetSdk = info?.applicationInfo?.targetSdkVersion,
            permissions = perms, components = components,
            nativeAbis = abis.toList(), dexCount = dexCount, assetCount = assetCount,
            hasResources = hasResources,
            signerFingerprints = fingerprints, certSubjects = subjects
        )
    }

    /** Truthful, structured report - the APK limitations are stated openly, never hidden. */
    fun toText(r: ApkReport): String = buildString {
        appendLine("APK ANALYSIS REPORT")
        appendLine("File: " + r.fileName + "  |  " + (r.sizeBytes / 1024 / 1024) + " MB")
        appendLine("SHA-256: " + r.sha256)
        appendLine("")
        if (r.packageName != null) {
            appendLine("Package: " + r.packageName)
            appendLine("Version: " + r.versionName + " (code " + r.versionCode + ")")
            appendLine("minSdk: " + (r.minSdk ?: -1) + "  |  targetSdk: " + (r.targetSdk ?: -1))
            appendLine("")
            appendLine("Permissions (" + r.permissions.size + "):")
            r.permissions.take(15).forEach { appendLine("  - " + it) }
            if (r.permissions.size > 15) appendLine("  ... and " + (r.permissions.size - 15) + " more")
            appendLine("")
            appendLine("Components: " + r.components.activities.size + " activities, " +
                r.components.services.size + " services, " + r.components.receivers.size +
                " receivers, " + r.components.providers.size + " providers")
            appendLine("Main launcher: " + r.components.activities.firstOrNull().orEmpty())
        } else {
            appendLine("Package metadata could not be read - the file may be corrupted or not a valid APK.")
        }
        appendLine("Native ABIs: " + r.nativeAbis.joinToString(", ").ifBlank { "none" })
        appendLine("DEX files: " + r.dexCount + "  |  assets: " + r.assetCount + "  |  resources: " + (if (r.hasResources) "yes" else "no"))
        if (r.signerFingerprints.isNotEmpty()) {
            appendLine("Signed by: " + r.certSubjects.firstOrNull().orEmpty())
            appendLine("Certificate SHA-256: " + r.signerFingerprints.first())
        } else {
            appendLine("Signing info not readable.")
        }
        appendLine("")
        appendLine("HONEST LIMITS:")
        appendLine("- An APK does NOT contain the original Android Studio project: no Kotlin sources, no Gradle files, no comments.")
        appendLine("- Full code recovery needs external decompilers (jadx/apktool) - not bundled here, and decompiled code is approximate only.")
        appendLine("- The imported APK stays unchanged as the reference artifact.")
        appendLine("- For reliable feature development, connect the original project repo (Creator > Repository Analyzer) or share the project ZIP.")
        appendLine("Confidence: HIGH for metadata and structure; LOW for anything needing decompiled code.")
    }
}
