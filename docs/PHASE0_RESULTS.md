# Phase 0: Feasibility Spike Results

**Device:** Samsung Galaxy A14 5G (SM-A146B)  
**OS:** Android 15 (API 35, Build: AP3A.240905.015.A2.A146BXXSFDZI2)  
**Package:** `com.spidey.spikes.debug`  
**Test Date:** 2026-10-25  

---

## Spike Summary Table

| Spike | Feature | Status | Key Observation / Route Tested |
|-------|---------|--------|--------------------------------|
| **Spike 1** | Wake Word | **PASS** | Verified on device with offline Vosk Indian English engine. Trigger phrase: `"Hey Spider"` / `"Spider"`. 100% on-device, zero API keys. |
| **Spike 2** | Mic Handoff | **PASS** | Ready for speech in **645 ms** from wake word (beep: ~400 ms, SpeechRecognizer startup: ~166 ms). Clean `en-IN` capture. |
| **Spike 3** | Background Launch | **PASS** (Screen On) / Requires Unlock (Screen Off) | Direct `startActivity` works when screen is on. When locked, Android blocks 3rd-party apps until keyguard is dismissed. |
| **Spike 4** | Unlock Prompt | **PASS** | Screen turns on reliably via screen-bright WakeLock + Full-Screen Intent; keyguard dismiss/PIN prompt displayed. Unlocked phone opens automatically. |
| **Spike 5** | Test Call & SIM | **PASS** | `TelecomManager.placeCall()` with `EXTRA_PHONE_ACCOUNT_HANDLE` dials directly through selected SIM (Airtel/Jio) without SIM picker dialog. |
| **Spike 6** | Screen-Off Stability | **PASS** | 64 continuous heartbeats over 10+ min screen-off; zero dropped ticks. Service remained alive without being killed by Samsung One UI. |

---

## Architectural Decisions (Phase 0 Exit Criteria)

- [x] **Background Execution Model:**
  - **Decision:** **Plain Foreground Service with Notification** (set to "Unrestricted" battery profile).
  - **Validation:** Spike 6 proved 100% stability (64/64 heartbeats) over screen-off with zero throttling. No need for the complexity of the system default-assistant role for background survival.
- [x] **Background Launch Mechanism:**
  - **Screen ON:** Plain `startActivity(FLAG_ACTIVITY_NEW_TASK)` from foreground service works reliably on Android 15.
  - **Screen OFF / LOCKED:** Third-party apps cannot show over keyguard. Spidey must wake the display and trigger keyguard dismissal (Spike 4) before launching target activities.
- [x] **Keyguard Unlock & Screen Wake:**
  - Screen wake requires `PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP` combined with a high-priority `FullScreenIntent` notification.
  - Transparent activity with `setShowWhenLocked(true)`, `setTurnScreenOn(true)` and `requestDismissKeyguard` reliably prompts for biometrics/PIN when locked, and opens immediately if no secure lock is set.
- [x] **Calling & Dual SIM Selection:**
  - `TelecomManager.placeCall()` with `EXTRA_PHONE_ACCOUNT_HANDLE` successfully forces the call on the specified SIM without prompting the user.
  - Requires `android.permission.READ_PHONE_STATE`, `android.permission.READ_PHONE_NUMBERS`, and `android.permission.CALL_PHONE`.
  - Carrier names (e.g. Jio, Airtel) are resolved via `SubscriptionManager.getActiveSubscriptionInfoList()`.
- [x] **Mic Handoff Pipeline & Latency:**
  - Engine stop & mic release: ~59 ms
  - Earcon beep: ~400 ms
  - `SpeechRecognizer` startup latency (`onReadyForSpeech`): ~166 ms
  - Total latency to ready: **~645 ms**.
  - `en-IN` SpeechRecognizer handles Indian names ("Guru Anna") accurately with realtime partial results.
- [x] **Wake Engine & Keyword:**
  - **Chosen Wake Phrase:** `"Hey Spider"` (with `"Spider"` as secondary trigger).
  - **Selected Engine:** **Vosk Indian English Model** (`vosk-model-small-en-in-0.4`, Apache 2.0, completely offline, zero API keys or company email walls).
  - **Secondary / Future Candidate:** **openWakeWord** custom `.onnx` model if single-word "Spidey" ONNX classifier is trained.

---

## Detailed Spike Results

### Spike 1: Wake Word
* **Decision:** **Vosk Offline Engine** (`vosk-model-small-en-in-0.4`).
* **Trigger Phrase:** `"Hey Spider"` / `"Spider"`.
* **Licensing / Constraints:** Apache 2.0 open-source, runs 100% on-device, no internet, no authentication, no subscription or developer keys required.
* **Result:** **PASS** — Verified live on Samsung Galaxy A14 5G. Immediate detection upon saying "Hey Spider".
 

### Spike 2: Mic Handoff Chain
* **Timings Measured:**
  - T0 → T1 (Engine stop & mic release): **59 ms**
  - T1 → T3 (Earcon beep audio feedback): **407 ms**
  - T3 → T5 (`startListening` to `onReadyForSpeech`): **166 ms**
  - **Total Latency to Ready (T0 → T5):** **645 ms**
* **Recognition Accuracy:** Verified on Indian English phrases (`en-IN`), correctly captured live streaming partials and final text results.
* **Result:** **PASS**

### Spike 3: Background App Launch
* **Screen ON:** **PASS** — Foreground service successfully launched YouTube over the previous app via `startActivity` with `FLAG_ACTIVITY_NEW_TASK`.
* **Screen OFF / LOCKED:** **BLOCKED BY OS KEYGUARD** — `startActivity` was issued without exception, but Android 15 prevents non-lockscreen activities from displaying over the keyguard.
* **Conclusion:** To open apps when screen is locked, Spidey must chain Spike 4 (wake + unlock prompt) before calling `startActivity`.

### Spike 4: Unlock Prompt
* **Screen OFF (Password / Biometric set):** **PASS** — The service successfully turns on the screen via WakeLock and FullScreenIntent; `requestDismissKeyguard` prompts the user for fingerprint/PIN.
* **Screen OFF (No lock / Swipe only):** **PASS** — The screen wakes immediately and `keyguard not locked` bypasses prompt, letting the app open directly.
* **Result:** **PASS**

### Spike 5: Test Call & SIM Selection
* **Dual SIM Detection:** **PASS** — Detected dual SIMs (`Airtel FastLane` & `Jio`) via `SubscriptionManager` and `callCapablePhoneAccounts`.
* **Direct SIM Dialing:** **PASS** — `TelecomManager.placeCall` with `EXTRA_PHONE_ACCOUNT_HANDLE` routed the call directly through the selected SIM without showing the OS SIM picker dialog.
* **Fallback Dialing:** **PASS** — `ACTION_CALL` initiated the call and showed the standard system SIM choice sheet when no SIM was pre-selected.
* **Result:** **PASS**

### Spike 6: Screen-Off Stability
* **Duration:** 10 minutes 38 seconds screen-off.
* **Heartbeats:** Exactly **64 of 64** heartbeats recorded (1 every 10 seconds).
* **Finding:** 0 drops, 0 stalls. Foreground service remained active throughout screen-off with Samsung battery optimization set to Unrestricted.
* **Result:** **PASS**

