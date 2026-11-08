# Jarvis 3.1.5 — Build & Test Report

Branch: `fix/wake-word-microphone` · workflow `android-apk.yml`

## Result summary
| Run | Head | Outcome |
| --- | --- | --- |
| 42463557222 | pre-3.1.5 voice round | success (compile + assembleDebug/Release path verified) |
| 42465848519 | c907f3e | **compile failure** — 5 Kotlin type errors in `JarvisConversationService.kt` (wrong `this` passed as `Context`, nullable summary text) |
| 42466026997 | e48a77f | **success** — compile + unit tests + signed release APK |

## Fix applied for the failure
Three speech call sites passed the enclosing coroutine scope / anonymous object where a `Context` is
required, and one passed a nullable string. They now pass `applicationContext` (the service context) and
normalise the summary text with `orEmpty()`. No behaviour change beyond the fix.

## Artifact
- Run 42466026997, artifact **Jarvis-release-apk** (≈94 MiB), built from commit `e48a77f`.
- `versionCode 315`, `versionName 3.1.5`.
- Step chain: install offline build tools → Gradle cache (wrapper 8.9) → compile + unit tests →
  restore signing key → `assembleRelease` → upload artifact. All green.

## On-device checklist (manual, after install)
1. Wake word still triggers after a cold start and after the app has been idle (regression guard for the
   3.0.5 fix).
2. Two questions asked back to back: the second reply never starts before the first finishes.
3. A long answer (multi-chunk) is spoken in order, with no repetition of the first chunk.
4. Ask Jarvis to perform an action (app open / call / message): the reply reports success only after the
   action really finished; a blocked action reports failure instead of staying on "Loading".
5. Interrupt with the stop word mid-reply: audio stops, the lane is free, the next request works.
6. Idle check-in no longer speaks over an in-flight answer.
