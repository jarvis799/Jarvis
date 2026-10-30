# JARVIS voice upgrade — source audit and integration record

Repository: `github.com/jarvis799/Jarvis` · audited commit `7f08b2b` (main)
Written after reading the real sources, not from assumptions. Every file named below exists
in this repository at the listed path.

> **Build status: not compiled.** This workspace has no Android SDK, no Gradle and no
> Android toolchain, so `./gradlew :app:testDebugUnitTest` was **not** run here. The code
> below was written against the real interfaces and reviewed by hand; it must be validated
> by your CI (`.github/workflows/android-apk.yml`) before it is trusted. Nothing in this
> document claims a passing build or measured latency numbers.

## 1. What the codebase actually does (audit)

| Area | Real file | Reality found |
| --- | --- | --- |
| Main voice pipeline | `background/JarvisConversationService.kt` (4026 lines) | Gemini Live over WebSocket. Mic → `audio/AudioRecorder.kt`, playback → `audio/AudioPlayer.kt` (24 kHz PCM). Vosk runs a continuous wake/command stream. |
| Second voice pipeline | `background/HttpChatSession.kt` (129 lines) | Custom HTTPS endpoint path: `SpeechRecognizer` → `AgentEngine.process()` → `HttpSpeechOutput` (Android `TextToSpeech`). **This path waited for the complete answer before speaking at all.** |
| LLM call | `network/HttpChatClient.kt` | OkHttp, three request shapes (prompt-URL GET, OpenAI messages, prompt/input JSON). **No streaming** (`"stream": false`). Cancellation already wired via `invokeOnCancellation`. |
| Command routing | `agent/AgentEngine.kt` | All device actions, plugins, permissions, shell and web search return *before* the final `HttpChatClient().ask(...)`. That call is the only plain conversational answer. |
| TTS | `background/HttpSpeechOutput.kt` | Android `TextToSpeech`, whole-string `say()`, has `interrupt()`, pause/resume with `onRangeStart` offsets. |
| Audio processing | `audio/AudioRecorder.kt` | Already uses `VOICE_COMMUNICATION` + hardware `AcousticEchoCanceler`, `NoiseSuppressor`, `AutomaticGainControl`. |
| Barge-in (Live path) | `JarvisConversationService.kt` ~1534 | Only with a headset connected: loudspeaker leakage would cause self-interruption. Interruption = `audioPlayer.flush()`. |
| Persistent memory | `data/memory/*` + `JarvisApp` | Room database, `MemoryManager`/`MemoryRepository` already exist and are wired into `JarvisApp` and `CommandExecutor`. **The HTTPS voice path did not use them.** |
| Accessibility | `automation/accessibility/JarvisAccessibilityService.kt` | Already present. |
| Offline runtime | `util/OfflineEngine.kt` | Offline answers exist; there is **no local LLM runtime** (no llama.cpp / MediaPipe dependency). |

## 2. Integration map — where each requirement landed

| Requirement | Implemented in |
| --- | --- |
| Incremental LLM output | `HttpChatClient.stream()` (new, SSE) + `AgentEngine.process(..., onDelta)` |
| Safe text chunking | `voice/SpeechChunker.kt` (sentence marks incl. `।` `॥`, soft break at whitespace, never mid-word) |
| Incremental playback | `background/StreamingSpeechOutput.kt` (phrase-level queue, bounded, ordered) |
| Stale-response rejection | `voice/VoiceTurnCoordinator.kt` (generation tokens) used by every callback |
| Interruption / barge-in | `voice/VoiceActivityGate.kt` + `voice/BargeInMonitor.kt` + `HttpChatSession.interrupt()` |
| Stop audio never restarts | `audio/AudioPlayer.kt` → `interruptForBargeIn()` + drop window; called from the two real barge-in sites |
| STOP command | `voice/StopCommandMatcher.kt` (bn/hi/en/romanised, device-object guard, bare stop only while working) |
| Unclear speech gating | `voice/SpeechConfidencePolicy.kt` (never invents a score, bounded clarification) |
| Persistent memory in voice | `HttpChatSession.memoryContext()` / `rememberTurn()` → existing `MemoryManager` |
| Tests | `app/src/test/java/com/jarvis/assistant/voice/*Test.kt` (JUnit 4, pure Kotlin) |

## 3. Behaviour now

1. Ask a question over the HTTPS voice path → the answer is requested as an SSE stream.
2. Each delta goes through `SpeechChunker`; the first completed sentence is spoken while the
   rest is still generating. Playback is ordered and gapless between phrases.
3. While Jarvis speaks, `BargeInMonitor` keeps the mic open. It requires **150 ms of
   sustained speech** above a stricter threshold, so a cough or a fan does not interrupt.
4. On confirmed barge-in: generation token invalidated → HTTP call cancelled (OkHttp call
   cancelled through `invokeOnCancellation`) → TTS queue dropped → mic released → listening.
5. Anything still in flight for the cancelled turn is dropped by token mismatch. Audio that
   already left the server cannot restart playback (`AudioPlayer` drop window).
6. An empty or contradictory transcript produces a clarification in the user's language
   instead of a guessed command, at most twice in a row.
7. "stop" / "থামো" / "रुको" (and named forms) stop speech before the text reaches the LLM.
   "ফোন বন্ধ করো" and "stop the music" are left to the device-command layer.
8. Completed turns are written to the existing Room memory, and a short context summary is
   added to the next prompt — the HTTPS path now remembers like the rest of the app.

## 4. Honest limitations (do not report these as working)

- **TTS is phrase-level, not audio-streaming.** Android `TextToSpeech` cannot synthesise
  incrementally. The win is that speech starts on sentence one instead of after the whole
  answer; there is still one synthesis round-trip of latency before each phrase sounds.
- **Barge-in is disabled unless the platform actually enables echo cancellation.** The
  monitor checks `AcousticEchoCanceler` after enabling it; if it did not engage, barge-in
  stays off rather than self-interrupting. Many mid-range devices will therefore keep the
  previous tap-to-interrupt behaviour. Verified only by log line `BargeInMonitor: barge-in
  capture: AEC=true …`.
- **Speech recognition is still Android `SpeechRecognizer`** — one locale per request, no
  automatic language identification, no reliable confidence on all devices. Bengali/Hindi
  accuracy cannot be improved without a different engine (see §5). When the engine supplies
  no confidence score, none is invented: only empty/conflicting/too-short transcripts are
  gated.
- **Barge-in does not understand words.** It detects sustained speech energy, not "stop".
  Speaking "stop" during playback interrupts by voice activity, not by recognition.
- The `AudioPlayer` drop window is 1500 ms, cleared early by the server's `turnComplete`.
  This is a bounded safety window, not a protocol guarantee.
- Gemini Live path is unchanged apart from using `interruptForBargeIn()` at its two real
  interruption sites.

## 5. Deferred (not started — no placeholder code was added for these)

- **Offline AI model manager + downloader** (PRD Feature E/F/H). Requires a native runtime
  dependency (llama.cpp/GGUF or MediaPipe/LiteRT), a foreground download service with
  `FOREGROUND_SERVICE_DATA_SYNC`, Hugging Face metadata validation and a model-routing layer.
  None of it exists in the repo today; `util/OfflineEngine` is not an LLM runtime.
- **Screen understanding beyond the existing AccessibilityService** (OCR/vision fusion,
  confirmation flow for consequential actions).
- **White/lime glassmorphism settings screen** + wiring the new voice controls
  (`assets/settings/settings.html` is the real settings UI, 979 lines, and needs its bridge).
- **WebRTC** — not introduced. It adds no LLM token streaming or TTS streaming, and the
  current OkHttp path already supports cancellation.
- **Multilingual STT upgrade** — the honest options are Vosk models for `bn`/`hi` (offline,
  already a dependency, needs models) or a server STT. Both are separate work items.

## 6. How to validate

```bash
./gradlew :app:testDebugUnitTest        # voice unit tests (chunker, VAD, stop, confidence, tokens)
./gradlew :app:assembleDebug            # compile check
```

Device checks that matter, in this order:

1. Ask a long question on the HTTPS path — speech must start before the answer finishes.
2. Interrupt mid-answer — no further audio from the old answer, ever.
3. Repeat interruptions rapidly — no crash, no overlapping speech, mic recovers.
4. Say "stop" while thinking — the request is cancelled.
5. Speak unclearly — a clarification, not a command.
6. Kill the app and reopen — the conversation is still in memory (Room).
7. Run with a fan on and with the loudspeaker — note whether AEC engaged in logcat.

Nothing above has been executed in this environment. Report the real results from your
device/CI run; do not copy these steps as if they passed.