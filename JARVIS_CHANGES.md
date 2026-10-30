# Jarvis Android working-copy changes

This ZIP contains the actual modified Android source, not a patch to apply and not a web assistant. Based on commit 7ad5fc899e04d05d8e006082ca679b7896f4e572. Use the Push button in the editor to commit these changes to GitHub.

- No bundled/default chat endpoint or developer API key. Configure your own Gemini key, WSS relay, or HTTPS endpoint in Android API Connection settings. Empty settings block chat and show configuration guidance.
- HTTPS URLs containing YOUR_PROMPT_HERE, {prompt}, or a prompt query parameter use GET; other HTTPS URLs use OpenAI-compatible JSON POST. An optional personal Bearer key and optional custom model are supported. This does not promise compatibility with unrelated/proprietary request formats, streaming-only endpoints, or image tools.
- Creator/owner: Ayan Ahamed, 16-year-old developer; portfolio https://arian00001.site. Existing DEV STYLE branding, Telegram, Instagram, YouTube and WhatsApp details are preserved. The app creator is not represented as having trained the foundation model.
- The recurring "DEV STYLE • Admin Contact" popup that appeared on app open is now disabled and no longer shows.
- Creator greeting: the scrollable Wake Words dialog saves a greeting phrase, creator name and optional email. An exact normalized phrase triggers a greeting, not admin authentication or additional privileges. HTTPS handles the greeting locally and does not send the phrase or saved email to the custom endpoint; Gemini Live processes microphone audio on its configured service as usual.
- Voice unlock request: saying "unlock phone" / "phone unlock koro" / "ফোন আনলক করো" creates an actionable notification from the conversation service. Tap it to open a non-exported activity that asks Android to dismiss the keyguard. Only the Android callback confirms success; secure locks still require PIN/password/biometrics. No Device Admin claim, full-screen-intent permission, accessibility PIN typing or stored phone credential. Old stored unlock PINs are deleted when these settings or an unlock request are opened.
- Android's existing Vision control now opens choices for Screen Vision, Screenshot, Screen Recording, and Stop/Save. Capturing requires Android screen-sharing consent each time and works without a chat API. Recording is silent MP4 and stops after ten minutes or when Stop is pressed. Protected/DRM screens remain protected. Android 10+ saves to Pictures/Jarvis or Movies/Jarvis; Android 8/9 saves in the app-specific external Pictures/Movies folder.
- The native foreground service handles “jarvis background e jao” and Bengali equivalents, minimizes Jarvis, stops chat, and starts its existing offline Vosk wake listener. “wake up jarvis” resumes conversation. The required microphone/notification permissions, installed Vosk model and device battery settings still apply; force-stopping the app stops wake listening. Owner voice checks are retained. HTTPS speech cannot bypass enrolled owner verification.
- Existing native stop/pause/resume controls also control HTTPS TTS. Resume precision depends on the installed speech engine's range callbacks.
- Capture permissions: RECORD_AUDIO remains for voice chat only; silent recording does not record microphone audio. POST_NOTIFICATIONS is requested on Android 13+; denial stops the capture flow with an explanation. Every capture requires fresh Android MediaProjection consent; the mediaProjection foreground service starts while the capture activity is visible, before minimizing. Existing FOREGROUND_SERVICE and FOREGROUND_SERVICE_MEDIA_PROJECTION declarations are preserved. Capture cancellation does not start recording; Stop and system revocation release capture resources.
- Actual home orb fixed: HomeFragment loads assets/orb.html, not assets/hud/index.html. Both now use the celestial glass blob. The previously missing Three.js runtime is bundled locally at assets/hud/three.min.js with a verified checksum; no runtime CDN. JarvisOrb state/theme/audio calls and native tap/hold handling are preserved. A CSS glass orb remains visible if WebGL is unavailable.
- Native liquid glass: the actual Android light theme, cards, settings surfaces, ambient glows and bottom dock now use translucent pearl-white surfaces and lime accents. A one-time upgrade selects this new light theme so existing installations see the redesign; subsequent user theme changes are preserved.
- Repair: duplicate edit-map keys previously discarded the creator settings and capture activity/service declarations. Edit groups now concatenate, and export/push validates all required source connections before committing.
- In-app updates: UpdateManager.kt checks a user-configured HTTPS URL for a newer version (JSON: versionCode, versionName, downloadUrl, changelog), downloads the APK to the app cache, and hands it to Android's package installer via FileProvider. The APK MUST be signed with the same key as the installed app; a different key forces uninstall first. REQUEST_INSTALL_PACKAGES permission added. Settings > App Identity now has a "Check for Updates" button. Version 1.9.1, build 145. Both APK workflows require the ORIGINAL permanent keystore and verify its certificate; no new debug keys are generated or substituted. See SIGNING.md. Missing signing secrets block APK packaging, but native compilation and unit tests still run first.
- Device Admin cannot bypass a secure Android lock screen; PIN/password/biometric authentication remains required.
- Human Agent Intelligence Engine (v2.0.0): modular agent system added behind feature flags, preserving all existing UI and features. Includes context-aware emotional intelligence (joy, empathy, sadness, curiosity, humor, excitement, surprise, gratitude, calmness — anger and aggression blocked), persistent user-approved memory (only facts the user explicitly asks to remember), dynamic personality that adapts over time, multi-step autonomous task planning, specialist AI personas (coder, researcher, therapist, tutor, creative), tool orchestration awareness, permission gating for consequential actions, result verification before claiming completion, and error recovery with retry. All modules backward-compatible, controlled by FeatureFlags, with unit tests. Existing HttpChatClient and all existing flows unchanged when the engine is disabled.
- New permanent signing key generated and stored as GitHub repository secrets. Future updates use this same key — no re-uninstall needed. See SIGNING.md.
- [v2.1.0] Premium upgrade: Advanced Voice Recognition (VAD, confidence thresholds, noise suppression, echo cancellation, interrupt phrases), Command Orchestrator (queue, request IDs, timing metrics, dedup, interruption), Bug Detection Service (auto-detect failures, 14-field reports, dedup), Bug Report Repository (JSON/CSV export, copy, filter, search, clear), Premium Access Service (server-side key validation via HTTPS, WhatsApp Get Key to 8801612509753), Creator Authentication Service (AHONA-I-LOVE-YOU dev key, secure session), Singing Service (original lyrics + singing audio via configurable provider), Google Search Service (failure detection + bug reports), Performance Monitoring Service (dashboard with latency, failure rates, most failing features), Command History (structured records), Settings Control Center menu (App Identity, Updates, Premium, Creator, Bugs, Performance, Singing, Search). Version 2.1.0, build 147.
- [v2.2.0] Ahona API integration: Premium validation now uses the live Ahona API (https://ahona-voice-os.base44.app). Full flow: user create (POST /api/user/create), key check (POST /api/key/check), premium activate (POST /api/user/{name}/{device}/premium/{key}), license activate (POST /api/license/activate — device token stored locally), heartbeat (POST /api/report/heartbeat with x-ahona-token), license verify (POST /api/license/verify — 403 USER_DISABLED locks features). Default base URL pre-configured; user enters name, device model and key in Settings. Admin can disable devices remotely from the Ahona panel. Version 2.2.0, build 148.
- [v2.3.0] Six new on-device features added to the Jarvis Control Center:
  1. Share-to-Jarvis: Android share sheet integration (intent-filter for ACTION_SEND text/plain). ShareReceiverActivity shows shared text/URLs with Summarize, Explain, Extract Key Points, Save, Ask Jarvis, Copy, Open URL. Local rule-based processing (LocalContentProcessor) — no upload unless user invokes configured AI. Validates input, truncates large content (100k chars), handles empty/malformed data. ShareNoteStore saves notes locally.
  2. Smart Notification Briefing: Optional NotificationListenerService (JarvisNotificationListenerService). User must grant notification access manually in Android Settings — never bypassed. Groups notifications into Messages, Reminders, Updates, Other. Deduplicates by package+id, highlights important items. App exclusion list stored locally. No persistent history by default. AI summarization only with explicit consent and configured AI. Never dismisses or acts on notifications.
  3. Smart Camera Scanner: CameraX + ML Kit barcode scanning (QR, Aztec, DataMatrix, PDF417, EAN-13/8, UPC-A/E, Code 128/39/93). ScannerActivity with live camera preview. On-device OCR via ML Kit Text Recognition (OcrProcessor). Copy, Share-to-Jarvis, Open URL (explicit tap only — never auto-executes QR content). Camera permission requested only when needed. Handles denied permission, no camera, recognition failures.
  4. Battery Intelligence: BatteryInfoProvider reads Android BatteryManager (percentage, charging state, charge type, health, technology, temperature, voltage, Battery Saver status). Estimated time-to-full when Android exposes it (clearly labeled estimate). BatteryActivity shows info + recommendations. No background polling — refreshes on screen entry. Does not fabricate per-app usage (Android restricts that data). No auto-disable of settings.
  5. Local Media Intelligence: MediaLibraryActivity uses MediaStore + Photo Picker (Android 13+) + Storage Access Framework. Search/filter by type (image, video, audio, document), name, date. Duplicate detection by name+size. Preview, copy, share, open with another app. Never deletes/moves/overwrites — confirmation required for destructive ops. Respects scoped storage. Does not assume access to every file.
  6. Screen Context Assistant: ScreenContextActivity accepts ACTION_PROCESS_TEXT (text selection) and ACTION_SEND images (screenshots). On-device OCR via ML Kit. Shows preview before processing. Local Summarize/Explain. AI upload only with explicit consent and configured AI. Integrates with Share-to-Jarvis. Never uploads automatically.
  New permissions: CAMERA, READ_MEDIA_IMAGES/VIDEO/AUDIO (Android 13+), READ_EXTERNAL_STORAGE (maxSdkVersion 32). New dependencies: CameraX 1.3.4, ML Kit barcode-scanning 17.2.0, ML Kit text-recognition 16.0.0. All features accessible from Settings > Jarvis Control Center. Version 2.3.0, build 149.

- [v2.4.0] Nine advanced developer productivity features added to the Jarvis Control Center:
  1. AI Project Planner: Create projects with goals, constraints, deadlines, success criteria. Break into milestones, tasks with dependencies and acceptance criteria. Track status (Not Started, In Progress, Blocked, Completed). Analyze for dependency conflicts and risky assumptions. Export/import via SAF. All local. Access from Settings > Control Center > Project Planner.
  2. Prompt Evolution Lab: Versioned prompt workspace with tags, ratings, notes, task type, provider. Duplicate versions without overwriting. Side-by-side text diff. Run through configured AI only. Export/import collections. Never fabricates quality scores. Access from Settings > Control Center > Prompt Lab.
  3. Personality & Memory Engine: Long-term conversation memory with timestamps, summaries, return-after-absence greetings. Simulated emotional state (mood, confidence, engagement, social tone) — bounded, transparent, resettable. Proactive help engine with suggestion scoring, quiet hours, dismissal tracking. Continuous learning from explicit instructions and corrections (distinguishes facts from inferences, asks confirmation). Memory management screen (view/edit/export/delete). Personality settings (style, humor intensity, proactive level). All local, no Firebase. Access from Settings > Control Center > Personality.
  4. Photo Memory System: Save photos with user-provided descriptions, context, dates, tags, notes. On-device OCR via ML Kit (clearly labeled as OCR-derived). Search by description/tags/date/context. Explicit Save required — never auto-saves. View, edit, delete with confirmation. Distinguishes user-provided info from OCR observations. Access from Settings > Control Center > Photo Memory.
  5. AI Browser Agent: Search and open websites in a secured WebView. Page understanding (title, text, links, login form detection). Login guidance (never fills passwords, uses Android Credential Manager). Form filling with explicit confirmation (never auto-submits). CAPTCHA/2FA detection (pauses, never bypasses). Clear browsing data. Treats web content as untrusted. Access from Settings > Control Center > Browser Agent.
  6. Decision DNA: Record decisions with context, options considered, chosen option, reasoning. Tag and search. Find similar past decisions by tag/context overlap. Distinguishes facts from inferred preferences. Never auto-creates preference rules. Export/import. Access from Settings > Control Center > Decision DNA.
  7. AI Debate Chamber: Structured multi-perspective review (Proposer, Critic, Reviewer). Generates role-based prompts. Discloses when roles are not independent models. Shows consent before sending to AI provider. Summarizes agreements, disagreements, unresolved questions. Never fabricates citations. Access from Settings > Control Center > Debate Chamber.
  8. Memory Time Machine: Versioned snapshots of Jarvis-owned data (prompts, plans, decisions, memories). Browse, preview, compare with diff, restore with confirmation. Export/import backup. Never snapshots unrelated device data. Never overwrites without confirmation. Access from Settings > Control Center > Time Machine.
  9. Failure Autopsy: Diagnostic screen for errors and failed operations. Records with sensitive content redacted (API keys, emails, phone numbers, hex tokens). Categorizes errors (Network, Permission, NullReference, IO, ParseError, Memory). Suggests likely causes and troubleshooting steps from actual evidence. Copy/export reports. AI diagnosis with explicit consent. Never fabricates root causes. Access from Settings > Control Center > Failure Autopsy.
  New dependencies: Robolectric 4.12.2, androidx.test:core 1.5.0 (for local JVM unit tests). Version 2.4.0, build 150.

## Verification
The editor serves these modified sources and includes them in this ZIP. Native APK compilation and real-device microphone, MediaProjection, TTS, wake-word, and permissions tests have not been run in the web workspace. Build with the project's normal Android toolchain and test on a device before release. No release signing or app identity checks were removed.

## Visual UI Intelligence (grounded, verified screen control)

- New package `vision/ui`: `UiCommandPlanner`, `UiTargetResolver`, `UiActionExecutor`,
  `UiVerifier`, `UiScreenOcr` and `AutomationSession`.
- Spoken UI commands now work directly ("tap the Search button", "find the Create button and open it",
  "scroll down and find the next option", "go back", "what's on the screen") in English, Banglish and Bangla.
- Targets are grounded only on real screen evidence (accessibility node text, content description,
  resource id, position, or OCR of the screen text). Nothing is tapped because a model predicted it.
- Semantic accessibility actions come first; a coordinate tap is used only when the node refuses.
- Every action is verified by re-inspecting the screen. A tap alone is never reported as success, and an
  action that was dispatched but produced no change is reported as unverified instead of retried blindly.
- Ambiguous matches ask the user to choose; blocked/secure screens and a disabled accessibility service are
  reported as exact Android limitations rather than hidden.
- Reuses the existing stack: `JarvisAccessibilityService`, `UiGroundingEngine`, `ScreenSignatureCalculator`,
  `AgentTaskEngine`, `ScreenCaptureManager`, ML Kit text recognition and the existing TTS/voice path.
- Existing device/app command routing, YouTube/WhatsApp automation, package names and signing keys untouched.

- Version 3.3.2 (154): installs over 3.3.1 (153); the release-APK publish step now recovers from transient GitHub API errors.

## 1.6.0 (165) — Email inbox + local AI models
- **Email Inbox** (`email/InboxActivity.kt`): lists the latest 20 messages over the existing IMAP client,
  opens the full body, sends replies through the existing SMTP client, and can mark a message as read.
  Uses the same saved address + Google App Password as Gmail Auto Reply; no new dependency, no new secret.
  Reachable from Jarvis Control Center → "Email Inbox (read & reply)".
- **Local AI models** (`model/` package):
  - `ModelCatalogue` — public Hugging Face GGUF search (no API key), prefers Q4_K_M files.
  - `ModelCompatibility` — RAM, free-storage and CPU-flavour verdict before a download.
  - `ModelDownloader` — resumable download into private app storage; pause keeps the partial file.
  - `ModelStore` — installed models and the active model, on-device only.
  - `LocalModelRuntime` — bridge to the optional native engine `libjarvis_llama.so`; reports honestly
    when the engine is absent instead of pretending to answer locally.
  - `LlmRouter` — API first, on-device model only when offline or the API fails and fallback is enabled.
  - `ModelCatalogueActivity` — search, compatibility, download/pause/resume, set active, delete.
- Memory level HIGH kept: 300 chat turns persisted, newest 200 in active context.
- Version 1.6.0 (165) builds through the existing `android-apk.yml` workflow on push.

## 1.6.1 (166) — Ahona API app-open gate + human-like voice styles
- **Every app open now calls the Ahona API** (`network/AhonaSync.kt`):
  - `POST /api/user/create` announces the owner name, the real phone (`Build.MANUFACTURER + Build.MODEL`)
    and the stable installation id, so each open lands as / refreshes a card in App users.
  - `GET /api/version` gates the build: when the panel has a newer `latest_version`, the dock is locked and a
    **non-dismissable** dialog forces the update; the update link opens the release page and the app closes.
  - `POST /api/report/heartbeat` runs only when the installation already holds a device token; client errors
    travel with it (max 5), as the reference specifies.
  - The 2-hour `UpdateAlarmReceiver` now repeats registration + heartbeat + version check in the background
    and posts the update notification, so an old build is chased even when the app is not opened.
  - No admin token, no secret, no key stored in the app.
- **Voice styles** (`voice/VoiceStyles.kt`): Natural, Warm, Deep, Bright, Calm, Energetic — pitch/pace
  presets layered over the real device voice. Picked in Voice Studio beside the voice list, saved with
  "Save voice", and applied on every spoken reply (`HttpSpeechOutput`) and every preview
  (`VoicePreviewPlayer`). The device voice itself is untouched and still chosen from the real engine list.
- Version 1.6.1 (166) builds through the existing `android-apk.yml` workflow on push.
## 2.0.1 (201) — Sub Agent architecture + Email HTML auto reply

### New: Sub Agent Showcase (`subagent/`)
- `SubAgentActivity` — premium glass hub (Professional / Premium pill, status badges, real switches)
  opened from **Settings → Sub Agent**. Every card either toggles a real background agent or opens the
  installed Jarvis tool that powers it. No mock cards, no dead buttons.
- `SubAgentCatalog` / `SubAgentModels` — the agent list, categories, and honest status model.
- `SubAgentStateResolver` — reads the REAL state (WhatsApp rules + notification access, Gmail connection
  and switch) and flips the real switch. It never reports an agent as active when it is not configured.
- `SubAgentRouter` — the conversation layer can answer "whatsapp agent status" or open the right
  sub agent screen; registered as tools in `ToolOrchestrator`.

### WhatsApp sub agent (`WhatsAppSubAgentActivity`)
- Master switch for the existing notification-based auto reply engine.
- Rule editor (contact / keyword / reply text) with delete, notification-access shortcut and an honest
  "what this cannot do" note. Replies still travel through WhatsApp's own remote-input reply action.

### Email sub agent (`EmailSubAgentActivity`)
- Gmail connection shortcut (address + Google's 16-character App Password), auto reply switch,
  HTML-design switch, template editor with a JavaScript-disabled live preview.
- `HtmlReplyTemplate` — placeholder engine ({name} {email} {subject} {reply}) that HTML-escapes every
  interpolated value (sender name, subject, AI draft) so page content can never inject markup.
- `GmailClient.sendHtml` sends multipart/alternative (HTML + generated plain-text fallback);
  `GmailAutoReplyScanner` uses it when the HTML design is enabled, otherwise the previous plain reply.

### Project
- Version **2.0.1 (201)**; application id unchanged, all preferences and the encrypted Gmail vault are
  preserved, so it installs over 1.6.x without touching user data.
- The release workflow now reads `versionName` from `app/build.gradle.kts`, so the GitHub tag and the APK
  file name can never drift from the real build again.
- New unit tests: `SubAgentCatalogTest`, `HtmlReplyTemplateTest`.
