# Phase 4: Structured AI Understanding

## Goal
Natural phrasing and follow-ups ("ring Guru", "no, the other one", "use SIM 2") become **validated typed actions**. The LLM proposes; local code decides.

## Depends on
Phase 3 passing (`phase-3-done`). Local rules still work as the fallback.

## Files you may touch
`core/IntentEngine` (split into `RuleIntentEngine` and `LlmIntentEngine` behind one interface), new `ai/` package, `data/` (conversation context, optional), `ui/settings` (AI on/off, privacy notice). Do not change handlers or the contact/calling logic.

## Tasks
1. Define one interface: `IntentEngine.interpret(text, context): AssistantAction`. Keep the existing rules as `RuleIntentEngine`.
2. `LlmIntentEngine`: call an LLM API with **structured tool/function calling** and a strict schema covering only the existing action types.
3. Local validation of every LLM output: known action type, required fields, string length limits, `AllowedApp` enum for apps. Anything invalid becomes a clarification or a safe failure, never executed.
4. Routing: try local rules first for simple commands (cheaper, faster, offline). Use the LLM when the rules don't match.
5. Short-term context: keep the last few turns (in memory) so follow-ups work. Clear on session end.
6. Privacy: send only the spoken text and minimal context. **Never send the contacts database or phone numbers.** The LLM gets the contact *query string*; resolution stays local.
7. API key handling: for a personal prototype use a restricted key with quotas, kept in `local.properties` / build config, not in git. Note in docs that a distributed app needs an authenticated backend.
8. Offline/network failure: fall back to rules; say honestly when the AI isn't available.
9. Settings toggle: AI on/off, with a short explanation that text is sent to an external provider.
10. Tests: a table of phrases mapped to expected actions, plus adversarial inputs (gibberish, "delete everything", prompt-injection text) that must be rejected or clarified.

## Test on phone
- Varied phrasings of calls and YouTube searches.
- Follow-up: "Call Guru" then "the LiveWires one".
- Airplane mode: rules still work; honest message for AI-only requests.
- Nonsense or dangerous requests: rejected safely.
- Check logs: no raw audio, contacts, or numbers.

## Exit criteria
Natural-language requests and follow-ups become validated actions. Invalid or unknown output is never executed. Works with AI off. No sensitive data leaves the device beyond the request text.

## Common problems
- LLM returns extra or missing fields: validate strictly and re-prompt or clarify.
- Latency feels slow: keep rules first and use short prompts.
- Hallucinated contact names: resolution stays local, so a made-up name just yields NotFound.

## Rollback
`git checkout phase-3-done`. Tag `phase-4-done` when passing.

## Antigravity prompt
> Work only on Phase 4. Introduce an IntentEngine interface with the existing rules engine and a new LLM engine using structured tool calling. Validate all output locally against the existing typed actions. Add short-term context, an AI toggle, offline fallback, and tests including adversarial inputs. Never send contacts or phone numbers to the model. Do not change handlers or the contact/calling modules.
