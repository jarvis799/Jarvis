# Offline models — 2.0.7

## User flow

Settings → Offline AI Models opens the premium lime/mint model page, without a payment gate. Choose TinyLlama 1.1B Q4_K_M (668,788,096 bytes) or Qwen2.5 1.5B Q4_K_M (1,117,320,736 bytes). The original upstream names, parameter sizes and licenses remain visible; Jarvis branding describes the assistant instructions, not ownership of model training.

The native CPU inference engine is compiled into the APK with NDK 27.0.12077973, CMake 3.22.1, and a SHA-256-pinned llama.cpp b5046 source archive. APKs support arm64-v8a and x86_64. No inference engine is fetched or executed from an untrusted download.

Downloads use immutable upstream revisions and known SHA-256 checksums. A .part file is never installed. Pause/resume validates the HTTP byte range; the final length, GGUF header and SHA-256 must match before the file is promoted. Partial bytes survive leaving the screen or restarting the app. A ViewModel retains an active transfer across rotation. Leaving the activity may pause the transfer; tap Resume on return.

Auto-setup loads the model, creates a bounded 2048-token context, and actually generates a small reply on the phone. Only then are the active model, tested-model marker and offline fallback saved. A setup failure is displayed inline with Retry; it is never presented as Ready. Previously verified files are reused on Retry rather than downloading another full copy.

Typed offline messages use the same native-command gateway and local inference path, including when a remote voice session is still running. Native phone commands remain ahead of model generation. Online HTTPS chat tries the API first, and an unreachable API can fall back to a tested local model. Cancellation is not treated as an API failure. Offline voice recognition/synthesis still depends on the device's installed language/voice packs; Gemini Live itself requires internet.

## Knowledge and identity

1B describes a learned parameter count, not a billion independent facts. The app does not retrain weights, claim a new training cutoff, or download a billion scraped facts. Concise Jarvis behaviour instructions, language preference and recent conversation are applied through each model's own chat template. Local models cannot know live news, prices or weather without internet. Their source models and language limitations are disclosed on the page.

## Validation status

Regression tests cover truncated downloads, wrong headers, same-size corruption, cancellation, and immutable model metadata. Both Android build workflows install the pinned native build tools. A local APK build and device inference have not been verified in the Base44 editing environment because the Java/Android toolchain is absent. Build with the existing GitHub Android pipeline, then verify a full download, activation, offline text reply, pause/resume and visible setup failure on a compatible phone.