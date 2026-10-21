# Runtime Update Center

A secure, signature-verified runtime update system for Jarvis. Runtime updates
deliver **declarative** resources (AI prompts, command aliases, remote config,
allowlisted workflow definitions, web tools) — never executable Kotlin/DEX,
native libraries, or arbitrary code. Native changes ship via the normal APK
update path.

> Status: **Stage 1** — manifest protocol, RSA signature verification, tests,
> and the release-signing GitHub Action are implemented. The downloader,
> installer, atomic activation, rollback, ViewModel and Update Center UI land in
> Stage 2. Do not enable the runtime update check in Settings until Stage 2 is
> merged.

## Architecture (target)

| Component | Role |
|---|---|
| `UpdateManifest` | Versioned manifest schema (v2) + structural validation |
| `ManifestSignatureVerifier` | SHA256withRSA signature verification over a canonical string |
| `UpdateManifestRepository` | Fetch + cache the published manifest (Stage 2) |
| `RuntimeUpdateDownloader` | HTTPS download to app-private staging, size limits, cancellation (Stage 2) |
| `RuntimeUpdateVerifier` | SHA-256 digest + signature + compatibility checks (Stage 2) |
| `RuntimeModuleRegistry` | Track active vs staged runtime versions (Stage 2) |
| `RuntimeUpdateInstaller` | Validate → stage → atomic switch → health check (Stage 2) |
| `RuntimeUpdateRollbackManager` | Restore last-known-good on failure (Stage 2) |
| `RuntimeUpdateViewModel` + Update Center UI | Lifecycle-aware state + screen (Stage 2) |

## Manifest protocol (schema v2)

```json
{
  "schema_version": 2,
  "runtime_version": "1.2.0",
  "minimum_supported_app_version": "3.6.0",
  "release_notes": "...",
  "published_at": "2026-10-21T18:00:00Z",
  "expires_at": "",
  "update_type": "runtime",
  "signing_key_id": "key-1",
  "signature": "<base64 RSA signature over the canonical string>",
  "compatibility": { "min_app_version": "3.6.0", "max_app_version": "", "min_runtime_version": "", "sdk_min": 0 },
  "modules": [
    { "id": "prompts-a", "name": "Prompts A", "version": "1.0.0", "type": "prompt_pack",
      "download_url": "https://github.com/OWNER/REPO/releases/download/runtime-v1.2.0/prompts-a.zip",
      "size": 12345, "sha256": "<64 hex>", "entry": "", "depends_on": [] }
  ]
}
```

Accepted `type`: `prompt_pack`, `commands`, `remote_config`, `workflow`, `web_tool`.
Rejected: schema != 2, non-https URLs, bad sha256, size <= 0 or > 50 MB, duplicate
ids, invalid type, missing `published_at`, unsigned, bad signature, tampered,
incompatible app/runtime version, expired.

## Canonical signing string

Signed bytes are the UTF-8 encoding of (note: the `signature` field is excluded):

```
v=<schema_version>
rv=<runtime_version>
mav=<minimum_supported_app_version>
ut=<update_type>
pa=<published_at>
ea=<expires_at>
kid=<signing_key_id>
m=<module count>
mi=<id>|<name>|<version>|<type>|<download_url>|<size>|<sha256>|<entry>
c=<min_app_version>|<max_app_version>|<min_runtime_version>|<sdk_min>
```

This is implemented identically in `ManifestSignatureVerifier.canonicalString`
(Kotlin) and the `runtime-release.yml` Node signer. Any mismatch = verify fails.

## Signing keys

Uses **RSA 3072 / SHA256withRSA** (universally supported on Android API 26+; no
extra dependencies). The app trusts only the public key bundled in the build.

### Generate the keypair (once, on your machine)

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out runtime-signing-private.pem
openssl rsa -in runtime-signing-private.pem -pubout -out runtime-signing-public.pem
# Private key -> GitHub secret (single line or PEM, see below)
# Public key -> gradle property JARVIS_RUNTIME_SIGNING_PUBLIC_KEY (PEM body)
```

### Secrets / config to set

1. **GitHub repo secret** `JARVIS_RUNTIME_SIGNING_PRIVATE_KEY` — the full PEM
   private key (used by `runtime-release.yml` to sign). Never bundle in the app.
2. **GitHub repo secret** `JARVIS_RUNTIME_SIGNING_KEY_ID` — e.g. `key-1`
   (optional; defaults to `key-1`).
3. **Gradle property / env** `JARVIS_RUNTIME_SIGNING_PUBLIC_KEY` — the public
   key PEM (the whole `-----BEGIN PUBLIC KEY----- ... -----END PUBLIC KEY-----`
   block). The build injects it into `BuildConfig.RUNTIME_SIGNING_PUBLIC_KEY`,
   which the verifier trusts. Set it in `~/.gradle/gradle.properties` or your CI
   environment — never commit it is fine (public key), but keep it out of source.

> Until `JARVIS_RUNTIME_SIGNING_PUBLIC_KEY` is configured, `verify()` returns
> `false` for every manifest — the safe default. No update can activate.

## Release flow

1. Author `docs/runtime-manifest.template.json` (unsigned; no `signature` field).
2. Push tag `runtime-v1.2.0` (or run the workflow manually with that tag).
3. `runtime-release.yml` computes module SHA-256/sizes (if `asset_path` set),
   signs the canonical string, writes `runtime-manifest.json`, attaches it to
   the GitHub Release.
4. The app (Stage 2) fetches the manifest, verifies the signature with the
   bundled public key, verifies each payload SHA-256, validates modules, stages,
   atomically activates, and rolls back on a failed health check.

## Tests (Stage 1)

- `UpdateManifestTest` — valid/invalid manifests, schema, types, sizes, sha256,
  duplicates, versions.
- `ManifestSignatureVerifierTest` — valid signature verifies, tampered fails,
  blank fails. Run: `./gradlew :app:testDebugUnitTest --tests "com.jarvis.assistant.runtime.update.*"`.


## Stage 2 - Downloader, activation + rollback, Update Center UI

Stage 2 turns the Stage 1 manifest protocol into a working pipeline:

- **UpdateDiscovery** fetches the manifest from JARVIS_RUNTIME_MANIFEST_URL (HTTPS, build-configured), parses and signature-verifies it (Stage 1 verifier), then gates on app version, Android SDK and minimum runtime version. The fetch retries with bounded exponential backoff (200ms / 400ms / 1000ms). Discovery is disabled (reports up-to-date) until the URL is set.
- **ModuleStore** (EncryptedSharedPreferences) is the registry: the active runtime version, every installed module (id/name/version/type/sha256/file/active), the retained previous version of each module for rollback, and the last-checked timestamp. Stored encrypted at rest.
- **RuntimeUpdater** is the orchestrator. For each module: download to a staging file with live progress and cooperative cancellation, verify SHA-256, then atomically rename into runtime-modules/<id>/<version>.bin, register and mark active. If a module fails after others activated, it rolls back: re-activates each module's previous version, deletes the failed files, and restores the prior runtime version - the device never runs a half-applied runtime. Manual per-module rollback(id) is also exposed.
- **RuntimeUpdateViewModel** exposes a StateFlow of UiState (Idle/Checking/UpToDate/Available/Downloading/Installing/Applied/RequiresAppUpdate/Disabled/Error) that the UI collects.
- **RuntimeUpdateCenterActivity** is the UI: current runtime version, last-checked, Check / Install / Cancel, per-module download progress, release notes + total size, active-module list with per-module Rollback (only shown when a previous version exists).
- **RuntimeUpdateCheckWorker** (WorkManager, periodic 12h, network-required) discovers in the background and posts a low-importance notification that opens the Update Center. It never downloads or installs automatically - every apply is signature-verified AND user-approved.

Nothing executable is shipped or run as a runtime update; native changes still use the APK path. As with Stage 1, until JARVIS_RUNTIME_SIGNING_PUBLIC_KEY is set, signature verification returns false and no update is ever applied.
