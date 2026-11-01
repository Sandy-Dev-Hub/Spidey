# Phase 5: Multi-App Actions

## Goal
Add useful actions beyond calling, each as an independent handler: YouTube search, browser search, more apps, and message preparation. Unsupported things fail safely and honestly.

## Depends on
Phase 4 passing (`phase-4-done`), though only the `ActionHandler` contract is really needed.

## Files you may touch
`actions/` (new handlers), `AssistantAction` (add subtypes), `AllowedApp` enum, manifest `<queries>`, `IntentEngine`/LLM schema (add the new action types), `docs/INTEGRATIONS.md`. Do not touch voice, contacts matcher, or calling code.

## Integration order of preference
1. Android intents.
2. Deep links.
3. Official APIs.
4. Accessibility service **only** if truly necessary, with explicit user enablement and policy-compliant use. Avoid in this phase.

## Tasks (one handler at a time; test each before the next)
1. `SearchYouTube(query)`: open YouTube search through the app's supported URL/intent; fall back to the browser if the app is missing.
2. `SearchWeb(query)`: open the browser with a search URL.
3. `OpenApp` expansion: grow the `AllowedApp` allowlist (Chrome, Maps, WhatsApp, and so on) with matching `<queries>` entries. Handle "app not installed" with a spoken message.
4. `PrepareMessage(contactQuery, message)`: resolve the contact locally, then open the messaging app's compose screen **with the text prefilled, never sent**. Say "I've prepared the message; please press send."
5. Media (optional): only what a supported intent allows (for example opening a music app's search). No silent playback assumptions.
6. Register each handler in the router; add each new action to the LLM schema and rule engine.
7. Write `docs/INTEGRATIONS.md`: for each integration, what works, what doesn't, and which Android/app versions were tested.
8. Unit tests for validation; manual tests for every integration.

## Test on phone
- "Search YouTube for Python tutorials": the correct search opens.
- "Search for Kotlin coroutines": the browser opens.
- App not installed: honest message, no crash.
- Unsupported deep link: fallback or honest failure.
- Message prepared for a contact: compose screen shows the text; **nothing is sent automatically**.
- Locked phone: unlock flow before opening the app.

## Exit criteria
Several useful actions work on the real device. Unsupported actions fail safely. Messages are never sent without the user pressing send. Each integration's limits are documented.

## Common problems
- `<queries>` missing: app looks "not installed" on Android 11+.
- Deep link opens the wrong screen: check the app's current URL scheme and verify on the device.
- WhatsApp or similar changes behavior with app updates: note the tested version in `INTEGRATIONS.md`.

## Rollback
`git checkout phase-4-done`. Tag `phase-5-done` when passing.

## Antigravity prompt
> Work only on Phase 5. Add independent ActionHandlers for SearchYouTube, SearchWeb, expanded OpenApp (allowlist and queries), and PrepareMessage (prefill only, never send). Register them in the router and add them to the rule engine and LLM schema. Write docs/INTEGRATIONS.md. Do not modify voice, contacts matching, or calling modules. Build, test on the phone, and report limitations honestly.
