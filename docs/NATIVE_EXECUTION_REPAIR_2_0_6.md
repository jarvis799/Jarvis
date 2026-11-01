# Native execution repair — 2.0.6

## Scope
This revision repairs the native action path. It is not a completed A–Z visual redesign or offline-model release.

- A shared native gateway now handles typed text, HTTPS voice, Gemini Live native tools, and the final user-transcript fallback.
- Time uses the device clock. Relative reminders are persisted with WorkManager and notify locally; Android may delay them. Alarms open the system Clock confirmation and do not report a saved alarm without evidence.
- SIM answer/reject actions check call state and the runtime phone permissions. The permissions screen now requests answer-call and phone-state permissions as well as call permission. OEM/default-dialer restrictions may still apply.
- PCM is gated before upload with a sustained-energy threshold and onset buffer. Isolated acoustic clicks and quiet frames are not uploaded. This is not a speaker-identification or TV-speech classifier; device tuning is still required.
- HTTPS recognition never forwards unintelligible hypotheses after exhausting clarification attempts. Engine confidence zero is treated as low confidence, not as a missing score.
- Wake matching requires a complete configured phrase, and failed Vosk sessions release their old listener before restarting. Incoming ringing calls no longer count as an ongoing off-hook call that suppresses wake detection.
- Gmail has a fresh runtime-state tool; App Password credentials are not confused with Workspace OAuth. The model is instructed to read saved Gmail through native commands.
- Email agent creation persists the actual reply text and schedules the worker only after verified connection. WhatsApp reply cooldown no longer suppresses all future replies to a sender merely because their previous reply text was identical.
- Native commands no longer require an AI key. Native-only Android recognition depends on the phone's installed speech recognizer/language; owner-only voice verification restrictions remain intact.
- HUD Target remains transparent. The native bridge reaches the target renderer, and iframe reloads receive the latest state/audio again.
- The existing model downloader is directly reachable from Settings rather than buried in the feature hub. The screen reports the missing native inference engine honestly.

## Not completed
- Bundled 1B/2B model inference, automatic download-to-working-model setup, and conversation with no API key remain incomplete. `LocalModelRuntime` currently has no bundled native engine; changing a model name or downloading a file cannot fix that.
- A full premium liquid-glass redesign with real backdrop blur is not part of this revision.
- Do not market this as all features working or 100% complete.

## Verification
Targeted regression tests were added for time parsing, PCM gating, and zero-confidence speech. Device call behavior, wake words with owner voice enabled, Gmail sign-in, notification delivery, OEM battery restrictions, and end-to-end sub-agent replies require a signed build and real-device verification. No successful runtime result is claimed from source inspection alone.