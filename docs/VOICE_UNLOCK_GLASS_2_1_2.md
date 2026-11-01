# Jarvis 2.1.2 — scoped native update

## Entry points
- Settings → Voice Unlock.
- Settings → Liquid Glass Lock Screen (also accessible within Voice Unlock).
- Settings → Offline AI Models → Use internet with local AI.

## Security and storage
Voice phrases request Android authentication; they are not identity credentials. Reference voice recordings are private AAC/M4A files and are not a biometric verifier. Android's secure keyguard is never disabled. PIN, pattern, password and biometrics remain the backup authentication path. Gallery photos are downsampled, converted to neutral grayscale and copied into private app storage. No API keys or provider settings are changed.

## Microphone lifecycle
Background matching reuses JarvisConversationService and its foreground microphone notification. No second background service is introduced. Foreground recording/testing borrows the microphone, stopping and restoring the existing active/standby mode. Phrase edits refresh the standby grammar. The bundled background Vosk model is English: custom background phrases must use its vocabulary; Android foreground speech testing uses available Android language packs. Android may stop background capture; re-enable it from the foreground.

## Custom lock screen
The companion activity is user-opened through a normal notification and uses showWhenLocked. It does not replace the Android system lock screen or use misleading alarm/call full-screen notification privileges. Gallery wallpaper is monochrome. Clock/date follow system time, timezone and 12/24-hour format. Blur uses RenderEffect on Android 12+ with a translucent fallback on older devices. Secure camera is used while the keyguard is locked. Flashlight is released on leaving the screen.

## Local models
The startup API-key gate now accepts a ready local engine/model with fallback enabled. The router uses local inference when there is no configured remote provider even if connected to Wi-Fi, or without internet. Optional web search uses the existing search infrastructure and sends the question to its provider; generation remains local. No web-search success is claimed on a failed lookup.

Gemma's pinned source was checked and serves HTTP 206 with the expected 3,106,738,272-byte size. Download no longer requires enough free RAM for inference before any bytes are transferred. Activation still checks RAM and must complete a real native test before marking the model ready.

## Legacy removal
Voice Experience activity/layout, overlay controller/panel/state/glass helpers, WakeOverlays, EdgeLighting service/view and their Settings/navigation/manifest references are removed. Owner Voice Enrollment is intentionally preserved because it is a separate existing feature.

## Delivery and limits
Native Kotlin/resources cannot be installed as a runtime data pack. The compatible Jarvis Native Update web_tool pack provides the official signed APK release entry point from the existing Runtime Feature Updates screen. Android must approve one in-place APK update signed by the same key. Runtime feature support otherwise stays intact.

The build pipeline compiles Kotlin/resources and runs unit tests. A successful build is not a claim of physical-device testing. Verify microphone permissions/denial, process lifecycle, lock-screen notification restrictions, secure-keyguard cancellation/success, camera/torch hardware and model inference on real target devices.