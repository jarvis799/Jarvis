# 3.1.7 wake-listener lifecycle repair

## Verified source defects
- A SpeechService construction/start failure only logged an error; no retry was scheduled. The standby flag remained true.
- MainActivity returned early for that standby flag, preventing foreground recovery after microphone permission or input contention changed.
- Both the home status and notification claimed standby/listening before the audio worker returned any data.
- Timeout attempted to start another worker without detaching the stopped SpeechService; late callbacks were shared across generations.
- Model readiness waited forever after extraction/load failure. Recognizer native handles were not explicitly closed.

## Repair
- Start confirmation requires an audio callback; startup/model/retry states are displayed through existing status and notification surfaces.
- Retry failed starts with one bounded-backoff job (1.5 seconds to 30 seconds); model readiness times out after 30 seconds.
- A monotonic 12-second callback heartbeat detects non-responsive workers. Generation-scoped callbacks cannot tear down a replacement worker or wake after Stop.
- Invalidate callbacks, cancel/join audio capture, release its recorder, then close the native recognizer before retry or conversation handoff.
- Foreground resume can request recovery even when the standby flag was already set. Microphone permission and deliberate powered-off state are respected.
- Wake vocabulary, confidence thresholds, owner verification, existing TTS voice, microphone handoff delay, API setup, and other features remain unchanged.

## Verification scope
Six deterministic JVM regression cases cover worker confirmation, heartbeat timeout, stale callbacks, stop, fresh restart and bounded retry backoff. CI must compile/test and build the signed release before delivery. These checks do not prove activation on the owner's physical phone; no on-device logs or microphone access are available here.

## On-phone acceptance
Open the updated app with microphone permission granted. If deliberately powered off, press Start first. Enter standby and wait for STANDBY (not WAKE_STARTING/WAKE_RETRYING). Say Hey Jarvis, then repeat after returning to standby. Temporarily deny/re-enable microphone permission and confirm recovery after returning to the foreground. A startup failure must show its reason instead of silently claiming to listen. Stop must still prevent all automatic restart paths.
