# Design: Screen-Off Resync Intervention Triggering

## Overview
When a user opens a protected application (e.g. Telegram), passes intervention, and uses the app, turning off the device screen and turning it back on currently fails to trigger a new intervention. This document specifies the design to ensure a fresh intervention is reliably shown upon turning the screen back on / unlocking.

## Root Cause Analysis
1. **Domain State**: `ProtectionReducer` already clears `sessionPermits` and `lastExitElapsedMs` on `ScreenOff`, setting state to `Suspended(locked = true)`. On `ScreenUnlocked`, it transitions to `Idle` and emits `ProtectionEffect.ResyncRequired`.
2. **Platform Constraints**:
   - Invariant I10 enforces `android:canRetrieveWindowContent="false"` in `accessibility_service_config.xml`. Consequently, `rootInActiveWindow` in `AppMonitorService.requestResync()` always returns `null`.
   - Android does not emit `TYPE_WINDOW_STATE_CHANGED` when waking up into an application that was already foreground prior to screen-off.
   - On devices without a keyguard lock screen (or with swipe/smart lock), `ACTION_USER_PRESENT` is not broadcast on screen on unless `KeyguardManager.isKeyguardLocked` is checked.
   - Result: `requestResync` fails to identify the active package, no candidate is sent, and Wattim remains dormant.

## Solution Architecture

### 1. `AppMonitorService`
- **Fallback in `requestResync`**:
  If `foregroundTracker.extractMetadataPackage(rootInActiveWindow)` returns `null`, fall back to `foregroundTracker.lastPackage`.
  Validate the candidate against:
  - Not blank
  - Not IME (`!foregroundTracker.isIme(...)`)
  - Not System UI (`!foregroundTracker.isSystemUi(...)`)
  - Not own package (`candidate != foregroundTracker.ownPackageName`)
  If valid, dispatch `ProtectionEvent.ForegroundCandidate` to `InterventionCoordinator`.
- **Keyguard-Aware Screen State in `screenReceiver`**:
  - `ACTION_SCREEN_OFF`: invoke `coord.onScreenOff()`.
  - `ACTION_SCREEN_ON`: inspect `KeyguardManager.isKeyguardLocked`:
    - If `false` (no lock screen or already unlocked): invoke `coord.onScreenUnlocked()`.
    - If `true`: invoke `coord.onScreenOnLocked()` and await `ACTION_USER_PRESENT`.
  - `ACTION_USER_PRESENT`: invoke `coord.onScreenUnlocked()`.

### 2. `InterventionCoordinator` & `ProtectionReducer`
- `ScreenUnlocked` moves state from `Suspended` to `Idle` and dispatches `ResyncRequired`.
- Upon receiving `ForegroundCandidate(targetPackage)`:
  - `RuleEngine.evaluate` evaluates target.
  - Since `sessionPermits` was cleared on `ScreenOff`, `RuleEngine` produces `Decision.Intervention`.
  - Reducer returns `ProtectionState.Intervening` and dispatches `ShowIntervention`.
  - `OverlayHost` launches `InterventionActivity`, naturally pausing the target app and presenting the breathing intervention.

## Edge Cases
- **Device locked on Launcher or unprotected app**: `lastPackage` resolves to launcher or non-target app, evaluated as `Decision.Allow(NOT_TARGET)`. No intervention shown.
- **Intervention in progress when screen turned off**: Screen-off marks attempt `INTERRUPTED`, dismisses old intervention. Upon waking up, a fresh intervention begins cleanly.
- **Idempotent Unlocks**: If both `ACTION_SCREEN_ON` (unlocked) and `ACTION_USER_PRESENT` fire, `ScreenUnlocked` is handled idempotently without duplicate interventions.

## Verification & Testing Plan
- `AppMonitorServiceWiringTest`: Verify fallback to `lastPackage` when `rootInActiveWindow` is null, verify keyguard receiver behavior.
- `InterventionCoordinatorUnlockTest`: Verify full cycle of Granted -> ScreenOff -> ScreenUnlocked -> Resync Candidate -> Intervening state transition.
- Full unit test suite regression run.
