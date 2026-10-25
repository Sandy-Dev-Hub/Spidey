# SPIDEY: Shared Context (paste this first in every phase)

## What SPIDEY is
A native Android voice assistant (Kotlin + Jetpack Compose) for a Samsung phone on Android 15. The user says "Hey Spidey", speaks a command, and SPIDEY performs supported actions (open apps, call contacts, search YouTube, and so on) and answers by voice.

## How to work (this is what prevents code collapse)
1. Work on **one phase at a time**. Never start the next phase until the current phase's exit criteria pass on the real phone.
2. Use Git. One branch per phase: `phase-N-name`. When the phase passes, merge to `main` and tag it: `git tag phase-N-done`.
3. If a later phase breaks something, `git checkout phase-N-done` gets you back to a known-good state.
4. A phase may only **add** files or make small edits to files listed in its "Files you may touch" section. If it needs to change a frozen file, stop and ask the user first.
5. Build and run tests after every meaningful change. Never fabricate test results.

## Frozen contracts (created in Phase 1, changed only on purpose)
These small interfaces keep phases independent. Later phases plug into them.

```kotlin
// AssistantState.kt
enum class AssistantState { SLEEPING, LISTENING, PROCESSING, AWAITING_CONFIRMATION, AWAITING_UNLOCK, EXECUTING, SPEAKING, ERROR }

// AssistantAction.kt
sealed interface AssistantAction { /* each phase adds its own subtypes */ }

// ActionResult.kt
sealed interface ActionResult {
    data class Success(val spokenMessage: String) : ActionResult
    data class Failure(val spokenMessage: String, val reason: String) : ActionResult
    data object Cancelled : ActionResult
    data class NeedsClarification(val question: String, val candidates: List<String>) : ActionResult
    data object NeedsUnlock : ActionResult
}

// ActionHandler.kt
interface ActionHandler<T : AssistantAction> {
    val actionType: Class<T>
    suspend fun handle(action: T): ActionResult
}
```

New abilities are added by writing a new `ActionHandler` and registering it with the `ActionRouter`. They are never added by editing a big if/else chain.

## Fixed rules for every phase
- Never store, read, log, or type the device lock PIN/password. Unlocking uses only the system prompt (`requestDismissKeyguard`).
- The LLM (Phase 4 onward) only proposes typed actions. Local code validates. No arbitrary code or shell execution.
- Wake-word detection stays on-device.
- Never log raw audio, contact lists, phone numbers, or message content.
- No secrets in source control.
- Never claim an action succeeded unless the result is known.
- Do not hard-code fake contact, SIM, or test results.
- Inspect existing files before editing; do not blindly overwrite Gradle or manifest files.

## Tech stack
Kotlin, Jetpack Compose, Gradle Kotlin DSL (wrapper, pinned versions), Coroutines/Flow, Room, DataStore, Android `SpeechRecognizer`, `TextToSpeech`, a local wake-word SDK (Porcupine first candidate), Telecom APIs, JUnit.

## Package layout (grows by phase)
```text
com.spidey.assistant/
  core/      ConversationManager, AssistantState, ActionRouter, ActionResult, IntentEngine
  voice/     WakeWordService, WakeWordEngine, SpeechRecognizerManager, SpeechOutput
  unlock/    UnlockCoordinator, UnlockActivity, PendingAction
  actions/   AssistantAction, one handler per ability
  contacts/  repository, matcher, aliases
  calling/   account repository, CallCoordinator
  safety/    SafetyGate, PermissionManager, ConfirmationPolicy
  data/      Room database, DataStore preferences
  ui/        home, conversation, settings
```

## Phase order
0 Feasibility, 1 Voice foundation, 2 Contact search, 3 Calling and SIM, 4 AI understanding, 5 Multi-app actions, 6 Background reliability, 7 Security and release.

## Prompt wrapper for Antigravity
Paste this, then paste the phase file's content:

> You are working on the SPIDEY Android project. Read 00_PROJECT_CONTEXT.md and the phase file I give you. Work only on that phase. Inspect the workspace first. Touch only the files the phase allows. Build and test after each step, and report honestly what works and what does not. Do not start any other phase.
