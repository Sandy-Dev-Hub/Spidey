# SPIDEY - Progress Tracker

## Current Phase: Phase 3 - Calling and SIM Selection

| Phase | Status | Branch | Tag |
|-------|--------|--------|-----|
| Phase 0 - Feasibility | **DONE** | `main` | `phase-0-done` |
| Phase 1 - Voice Foundation | **DONE** | `main` | `phase-1-done` |
| Phase 2 - Contact Search | **DONE** | `main` | `phase-2-done` |
| Phase 3 - Calling and SIM | **VERIFIED ON DEVICE** | `phase-3-calling` | `phase-3-done` |
| Phase 4 - AI Understanding | NEXT UP | - | - |
| Phase 5 - Multi-App Actions | WAITING | - | - |
| Phase 6 - Background Reliability | WAITING | - | - |
| Phase 7 - Security and Release | WAITING | - | - |

---

## Phase 3 - Calling and SIM Selection

**Goal:** "Call Guru Anna" resolves the contact, picks a number, asks for the SIM if needed, confirms, and starts the call through a supported flow. It reports honestly what happened.

**Started:** 2026-10-30  
**Completed:** 2026-10-30  

### Implementation Deliverables
- [x] **CallingAccountRepository:** Discovers live SIM and phone accounts via Android Telecom and Subscription APIs without assuming static SIM slots.
- [x] **ConfirmationPolicy:** Evaluates spoken confirmation ("yes", "go ahead", "sure", etc.) and cancellation ("no", "cancel", "stop", etc.) and formats prompt.
- [x] **CallCoordinator:** Places direct calls with `TelecomManager.placeCall()` passing `EXTRA_PHONE_ACCOUNT_HANDLE`, with `ACTION_CALL` and `ACTION_DIAL` (dialer fallback).
- [x] **CallContactAction & CallActionHandler:** Complete multi-turn calling handler with contact resolution, multiple number disambiguation, dual SIM selection, spoken confirmation, and dialer fallback.
- [x] **Locked Device Handling & In-Memory Resume:** Re-validates keyguard lock status, requests system unlock via `UnlockActivity`, and tracks pending actions in-memory with a 30s TTL.
- [x] **IntentEngine Calling Expansion:** Rules for natural speech calling ("call [name]", "call [name] on [sim]", "dial [name]"), number choices, SIM choices, and spoken confirmations.
- [x] **Service Integration:** Registered `CallActionHandler` in `WakeWordService`.
- [x] **Unit Tests:** 40/40 tests passing across `CallActionHandlerTest`, `CallingAccountRepositoryTest`, `ConfirmationPolicyTest`, and `IntentEngineTest`.
- [x] **Phone Verification:** Deployed and running on Samsung Galaxy A14 5G.


---

## Phase 2 - Contact Search

**Goal:** "Guru Anna" resolves to a **real contact record** or to a clarification question. No calling yet.

**Started:** 2026-10-30  
**Completed:** 2026-10-30  

### Implementation Deliverables
- [x] **PermissionManager:** Runtime check and request flow for `READ_CONTACTS`.
- [x] **ContactRepository:** Queries Android Contacts Provider (`ContactsContract.CommonDataKinds.Phone`), in-memory cache, no contact logging.
- [x] **ContactMatcher Pipeline:** 6-stage pipeline (Normalization → Exact → User Alias → Token containment → Levenshtein fuzzy similarity → Ambiguity thresholding).
- [x] **Ambiguity Tie-breaking:** Detects ambiguous candidates when top scores are close (< 0.08 delta) or confidence is borderline; prompts clarification without guessing.
- [x] **Room Aliases Database:** `ContactAlias` entity, `AliasDao`, `SpideyDatabase` with KSP compiler.
- [x] **Alias Settings Screen:** Compose screen to view, add, delete, and clear contact aliases.
- [x] **FindContactAction & Handler:** Typed `FindContactAction` with `FindContactHandler` returning `Resolved`, `Ambiguous`, `NotFound`, or `PermissionDenied`.
- [x] **IntentEngine v2:** Natural speech contact search rules ("find [name]", "who is [name]", "search contact [name]", "lookup [name]") and ordinal clarification resolution ("the first one", "second", candidate substring).
- [x] **Unit Tests:** Full suite passing (including synthetic test cases: "Guru Anna LW", "Guru Anna LiveWires", "Guru Krishnan", "Anna LiveWires").
- [x] **Phone Verification:** Deployed and verified on Samsung Galaxy A14 5G.

---

## Phase 1 - Voice Foundation

**Goal:** The core loop works reliably: "Hey Spider" → prompt → you speak → SPIDEY answers by voice → back to listening. Only harmless commands.

**Started:** 2026-10-30  
**Completed:** 2026-10-30  

### Implementation Deliverables
- [x] **Frozen Contracts:** `AssistantState`, `AssistantAction`, `ActionResult`, `ActionHandler` in `com.spidey.assistant`.
- [x] **ConversationManager:** Authoritative thread-safe state machine (`StateFlow`) coordinating the voice lifecycle.
- [x] **WakeWordService:** Foreground microphone service with ongoing notification and Stop button.
- [x] **Mic Handoff:** Clean state transition: pause wake engine, release mic, beep cue, listen (`en-IN`), re-arm wake engine.
- [x] **SpeechRecognizerManager:** Bounded listening (`en-IN`) with silence timeout watchdog and fresh session lifecycle.
- [x] **SpeechOutput:** Android TTS with self-trigger suppression.
- [x] **IntentEngine v1:** Local rules for "hello" (`GreetingAction`) and "open youtube" (`OpenAppAction`).
- [x] **ActionRouter:** Type-safe handler registry rejecting unknown actions.
- [x] **Handlers:** `GreetingHandler`, `OpenAppHandler` with `AllowedApp.YOUTUBE` allowlist.
- [x] **Compose Home Screen:** Dynamic state badge, live transcript cards, Start/Stop toggle, permissions status, spikes navigation.
- [x] **Unlock Skeleton:** `UnlockCoordinator` and transparent `UnlockActivity` for keyguard dismissal on locked devices.
- [x] **Unit Tests:** 12 tests passing across `IntentEngineTest`, `ActionRouterTest`, and `ConversationManagerTest`.
- [x] **Phone Verification:** Verified live on Samsung Galaxy A14 5G. Spoken "Hey Spider" → "open YouTube" captured and launched successfully.

---

## Phase 0 - Feasibility Spikes

**Goal:** Prove the risky parts work on the real Samsung phone before building anything real.

**Started:** 2026-10-20  
**Completed:** 2026-10-25  

### Environment Status (verified 2026-10-20)

| Check | Status | Verified Detail |
|-------|--------|-----------------|
| Git repo initialized | DONE | Branch: main (origin: github.com/Sandy-Dev-Hub/Spidey) |
| Android Studio | DONE | C:\Program Files\Android\Android Studio |
| Bundled JDK | DONE | OpenJDK 25.0.3 (jbr\bin\java.exe) |
| Android SDK root | DONE | C:\Users\cvbha\AppData\Local\Android\Sdk |
| Platform API 35 (Android 15) | DONE | android.jar present at platforms\android-35 |
| Platform API 37 (future) | PRESENT | android-37.0 also installed |
| Build-Tools 36.0.0 | DONE | aapt2.exe present |
| Platform-Tools (adb) | DONE | adb.exe present at platform-tools |
| Command-line Tools | DONE | sdkmanager.bat at cmdline-tools\latest\bin |
| Phone connected via adb | DONE | RZCW60PWC7J device (Samsung SM-A146B) |
| Phone: Android version | DONE | Android 15 (SDK 35) |
| Phone: Brand / Model | DONE | Samsung SM-A146B (Galaxy A14 5G) |
| Phone: Build | DONE | AP3A.240905.015.A2.A146BXXSFDZI2 |
| Phone: ro.debuggable | DONE | 0 (production build, normal) |

---

### Spike Results

| Spike | Status | Notes |
|-------|--------|-------|
| Spike 1: Wake word | **PASS** | Vosk Indian English offline model; trigger phrase: "Hey Spider" / "Spider". |
| Spike 2: Mic handoff | **PASS** | 645 ms total time to ready; accurate `en-IN` capture on Samsung device. |
| Spike 3: Background app launch | **PASS** | `startActivity` works screen-on; locked screen requires keyguard dismissal flow. |
| Spike 4: Unlock prompt | **PASS** | WakeLock + FullScreenIntent wakes screen, presents PIN/fingerprint prompt. |
| Spike 5: Test call | **PASS** | Detected dual SIMs (`Airtel FastLane`, `Jio`); `TelecomManager` dials directly. |
| Spike 6: Screen-off stability | **PASS** | 64/64 heartbeats over 10+ min screen-off; service stays alive without wakeLock. |

### Exit Criteria

- [x] docs/PHASE0_RESULTS.md with pass/fail table for all six spikes
- [x] Written decision: plain foreground service vs. default-assistant role (Decision: Plain foreground service with Unrestricted battery)
- [x] Chosen wake phrase and engine confirmed (Phrase: "Hey Spider" / "Spider"; Engine: Vosk Indian English offline model)

---

*Updated: 2026-10-25*

