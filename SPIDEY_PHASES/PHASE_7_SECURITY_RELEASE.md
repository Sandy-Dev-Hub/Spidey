# Phase 7: Security and Release

## Goal
A tested, documented, signed build that you can install and reinstall reliably, with permissions and data handling reviewed.

## Depends on
Phase 6 passing (`phase-6-done`).

## Files you may touch
Tests (`test/`, `androidTest/`), Gradle signing/release config, manifest (permission cleanup), `docs/`, `README.md`. Fix bugs found by tests, but no new features.

## Tasks
1. Permission review: list every permission in `docs/PERMISSIONS.md` with the reason. Remove any not used (for example overlay or full-screen-intent if Phase 0 didn't need them).
2. Data review in `docs/PRIVACY.md`: what is stored (aliases, settings, optional history), what is sent externally (LLM text only), retention, and how to clear it. Confirm there are no sensitive logs: search the code for logging of contacts, numbers, and message content; release builds should strip verbose logs.
3. Secrets: confirm no API keys in git history or source. Rotate any key that was ever committed. Document the plan to use a backend proxy if the app is ever distributed.
4. Security checks: unknown action types rejected, invalid parameters rejected, allowlist enforced for apps, pending actions expire, no code or shell execution from AI output.
5. Tests:
   - Unit tests: state machine, intent rules, validation, contact matcher, call decision logic, LLM-output validation.
   - Instrumentation tests where practical (permission flows, UI states).
   - Manual test checklist in `docs/TESTING.md` covering every phase.
6. Network failure tests: airplane mode, slow network, API errors.
7. Release build: enable minify/shrink carefully (check the wake-word SDK needs keep-rules), create a keystore **kept out of git**, build a signed APK/AAB, and install it on the phone to test the release variant.
8. Write `README.md`: setup steps (Windows, Android Studio, Samsung settings), how to build, how to install, how to run tests, known limitations, and a troubleshooting section.
9. Known-limitations list: no auto-start after reboot, background behavior varies by Samsung/One UI version, integration limits, and so on.
10. Trademark note: "Spidey" is fine for personal use; rename before any public distribution.

## Test on phone
- Fresh install of the signed release build: grant permissions from scratch, run the full voice loop.
- Run the complete manual checklist across all phases.
- Revoke each permission one at a time: the app degrades gracefully with honest messages.

## Exit criteria
Tests pass. Signed release installs and works. README lets a fresh setup succeed. Permissions and privacy documents match the actual code. Known limitations are listed.

## Common problems
- Release build crashes but debug works: usually missing shrinker keep-rules for the wake-word SDK or Room.
- Lost keystore: back it up securely; without it you can't update the installed app.

## Rollback
`git checkout phase-6-done`. Tag `v0.1.0` or `v1.0.0` when done.

## Antigravity prompt
> Work only on Phase 7. Review and document permissions and privacy, remove unused permissions, ensure no sensitive logging or committed secrets, add the unit and instrumentation tests described, configure a signed release build (keystore kept out of git), and write README.md and docs/TESTING.md. Fix bugs found, but add no new features. Report test results honestly.
