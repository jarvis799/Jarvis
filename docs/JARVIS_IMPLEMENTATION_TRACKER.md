# JARVIS — historical implementation inventory

**Current tracker: [`../JARVIS_IMPLEMENTATION_TRACKER.md`](../JARVIS_IMPLEMENTATION_TRACKER.md), updated 2026-11-01.** The sections retained below are the earlier inventory, not current verification evidence. The root tracker supersedes historical version, delivery, model and test claims.

Current inspected remote: `jarvis799/Jarvis`, branch `main`, commit `85c82678e064ff3b710db56b9faa52d2362da99e`; source version 2.0.3 / 203. The existing local checkout retains earlier uncommitted work. No push, build success, install or release is claimed for this repair.

The new native 18+ Adult Education Mode is wired to a separate persistent flag and both Live and HTTPS response paths. Status: **IMPLEMENTED, NOT FULLY TESTED**. Three XML files and source/resource wiring passed static checks. Build and focused unit-test commands both exited 1 because Java was not available. Nine tests were authored, not successfully executed.

The actual UI is native XML/ViewBinding with local WebViews for visuals. The Base44 web preview does not run the Android project. The installed phone version and APK contents have not been inspected, so this document no longer asserts that a particular old APK is installed.

The earlier status symbols below describe source inventory only: `[ ]` NOT IMPLEMENTED, `[~]` PARTIALLY IMPLEMENTED, `[UI]` UI ONLY, `[CODE]` IMPLEMENTED BUT UNVERIFIED, `[BLOCKED]` BLOCKED. No feature is VERIFIED merely because its class or UI exists.

---

## 4. LOCKED DESIGN SYSTEM (direction: premium lime)

| Token | Value | Purpose |
|---|---|---|
| `lime_primary` | `#A8FF7A` | primary accents, active controls, mic glow |
| `lime_secondary` | `#E6F766` | secondary accent, highlights, progress |
| `bg_pale` | `#F8FBF7` | app background (light) |
| `surface` | `#FFFFFF` | cards, sheets |
| `surface_soft` | `#F1F5EC` | inset rows, grouped cards |
| `text_primary` | `#101410` | headings |
| `text_secondary` | `#5B6656` | body/secondary |
| `text_muted` | `#93A08C` | captions |
| `status_green` | `#22C55E` | connected |
| `status_red` | `#EF4444` | disconnected / destructive |
| `status_amber` | `#F59E0B` | pending |
| `status_gray` | `#9CA3AF` | unavailable |
| Heading 1 / 2 / Body / Caption | 24sp bold / 18sp semibold / 14sp regular / 12sp regular | typography scale |
| Radius | 20dp cards, 14dp controls, 999dp pills | corners |
| Spacing | 4 / 8 / 12 / 16 / 24 / 32 dp | rhythm |
| Touch target | ≥ 48dp | accessibility |

**Documented conflict:** `app/src/main/res/values/colors.xml` currently ships a **dark holographic cyan** palette (`primary_cyan #00F0FF`, `bg_space #020713`) with per-theme overrides in `ThemeManager`. The locked direction above is **light lime**. Resolution rule: the lime tokens are authoritative for new and reworked screens; the cyan set stays only as a selectable legacy theme until each screen is converted, so no screen silently mixes both.

**Design-system status:** `[~]` — tokens specified here. Code-level consumption (`JarvisTokens.kt` + restyled screens) is deferred until a build is available, so every change can be seen on a device rather than assumed.

---

## 5. FEATURE TRACKER

| ID | Feature | Screen | Component / class | Status | Evidence | Remaining |
|---|---|---|---|---|---|---|
| DEL-01 | APK delivery pipeline | — | `app/build.gradle.kts`, `.github/workflows/android-apk.yml` | `[BLOCKED]` | no APK, no build dir, no new commit | build or push; then install |
| DS-01 | Design tokens (lime) | all | `values/colors.xml` | `[~]` | §4 of this file; colors.xml still cyan | add tokens, convert screens |
| HOME-01 | Home shell: avatar, status, orb, dock | Home | `fragment_home.xml`, `HomeFragment.kt`, `HudHomeSurface.kt`, `MainActivity.kt` | `[CODE]` | layouts + binding present | device verify; apply lime tokens |
| HOME-02 | Conversation area + message bubbles | Home/Chat | `fragment_chat.xml`, `ChatFragment.kt`, `ChatAdapter.kt` | `[CODE]` | adapters + layouts present | device verify |
| HOME-03 | Mic / send / cancel | Home | `VoiceTurnCoordinator.kt`, `VoiceRecognitionService.kt`, `BargeInMonitor.kt` | `[CODE]` | services wired; unit tests for coordinator/chunker exist | device verify |
| HOME-04 | Model/provider indicator | Home | `HomeFragment` status views | `[CODE]` | reads `preferences.aiModel` | verify no fake online badge |
| AI-01 | Provider + API key setup | API Connection | `ApiConnectionActivity.kt`, `activity_api_connection.xml` | `[CODE]` | key stored in EncryptedSharedPreferences (`AppPreferences.kt`) | device verify; surface validation errors |
| AI-02 | Gemini model selection | API Connection | `GeminiLiveModels.kt`, `GeminiLiveWebSocket.kt`, radio group in layout | `[BLOCKED]` | existing model IDs have NOT been revalidated against current official documentation in this repair | official documentation check and real endpoint tests required; historical replacement claim is not validation |
| TTS-01 | Engine + voice discovery | Voice Studio | `TtsVoiceCatalog.kt`, `VoiceStudioActivity.kt` | `[CODE]` | voices discovered from installed `TextToSpeech` | device verify |
| TTS-02 | Pitch / rate persistence | Voice Studio | `VoiceStyles.kt`, `AppPreferences.kt` | `[CODE]` | persisted; `current(context)` returns saved style | device verify |
| TTS-03 | Preview + test speech | Voice Studio | `VoicePreviewPlayer.kt` | `[CODE]` | preview path uses selected voice | device verify |
| TTS-04 | Voice applied on service announcements | Home (service TTS) | `JarvisConversationService.kt:655` → `TtsVoiceSelector.applyTo` | `[CODE]` | call present; `applyTo` defined at `TtsVoiceCatalog.kt:102` | device verify |
| TTS-05 | Bengali/English offline availability | Voice Studio | `EndpointConfig.speechLocale` | `[CODE]` | locale mapping present | device verify; report actual engine coverage |
| ID-01 | Assistant name + user name | Settings | `SettingsFragment` edit-name dialog, `AppPreferences` | `[CODE]` | persists | device verify |
| ID-02 | Avatar photo selection | Settings / Home | `ProfileAvatarStore.kt`, `SettingsFragment.kt`, `fragment_settings.xml` (`ivProfileAvatar`) | `[CODE]` | photo-picker contract, 512px JPEG in app storage, change/remove dialog, icon fallback | device verify |
| SCAN-01 | Camera scan / QR / OCR | Scanner | `ScannerActivity.kt`, `activity_scanner.xml`, `BarcodeAnalyzer`, `OcrProcessor` | `[CODE]` | layout↔binding ids match; permission + back handling fixed earlier | device verify |
| SCAN-02 | Torch + gallery import | Scanner | `ScannerActivity`, `ic_flashlight`, `ic_gallery` | `[CODE]` | controls sized and wired | device verify torch on hardware with flash |
| SUB-01 | Sub-agent list + live status | Sub Agents | `SubAgentActivity.kt`, `SubAgentRouter.kt`, `SubAgentStateResolver.kt` | `[CODE]` | status resolved from real stores | device verify |
| SUB-02 | WhatsApp auto-reply agent | Sub Agents | `WhatsAppSubAgentActivity`, `WhatsAppAutoReplyManager` | `[CODE]` | rules + notification listener | device verify (needs notification access) |
| SUB-03 | Gmail auto-reply agent | Sub Agents | `EmailSubAgentActivity`, `GmailAutoReplyWorker`, `GmailClient` | `[CODE]` | IMAP/SMTP app-password flow; caps enforced | device verify with real mailbox |
| CONN-01 | Google Workspace OAuth | Connectors | `GoogleWorkspaceActivity`, `GoogleWorkspaceManager` | `[CODE]` | `play-services-auth` AuthorizationClient | device verify consent flow |
| CONN-02 | Connector grid screen (Google/Gmail/Calendar/WhatsApp/Facebook) | Connectors | — | `[ ]` | no such screen located | implement if required by blueprint |
| BACKUP-01 | Create/export backup | Backup | `BackupManager.kt`, `BackupActivity.kt` | `[CODE]` | SAF create-document, versioned JSON, allowlist only — no key/token/password keys | device verify |
| BACKUP-02 | Restore with validation | Backup | `BackupManager.kt`, `BackupActivity.kt` | `[CODE]` | format+app tag checked, per-key type check, confirm dialog, `commit()` failure reported | device verify |
| PERM-01 | Permission status list + system intents | Permissions | `PermissionActivity.kt`, `activity_permission.xml` | `[CODE]` | status read from real checks | device verify denied/granted states |
| SET-01 | Settings main menu | Settings | `fragment_settings.xml`, `SettingsFragment.kt` | `[CODE]` | row set stable after overlay removal | device verify |
| SET-02 | Control Center sub-menu | Settings | `SettingsFragment.showControlCenterDialog` | `[CODE]` | 27 labels ↔ 27 dispatch cases | device verify |
| ONB-01 | Onboarding with carousel | Onboarding | — | `[ ]` | no onboarding screen in source | implement |
| UI-01 | Chat border beam animation | Home/Chat | `ChatBorderBeamView.kt`, `fragment_chat.xml` (`chatBorderBeam`) | `[CODE]` | sweeping gradient border, 3400ms loop, non-clickable overlay over the list | device verify |
| TYPE-01 | Typing settings (autocorrect, suggestions, vibration) | Typing | — | `[ ]` | not located | implement |
| TOUCH-01 | Touch Guard toggle | Touch Guard | — | `[ ]` | not located | implement |
| EVT-01 | Event triggers (charger/battery announcements) | Advanced | — | `[ ]` | not located | implement |
| OPT-01 | Optional features toggles (maps, web search, image gen) | Optional | `GoogleSearchService`, `GoogleWorkspaceManager` | `[UI]` | search endpoint settings exist; image generation absent | define toggles; label unavailable |
| STATE-01 | Loading / empty / error states | global | per-screen | `[UI]` | partial (Home error card exists) | audit each screen |

**Rule:** a `[CODE]` entry means the code exists and resolves statically (resources, ids, imports, nav targets) — not that a human has seen it run.

---

## 6. BLUEPRINT PANEL COVERAGE (25 panels)

| # | Panel | Exists | Tracker IDs |
|---|---|---|---|
| 1 | Onboarding | no | ONB-01 |
| 2 | Permissions | yes | PERM-01 |
| 3 | Home | yes | HOME-01..04 |
| 4 | Scanner | yes | SCAN-01..02 |
| 5 | Personal settings | yes | SET-01, ID-01 |
| 6 | Secret settings / reset | partial | SET-01 |
| 7 | Voice settings | yes | TTS-01..05 |
| 8 | Typing settings | no | TYPE-01 |
| 9 | Touch guard | no | TOUCH-01 |
| 10 | Advanced settings | partial | SET-02 |
| 11 | Event triggers | no | EVT-01 |
| 12 | Optional features | partial | OPT-01 |
| 13 | Sub-agents | yes | SUB-01..03 |
| 14 | Connectors | partial | CONN-01, CONN-02 |
| 15 | Email | yes | SUB-03 |
| 16 | Messaging | yes | SUB-02 |
| 17 | Social media | no | — |
| 18 | Website/coding | yes (dev studio, not "coming soon") | — |
| 19 | Backup & restore | no | BACKUP-01..02 |
| 20 | Settings main menu | yes | SET-01 |
| 21 | Loading state | partial | STATE-01 |
| 22 | No-data state | partial | STATE-01 |
| 23 | Error state | partial | STATE-01 |
| 24 | Confirmation dialog | partial | SET-01 |
| 25 | Success toast | yes (`Toast`/snackbar usage) | — |

---

## 7. IMPLEMENTATION MAP (core screens)

### SCREEN: Home / Main Assistant — `HomeFragment`
- **Entry point:** `MainActivity` → nav Home tab → `fragment_home.xml`.
- **Controls:** orb WebView, mic button (`ic_mic_jarvis`), send (`ic_send_up`), chat input, quick actions, weather card, model label.
- **Actions:** mic → `VoiceRecognitionService` → `VoiceTurnCoordinator` → provider; send → `ChatRepository`; orb tap → style from `preferences.orbStyle`.
- **Data flow:** `AppPreferences` ↔ `MainViewModel` → adapters.
- **Permissions:** `RECORD_AUDIO`, `INTERNET`, optional `POST_NOTIFICATIONS`.
- **Dependencies:** Gemini Live WebSocket / HTTP chat, `LlmRouter`, TTS.
- **Success state:** reply appears in list, spoken with selected voice.
- **Failure state:** error card + retry; offline falls back to local model only when the switch is on.
- **Test method:** device — send text, speak a query, toggle airplane mode, verify fallback and voice.

### SCREEN: AI Provider & Model — `ApiConnectionActivity`
- **Controls:** API key field, model radio group (3), endpoint/model fields, test, save.
- **Actions:** test → live request; save → `AppPreferences` (encrypted) + session restart.
- **Success:** status shows connected/latency. **Failure:** inline error with reason. **Permissions:** `INTERNET`.
- **Test method:** device — invalid key, valid key, app restart persistence.

### SCREEN: Voice Studio — `VoiceStudioActivity`
- **Controls:** engine, voice list, rate, pitch, language, preview, test, offline badge.
- **Preview action:** read engine+voice → validate availability → init if needed → speak sample → report failure.
- **Success:** audible preview in the chosen voice. **Failure:** toast naming the missing engine.
- **Test method:** device — switch engines, uninstall a voice pack, confirm the UI stops offering it.

### SCREEN: Scanner — `ScannerActivity`
- **Controls:** CameraX preview, capture, torch, gallery.
- **Actions:** capture → ML Kit barcode → OCR fallback; gallery → `PickVisualMedia`.
- **Success:** decoded text in result area. **Failure:** permission denied message → system settings route.
- **Test method:** device — scan a QR, deny camera, use torch on a flash-capable phone.

### SCREEN: Permissions — `PermissionActivity`
- **Controls:** per-permission rows with real granted/denied status + settings shortcut.
- **Success:** status matches Android truth. **Failure:** denied shows actionable steps; never claims granted.
- **Test method:** device — revoke microphone in settings, reopen screen.

---

## 8. RELEASE PROCEDURE (project policy)

1. Build: `./gradlew :app:assembleRelease -PJARVIS_CREATOR_KEY=<key> -PJARVIS_RELEASE_STORE_FILE=…` (signing inputs are mandatory — `verifyReleaseSecurityInputs` fails the build without them).
2. Current inspected source is 2.0.3 / 203. This focused repair does not bump the version or claim a successful build.
3. Artifact: `app/build/outputs/apk/release/app-release.apk`.
4. Distribution: GitHub Actions `android-apk.yml` builds on push to `main` / `workflow_dispatch`; the APK must be signed with the same key already installed, or Android requires an uninstall.
5. Never publish or push without the owner explicitly authorizing it.

## 9. CHANGE LOG OF THIS AUDIT SERIES

- Overlay/ghost-avatar feature removed (layouts, manifest, settings indices renumbered).
- Scanner: binding/id mismatches fixed, permission + back handling repaired.
- Historical Gemini model replacement was not evidence of provider support. Current model verification remains pending.
- TTS: earlier source added `TtsVoiceSelector.applyTo()`; actual engine/announcement behavior remains device-unverified.
- 2026-11-01: implemented the persistent 18+ Adult Education Mode in the real persona screens and Live/HTTPS request paths; see the root tracker for exact files, commands, blocked tests and remaining work.
- 2026-11-01: added the chat border beam (`ChatBorderBeamView`), the operator profile photo picker (`ProfileAvatarStore` + profile card in Settings) and settings backup/restore (`BackupManager` + `BackupActivity`, secrets excluded). All three are source-implemented and dev-unverified.