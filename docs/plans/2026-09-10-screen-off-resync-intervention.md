# Screen-Off Resync Intervention Implementation Plan

> **For Antigravity:** REQUIRED SUB-SKILL: Load executing-plans to implement this plan task-by-task.

**Goal:** Ensure that after entering a protected app (e.g. Telegram), passing the intervention check, and working in the app, turning the smartphone screen off and back on reliably triggers a new intervention upon unlock.

**Architecture:** Use `ForegroundTracker.lastPackage` as a safe, privacy-preserving (Invariant I10) fallback in `AppMonitorService.requestResync()` when `rootInActiveWindow` returns null. Update `AppMonitorService.screenReceiver` to inspect `KeyguardManager.isKeyguardLocked` on `ACTION_SCREEN_ON` so that devices without a keyguard lock screen transition to `ScreenUnlocked` without waiting for `ACTION_USER_PRESENT`.

**Tech Stack:** Kotlin, Android AccessibilityService, KeyguardManager, Kotlin Coroutines, Robolectric, JUnit 4.

---

### Task 1: Add unit tests for `AppMonitorService.requestResync` fallback and Keyguard state handling

**Files:**
- Modify: `app/src/test/kotlin/io/ronesec/android/platform/accessibility/AppMonitorServiceWiringTest.kt`

**Step 1: Write failing tests**
- Add test verifying that when `rootInActiveWindow` is null, `requestResync` uses `foregroundTracker.lastPackage` (when valid and not IME/SystemUI/own package) to emit `ForegroundCandidate`.
- Add test verifying that `screenReceiver` on `ACTION_SCREEN_ON` checks `KeyguardManager.isKeyguardLocked`:
  - If `false`, immediately calls `coord.onScreenUnlocked()`.
  - If `true`, calls `coord.onScreenOnLocked()`.

**Step 2: Run test to verify it fails**
Run: `./gradlew :app:testDebugUnitTest --tests "io.ronesec.android.platform.accessibility.AppMonitorServiceWiringTest"`
Expected: FAIL (because fallback on null root is not yet implemented).

---

### Task 2: Implement `lastPackage` fallback and Keyguard-aware receiver in `AppMonitorService.kt`

**Files:**
- Modify: `app/src/main/kotlin/io/ronesec/android/platform/accessibility/AppMonitorService.kt:232-249` and `AppMonitorService.kt:299-320`

**Step 1: Update `screenReceiver`**
- In `screenReceiver.onReceive`:
  ```kotlin
  when (intent?.action) {
      Intent.ACTION_SCREEN_OFF -> coord.onScreenOff()
      Intent.ACTION_SCREEN_ON -> {
          val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
          if (km?.isKeyguardLocked == false) {
              coord.onScreenUnlocked()
          } else {
              coord.onScreenOnLocked()
          }
      }
      Intent.ACTION_USER_PRESENT -> coord.onScreenUnlocked()
  }
  ```

**Step 2: Update `requestResync`**
- In `requestResync`:
  ```kotlin
  val root = rootInActiveWindow
  val pkg = foregroundTracker.extractMetadataPackage(root)
      ?: foregroundTracker.lastPackage?.takeIf { candidate ->
          candidate.isNotBlank() &&
          !foregroundTracker.isIme(candidate) &&
          !foregroundTracker.isSystemUi(candidate) &&
          candidate != foregroundTracker.ownPackageName
      }
  if (pkg != null) {
      coord.onForegroundCandidate(
          ProtectionEvent.ForegroundCandidate(
              packageName = pkg,
              sourceUptimeMs = SystemClock.uptimeMillis(),
              eventSequence = foregroundTracker.currentSequence + 1
          )
      )
  }
  ```

**Step 3: Run test to verify it passes**
Run: `./gradlew :app:testDebugUnitTest --tests "io.ronesec.android.platform.accessibility.AppMonitorServiceWiringTest"`
Expected: PASS.

---

### Task 3: Add integration regression test for Screen-Off -> Unlock -> Re-intervention flow

**Files:**
- Modify: `app/src/test/kotlin/io/ronesec/android/protection/InterventionCoordinatorUnlockTest.kt`

**Step 1: Write integration test**
- Test scenario:
  1. Service connected, target configured for Telegram (`org.telegram.messenger`).
  2. Telegram enters foreground -> `Intervening`.
  3. ActionContinue received -> `Granted(ACTIVE_SESSION_PERMIT)`.
  4. ScreenOff received -> `Suspended(locked = true)`.
  5. ScreenUnlocked received -> state becomes `Idle`, dispatches `ResyncRequired`.
  6. Service resyncs with Telegram -> coordinator receives `ForegroundCandidate("org.telegram.messenger")`.
  7. Verify state becomes `Intervening` (new intervention cycle) with `ShowIntervention` dispatched.

**Step 2: Run test to verify it passes**
Run: `./gradlew :app:testDebugUnitTest --tests "io.ronesec.android.protection.InterventionCoordinatorUnlockTest"`
Expected: PASS.

---

### Task 4: Full regression testing and build verification

**Step 1: Run domain and app debug unit tests**
Run: `./gradlew :domain:test :app:testDebugUnitTest`
Expected: ALL PASS.

**Step 2: Run check/lint to ensure zero regressions**
Run: `./gradlew :domain:check`
Expected: PASS.
