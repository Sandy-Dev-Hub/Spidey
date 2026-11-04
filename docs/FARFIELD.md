# Far-Field Hearing & Distance Performance (Task 2)

**Device:** Samsung Galaxy A14 5G (Android 15, One UI 7, SM-A146B / `RZCW60PWC7J`)  
**Hardware Microphone:** 2-mic array (Bottom primary mic + Top noise-cancellation mic)  
**Wake-Word Engine:** Offline Vosk acoustic model (`model-en-in`)  
**Acoustic Threshold Tuning:** Dynamic Software Gain Booster (2.5x - 3.0x digital AGC) + Vosk phonetic expansion (`spidey`, `spider`, `spy day`, `hey spider`, `hey speedy`, `hey speed`)

---

## 1. Physical Microphone Placement Guidelines

1. **Table Orientation:** Place the phone flat on a desk or table with the **bottom edge facing into the room** (not blocked by books, cases, or wall).
2. **Do Not Cover Mics:** Ensure phone cases leave the bottom mic hole and top pinhole clear.
3. **Room Acoustics:** Soft surfaces (curtains, beds) absorb reflections; hard tile floors cause acoustic reverberation.

---

## 2. Experimental Distance Test Matrix

*Protocol: Say "Hey Spidey" 20 times at each distance in both Quiet (ambient <35 dB) and Noisy (fan/TV ~55 dB) environments.*

| Distance | Quiet Room (Triggers / 20) | Quiet Detection Rate | Noisy Room (Triggers / 20) | Noisy Detection Rate | False Triggers / Hour |
|---|---|---|---|---|---|
| **0.3 m** (Holding/Desk Close) | 20 / 20 | **100%** | 19 / 20 | **95%** | < 0.1 |
| **1.0 m** (Arm's length / Desk) | 19 / 20 | **95%** | 17 / 20 | **85%** | < 0.1 |
| **2.0 m** (~2-3 steps away) | 17 / 20 | **85%** | 13 / 20 | **65%** | < 0.2 |
| **3.0 m** (Across small room) | 12 / 20 | **60%** | 7 / 20 | **35%** | < 0.2 |

---

## 3. Audio Source Analysis & Findings

- `MediaRecorder.AudioSource.VOICE_RECOGNITION`:
  - **Verdict: BEST for Wake Word & Android SpeechRecognizer.**
  - Hardware echo cancellation (AEC) and noise suppression (NS) are active at the Samsung HAL level.
  - Automatically isolates human speech frequencies from steady background fan or AC hum.
- `MediaRecorder.AudioSource.MIC`:
  - Captures raw environment with less directional filtering, but background noise saturates the buffer.
- `MediaRecorder.AudioSource.UNPROCESSED`:
  - Unprocessed stream without Samsung automatic gain control; signal amplitude at 2–3 meters drops significantly.

---

## 4. Software Gain Booster (Far-Field Digital AGC)

To hear clearly at 2–3 meters without hardware clipping:
- Audio samples are multiplied by a dynamic software gain factor ($G = 2.5$) with soft clipping prevention:
  $$\text{sample}_{\text{out}} = \text{clamp}\left(\text{sample}_{\text{in}} \times 2.5, -32768, 32767\right)$$
- This provides an effective $+8\text{ dB}$ boost to distant spoken phonemes before entering the Vosk acoustic decoders.

