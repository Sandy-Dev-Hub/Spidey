# SPIDEY - Progress Tracker

## Current Phase: Phase 0 - Feasibility Spikes

| Phase | Status | Branch | Tag |
|-------|--------|--------|-----|
| Phase 0 - Feasibility | IN PROGRESS | `phase-0-feasibility` | - |
| Phase 1 - Voice Foundation | WAITING | - | - |
| Phase 2 - Contact Search | WAITING | - | - |
| Phase 3 - Calling and SIM | WAITING | - | - |
| Phase 4 - AI Understanding | WAITING | - | - |
| Phase 5 - Multi-App Actions | WAITING | - | - |
| Phase 6 - Background Reliability | WAITING | - | - |
| Phase 7 - Security and Release | WAITING | - | - |

---

## Phase 0 - Feasibility Spikes

**Goal:** Prove the risky parts work on the real Samsung phone before building anything real.

**Started:** 2026-10-20

### Environment Status

| Check | Status | Notes |
|-------|--------|-------|
| Git repo initialized | DONE | Branch: phase-0-feasibility |
| Android Studio installed | MISSING | Not found on this machine |
| Android SDK installed | MISSING | Not found on this machine |
| Java / JDK on PATH | MISSING | java not found in PATH |
| adb on PATH | MISSING | Not found in PATH |
| Phone USB debugging enabled | NEEDS CHECK | Cannot verify without adb |
| Samsung USB driver installed | NEEDS CHECK | Cannot verify without adb |
| Phone visible via adb | NEEDS CHECK | Cannot verify without adb |

### Spike Results

| Spike | Status | Notes |
|-------|--------|-------|
| Spike 1: Wake word | PENDING | - |
| Spike 2: Mic handoff | PENDING | - |
| Spike 3: Background app launch | PENDING | - |
| Spike 4: Unlock prompt | PENDING | - |
| Spike 5: Test call | PENDING | - |
| Spike 6: Screen-off stability | PENDING | - |

### Exit Criteria

- [ ] docs/PHASE0_RESULTS.md with pass/fail table for all six spikes
- [ ] Written decision: plain foreground service vs. default-assistant role
- [ ] Chosen wake phrase and engine confirmed

### Known Issues / Blockers

- Android Studio not installed - required to build and install the spike APK.
- Java JDK not on PATH - required by Gradle.
- adb not on PATH - required to install and test on device.

---

*Updated: 2026-10-20*
