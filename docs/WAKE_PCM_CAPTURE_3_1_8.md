# 3.1.8 wake path: actual PCM capture and recoverable model cache

The owner reports 3.1.7 still does not wake. Physical-device cause is not yet proven; prior threshold changes are not repeated.

## Verified source problems
- The old model cache considered am/final.mdl alone sufficient. Interrupted extraction could permanently leave graph/ivector files absent; errors while copying assets were swallowed. Reinstalling over the same app retained that bad private cache.
- Vosk SpeechService used a fixed 6400-byte recorder buffer instead of the phone's AudioRecord.getMinBufferSize. Its worker could throw on startup/read without the app owning the failure boundary; cancel used an unbounded thread join.
- 3.1.7 treated decoder callbacks (including empty hypotheses) as microphone health. Those are not proof of non-zero microphone input.
- HomeFragment rendered OFF before checking standby, so a running wake listener was shown as powered off. Its retry button started a conversation rather than retrying the wake listener.
- Packaged Gr.fst was inspected: jarvis and jervis are real vocabulary symbols. A missing built-in Jarvis vocabulary entry is not the explanation.

## Changes
- Wake-only capture now reads PCM directly with a device-sized buffer, validates start and every read, catches worker failures, tries MIC when VOICE_RECOGNITION cannot open, and alternates source on retries. Native recognition and existing grammar/thresholds remain.
- Readiness requires non-zero PCM; ten seconds of exact digital silence reports the missing signal through the existing error surface. Quiet but non-zero input is not treated as mute. PCM is not recorded to disk or uploaded.
- Stop invalidates callbacks before closing; the worker owns the native decoder; stop unblocks the audio read and the join is bounded.
- Cache reuse requires the packaged UUID, all required non-empty files and a completion marker written only after native load. This version repairs old caches automatically. Extraction failures are no longer swallowed.
- Existing status and retry controls now reflect the actual standby state. Owner voice verification, Stop persistence, online/offline reply behavior, TTS and unrelated features were not changed.

## Verification
- Added six PCM/buffer regression cases and three cache-completeness regression cases; existing six listener-generation cases remain.
- CI results will be recorded after the signed build finishes. No physical-phone success claim is made.

## Device acceptance (not yet performed)
Open the update and allow Microphone. In standby, wait for Microphone signal confirmed, then say Hey Jarvis. If startup fails or Android returns zero audio, the existing error panel must give the reason. Repeat wake/standby and ensure deliberate Stop still prevents automatic capture. Neither compilation nor synthetic/JVM tests prove recovery on the owner's phone.
