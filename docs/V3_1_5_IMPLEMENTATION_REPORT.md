# Jarvis 3.1.5 — Implementation Report

Branch: `fix/wake-word-microphone` · versionName **3.1.5**, versionCode **315**

## 1. Release identity
`app/build.gradle.kts` now reports `versionCode = 315` / `versionName = "3.1.5"`.
The wake-word + microphone handover fix from 3.0.5/2.1.x is preserved untouched; every change below
sits on top of it.

## 2. One shared speech lane (no overlapping or late voice)
- `voice/SpeechQueue.kt` is a single-lane queue: replies, announcements and task results are spoken
  one after another, never on top of each other.
- `voice/VoiceSession.kt` is the only entry point for speaking, so every caller (conversation service,
  task results, idle check-in, voice interaction session) goes through the same lane and the same
  completion callback.
- Each utterance carries an owner tag and a completion callback, so a finished reply releases the
  microphone and the UI exactly once, and a cancelled or superseded reply can no longer "resume late".
- `voice/SpeechChunker.kt` / `voice/ChatSpeech.kt` keep long answers chunked with a stable locale so a
  chunked reply still sounds like one sentence flow.

## 3. Verified action status (no more permanent "Loading")
- `automation/status/ActionStatusPolicy.kt` defines the lifecycle for every executed action
  (pending → running → succeeded / failed / cancelled).
- `automation/status/ActionStatusTracker.kt` holds the current status and is the single source the
  spoken reply is derived from: Jarvis announces completion only after the executor reports a terminal
  status, and announces failure instead of hanging on "Loading".
- The conversation service uses the tracker for its task-done path, so the spinner and the voice answer
  can no longer disagree.

## 4. Agent crew
- `agent/crew/` adds a three-agent hierarchy: `PrimaryReasoningAgent` (planning), `ReviewAgent`
  (critique/safety pass) and `AssistantAgent` (the only agent that talks to the user).
- `MultiAgentCoordinator` + `CrewPolicy` + `CrewCall` + `CrewModels` exchange short reasoning summaries
  instead of unlimited dialogue, keeping token use and latency bounded.
- The crew is wired into the live service rather than running as a side demo: a user request is planned,
  reviewed, then executed and narrated by the assistant.

## 5. Persistent context
- Existing `data/memory/*` (DAO, repository, manager) and `personality/ConversationMemoryStore.kt` are
  used for durable facts and conversation continuity, with `memory/MemoryRetention.kt` bounding growth.

## 6. Reliability / correctness pass
- TTS engine creation uses the stored application context, with a recoverable start path, so a dead or
  busy engine no longer swallows a typed reply.
- Speech calls in coroutine scopes now pass the service context explicitly (fixed the 3.1.5 compile
  errors found in run 42465848519).

## 7. Verification
Compile, unit tests and a signed release APK were produced on CI — see
`docs/V3_1_5_TEST_REPORT.md`.
