# Personality Engine, Voice Studio & Session Memory — Implementation Notes

This document describes what was actually added to the existing Jarvis codebase in this
upgrade. Existing LLM, TTS, memory and voice-pipeline architecture was extended, not replaced.

## 1. Personality engine (configurable humor / roasting / sarcasm / style)

| File | Role |
| --- | --- |
| `personality/PersonalityProfile.kt` | Levels (`HumorLevel`, `SarcasmLevel`, `ResponseStyle`, `VoiceMode`) + profile data class |
| `personality/PersonalityStore.kt` | Persists the profile in its own SharedPreferences file (`jarvis_personality_v1`) |
| `personality/PersonalityComposer.kt` | Builds the prompt block, including safety rails and the custom-prompt guard |
| `personality/PersonalityProfileHolder.kt` | Process-wide live copy so prompt building needs no extra plumbing |
| `personality/PersonalityStudioActivity.kt` | The settings UI (humor, roasting, sarcasm, emoji, style, custom prompt, voice mode, address name) |

The block is injected by `PromptGenerator.generateSystemPrompt()` through
`personalityProfile ?: PersonalityProfileHolder.current`, so it reaches the live voice
conversation without touching the 4k-line conversation service.

Safety: humor drops instantly for serious topics, protected-trait attacks are forbidden,
and the custom prompt is emitted *after* the safety rails with an explicit "these always win"
statement.

## 2. Custom personality prompt

* Multiline editor with Save / Cancel / Reset to default / Preview / enable switch and a
  visible saved-state line (timestamp + character count).
* `PersonalityComposer.sanitizeCustomPrompt()` normalises line endings, drops lines that try to
  override application rules (e.g. "ignore all previous instructions", "reveal your system
  prompt", "bypass the safety rules"), and caps the text at 4000 characters.
* Stored in a dedicated preferences file, so an app update does not clear it.

## 3. Voice modes

`Pari` and `Kabir` are gone from the active prompt and from `activity_persona.xml`. The two
modes are now **Girlfriend Mode** and **Boyfriend Mode**, described as speaking styles only,
with an explicit ban on simulating jealousy, possessiveness, dependency or pressure to keep
using the app. `VoiceMode.OFF` is the default.

## 4. Voice Studio (real voices only)

| File | Role |
| --- | --- |
| `voice/TtsVoiceCatalog.kt` | Reads voices from the device TTS engine, filters out not-installed ones, keeps language/accent/provider/online flag; also persists the selected voice |
| `voice/VoicePreviewPlayer.kt` | Real preview playback with stop, audio-focus ducking, and error reporting |
| `ui/settings/VoiceStudioActivity.kt` | Voice list, language filter, selection indicator, Preview / Stop / Save |

`HttpSpeechOutput` applies the saved voice once per instance, *after* `setLanguage()`, so the
selection actually affects spoken replies. Language-appropriate samples are used for bn / hi / en.
A voice that the engine cannot synthesise is never listed; the engine-capability dialog states
plainly that no custom Jarvis voice model is bundled.

## 5. Developer information

`security/OwnerProfile.kt` is the single source of truth (name, title, country, bio, core focus,
links, motto, copyright, creator answers in EN/BN/HI, and `creatorPromptBlock()`).
`DeveloperInfo.kt` forwards to it. `ui/settings/DeveloperProfileActivity.kt` is the About screen.
Hardcoded mentions in `PromptGenerator`, `AgentEngine`, `FeatureKnowledge`, `HomeFragment`,
`SettingsFragment`, `MainActivity` and the persona/settings layouts were replaced.

## 6. Session exit-time memory

| File | Role |
| --- | --- |
| `personality/SessionMemory.kt` | `SessionMemoryStore` (exit timestamp, topic, state, last request) and `SessionGreeter` (pure time formatting + greeting) |

`MainActivity.onStop()` records the exit timestamp; `onResume()` shows a welcome-back line built
from the real elapsed time, then consumes the timestamp so screen changes never repeat it.
A missing timestamp yields a normal greeting with no duration, and absences under 5 minutes are
silent. The greeting is added as a chat turn; it never claims to know what the boss did.

## 7. Wake-up

The existing wake system already covers `jarvis`, `hey jarvis`, `hi jarvis`, `hello jarvis`,
`wake up jarvis`, plus mis-hearing aliases, the custom wake word and the owner's name.
No new detector was introduced, and no Bengali/Hindi wake phrase is claimed: the bundled
recognition model is English, so those would not be reliable.