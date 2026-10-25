# Phase 3: Calling and SIM Selection

## Goal
"Call Guru Anna" resolves the contact, picks a number, asks for the SIM if needed, confirms, and starts the call through a supported flow. It reports honestly what happened.

## Depends on
Phase 2 passing (`phase-2-done`). Phase 0's findings on calling and the unlock prompt.

## Files you may touch
`calling/` (new), `actions/CallActionHandler`, `safety/ConfirmationPolicy`, `unlock/` (hook up the real pending-action resume), `IntentEngine` (call rules). Do not change the contact matcher except by calling it.

## Tasks
1. Add the `CallContact` action and `CallActionHandler`.
2. `CallingAccountRepository`: list phone accounts via Telecom (needs `READ_PHONE_STATE`). **Never assume SIM 1/SIM 2 map to fixed IDs**; show labels from the live accounts.
3. Multiple numbers: if a contact has several, ask which (mobile, work, and so on).
4. SIM choice: if more than one account is available and no valid preference exists, ask. Validate the user's answer against the currently available accounts.
5. `ConfirmationPolicy`: calls need a spoken confirmation ("Calling Guru Anna on SIM 2. Shall I go ahead?"). User can say yes, no, or cancel.
6. `CallCoordinator`: place the call with `TelecomManager.placeCall()` / `ACTION_CALL` and `EXTRA_PHONE_ACCOUNT_HANDLE`. Needs `CALL_PHONE`.
7. Fallback: if direct initiation is unsupported or fails, open the system dialer with the number filled in and tell the user.
8. Locked phone: set `NeedsUnlock`, run the unlock prompt, then **re-validate** and resume the pending call. Pending action lives in memory only, with a timeout of about 30 s.
9. Saved SIM preference per contact (optional, DataStore), clearable.
10. Unit tests for the decision logic (one account, two accounts, missing number, cancel).

## Test on phone
- Single-match contact, one number, direct call.
- Two numbers: asked which one.
- Dual SIM: asked which SIM; the correct SIM is used (check the call screen).
- Ambiguous contact: Phase 2 clarification, then the call.
- "No" or "cancel" at confirmation: nothing is dialed.
- Permission denied (`CALL_PHONE`, `READ_PHONE_STATE`): honest spoken message.
- Locked phone: unlock prompt, authenticate, call proceeds; cancel unlock means no call.
- Use test numbers or your own second number while testing.

## Exit criteria
The user can complete the calling flow on the real phone, and every failure mode produces an accurate spoken message. SPIDEY never says "calling" unless the call was actually started.

## Common problems
- `placeCall` succeeds but on the wrong SIM: handle not passed or wrong account object.
- No accounts listed: `READ_PHONE_STATE` not granted or SIM not active.
- Call blocked from background: revisit Phase 0's chosen background-launch route.

## Rollback
`git checkout phase-2-done`. Tag `phase-3-done` when passing.

## Antigravity prompt
> Work only on Phase 3. Implement the CallContact action, phone-account listing, SIM selection, number selection, confirmation policy, CallCoordinator with system-dialer fallback, and the locked-phone unlock-and-resume flow. Use the existing contact resolver; do not rewrite it. Add unit tests, build, install, and report honestly.
