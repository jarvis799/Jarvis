package com.jarvis.assistant.runtime

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class, sdk = [28])
class RuntimeFeatureTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private fun zip(vararg entries: Pair<String, String>): File {
        val file = File.createTempFile("feature", ".zip")
        ZipOutputStream(file.outputStream()).use { out ->
            for ((name, content) in entries) {
                out.putNextEntry(ZipEntry(name))
                out.write(content.toByteArray())
                out.closeEntry()
            }
        }
        return file
    }

    private fun releaseFeature(id: String, version: String, type: String): ReleaseFeature = ReleaseFeature(
        id = id, name = "Feature " + id, version = version,
        description = "Test feature", minimumAppVersion = "1.0.0",
        downloadUrl = "https://github.com/devayan000/Jarvis-app/releases/download/v1/" + id + "-" + version + ".zip",
        sha256 = "a".repeat(64), featureType = type, changelog = "Initial",
        downloadSize = 100, entry = "index.html"
    )

    private fun packageManifest(id: String, type: String): String = JSONObject()
        .put("format_version", 1).put("feature_id", id)
        .put("feature_type", type).put("entry", "index.html").toString()

    private fun pluginJson(id: String): String = JSONObject()
        .put("id", id).put("name", "Pack " + id).put("version", 1)
        .put("description", "Runtime feature commands").put("author", "Ayan Sir")
        .put("commands", JSONArray().put(JSONObject()
            .put("pattern", "focus timer").put("isRegex", false)
            .put("reply", "Starting a focus session, Sir.")
            .put("action", JSONObject().put("type", "").put("value", "")))).toString()

    private fun manifestEntry(id: String, sha: String, url: String, type: String): JSONObject = JSONObject()
        .put("feature_id", id).put("feature_name", "Feature " + id).put("version", "1.0.0")
        .put("description", "d").put("minimum_app_version", "1.0.0")
        .put("download_url", url).put("sha256", sha).put("feature_type", type)
        .put("changelog", "c").put("download_size", 10).put("entry", "index.html")

    @Test fun releaseManifestIsParsedAndOnlyValidFeaturesPassValidation() {
        val manifest = JSONObject().put("format_version", 1).put("features", JSONArray()
            .put(manifestEntry("unit-converter", "b".repeat(64),
                "https://github.com/devayan000/Jarvis-app/releases/download/v1/unit-converter-1.0.0.zip", "web_tool"))
            .put(manifestEntry("bad-sha", "nothex", "https://github.com/devayan000/Jarvis-app/releases/download/v1/bad.zip", "web_tool"))
            .put(manifestEntry("bad-url", "b".repeat(64), "http://evil.example.com/pack.zip", "web_tool"))
            .put(manifestEntry("bad-type", "b".repeat(64), "https://github.com/devayan000/Jarvis-app/releases/download/v1/t.zip", "native_code"))).toString()
        val parsed = FeatureCodec.parseReleaseManifest(manifest)
        assertEquals(4, parsed.size)
        val valid = parsed.filter { FeatureCodec.validate(it) == null }
        assertEquals(listOf("unit-converter"), valid.map { it.id })
    }

    @Test fun versionComparisonOrdersCorrectly() {
        assertTrue(FeatureCodec.compareVersions("1.2.10", "1.2.9") > 0)
        assertEquals(0, FeatureCodec.compareVersions("1.2", "1.2.0"))
        assertTrue(FeatureCodec.compareVersions("1.9", "1.10") < 0)
    }

    @Test fun updateListShowsOnlyNewerFeaturesAndFlagsAppUpdateRequirements() {
        val available = listOf(
            releaseFeature("new-tool", "1.0.0", "web_tool"),
            releaseFeature("updated-tool", "2.0.0", "web_tool"),
            releaseFeature("future-tool", "1.0.0", "web_tool")
                .let { it.copy(minimumAppVersion = "9.0.0") }
        )
        val installed = listOf(InstalledFeature(
            "updated-tool", "Updated Tool", "1.0.0", "web_tool", "d", "index.html", true, 0L, null))
        val result = FeatureCodec.computeUpdates(available, installed, "3.3.1")
        assertEquals(listOf("new-tool", "updated-tool"), result.available.map { it.id })
        assertEquals(listOf("future-tool"), result.requiresAppUpdate.map { it.id })
    }

    @Test fun hashVerificationDetectsTampering() {
        val file = File.createTempFile("payload", ".bin")
        file.writeBytes("jarvis-runtime-feature".toByteArray())
        val other = File.createTempFile("payload", ".bin")
        other.writeBytes("tampered-runtime-feature".toByteArray())
        val digest = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        assertEquals(digest, FeatureInstaller.sha256Of(file))
        assertNotEquals(digest, FeatureInstaller.sha256Of(other))
        file.delete()
        other.delete()
    }

    @Test fun commandFeatureInstallsActivatesAndPersistsInTheRegistry() {
        val feature = releaseFeature("study-helper", "1.0.0", "commands")
        val archive = zip(
            "feature.json" to packageManifest("study-helper", "commands"),
            "plugin.json" to pluginJson("study-helper")
        )
        val installed = FeatureInstaller.install(context, archive, feature)
        archive.delete()
        assertEquals("1.0.0", installed.version)
        assertEquals("1.0.0", FeatureRegistry.get(context, "study-helper")?.version)
        val activation = FeatureRunner.activate(context, installed)
        assertTrue(activation.contains("installed"))
    }

    @Test fun corruptedPackageIsRejectedAndTheWorkingVersionSurvives() {
        val good = FeatureInstaller.install(context, zip(
            "feature.json" to packageManifest("dice-pack", "commands"),
            "plugin.json" to pluginJson("dice-pack")
        ), releaseFeature("dice-pack", "1.0.0", "commands"))
        FeatureRunner.activate(context, good)
        val broken = zip(
            "feature.json" to packageManifest("dice-pack", "commands"),
            "plugin.json" to "{ this is not json"
        )
        var failure: Exception? = null
        try {
            FeatureInstaller.install(context, broken, releaseFeature("dice-pack", "2.0.0", "commands"))
        } catch (e: Exception) { failure = e }
        broken.delete()
        assertNotNull(failure)
        assertEquals("1.0.0", FeatureRegistry.get(context, "dice-pack")?.version)
        assertTrue(File(FeatureRegistry.featureDir(context, "dice-pack"), "1.0.0/plugin.json").isFile)
    }

    @Test fun rollbackRestoresThePreviousWorkingVersion() {
        FeatureInstaller.install(context, zip(
            "feature.json" to packageManifest("rollback-tool", "web_tool"),
            "index.html" to "<html><body>one</body></html>"
        ), releaseFeature("rollback-tool", "1.0.0", "web_tool"))
        FeatureInstaller.install(context, zip(
            "feature.json" to packageManifest("rollback-tool", "web_tool"),
            "index.html" to "<html><body>two</body></html>"
        ), releaseFeature("rollback-tool", "2.0.0", "web_tool"))
        val restored = FeatureInstaller.rollback(context, "rollback-tool")
        assertEquals("1.0.0", restored?.version)
        assertEquals("1.0.0", FeatureRegistry.get(context, "rollback-tool")?.version)
        assertTrue(File(FeatureRegistry.featureDir(context, "rollback-tool"), "1.0.0/index.html").isFile)
    }

    @Test fun packagesWithUnsafePathsOrExecutablesAreRejected() {
        try {
            FeatureInstaller.install(context, zip(
                "feature.json" to packageManifest("evil-pack", "web_tool"),
                "ok.txt" to "hi",
                "../escape.txt" to "no"
            ), releaseFeature("evil-pack", "1.0.0", "web_tool"))
            fail("zip-slip package was accepted")
        } catch (e: FeatureInstaller.InstallException) { /* expected */ }
        try {
            FeatureInstaller.install(context, zip(
                "feature.json" to packageManifest("evil-pack", "web_tool"),
                "payload.dex" to "dex-bytes"
            ), releaseFeature("evil-pack", "1.0.0", "web_tool"))
            fail("executable payload was accepted")
        } catch (e: FeatureInstaller.InstallException) { /* expected */ }
    }

    @Test fun registryPersistsEnabledState() {
        FeatureRegistry.upsert(context, InstalledFeature(
            "toggle-tool", "Toggle Tool", "1.0.0", "web_tool", "d", "index.html", true, 0L, null))
        FeatureRegistry.setEnabled(context, "toggle-tool", false)
        assertEquals(false, FeatureRegistry.get(context, "toggle-tool")?.enabled)
    }
}
