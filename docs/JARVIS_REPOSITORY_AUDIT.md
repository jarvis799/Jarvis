# JARVIS — repository audit (branch `fix/wake-word-microphone`)

Audited at commit `b9abc7e`. The instruction for this round was explicit: inspect first, do not
duplicate what already exists, do not rewrite the app.

## What the repository actually is

| Fact | Value |
| --- | --- |
| Tracked files | 833 (497 Kotlin sources) |
| Module | single `app` module, package `com.jarvis.assistant` (unchanged) |
| Toolchain | Gradle wrapper 8.9, AGP 8.7.2, Kotlin 2.0.20, JDK 17, compile/target SDK 34 |
| CI | `.github/workflows/android-apk.yml` — compiles, runs unit tests, builds a signed release APK, verifies the offline engine, uploads `Jarvis-release-apk` and publishes a direct `.apk` release asset |
| Unit tests | `app/src/test/...` — 20+ suites, including `AgentTaskEngineTest`, `WhatsAppAutoReplyTest`, `CommandParserMediaTest`, `PermissionGateTest` |

## Phase-by-phase: what already existed before this round

**Wake word / microphone — already fixed in `bf67b5f` (do not touch).**
`background/JarvisConversationService.kt` (258 KB) started the Vosk wake engine, handed the
microphone to the recognition session and restarted on error. Audited again in this round: the
single-listener guard, the debounced restart job and the 300 ms handover are in place and the
commit built successfully (`dev style — JARVIS Android APK`, run 42462737905).

**Phase 1 — TTS.** Existing and reused, not rewritten:
`background/StreamingSpeechOutput.kt` (live voice replies), `background/HttpSpeechOutput.kt`
(typed/HTTP replies), `voice/TtsDelivery.kt` (language → chosen voice → prosody, one place),
`voice/TtsVoiceCatalog.kt`, `voice/TtsVoiceSelector`, `voice/SpeechText.kt` (markdown/code
clean-up), `voice/SpeechChunker.kt`, plus `voice/VoicePreviewPlayer.kt` and
`battery/ChargerSpeechCache.kt` for the preview and charger lines.
Defects found by reading the code (all fixed this round, see the implementation report):
a failed engine start muted the session for the whole process; one rejected piece of text was
treated as *spoken* and skipped, leaving a hole mid-reply; pausing replayed the current piece
from its start; `HttpSpeechOutput` could be left waiting forever when `engine` was null.

**Phase 2 — long-term memory.** Already implemented, nothing new invented:
`data/memory/MemoryManager.kt` (22.8 KB), `MemoryEntity/Dao/Database/Repository`,
`memory/MemoryRetention.kt` (30-day default, owner-adjustable, explicit prune, "30 days is the
guaranteed minimum, never an automatic wipe"), `personality/ConversationMemoryStore.kt`,
`personality/SessionMemory.kt`, `agent/memory/UserMemory.kt`, with review/delete UI in
`ui/memory/MemoryActivity.kt`, `ui/memory/MemoryAdapter.kt` and
`personality/MemoryManagementActivity.kt`. Retention, expiry and user-controlled deletion are
therefore present in the existing architecture; this round did not re-verify every ranking path.

**Phase 3 — multi-task agent engine.** Already implemented:
`automation/agent/AgentTaskEngine.kt` (29.7 KB), `AgentModels.kt` (task states),
`SerializedActionExecutor.kt`, `ActionVerifier.kt`, `ScreenSignatureCalculator.kt`,
`agent/planning/TaskPlanner.kt`, `agent/planning/ParallelUniversePlanner.kt`,
`agent/team/AgentTeam.kt`, `agent/recovery/ErrorRecovery.kt`, `developer/TaskForceManager.kt`,
with an existing test suite `AgentTaskEngineTest.kt`. A parallel task queue was **not** added —
that would have duplicated this engine.

**Phase 4 — external AI + verification.** Already implemented:
`ai/ExternalAgentRegistry.kt`, `util/AppLauncher.kt`, `automation/flows/AppFlows.kt`,
`browser/BrowserSessionManager.kt`, `vision/ui/AutomationSession.kt`,
`automation/agent/ActionVerifier.kt` (post-action verification), `network/AiGateway.kt`,
`model/LlmRouter.kt`, `network/GeminiLiveWebSocket.kt`.

**Phase 5 — notification intelligence.** Already implemented:
`notifications/JarvisNotificationListenerService.kt`,
`notifications/NotificationAnnouncementPolicy.kt` — master switch, per-app exclusions, quiet
hours window, and a 45-second duplicate window kept in memory only (message contents are never
written to storage by the policy), `NotificationBriefingBuilder/Activity` for summaries, and
`automation/whatsapp/WhatsAppAutoReplyManager.kt` + `AutoReplyRules.kt` for explicitly
allowlisted auto-reply policies, covered by `WhatsAppAutoReplyTest.kt`.

**Phase 6 — service and performance.** Existing: foreground service + wake boot receiver
(`background/WakeBootReceiver.kt`), charger monitor, `performance/PerformanceMonitoringService.kt`,
`util/CrashGuard.kt`, `reminders/ReminderScheduler.kt`. Not modified this round.

## Conclusion

Six of the eight phases were **already built** in this repository. The only verified defects
found by reading the actual source were in the speech-output path, so that is the only code
changed. Everything else is documented here rather than re-implemented.
