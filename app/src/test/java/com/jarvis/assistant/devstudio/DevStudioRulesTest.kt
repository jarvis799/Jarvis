package com.jarvis.assistant.devstudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests for the Developer Studio's safety, naming and packaging rules. */
class DevStudioRulesTest {

    @Test
    fun sourcePackageSkipsHeavyAndSecretFiles() {
        assertTrue(DevStudioRules.isExcludedPath("node_modules/react/index.js"))
        assertTrue(DevStudioRules.isExcludedPath(".git/config"))
        assertTrue(DevStudioRules.isExcludedPath("src/.env"))
        assertTrue(DevStudioRules.isExcludedPath("secrets.json"))
        assertTrue(DevStudioRules.isExcludedPath("app/release.keystore"))
        assertTrue(DevStudioRules.isExcludedPath("dist/bundle.js"))
        assertFalse(DevStudioRules.isExcludedPath("src/App.jsx"))
        assertFalse(DevStudioRules.isExcludedPath("index.html"))
        assertFalse(DevStudioRules.isExcludedPath("dist/bundle.js", includeBuildOutput = true))
    }

    @Test
    fun unsafePathsAreRejected() {
        assertFalse(DevStudioRules.isSafeRelativePath("../../etc/passwd"))
        assertFalse(DevStudioRules.isSafeRelativePath("/etc/hosts"))
        assertFalse(DevStudioRules.isSafeRelativePath("C:/windows/system32"))
        assertTrue(DevStudioRules.isSafeRelativePath("src/components/Card.jsx"))
    }

    @Test
    fun projectNamesAreSlugs() {
        assertEquals("my-portfolio-site", DevStudioRules.sanitizeProjectName("My Portfolio Site"))
        assertEquals("jarvis-project", DevStudioRules.sanitizeProjectName("!!!"))
        assertEquals("jarvis-my-shop-2026-10-19.zip", DevStudioRules.zipFileName("my shop", "2026-10-19"))
    }

    @Test
    fun planJsonIsRecoveredFromChattyReplies() {
        val reply = "Sure! Here is the plan:\n```json\n{\"name\":\"shop\",\"summary\":\"A shop\"}\n```\nHope that helps."
        assertEquals("{\"name\":\"shop\",\"summary\":\"A shop\"}", DevStudioRules.extractJson(reply))
        assertEquals("{\"files\":[]}", DevStudioRules.extractJson("{\"files\":[]}"))
    }

    @Test
    fun externalBrowserActionsNeedApproval() {
        assertEquals(DevStudioRules.BrowserActionKind.READ_ONLY, DevStudioRules.classifyBrowserAction("ei site ta scan koro"))
        assertTrue(DevStudioRules.requiresApproval(DevStudioRules.classifyBrowserAction("form submit koro")))
        assertTrue(DevStudioRules.requiresApproval(DevStudioRules.classifyBrowserAction("ei page e message pathao")))
        assertTrue(DevStudioRules.requiresApproval(DevStudioRules.classifyBrowserAction("account settings change koro")))
        assertFalse(DevStudioRules.requiresApproval(DevStudioRules.classifyBrowserAction("page ta analyze koro")))
    }

    @Test
    fun captchaBypassIsRefused() {
        assertTrue(DevStudioRules.bypassRefusal("captcha bypass kore login koro") != null)
        assertTrue(DevStudioRules.bypassRefusal("anti-bot protection bypass") != null)
        assertTrue(DevStudioRules.bypassRefusal("brute force the login") != null)
        assertNull(DevStudioRules.bypassRefusal("google e search koro"))
    }

    @Test
    fun credentialsAreNeverWrittenIntoCode() {
        val token = "ghp_1234567890abcdef"
        assertTrue(DevStudioRules.containsSecret("storeKey(" + token + ")", listOf(token)))
        assertFalse(DevStudioRules.containsSecret("storeKey(readConfig())", listOf(token)))
    }
}
