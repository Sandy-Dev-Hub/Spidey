# SPIDEY - App Skills Reference & Diagnostics

This document outlines the deterministic, per-app skills integrated into SPIDEY for **Android 15 (One UI 7)** on the **Samsung Galaxy A14 5G (`RZCW60PWC7J`)**.

---

## 1. Supported Skills Overview

| Skill | Platform / Target | Mechanism | Automated Action | Safety / Confirmation |
|---|---|---|---|---|
| **WhatsApp Calling** | `com.whatsapp` | `ContactsContract.RawContactsEntity` VoIP URI (`vnd.android.cursor.item/vnd.com.whatsapp.voip.call`) with fallback to `https://api.whatsapp.com/send` | Lands directly on contact's conversation screen | Prompt user to tap call button (WhatsApp blocks automated dialing to prevent spam) |
| **WhatsApp Messaging** | `com.whatsapp` | `https://api.whatsapp.com/send?phone=...&text=...` + `SpideyAccessibilityService` | Prefills message, auto-taps Send button via accessibility | Reads back message and requires explicit spoken confirmation ("Send it boss? -> Yes") |
| **Email Messaging** | `com.google.android.gm` / default mail | `Intent.ACTION_SENDTO` with `mailto:recipient?subject=...&body=...` + `SpideyAccessibilityService` | Prefills email recipient, subject, body, auto-taps Send button | Reads back recipient/body, normalizes "at/dot" dictated addresses, requires confirmation |
| **SMS Messaging** | `smsto:` / Default SMS | `Intent.ACTION_SENDTO` with `sms_body` extra | Prefills SMS to contact | Requires spoken confirmation before launching compose |
| **Spotify Playback** | `com.spotify.music` | `MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH` + Media Key Events (`KEYCODE_MEDIA_NEXT`, `KEYCODE_MEDIA_PLAY`, `KEYCODE_MEDIA_PAUSE`) | Opens requested song/playlist directly without unpausing previous tracks | Local media keys; no unprompted network media resuming |
| **YouTube Autoplay** | `com.google.android.youtube` | `vnd.youtube:<videoId>` direct watch URI with instant HTML scraper fallback | Autoplays video immediately | Direct native launch |
| **Phone Dialer** | `com.samsung.android.dialer` | `TelecomManager.placeCall` with Dual-SIM disambiguation | Dials cellular call | Spoken confirmation + SIM selection on dual SIM |

---

## 2. Messaging & Dictation Workflow (Task J6)

### A. Spoken Voice Commands
- **WhatsApp:**
  - *"Send a message to Amma on WhatsApp saying I will be home by 9 PM"*
  - *"Message Vaseem on WhatsApp: Where are you?"*
  - *"Send WhatsApp message to John see you tomorrow"*
- **Email:**
  - *"Send an email to vikki at gmail dot com saying I will be late for the meeting"*
  - *"Send email to boss at company dot com subject Project update body The build is ready"*
- **SMS:**
  - *"Send SMS to Amma saying on my way"*
  - *"Text Mom I will call you back shortly"*

### B. Dictation & Confirmation Flow
1. **Parsing & Address Normalization:**
   - Dictated email addresses like *"vikki at gmail dot com"* are normalized automatically to `vikki@gmail.com`.
2. **Safety Read-Back:**
   - Spidey announces: *"Message to Amma on WhatsApp: 'I will be home by 9 PM'. Shall I send it boss?"*
3. **User Confirmation Options:**
   - **Confirm:** *"Yes"*, *"Send"*, *"Send it"*, *"Confirm"* $\rightarrow$ Executes send.
   - **Cancel:** *"No"*, *"Cancel"*, *"Stop"*, *"Discard"* $\rightarrow$ Cancels with *"Message cancelled boss."*
   - **Quick Edit:** *"Change 9 to 10"* or *"Change message to I am stuck in traffic"* $\rightarrow$ Updates body and prompts for confirmation with updated text.

---

## 3. Hands-Free Auto-Send via `SpideyAccessibilityService`

- **Role:** Automates clicking the "Send" button in WhatsApp (`com.whatsapp:id/send`) and Gmail (`com.google.android.gm:id/send`) immediately after the user verbally confirms *"Yes"*.
- **Safety Blocks:**
  - Automatically disabled and ignores all password fields (`node.isPassword == true`).
  - Blocklisted packages (e.g. `com.phonepe.app`, `net.one97.paytm`, `com.google.android.apps.nbu.paisa.user`, banking apps, keyguard) are completely ignored.
- **Enabling on Android 15 (One UI 7):**
  1. Go to **Settings $\rightarrow$ Accessibility $\rightarrow$ Installed apps**.
  2. Select **SPIDEY Hands-Free Actions** and toggle **ON**.
  3. *Note:* If the toggle is greyed out on sideloaded builds: Go to **Settings $\rightarrow$ Apps $\rightarrow$ SPIDEY $\rightarrow$ ⋮ (top-right menu) $\rightarrow$ Allow restricted settings**, then return to Accessibility and toggle ON.
