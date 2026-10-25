# Phase 1: Voice Foundation

## Goal
The core loop works reliably: "Hey Spidey" → "Yes, boss?" → you speak → SPIDEY answers by voice → back to listening. Only harmless commands.

## Depends on
Phase 0 results (chosen wake word/engine, chosen service architecture).

## Files you may touch
`core/`, `voice/`, `unlock/` (only the locked-screen skeleton), `actions/` (Greeting and OpenApp for YouTube only), `ui/home`, `ui/conversation`, manifest, Gradle. Create the **frozen contracts** from `00_PROJECT_CONTEXT.md` here.

## Tasks
1. Create the frozen contracts: `AssistantState`, `AssistantAction`, `ActionResult`, `ActionHandler`.
2. `ConversationManager`: single authoritative, thread-safe state (a `StateFlow`) shared by UI and voice pipeline.
3. `WakeWordService`: microphone foreground service with persistent notification and a Stop button; correct service type and permissions.
4. Mic handoff as an **explicit** state transition: pause the wake word, release the mic, beep or "Yes, boss?", listen, re-arm the wake word.
5. `SpeechRecognizerManager` (`en-IN`): bounded listening with timeout, empty-result handling (reprompt once, then sleep), error mapping.
6. `SpeechOutput` (TextToSpeech): ignore wake events while speaking, so SPIDEY doesn't hear itself.
7. `IntentEngine` v1: simple local rules for "hello" and "open YouTube". Output a typed `AssistantAction`.
8. `ActionRouter` with handler registration; reject unknown actions.
9. Handlers: `GreetingHandler`, `OpenAppHandler` using an **allowlist enum** (`AllowedApp`), with a `<queries>` entry for it.
10. Compose home screen: shows current state, last heard text, last response, Start/Stop listening button, permission status.
11. Unlock skeleton: if the device is locked and the action needs unlock, set `AWAITING_UNLOCK` and use the approach Phase 0 proved. Keep it minimal.
12. Unit tests for the state machine transitions and intent rules.

## Permissions needed
`RECORD_AUDIO`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`, `POST_NOTIFICATIONS`, plus anything Phase 0 showed is needed for background launch.

## Test on phone
- Wake, say "hello": hears you, replies by voice, returns to sleeping.
- Wake, say "open YouTube": YouTube opens (screen on, then screen off/locked per Phase 0 findings).
- Wake, stay silent: times out and returns to sleeping.
- Say gibberish: polite "I didn't understand", then sleeps.
- Stop button: service stops, mic released, notification gone.
- SPIDEY does not trigger on its own voice.

## Exit criteria
Wake, command, spoken response, and return to listening works repeatedly (try 20 cycles). Timeouts, cancellation and errors all end in a valid state. Unit tests pass. Mic is released when stopped.

## Common problems
- Wake word stops working after the first command: forgot to re-arm the engine.
- First words lost: add or lengthen the beep/prompt before listening.
- Recognizer "busy" errors: the wake engine still holds the mic; release it first.
- Service killed on Samsung: battery settings (see Phase 0 notes).

## Rollback
`git checkout phase-0-done` if the foundation gets messy. Tag `phase-1-done` when passing.

## Antigravity prompt
> Work only on Phase 1. Create the frozen contracts, the state machine, the wake-word foreground service, speech recognition, text-to-speech, the local intent rules for hello and open YouTube, and the Compose home screen. Use the findings in docs/PHASE0_RESULTS.md. Do not implement contacts, calling, or any LLM. Build, run unit tests, install on the phone, and report what works.
