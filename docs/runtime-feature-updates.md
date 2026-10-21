# Runtime Feature Updates

Jarvis installs feature packs at runtime from the GitHub Releases of your
repository - no APK download and no reinstall for ordinary feature updates.
Open it in Settings > Runtime Feature Updates.

## Release manifest

Attach an asset named feature-manifest.json to a published GitHub Release.
The app reads the latest published release and looks for this asset.

    {
      "format_version": 1,
      "features": [
        {
          "feature_id": "unit-converter",
          "feature_name": "Unit Converter",
          "version": "1.0.0",
          "description": "Offline unit converter mini-tool.",
          "minimum_app_version": "3.3.1",
          "download_url": "https://github.com/OWNER/REPO/releases/download/TAG/unit-converter-1.0.0.zip",
          "sha256": "<64-hex SHA-256 of the zip>",
          "feature_type": "web_tool",
          "changelog": "First release.",
          "download_size": 4321,
          "entry": "index.html"
        }
      ]
    }

Fields:

- feature_id: unique id, 2-40 chars, lowercase letters, digits or dashes
- feature_name: display name
- version: dotted version, compared against the installed version
- description: short summary shown in the list
- minimum_app_version: lowest Jarvis version that can run the feature; a
  feature whose minimum is above the installed version is shown as
  "Requires App Update" and cannot be installed as a runtime feature
- download_url: https GitHub Releases URL of the feature package zip
- sha256: SHA-256 of the zip, verified before anything is unpacked
- feature_type: commands, web_tool or prompt_pack
- changelog: what changed in this version
- download_size: package size in bytes (limit 50 MB)
- entry: web_tool only, the HTML page to open

## Feature package format (the zip asset)

Every package zip must contain feature.json:

    {
      "format_version": 1,
      "feature_id": "unit-converter",
      "feature_type": "web_tool",
      "entry": "index.html"
    }

Per type:

- commands: plugin.json, the existing Plugin Lab manifest (its id must equal
  feature_id). Installs hot-reload into the live plugin engine - no restart.
- web_tool: the entry HTML page plus any local HTML, CSS, JavaScript and SVG
  files. Opened in a sandboxed WebView with no file access and no JS bridges.
- prompt_pack: prompts.json, an array of
  { "name": ..., "content": ..., "tags": [...], "task_type": ..., "provider": ... }.
  Prompts land in the Prompt Lab.

Enforced rules:

- https GitHub Releases URLs only; the manifest is strictly validated
- SHA-256 verification before unpacking, 50 MB size cap
- zip-slip paths and executable payloads (dex/jar/apk/so/class) are rejected
- installation is atomic with rollback to the previous version
- no dynamic code loading: a feature is data, never executed code
- a feature that needs new native Kotlin code, permissions, services or
  manifest entries must ship as a normal APK update instead

## Publishing a feature

1. Zip the package (feature.json + payload files).
2. Compute the digest: sha256sum unit-converter-1.0.0.zip
3. Create a published GitHub Release and upload the zip as an asset.
4. Add the entry to feature-manifest.json and attach that asset to the same
   release.

Private repositories: store a personal access token under Settings >
Runtime Feature Updates > GitHub token. It is kept encrypted on the device
and is never hardcoded. Public repositories need no token.
