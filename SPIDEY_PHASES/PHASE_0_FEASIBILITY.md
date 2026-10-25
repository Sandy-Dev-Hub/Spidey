# Phase 0: Feasibility Spikes

## Goal
Prove on the real Samsung phone that the risky parts work **before** building anything big. These are throwaway test apps/screens. The results decide the architecture.

## Depends on
Nothing. This is the first phase.

## Files you may touch
A fresh project plus a `docs/PHASE0_RESULTS.md` report. Test code can live in a `spikes/` package that is deleted or isolated after.

## Setup (do first)
1. Install Android Studio and the Android SDK; install Samsung's USB driver on Windows.
2. Phone: enable Developer options and USB debugging. Turn **off Auto Blocker** (Settings, Security and privacy) if installs fail.
3. Create a minimal Kotlin + Compose project with the Gradle wrapper. `git init`.
4. Confirm `adb devices` lists the phone, then install a hello-world debug build.

## Spikes (one at a time; record pass/fail/notes for each)

### Spike 1: Wake word
- Check the chosen SDK supports a custom keyword; train "Hey Spidey" and "Spidey". Note licensing and AccessKey rules (including whether it needs internet validation).
- Test in: quiet room, noisy room, screen on, **screen off**, **locked**, another app open.
- Count false triggers over about 1 hour of normal use. Record misses.
- Name a fallback engine if accuracy is poor.

### Spike 2: Mic handoff
- Wake word detected, then stop the engine and release the mic, then play a beep, then `SpeechRecognizer` (`en-IN`), then restart the wake word.
- Measure how much of the first words is lost with and without a beep. Try on-device recognition if available.

### Spike 3: Background app launch
- From a foreground service, try to open YouTube with the app in the background and the screen on, off, and locked.
- Try each route and record which works: plain service, full-screen-intent notification, overlay (`SYSTEM_ALERT_WINDOW`), and registering as default assistant via `VoiceInteractionService`.

### Spike 4: Unlock prompt
- With the screen off or locked: turn the screen on over the keyguard (`setShowWhenLocked(true)`, `setTurnScreenOn(true)`), call `KeyguardManager.requestDismissKeyguard`, authenticate by fingerprint, and confirm the callback fires and a pending action resumes.
- Test cancel and error callbacks.

### Spike 5: Test call
- Place a call to a test number with `ACTION_CALL` / `TelecomManager.placeCall()`.
- List phone accounts (needs `READ_PHONE_STATE`) and try passing `EXTRA_PHONE_ACCOUNT_HANDLE` to pick a SIM.

### Spike 6: Screen-off stability
- Run the listening service for 30+ minutes screen-off. Check if audio capture stalls; test with a partial wake lock. Note battery use.

## Exit criteria
- `docs/PHASE0_RESULTS.md` has a pass/fail table for all six spikes with notes.
- A written decision: **plain foreground service** or **default-assistant role**, and which background-launch route works.
- Chosen wake phrase and wake-word engine confirmed (or a fallback chosen).

## Common problems
- Install fails: Auto Blocker on, USB driver missing, or "Install via USB" disabled.
- Wake word never fires: wrong keyword model, expired AccessKey, or mic permission not granted.
- Screen-off silence: Samsung battery optimization; set the app to Unrestricted and add to "Never auto sleeping apps".

## Rollback
Everything here is disposable. Tag `phase-0-done` once the report is written.

## Antigravity prompt
> Work only on Phase 0. Create a minimal Kotlin/Compose project and build small, separate spike screens for: wake-word test, mic handoff, background launch, unlock prompt, test call, and a screen-off service test. Add logging that shows results on screen. Do not build the real assistant yet. Build, install on the phone, and help me fill in `docs/PHASE0_RESULTS.md` honestly.
