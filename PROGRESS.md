# SPIDEY - Progress Tracker

## Current Phase: Phase 1 - Voice Foundation

| Phase | Status | Branch | Tag |
|-------|--------|--------|-----|
| Phase 0 - Feasibility | **DONE** | `main` | `phase-0-done` |
| Phase 1 - Voice Foundation | **VERIFIED ON DEVICE** | `phase-1-voice` | `phase-1-done` |
| Phase 2 - Contact Search | NEXT UP | `phase-2-contacts` | - |
| Phase 3 - Calling and SIM | WAITING | - | - |
| Phase 4 - AI Understanding | WAITING | - | - |
| Phase 5 - Multi-App Actions | WAITING | - | - |
| Phase 6 - Background Reliability | WAITING | - | - |
| Phase 7 - Security and Release | WAITING | - | - |

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

