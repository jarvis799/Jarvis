package com.jarvis.assistant.runtime.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManifestTest {

    private val goodShaA = "a".repeat(64)

    private fun manifestJson(modules: String, updateType: String = "runtime",
                             schema: Int = 2, expiresAt: String = "",
                             runtimeVersion: String = "1.2.0"): String {
        return "{\"schema_version\":" + schema +
            ",\"runtime_version\":\"" + runtimeVersion + "\"" +
            ",\"minimum_supported_app_version\":\"3.6.0\"" +
            ",\"release_notes\":\"fix\"" +
            ",\"published_at\":\"2026-10-21T18:00:00Z\"" +
            ",\"expires_at\":\"" + expiresAt + "\"" +
            ",\"update_type\":\"" + updateType + "\"" +
            ",\"signing_key_id\":\"key-1\"" +
            ",\"signature\":\"sig\"" +
            ",\"compatibility\":{\"min_app_version\":\"3.6.0\",\"max_app_version\":\"\",\"min_runtime_version\":\"\",\"sdk_min\":0}" +
            ",\"modules\":" + modules + "}"
    }

    private val oneModule = "[" +
        "{\"id\":\"prompts-a\",\"name\":\"Prompts A\",\"version\":\"1.0.0\",\"type\":\"prompt_pack\"," +
        "\"download_url\":\"https://github.com/o/r/releases/download/v1/p.zip\",\"size\":1024," +
        "\"sha256\":\"" + goodShaA + "\",\"entry\":\"\",\"depends_on\":[]}]"

    @Test fun parses_valid_manifest() {
        val m = UpdateManifest.parse(manifestJson(oneModule))
        assertEquals(2, m.schemaVersion)
        assertEquals("1.2.0", m.runtimeVersion)
        assertEquals(1, m.modules.size)
        assertEquals("prompts-a", m.modules[0].id)
        assertEquals("prompt_pack", m.modules[0].type)
    }

    @Test fun rejects_unsupported_schema() {
        assertThrows(RuntimeUpdateException::class.java) {
            UpdateManifest.parse(manifestJson(oneModule, schema = 1))
        }
    }

    @Test fun rejects_bad_update_type() {
        assertThrows(RuntimeUpdateException::class.java) {
            UpdateManifest.parse(manifestJson(oneModule, updateType = "dex"))
        }
    }

    @Test fun rejects_non_https_download_url() {
        val bad = oneModule.replace("https://", "http://")
        assertThrows(RuntimeUpdateException::class.java) {
            UpdateManifest.parse(manifestJson(bad))
        }
    }

    @Test fun rejects_bad_sha256() {
        val bad = oneModule.replace(goodShaA, "xyz")
        assertThrows(RuntimeUpdateException::class.java) {
            UpdateManifest.parse(manifestJson(bad))
        }
    }

    @Test fun rejects_size_out_of_range() {
        val bad = oneModule.replace("\"size\":1024", "\"size\":60000000")
        assertThrows(RuntimeUpdateException::class.java) {
            UpdateManifest.parse(manifestJson(bad))
        }
    }

    @Test fun rejects_empty_modules() {
        assertThrows(RuntimeUpdateException::class.java) {
            UpdateManifest.parse(manifestJson("[]"))
        }
    }

    @Test fun rejects_duplicate_module_id() {
        val two = "[" +
            "{\"id\":\"prompts-a\",\"name\":\"A\",\"version\":\"1.0.0\",\"type\":\"prompt_pack\",\"download_url\":\"https://github.com/o/r/a.zip\",\"size\":1024,\"sha256\":\"" + goodShaA + "\",\"entry\":\"\",\"depends_on\":[]}," +
            "{\"id\":\"prompts-a\",\"name\":\"B\",\"version\":\"1.0.0\",\"type\":\"commands\",\"download_url\":\"https://github.com/o/r/b.zip\",\"size\":1024,\"sha256\":\"" + "b".repeat(64) + "\",\"entry\":\"\",\"depends_on\":[]}]"
        assertThrows(RuntimeUpdateException::class.java) {
            UpdateManifest.parse(manifestJson(two))
        }
    }

    @Test fun rejects_invalid_module_type() {
        val bad = oneModule.replace("\"type\":\"prompt_pack\"", "\"type\":\"executable\"")
        assertThrows(RuntimeUpdateException::class.java) {
            UpdateManifest.parse(manifestJson(bad))
        }
    }

    @Test fun rejects_missing_published_at() {
        val json = manifestJson(oneModule).replace(",\"published_at\":\"2026-10-21T18:00:00Z\"", "")
        assertThrows(RuntimeUpdateException::class.java) {
            UpdateManifest.parse(json)
        }
    }

    @Test fun compareVersions_orders_correctly() {
        assertTrue(UpdateManifest.compareVersions("1.2.0", "1.1.9") > 0)
        assertEquals(0, UpdateManifest.compareVersions("3.6.0", "3.6.0"))
        assertTrue(UpdateManifest.compareVersions("1.0", "1.0.1") < 0)
    }
}
