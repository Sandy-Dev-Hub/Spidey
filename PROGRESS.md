# SPIDEY - Progress Tracker

## Current Phase: Fix Round - Task 4 (Fix Command Routing & Follow-Up Window)

| Phase | Status | Branch | Tag |
|---|---|---|---|
| Phase 0 - Feasibility | **DONE** | `main` | `phase-0-done` |
| Phase 1 - Voice Foundation | **DONE** | `main` | `phase-1-done` |
| Phase 2 - Contact Search | **DONE** | `main` | `phase-2-done` |
| Phase 3 - Calling and SIM | **VERIFIED ON DEVICE** | `phase-3-calling` | `phase-3-done` |
| Multi-App & Auto-Wake Hotfix | **VERIFIED ON DEVICE** | `main` | `assistant-hotfix-done` |
| Fix Round: Task 1 (Wake Word Outside App) | **VERIFIED ON DEVICE** | `phase-0-feasibility` | - |
| Fix Round: Task 2 (Background Launch) | **VERIFIED ON DEVICE** | `phase-0-feasibility` | - |
| Fix Round: Task 3 (Any App By Name) | **VERIFIED ON DEVICE** | `phase-0-feasibility` | - |
| **Fix Round: Task 4 (Command Routing Fix)** | **BUILT & TESTED** | `phase-0-feasibility` | - |
| Fix Round: Task 5 (YouTube Search & Play) | WAITING | - | - |
| Fix Round: Task 6 (Supported Unlock Flow) | WAITING | - | - |

---

## Fix Round - Task 4: Fix Command Routing (Bugs 3 & 4) & Follow-Up Window

**Goal:** Each utterance goes to the right action; commands are never typed into another app's search bar by mistake; follow-up window (8s) allows consecutive in-app commands like "play the first one" without wake-word repetition.

**Completed:** 2026-11-01

### Diagnosis & Root Cause
1. **Why "open WhatsApp" was searched in YouTube:**
   - Previous versions evaluated `matchYouTubeSearch` or allowed generic search capture when `isYouTubeContext` was true, before resolving general app launches.
   - When YouTube was in front, `ConversationManager` passed `context:youtube`. A phrase like "open whatsapp" or "go to whatsapp" fell through to `SearchYouTubeAction(query = "open whatsapp")` instead of `OpenAppAction`.
   - `SearchYouTubeHandler` launched `Intent(Intent.ACTION_SEARCH)` with `SearchManager.QUERY = "open whatsapp"` targeted at `com.google.android.youtube`, causing YouTube to open its search bar and search for the phrase.
2. **Why "Play [Song]" was spoken but failed to play:**
   - YouTube `ACTION_SEARCH` intent only displays search results and does not autoplay.
   - Background activity starts from the background service were not consistently routed through Android 15 BAL exemptions in `SearchYouTubeHandler`.
   - "play the first one" had no dedicated handler and was being matched as a search for a video titled `"the first one"`.

### Implementation Deliverables
- [x] **Deterministic Priority Order (`IntentEngine.kt`):**
  1. Active Clarification / Confirmations (SIM choices, ambiguous app choices, call confirmations).
  2. System Commands (`stop`, `cancel`, `unlock my phone`, `TimeAction`, `GreetingAction`).
  3. Open App (`OpenAppAction`) - **ALWAYS wins over in-app search**, even when YouTube is active. "Open WhatsApp" and "go to WhatsApp" strictly switch apps and can never reach YouTube search.
  4. In-App Commands (e.g. YouTube: "play the first one", "search <query>", "play <query>").
  5. Calling & Contacts (`CallContactAction`, `FindContactAction`).
  6. Clarify (`null` -> "I didn't understand that"). Never types raw transcripts into active fields.
- [x] **Strict Open-Verb Search Guard (`sanitizeSearchQuery`):**
  - Search queries strictly strip command words: `"play Jailer 2 song on YouTube"` -> `"Jailer 2 song"`.
  - A search query is asserted to **NEVER** begin with open/launch verbs (`open`, `go to`, `launch`, `switch to`, `start`).
- [x] **In-App "Play the First One" Command (`PlayFirstResultAction.kt`):**
  - Created `PlayFirstResultAction` and `PlayFirstResultHandler`.
  - Parses phrases: "play the first one", "play first one", "play the first video", "play first video", "play the first result", etc.
  - Carries short-term query context (`query:Jailer 2 song`) to confirm `"Playing the first result for Jailer 2 song."`
- [x] **Supported Unlock Command (`UnlockAction.kt`):**
  - Created `UnlockAction` and `UnlockHandler` for system unlock commands ("unlock my phone", "unlock the phone").
- [x] **8-Second Follow-Up Window (`ConversationManager.kt` & `WakeWordService.kt`):**
  - Set `followUpTimeoutMs = 8_000L`.
  - Keeps short-term context: `lastOpenedApp` and `lastSearchQuery`.
  - Resets the 8-second timer when Spidey finishes speaking (`enterFollowUpListening()`), giving the user a full 8 seconds to say "play the first one" or "open WhatsApp" without repeating the wake word.
- [x] **Android 15 BAL Compliance (`SearchYouTubeAction.kt`):**
  - Updated `SearchYouTubeHandler` to use `BackgroundAppLauncher.launch(context, intent)` for background launch reliability.
- [x] **Comprehensive Unit Test Suite (`IntentEngineTest.kt`):**
  - 58/58 unit tests passing.
  - Includes explicit tests for failing examples: "open WhatsApp", "go to WhatsApp", "open contacts", "go to YouTube", "play Jailer 2 song on YouTube", "search Jailer 2 song", "play the first one", "unlock my phone", plus gibberish.
  - Dedicated test asserting 35 open/launch verb phrases across contexts never produce `SearchYouTubeAction`.
- [x] **Device Deployed:** Built and installed on Samsung Galaxy A14 5G (`RZCW60PWC7J`).

---

## Fix Round - Task 3: Open Any Installed App By Name

**Goal:** No hard-coded allowlist. Open any installed app by spoken name using PackageManager, alias table, and Levenshtein fuzzy matching, with exclusion settings.

**Completed:** 2026-11-01

### Implementation Deliverables
- [x] **Dynamic App Discovery (`AppLauncherRepository.kt`):** Discovers all launchable applications via `PackageManager.queryIntentActivities` with `ACTION_MAIN` + `CATEGORY_LAUNCHER`. Note: Package visibility declared via `QUERY_ALL_PACKAGES` (acceptable for personal sideloaded build).
- [x] **Fuzzy Matching & Alias Resolution (`AppMatcher.kt`):** Normalizes app labels, matches through an alias dictionary ("insta", "wa", "yt", "calc", "cam", "browser", etc.), and uses Levenshtein distance for misheard/spoken variations. Returns `Ambiguous` if multiple apps match closely.
- [x] **Local Label Resolution Security Rule:** Package names are strictly resolved on-device from the matched app label; speech transcripts / LLMs never supply raw package names directly.
- [x] **App Exclusion Settings (`AppExclusionRepository.kt` & `AppExclusionScreen.kt`):** User settings screen with real-time search and toggle switches to exclude sensitive apps (e.g. banking, payment, private messengers) from voice launch.
- [x] **Clarification & Disambiguation (`IntentEngine.kt`):** Disambiguates close app matches ("Did you mean WhatsApp or WhatsApp Business?") via `open_app_choice`.
- [x] **Unit Tests (`AppMatcherTest.kt`):** 55/55 unit tests passing covering exact, alias, fuzzy, ambiguous ties, and non-existent apps.
- [x] **Device Deployed:** Built and installed on Samsung Galaxy A14 5G (`RZCW60PWC7J`).

---

## Fix Round - Task 2: Background App Launch & Foreground Verification

**Goal:** While YouTube (or any app) is in front, "open WhatsApp" reliably switches to WhatsApp, and never announces success unless verified in foreground.

**Completed:** 2026-10-31

### Implementation Deliverables
- [x] **Route Analysis & Exemption (`BackgroundAppLauncher.kt`):** Implemented Android 15 BAL-exempt launching using `PendingIntent` with `MODE_BACKGROUND_ACTIVITY_START_ALLOWED` backed by `SYSTEM_ALERT_WINDOW` permission.
- [x] **Foreground Verification (`ForegroundAppVerifier` via `UsageStatsManager`):** Queries recent `ACTIVITY_RESUMED` events to verify target package reached foreground before reporting success.
- [x] **Eliminated False-Success (`OpenAppAction.kt`):** If target app is verified within 1.5s, announces `"Opening [App]."`; if unverified, honestly announces `"I sent the request to open [App], but can't confirm it worked."`
- [x] **Routing Priority Fix (`IntentEngine.kt`):** In-app search (YouTube) no longer captures app open commands. `matchOpenApp` prioritised before `matchYouTubeSearch`, explicit open verbs guarded from search fallback.
- [x] **Permissions Card Expansion (`HomeScreen.kt`):** Added quick status toggles for `Appear on top (SYSTEM_ALERT_WINDOW)` and `Usage Access (GET_USAGE_STATS)`.
- [x] **Device Verified:** Permissions granted via adb, built, installed on Samsung Galaxy A14 5G.

---

## Fix Round - Task 1: Wake Word Outside App

**Goal:** "Hey Spidey" / "Spider" is detected on the home screen, inside other apps, with the screen off or locked, without needing the spike app on screen.

**Completed:** 2026-10-31

### Implementation Deliverables
- [x] **Decoupled from Composable:** Replaced in-screen spike Vosk listener with a persistent `WakeWordService` foreground service of type `microphone`.
- [x] **Real-Time Audio Loop & FPS Metrics (`WakeWordEngine.kt`):** Explicit `AudioRecord` thread loop (16 kHz, 16-bit mono, 200 ms / 3200-short buffers) directly feeding Vosk Kaldi model. Real-time audio FPS calculation (~5.0 FPS nominal) with OS silence detection (`isSilenced`).
- [x] **Partial WakeLock (`WakeWordService.kt`):** Acquires `PARTIAL_WAKE_LOCK` (`Spidey::WakeWordService`) to prevent CPU suspension when the screen turns off or device is locked.
- [x] **Persistent Notification with Stop Button:** `FOREGROUND_SERVICE_TYPE_MICROPHONE` notification with dynamic state text and explicit `Stop` intent.
- [x] **Diagnostics Panel (`HomeScreen.kt`):** Real-time display showing Foreground Service alive, Audio FPS, total detection count, last wake timestamp & keyword, and CPU WakeLock status.
- [x] **Unified Logging (`SpikeLogger.kt`):** Logcat tag updated to `SPIDEY` and persistent log file set to `spidey_log.txt`.
- [x] **Keyword Recognition Support:** Matches "Hey Spidey", "Spidey", "Hey Spider", "Spider" (plus phonetic variants).
- [x] **Unit Tests & Build:** Clean build and 42/42 unit tests passing.
