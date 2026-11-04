# SPIDEY - Progress Tracker

## Current Phase: Fix Round - Task 6 (Supported Unlock Flow)

| Phase | Status | Branch | Tag |
|---|---|---|---|
| Phase 0 - Feasibility | **DONE** | `main` | `phase-0-done` |
| Phase 1 - Voice Foundation | **DONE** | `main` | `phase-1-done` |
| Phase 2 - Contact Search | **DONE** | `main` | `phase-2-done` |
| Phase 3 - Calling and SIM | **VERIFIED ON DEVICE** | `phase-3-calling` | `phase-3-done` |
| Multi-App & Auto-Wake Hotfix | **VERIFIED ON DEVICE** | `main` | `assistant-hotfix-done` |
| Fix Round: Task 1 (Wake Word Outside App) | **VERIFIED ON DEVICE** | `phase-0-feasibility` | - |
| Fix Round 2: Task 1 (Wake Word Robust Startup) | **VERIFIED ON DEVICE** | `phase-0-feasibility` | - |
| Fix Round 2: Task 2 (Far-Field Hearing) | **IN PROGRESS** | `phase-0-feasibility` | - |
| Fix Round: Task 2 (Background Launch) | **VERIFIED ON DEVICE** | `phase-0-feasibility` | - |
| Fix Round: Task 3 (Any App By Name) | **VERIFIED ON DEVICE** | `phase-0-feasibility` | - |
| Fix Round: Task 4 (Command Routing Fix) | **VERIFIED ON DEVICE** | `phase-0-feasibility` | - |
| Fix Round: Task 5 (YouTube Search & Play) | **VERIFIED ON DEVICE** | `phase-0-feasibility` | - |
| Fix Round: Task 6 (Supported Unlock Flow) | **VERIFIED ON DEVICE** | `phase-0-feasibility` | - |

---

## Fix Round - Task 6: Supported Biometric/PIN Unlock Flow

**Goal:** "unlock my phone" wakes the screen, prompts the user to enter their PIN or use their fingerprint via Android's `KeyguardManager.requestDismissKeyguard`, confirms "Unlocked.", and resumes any pending action once authenticated.

**Completed:** 2026-11-01

### Security Rationale (Bug 7 Prevention)
- **STRICT RULE:** NEVER implement voice password/PIN entry. Speaking a PIN in public is a critical security vulnerability and violates platform security principles. Spidey NEVER listens for, stores, logs, or types PINs/passwords via AccessibilityService.
- Supported unlock delegates biometric/PIN authentication strictly to the secure Android system keyguard.

### Implementation Deliverables
- [x] **Screen Wake & Keyguard Dismissal (`UnlockCoordinator.kt`):**
  - Acquires `SCREEN_BRIGHT_WAKE_LOCK` to turn on the screen.
  - Launches `UnlockHelperActivity` configured with `setShowWhenLocked(true)` and `setTurnScreenOn(true)`.
  - Calls `KeyguardManager.requestDismissKeyguard` with `KeyguardDismissCallback`.
- [x] **Dual Unlock Detection (`ACTION_USER_PRESENT` + Callback):**
  - Handled both `KeyguardDismissCallback.onDismissSucceeded` and system broadcast `Intent.ACTION_USER_PRESENT` to guarantee detection on Samsung One UI 7.
  - Idempotency guard prevents double execution of callbacks.
- [x] **Voice Prompts & Seamless Resumption (`ConversationManager.kt`):**
  - Speaks: *"Please enter your PIN or use your fingerprint."*
  - Automatically transitions state to `AWAITING_UNLOCK`.
  - Holds `pendingActionAfterUnlock`.
  - When unlocked via fingerprint/PIN:
    - If explicit unlock: Speaks *"Unlocked."*
    - If pending action (e.g. "open WhatsApp" spoken while locked): immediately executes the action without asking again.
- [x] **Unit Tests (`UnlockHandlerTest.kt`):**
  - Unit tests verifying locked state, already unlocked state, and prompted unlock state pass cleanly.


---

## Fix Round - Task 5: YouTube Search & Play First Result (Autoplay)

**Goal:** "search `<song>`" shows YouTube results, then "play the first one" / "play the first song" directly plays the first result without requiring manual screen taps.

**Completed:** 2026-11-01

### Spike Comparison & Route Results
- **Route A: MediaStore Intent (`MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH`):**
  - Tested on live Samsung Galaxy A14 5G.
  - Result: **FAIL**. The intent opens YouTube's search results page, but does not autoplay (`dumpsys media_session` confirmed no playback state was initiated).
- **Route B: Direct Video Watch Intent (`vnd.youtube:<videoId>` / `https://www.youtube.com/watch?v=<videoId>`):**
  - Tested on live Samsung Galaxy A14 5G.
  - Result: **PASS**. When a direct video URI is launched, YouTube immediately starts playing (`dumpsys media_session` verified `PlaybackState {state=PLAYING(3), speed=1.0}`).
- **Route C: AccessibilityService Click:**
  - High risk of UI breakage on YouTube app updates; unnecessary since Route B autoplays natively.

### Implementation Deliverables
- [x] **Lightweight Video Resolver (`YouTubePlayerHelper.kt`):**
  - Resolves the first video ID for any query by streaming search results line-by-line (`Dispatchers.IO`) in ~0.5s without requiring an API key.
  - Matches `"videoId":"([a-zA-Z0-9_-]{11})"`.
- [x] **Direct Autoplay Launch (`YouTubePlayerHelper.playVideo`):**
  - Launches `vnd.youtube:$videoId` (with fallback to watch URL) via `BackgroundAppLauncher`.
- [x] **In-App "Play the First Song" (`PlayFirstResultAction.kt`):**
  - `PlayFirstResultHandler` receives the previous query (`query:Jailer 2 song`) from the 8-second conversation window.
  - Resolves the video ID and launches direct playback.
- [x] **Direct "Play [Song]" Execution (`SearchYouTubeAction.kt`):**
  - When the user says `"play Jailer 2 song on YouTube"` or `"play [song]"`, Spidey directly resolves and autoplays the song rather than merely leaving the user on a search results page.
- [x] **Manifest Permissions:**
  - Declared `android.permission.INTERNET` in `AndroidManifest.xml` for network resolution.
- [x] **Unit Tests & Build:**
  - 58/58 unit tests passing.
- [x] **Device Deployed:**
  - Built and installed on Samsung Galaxy A14 5G (`RZCW60PWC7J`).

---

## Fix Round - Task 4: Fix Command Routing (Bugs 3 & 4) & Follow-Up Window

**Goal:** Each utterance goes to the right action; commands are never typed into another app's search bar by mistake; follow-up window (8s) allows consecutive in-app commands like "play the first one" without wake-word repetition.

**Completed:** 2026-11-01

### Implementation Deliverables
- [x] **Deterministic Priority Order (`IntentEngine.kt`):** Clarifications -> System commands -> Open App (always wins) -> In-app commands -> Contacts -> Clarify.
- [x] **Strict Open-Verb Search Guard (`sanitizeSearchQuery`):** Search queries never start with open verbs.
- [x] **8-Second Follow-Up Window (`ConversationManager.kt`):** `followUpTimeoutMs = 8_000L` with query context persistence.
- [x] **Device Verified:** Installed and tested on Samsung Galaxy A14 5G (`RZCW60PWC7J`).

---

## Fix Round - Task 3: Open Any Installed App By Name

**Goal:** No hard-coded allowlist. Open any installed app by spoken name using PackageManager, alias table, and Levenshtein fuzzy matching, with exclusion settings.

**Completed:** 2026-11-01

---

## Fix Round - Task 2: Background App Launch & Foreground Verification

**Goal:** While YouTube (or any app) is in front, "open WhatsApp" reliably switches to WhatsApp, and never announces success unless verified in foreground.

**Completed:** 2026-10-31

---

## Fix Round - Task 1: Wake Word Outside App

**Goal:** "Hey Spidey" / "Spider" is detected on the home screen, inside other apps, with the screen off or locked, without needing the spike app on screen.

**Completed:** 2026-10-31
