# SPIDEY — Regression Checklist (J0 Baseline)

Run this checklist on-device **before merging any new feature branch**.
Check every item. Any ❌ blocks the merge.

---

## Setup

- Phone: Samsung Galaxy A14 5G (RZCW60PWC7J)
- Branch under test noted in results
- Each item: ✅ Pass · ❌ Fail · ⚠️ Partial

---

## 1. Wake Word Detection

| # | Test | Expected | Result |
|---|---|---|---|
| W1 | Phone screen on, app open. Say "Hey Spidey" at normal volume from 30 cm. | Mic icon animates; state changes to LISTENING within 1 s. | |
| W2 | Phone screen on, **another app** (WhatsApp) in foreground. Say "Hey Spidey". | SPIDEY activates; overlay or notification visible. | |
| W3 | Screen **off** (not locked). Say "Hey Spidey". | Screen wakes; SPIDEY activates. | |
| W4 | Phone **locked** (PIN/pattern). Say "Hey Spidey". | SPIDEY activates; says "Please enter your PIN or fingerprint"; resumes action after unlock. | |
| W5 | From **2 metres away**, normal voice. Say "Spidey". | Detected ≥ 4 out of 5 attempts. | |
| W6 | App killed from Recents. Wait 5 seconds. Say "Hey Spidey". | Service restarts (via AlarmManager); wake word re-armed within 3 s. | |
| W7 | Reboot device. Wait for boot to complete. Say "Hey Spidey". | Service starts automatically; wake word active without opening app. | |

## 2. Foreground Service Persistence

| # | Test | Expected | Result |
|---|---|---|---|
| S1 | Pull down notification shade. | "SPIDEY Listening…" notification visible. | |
| S2 | Leave phone idle 15 minutes. Say "Hey Spidey". | Still responds — not killed by battery optimisation. | |
| S3 | Tap Quick Settings tile. | Tile toggles service on/off; label reflects state. | |

## 3. Far-Field Hearing

| # | Test | Expected | Result |
|---|---|---|---|
| F1 | 1 metre, normal voice. Say "open WhatsApp". | Detected and executed. | |
| F2 | 3 metres, raised voice. Say "Hey Spidey". | Detected ≥ 3 out of 5 attempts. | |
| F3 | Background music playing at 50% volume. Say "Hey Spidey" at 1 m. | Detected ≥ 3 out of 5 attempts. | |

## 4. Contact Search

| # | Test | Expected | Result |
|---|---|---|---|
| C1 | "Find Vaseem" (exact name). | Returns Vaseem's number; speaks the result. | |
| C2 | "Find mum" (alias). | Resolves alias to saved contact; speaks result. | |
| C3 | "Find [name that doesn't exist]". | Speaks "I couldn't find that contact." | |

## 5. Calling & SIM

| # | Test | Expected | Result |
|---|---|---|---|
| PH1 | "Call Vaseem". (dual SIM, no default) | Asks "Which SIM — first or second?"; places call after answer. | |
| PH2 | "Call Vaseem on SIM 1". | Places call directly on SIM 1 without asking. | |
| PH3 | During call. Say "Hey Spidey, hang up". | Ends call. | |

## 6. Open Any App

| # | Test | Expected | Result |
|---|---|---|---|
| A1 | "Open WhatsApp". | WhatsApp opens; SPIDEY confirms. | |
| A2 | "Open Camera". | Camera opens. | |
| A3 | "Open [app not installed]". | SPIDEY says app not found. | |
| A4 | Screen locked. "Open WhatsApp". | Prompts unlock first; opens WhatsApp after unlock. | |

## 7. YouTube Search & Autoplay

| # | Test | Expected | Result |
|---|---|---|---|
| Y1 | "Play Blinding Lights on YouTube". | YouTube opens; song starts playing within 3 s. | |
| Y2 | "Search Calm music on YouTube" then "play the first one". | YouTube opens search results; then first video plays directly. | |
| Y3 | "Play the third video". (after a previous search) | Third search result plays. | |

## 8. Unlock Flow

| # | Test | Expected | Result |
|---|---|---|---|
| U1 | "Unlock my phone". | Screen brightens; keyguard prompt appears; SPIDEY says "Please enter your PIN or fingerprint." | |
| U2 | While locked, say "Open WhatsApp". | Prompts unlock; after successful PIN/fingerprint — opens WhatsApp. | |

## 9. Voice & Persona

| # | Test | Expected | Result |
|---|---|---|---|
| V1 | SPIDEY speaks any response. | Voice is clearly male; pitch noticeably lower than default TTS. | |
| V2 | "Hey Spidey, hi" / "Hey Spidey, hello". | Responds with a bro-style greeting (not robotic). | |
| V3 | "Stop" / "That's enough". | SPIDEY acknowledges and goes silent. | |

## 10. Groq AI Chat (J1 target — add after J1 is complete)

| # | Test | Expected | Result |
|---|---|---|---|
| G1 | "My day is ruined". | AI replies with a unique empathetic response (not the hardcoded fallback). | |
| G2 | "Tell me a joke". | AI tells a short joke. | |
| G3 | "I failed in maths". | AI response is different from G1's response. | |
| G4 | Airplane mode ON. Say anything conversational. | SPIDEY says "Can't reach the AI right now bro, I'm offline." | |
| G5 | Logcat shows `SPIDEY: Groq key present, length=56` at startup. | Key length logged (never the value). | |

---

## Results Log

| Date | Branch | Tester | W1-W7 | S1-S3 | F1-F3 | C1-C3 | PH1-PH3 | A1-A4 | Y1-Y3 | U1-U2 | V1-V3 | G1-G5 | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| (baseline) | phase-0-feasibility | | | | | | | | | | | N/A | Pre-J0 baseline |

