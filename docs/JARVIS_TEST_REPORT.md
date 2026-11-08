# JARVIS — test and build report

Branch `fix/wake-word-microphone`. Nothing was pushed to `main`.

## Builds actually run (GitHub Actions, workflow `android-apk.yml`)

| Commit | Run | Compile | Unit tests | Release APK | Result |
| --- | --- | --- | --- | --- | --- |
| `bf67b5f` (wake word / mic) | 42462737905 | pass | pass | produced | **success** |
| `b9abc7e` (typed TTS first attempt) | 42463374092 | **fail** | — | — | failure |
| `ddb1044` (compile fix) | 42463557222 | pass | pass | produced | **success** |

The single failure was a real compile error introduced by this round and is documented honestly:
`HttpSpeechOutput.kt:38:32 Unresolved reference 'context'` — `startEngine()` is a member function and
the constructor parameter is not a property, so it must use the stored `appContext`. Fixed in
`ddb1044`, and the rebuild passed.

## Verification of the final run (42463557222, commit `ddb1044`)

- `./gradlew :app:compileDebugKotlin :app:processDebugResources :app:testDebugUnitTest` → **BUILD SUCCESSFUL**
  (the existing unit-test suite — `AgentTaskEngineTest`, `WhatsAppAutoReplyTest`, `PermissionGateTest`,
  `CommandParserMediaTest`, `FailureAutopsyTest`, `BackgroundRemovalTest`, the emotion/persona/planner
  suites and the rest — ran as part of this step).
- `./gradlew :app:assembleRelease` → **BUILD SUCCESSFUL** (5m 46s), signed with the permanent release key.
- Step "Verify the APK was produced" → passed.
- Step "Verify offline engine is inside the APK" → passed, both `lib/arm64-v8a/libjarvis_llama.so` and
  `lib/x86_64/libjarvis_llama.so` confirmed inside the APK.
- Step "Verify the APK signature" → `apksigner verify --print-certs` printed the certificate.
- Uploaded artifact: **`Jarvis-release-apk`**, 98,867,069 bytes, not expired.
- Published download: release **`v3.0.5-build.264`**, asset **`Jarvis-V3.0.5.apk`**, 142,747,727 bytes.

So the APK was verified, not assumed: it exists as a run artifact and as a release asset, contains the
offline engine, and passed signature verification.

## What these tests do and do not prove

**Proven:** the code compiles, the existing unit-test suite still passes, the release APK is produced,
signed, contains the native engine and is downloadable.

**Not proven by CI (requires a physical device):** audible behaviour. The TTS fixes were verified by
reading the code path and by the compile + test gate, not by listening to a response on a phone. The
three speech behaviours to confirm by ear on the device are listed below.

## Device checks still needed (regression list)

1. Wake word still fires after the TTS round — `bf67b5f` is untouched, and microphone ownership is
   unchanged (no edit to `AudioRecorder` or `JarvisConversationService` in this round).
2. A long answer is spoken to the end, with no missing or repeated sentence.
3. Stop / interrupt mid-sentence, then speak again: the new answer starts, and the old one never resumes.
4. Typed command reply after the screen was idle for a long time: first tap should not be silent
   (this is the re-created-engine path).

## Known limitations (unchanged by this round)

- Nothing can guarantee uninterrupted background audio: Android and OEM battery restrictions still apply.
- `StreamingSpeechOutput` and `HttpSpeechOutput` each own a `TextToSpeech` instance. Turn tokens and
  `QUEUE_FLUSH` keep turns from overlapping, but a process-wide single voice session was **not**
  introduced — that is a larger refactor and was out of scope for a reliability fix.
- Phases 2–6 were audited, not rewritten: the memory store, retention policy, agent task engine,
  external-app flows and notification policy already exist in this repository (see the audit doc).

## Remaining tasks

- Device verification of the four checks above.
- Optional next round: a shared voice session so the charger line, the preview and a reply can never
  talk over each other.
- Optional next round: re-verify memory relevance ranking and task pause/resume paths end-to-end on a
  device, since they were only read, not exercised.
