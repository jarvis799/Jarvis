# JARVIS — Master Feature List

Living, numbered, de-duplicated list of every requirement sent so far.
Status legend: **Done in build** · **In progress** · **Queued** (not started) .
The PRD is intentionally NOT written yet — it is generated only when the user says `Ebar PRD dao`.

## Live status

| # | Feature | Status |
|---|---------|--------|
| 1 | Visual Core — orb styles + premium picker | **Done in build** (branch `android`) |
| 2 | Full Termux working terminal | Queued |
| 3 | Jarvis self-knowledge | Queued |
| 4 | Background development workspace | Queued |
| 5 | Built-in audio/video player | Queued |
| 6 | Favorite song playlists | Queued |
| 7 | Voice-first command handling | Queued |
| 8 | Audio/video understanding | Queued |
| 9 | 'Hey Jarvis' cross-app activation | Queued |
| 10 | Auto Error Fixer | Queued |
| 11 | Smart Media Memory | Queued |
| 12 | Task history + resumable jobs | Queued |
| 13 | Terminal confirmation mode for destructive commands | Queued |
| 14 | Project templates | Queued |
| 15 | Searchable media library | Queued |
| 16 | Playback queue | Queued |
| 17 | Local-first settings storage | Queued |
| 18 | Privacy indicator (mic / screen / files) | Queued |

## 1. Visual Core — orb styles (Done in build, branch `android`)

- Home screen now renders an orb stage instead of the previous avatar artwork: `assets/orbs/orb-host.html`.
- Default orb = the user's **Speaking Orb** (`assets/orbs/jarvis1.html`).
- Additional styles shipped: **Voice Orb** (`assets/orbs/voice-orb.html`) and **Face Flow 3D** (`assets/orbs/face-flow/`, with all licenses/credits kept alongside).
- Premium picker with live previews: API screen → *Visual Core — Orb Style* → **Open Orb Studio** (`OrbVisualActivity` + `assets/orbs/picker.html`).
- The choice is stored in `AppPreferences.orbStyle` and applied to the home screen immediately when it changes.
- Previous styles are untouched and still on disk, so nothing existing was deleted.

## 2. Full Termux working terminal

Real terminal experience on a Android-compatible shell/runtime: command execution, streamed output,
process control, working directories, storage access with permission, background tasks.
Unsupported packages and failed commands are reported honestly. No claim of full Linux compatibility.

## 3. Jarvis self-knowledge

Jarvis explains its real features, capabilities, granted permissions, connected services, limits and tools
from actual configuration and runtime state — never invented capabilities.

## 4. Background development workspace

Jarvis inspects user-selected storage/project folders, creates React apps, manages project files, runs
npm commands where supported, starts a local dev server and opens the localhost URL in Chrome.
Handles storage permissions, process lifecycle, port conflicts, logs and errors; long tasks stay manageable.
On-device Node-compatible runtime preferred where feasible.

## 5. Built-in audio/video player

Plays supported local audio and video inside Jarvis (no external player app). Video uses a compact
horizontal mini-player: play/pause, seek, progress, expand. Respects Android background/lifecycle limits.

## 6. Favorite song playlists

When the user marks a song as a favorite, its track reference and metadata are saved to a persistent local
playlist. Voice command "play my favorite songs" plays that playlist in order. Add/remove, shuffle, repeat
and missing-file handling included. Only tracks the user has access to — copyright/access limits never bypassed.

## 7. Voice-first command handling

While media plays, Jarvis reacts to explicit wake words and user commands instead of the video's dialogue:
echo cancellation, audio focus, wake-word detection and voice activity detection where feasible.
No promise of perfect separation from all background speech. Captions/transcripts or media analysis
when available and authorized.

## 8. Audio/video understanding

On request, an accessible video/audio track is analyzed to summarize what is said, identify the topic,
describe visible scenes and answer questions about the content. Respects file permissions, available
models/APIs and processing limits. Never claims to have seen or heard content that was not processed.

## 9. 'Hey Jarvis' cross-app activation

Where Android permits, wake-word activation while Jarvis runs in the background or another app is open
(e.g. Instagram). Premium side-lighting animation with a futuristic AI visual, then the command is accepted.
Android foreground service and required permissions used where appropriate; battery, microphone and OS
restrictions explained honestly — no promise of unrestricted background activation.

## 10. Auto Error Fixer

When a React app or npm build errors, Jarvis reads the log, explains the problem in plain language, takes a
backup before changing anything, applies the fix and then verifies the fix (re-runs the build) before
reporting success.

## 11. Smart Media Memory

Stores songs, playlists, last playback position and user preferences so playback can resume exactly where it
was left, across app restarts.

## 12–18. Evaluated ideas (kept as separate items, no duplicates)

- **12. Task history + resumable jobs** — every longer job is listed and can be resumed.
- **13. Terminal confirmation mode** — destructive commands require explicit confirmation.
- **14. Project templates** — starter templates for new projects.
- **15. Searchable media library** — search across local media by name/artist/album.
- **16. Playback queue** — build, reorder and persist a queue.
- **17. Local-first settings storage** — settings stay on-device first.
- **18. Privacy indicator** — clear on-screen indicator when the mic, screen or files are being accessed.

Merge notes (kept de-duplicated): automatic error diagnosis → item 10; persistent playlists → items 6 and 11;
resume-where-you-left-off → item 11.

## Implementation rules (apply to every item)

1. Upgrade the existing project in place — never rebuild from scratch.
2. Inspect the current code before changing anything.
3. Reuse the existing architecture and the existing TTS.
4. Never duplicate an existing feature.
5. Request only permissions that the feature actually needs.
6. Protect credentials (API keys stay in encrypted storage).
7. Test each feature on the target Android device; keep `assembleDebug` and the unit-test suite green.
8. Separate what works fully on-device from what needs internet, external APIs or extra runtimes.
9. Never silently replace signing keys; never break current backend integrations.
