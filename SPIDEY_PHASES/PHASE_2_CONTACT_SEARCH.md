# Phase 2: Contact Search

## Goal
"Guru Anna" resolves to a **real contact record** or to a clarification question. No calling yet.

## Depends on
Phase 1 passing (`phase-1-done` tag). Uses the frozen contracts as they are.

## Files you may touch
`contacts/` (new), `data/` (alias table), `actions/` (add `FindContact` action and handler only), `safety/PermissionManager` (contacts permission), `ui/settings` (alias screen), `IntentEngine` (add contact-query rules). Do not change the voice pipeline.

## Tasks
1. `PermissionManager`: request `READ_CONTACTS` while the app is visible; handle denial with a spoken, honest message.
2. `ContactRepository`: read contacts via the Contacts Provider (name, numbers, labels). Cache lightly in memory; never persist the full list.
3. `ContactMatcher` pipeline:
   1. Normalize case, whitespace, punctuation.
   2. Exact full-name match.
   3. Token and partial-name match.
   4. User alias match.
   5. Fuzzy similarity (Jaro-Winkler or Levenshtein) for spelling or recognition errors.
   6. Combine into a score and rank candidates.
4. Do **not** use Soundex-style phonetics as the main method; it works poorly for Indian names and Hinglish/Tamil-English mixes.
5. `ContactResolution` result type: `Resolved(contact)`, `Ambiguous(candidates)`, `NotFound`, `PermissionDenied`.
6. Ambiguity rule: if the top two scores are too close, or confidence is below a threshold, return `Ambiguous`; never pick silently.
7. Aliases: Room table (`alias`, `contactId`), screen to add/edit/delete, plus a "clear all aliases" option. Optional voice flow: "Remember Guru as Guru Krishnan" (confirm first).
8. Handle "which one?" answers: user says "the second one" or "LiveWires" and the system narrows the candidates.
9. Unit tests with **synthetic** contact lists, using these cases: Guru Anna LW, Guru Anna LiveWires, Guru Krishnan, Anna LiveWires.

## Test on phone
- Exact name, partial name, alias, small misspelling.
- Two similar contacts triggers a question that lists them.
- No match: "I couldn't find that contact."
- Permission denied: clear spoken message; no crash.
- Contact with multiple numbers: recorded in the result, chosen in Phase 3.

## Exit criteria
Every test case yields a real contact or a clarification question. Unit tests pass. No contact data in logs.

## Common problems
- Matching too loose (wrong person) or too strict (misses). Tune thresholds using the unit test dataset, not guesses.
- Slow search on large contact lists: query the provider with filters instead of loading everything.

## Rollback
`git checkout phase-1-done`. Tag `phase-2-done` when passing.

## Antigravity prompt
> Work only on Phase 2. Add contacts reading with permission, the ContactMatcher pipeline, the alias table with a settings screen, the resolution types, clarification handling, and unit tests with synthetic contacts. Do not implement calling. Do not modify the wake-word or speech code. Build, test, and report.
