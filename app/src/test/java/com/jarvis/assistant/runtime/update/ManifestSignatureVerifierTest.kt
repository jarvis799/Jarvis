package com.jarvis.assistant.runtime.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

class ManifestSignatureVerifierTest {

    private fun newManifest(sig: String = ""): UpdateManifest = UpdateManifest(
        schemaVersion = 2, runtimeVersion = "1.2.0", minimumSupportedAppVersion = "3.6.0",
        releaseNotes = "fix", publishedAt = "2026-10-21T18:00:00Z", expiresAt = "",
        updateType = "runtime",
        modules = listOf(RuntimeModule("prompts-a", "Prompts A", "1.0.0", "prompt_pack",
            "https://github.com/o/r/releases/download/v1/p.zip", 1024, "a".repeat(64), "", emptyList())),
        compatibility = RuntimeCompatibility("3.6.0", "", "", 0),
        signingKeyId = "key-1", signature = sig
    )

    private fun pemPublic(key: java.security.PublicKey): String {
        val b64 = Base64.getMimeEncoder(64, byteArrayOf(10)).encodeToString(key.encoded)
        return "-----BEGIN PUBLIC KEY-----\n" + b64 + "\n-----END PUBLIC KEY-----\n"
    }

    private fun sign(canon: String, priv: java.security.PrivateKey): String {
        val s = Signature.getInstance("SHA256withRSA")
        s.initSign(priv)
        s.update(canon.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(s.sign())
    }

    @Test fun valid_signature_verifies() {
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val m = newManifest()
        val sig = sign(ManifestSignatureVerifier.canonicalString(m), kp.private)
        assertTrue(ManifestSignatureVerifier.verify(newManifest(sig), pemPublic(kp.public)))
    }

    @Test fun tampered_manifest_fails() {
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val m = newManifest()
        val sig = sign(ManifestSignatureVerifier.canonicalString(m), kp.private)
        val tampered = m.copy(runtimeVersion = "9.9.9", signature = sig)
        assertFalse(ManifestSignatureVerifier.verify(tampered, pemPublic(kp.public)))
    }

    @Test fun blank_signature_fails() {
        assertFalse(ManifestSignatureVerifier.verify(newManifest(""), ""))
    }
}
