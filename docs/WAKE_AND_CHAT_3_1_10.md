# Jarvis 3.1.10 — composer overflow and muted wake handoff

## Findings from the 3.1.9 branch
- Send existed, but its unbounded horizontal row placed three text chips, attachment and Send in the same width. At phone widths the earlier children exhausted the row before Send was laid out.
- Mic mute only silenced the active HTTP/Gemini session. Standby never started while isSessionOn remained true, so there was no wake recognizer to hear the next Wake up Jarvis.
- An already-muted preference was also passed to both conversation engines after a detected wake; the session could be active but deaf.
- MainViewModel retained the old hasAllRequiredPermissions gate even though the Activity and Service had been repaired in 3.1.9.

## Targeted changes
- Dedicated fixed-width labelled Send beside the weighted message field. Existing chips are horizontally scrollable and attachment remains separately reachable. Existing send/keyboard handlers and reply persistence stay unchanged.
- Muting an active conversation hands ownership to the existing wake-only standby flow; it does not run two recorders. Wake activation clears saved mute before either voice engine starts. Explicit Start clears mute too.
- Explicit Stop/Power Off remains a real stop, including preventing a delayed wake handoff from reopening the mic. No thresholds, owner verification, signing or unrelated features changed.
- The ViewModel uses the same microphone-only start permission contract as the Activity and Service.

## Verification scope
- Added a real inflated-layout Robolectric regression for 320/360/411dp, normal/large fonts and keyboard-reduced height.
- CI must pass all native unit tests, resource/Kotlin compilation, the existing real-PCM Vosk acoustic probe, release assembly and permanent-key signature checks before publishing.
- Source trace: mic mute -> stopActiveConversation -> standby -> one PCM wake capture -> wake detection -> release capture -> handoff delay -> clear mute -> HTTP/Gemini active microphone -> existing command pipeline.
- Android hardware microphone/audio routing has NOT been exercised on the user's phone. A successful CI build is not proof of a device-level wake test.

## Use
Open the updated app and allow Microphone. Tap Start once if previously fully powered off. A normal mic-off tap now means wake standby; say Wake up Jarvis, wait for acknowledgement, then speak the command. A full Stop/Power Off or Android Force Stop deliberately disables wake listening until Start is pressed again.

## Completed release evidence
- Build run: https://github.com/jarvis799/Jarvis/actions/runs/42491825945 — completed successfully.
- Built commit: 6e62c6b7ed7f58e571d0290e0f7d5f34e31f8c97.
- Real-PCM wake probe, Kotlin/resources, full native unit suite including the new composer regression, signed release assembly, packaged offline engine checks and APK signature verification: passed.
- Resource-enabled unit tests use android.app.Application to avoid accidentally starting the production network/model/microphone services inside unit tests. No test was disabled or skipped to work around the first-run startup race.
- Published APK: https://github.com/jarvis799/Jarvis/releases/download/v3.1.10/Jarvis-V3.1.10.apk
- APK SHA-256: 58aedf945f57fb23674288c1959d4fd880add1ddfe2ff79c9fceff3f50150bc9.
- Physical-device wake and command capture remain unverified; the existing explicit Power Off behavior is intentionally retained.
