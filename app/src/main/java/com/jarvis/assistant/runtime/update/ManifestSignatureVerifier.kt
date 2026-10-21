package com.jarvis.assistant.runtime.update

import com.jarvis.assistant.BuildConfig
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Verifies the manifest signature (SHA256withRSA) against a trusted public key.
 * The signed bytes are the canonical representation of all security-relevant
 * fields EXCLUDING "signature". Canonicalization is deterministic and must match
 * the release signer exactly (see docs/runtime-update-center.md).
 */
object ManifestSignatureVerifier {

    @Throws(RuntimeUpdateException::class)
    fun canonicalString(m: UpdateManifest): String {
        val sb = StringBuilder()
        sb.append("v=").append(m.schemaVersion).append('\n')
        sb.append("rv=").append(m.runtimeVersion).append('\n')
        sb.append("mav=").append(m.minimumSupportedAppVersion).append('\n')
        sb.append("ut=").append(m.updateType).append('\n')
        sb.append("pa=").append(m.publishedAt).append('\n')
        sb.append("ea=").append(m.expiresAt).append('\n')
        sb.append("kid=").append(m.signingKeyId).append('\n')
        sb.append("m=").append(m.modules.size).append('\n')
        for (mod in m.modules) {
            sb.append("mi=").append(mod.id).append('|').append(mod.name).append('|')
              .append(mod.version).append('|').append(mod.type).append('|')
              .append(mod.downloadUrl).append('|').append(mod.size).append('|')
              .append(mod.sha256).append('|').append(mod.entry).append('\n')
        }
        sb.append("c=").append(m.compatibility.minAppVersion).append('|')
          .append(m.compatibility.maxAppVersion).append('|')
          .append(m.compatibility.minRuntimeVersion).append('|')
          .append(m.compatibility.sdkMin)
        return sb.toString()
    }

    /** Verify against an explicit trusted public key (PEM, X.509 SubjectPublicKeyInfo). */
    fun verify(manifest: UpdateManifest, publicKeyPem: String): Boolean {
        if (publicKeyPem.isBlank() || manifest.signature.isBlank()) return false
        return try {
            val pubKey = KeyFactory.getInstance("RSA")
                .generatePublic(X509EncodedKeySpec(decodePem(publicKeyPem)))
            val sig = Signature.getInstance("SHA256withRSA")
            sig.initVerify(pubKey)
            sig.update(canonicalString(manifest).toByteArray(Charsets.UTF_8))
            sig.verify(Base64.getMimeDecoder().decode(manifest.signature))
        } catch (e: Exception) {
            false
        }
    }

    /** Convenience: verify against the public key bundled in the build. */
    fun verify(manifest: UpdateManifest): Boolean = verify(manifest, BuildConfig.RUNTIME_SIGNING_PUBLIC_KEY)

    private fun decodePem(pem: String): ByteArray {
        val body = pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("-----BEGIN RSA PUBLIC KEY-----", "")
            .replace("-----END RSA PUBLIC KEY-----", "")
            .replace(Regex("\\s"), "")
        return Base64.getMimeDecoder().decode(body)
    }
}
