# Phase 6: Background Reliability

## Goal
Make SPIDEY dependable in real life: screen off, locked, other apps open, battery limits, interruptions. Measure it and document the limits honestly.

## Depends on
Phase 5 passing (`phase-5-done`). Phase 0 gave the initial findings; this phase hardens them.

## Files you may touch
`voice/WakeWordService`, `core/ConversationManager` (recovery only), `util/` (diagnostics), `ui/settings` (reliability and diagnostics screen), `docs/RELIABILITY.md`. No new features. Do not change action handlers.

## Tasks
1. Service robustness: handle audio focus changes, phone calls, other apps taking the mic, Bluetooth headset connect/disconnect, and errors; re-arm the wake word after each.
2. Wake lock: use a **partial wake lock** only if tests prove it is needed, and release it correctly.
3. Recovery: detect service death (for example a heartbeat or last-alive timestamp) and show a clear in-app status. Provide a one-tap restart.
4. Boot: Android 15 blocks starting microphone foreground services from a boot receiver. **Do not promise** auto-restart after reboot. Instead, show a notification or prompt that lets the user restart listening in one tap, and document the behavior.
5. Samsung guidance screen: a short in-app checklist (Microphone permission, Unrestricted battery, "Never auto sleeping apps", keep the notification visible) with buttons opening the relevant settings.
6. Diagnostics (privacy-safe): counters for wake detections, false-wake estimates (detections with no speech), recognizer errors, service restarts, battery drain estimate. **No audio or content.**
7. Long-run tests: record results for each scenario below in `docs/RELIABILITY.md`.
8. Note the Android/One UI version tested; add a reminder to re-run these tests after system updates.

## Test matrix (record pass/fail and notes)
| Scenario | Result |
|---|---|
| App open |  |
| Another app in foreground |  |
| Screen off, 30 min |  |
| Screen off, 2+ hours |  |
| Locked phone |  |
| Battery saver on |  |
| Unrestricted vs optimized battery |  |
| During a phone call |  |
| Music/video playing |  |
| Another app using the mic |  |
| Bluetooth headset connected |  |
| Service force-stopped |  |
| After reboot (document expected behavior) |  |

## Exit criteria
Every scenario is tested and documented. Known limits are written down. Recovery paths work. False wake-ups and battery cost are measured.

## Common problems
- Silent failure after hours: Samsung put the app to sleep; check the battery lists and the notification's presence.
- Wake word dies after an audio interruption: not re-armed in the focus-loss handler.

## Rollback
`git checkout phase-5-done`. Tag `phase-6-done` when passing.

## Antigravity prompt
> Work only on Phase 6. Harden the wake-word service for audio focus, interruptions, mic contention, Bluetooth, and service recovery. Add a privacy-safe diagnostics counter set and a Samsung settings checklist screen. Do not promise auto-start after reboot; provide a one-tap restart instead. Do not add new features or change action handlers. Help me record results in docs/RELIABILITY.md.
