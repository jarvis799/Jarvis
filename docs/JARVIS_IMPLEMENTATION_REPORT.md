# JARVIS — implementation report (this round)

Branch: `fix/wake-word-microphone` (never `main`). Wake-word commit `bf67b5f` is preserved
untouched and its build passed.

## Commits added

| Commit | File | Why |
| --- | --- | --- |
| `534d2f4` | `app/src/main/java/com/jarvis/assistant/background/StreamingSpeechOutput.kt` | live voice replies |
| `b9abc7e` | `app/src/main/java/com/jarvis/assistant/background/HttpSpeechOutput.kt` | typed/HTTP replies |

No other source file was modified. `voice/TtsDelivery.kt` (language, chosen voice, prosody) was
deliberately left alone, so the Jarvis voice and the selected voice style are unchanged.

## What was wrong and what changed

### `StreamingSpeechOutput` (commit `534d2f4`)

1. **A failed engine start muted the session permanently.**
   `ready` was completed once and never retried, so an engine that failed to initialise (busy,
   still loading, another engine starting) silenced every reply until the process died.
   *Now:* the engine lives in `startEngine()`, the progress listener is reusable, `ready` is
   re-creatable and `ensureReady()` recreates the engine up to `MAX_INIT_ATTEMPTS` times before
   reporting "Android TTS is unavailable".

2. **A rejected piece of text was marked as spoken and skipped.**
   `onError` completed the same deferred as `onDone`, and the loop then advanced to the next
   piece — the words were lost, which is the "missing speech chunks" symptom.
   *Now:* `onError` sets `failedNow`; the loop retries the same piece from the last confirmed word
   position (`resumeOffset` from `onRangeStart`) up to `MAX_PIECE_RETRIES` times, and only then
   reports an honest error.

3. **Pause replayed already-heard audio.**
   Resume position was ambiguous when the engine never reported word progress.
   *Now:* `rangeKnown` records whether the engine reported a real position; resuming continues
   mid-piece with that position, and only restarts the piece when the position is unknown (so no
   word is ever lost and no confirmed-spoken text is repeated).

4. **Overlap safety kept.** `QUEUE_FLUSH` per piece, per-turn token checks and the bounded
   channel stay as they were: only one piece can be speaking, and a cancelled turn never resumes.

### `HttpSpeechOutput` (commit `b9abc7e`)

1. **A null engine left the reply waiting forever.** `engine?.speak(...)` returned `null`,
   which is not `ERROR`, so nothing completed and `done.await()` never returned.
   *Now:* `playRemaining()` handles `null` and `ERROR` the same way, recreates a dead engine and
   retries the identical words after a short backoff (max `MAX_SPEAK_ATTEMPTS`).

2. **A single engine rejection killed the whole reply.** The synchronous-error path called
   `failSpeech()` immediately while only the asynchronous path retried.
   *Now:* both paths retry the same chunk; `failSpeech()` is reached only when the retries are
   exhausted, and it says so out loud instead of silently dropping the rest.

3. **Engine init was one-shot.** Same fix as the streaming class: `startEngine()` + `ensureReady()`.

Neither file gained a new dependency, a new permission or a new class.

## What was deliberately *not* done

- **No new task queue / agent engine (Phase 3).** `automation/agent/AgentTaskEngine.kt` and
  `agent/planning/TaskPlanner.kt` already implement task states, execution and verification;
  adding a second engine would have created the duplicate-execution bug the brief warns about.
- **No new memory store (Phase 2).** `data/memory/*` plus `memory/MemoryRetention.kt` already
  implement 30-day retention, pruning and user review/deletion.
- **No new notification policy (Phase 5).** `NotificationAnnouncementPolicy` already has the
  master switch, per-app exclusions, quiet hours and duplicate suppression.
- **No claim of always-on background operation.** Android and OEM restrictions still apply.
