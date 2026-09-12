# Detailed Degraded Notification & Permissions Navigation Implementation Plan

> **For Antigravity:** REQUIRED SUB-SKILL: Load executing-plans to implement this plan task-by-task.

**Goal:** Provide clear, specific missing-permission information directly in the foreground degraded notification and navigate users directly to the Settings ("CONFIG") tab focused on the Permissions card upon tapping the notification.

**Architecture:** Extend `FocusForegroundService` to inspect `PermissionSnapshot` and format individual or multi-item missing-permission strings and styles with deep-link intent extras. In `MainActivity` and `WattimNavHost`, support the `EXTRA_OPEN_PERMISSIONS` intent extra, allowing `AppRoute.Main(TerminalTab.CONFIG)` even during ungranted permission states while protecting access to core feature tabs. In `ConfigScreen`, auto-scroll and highlight `PermissionStatusCard` when opened from this flow.

**Tech Stack:** Kotlin, Android Jetpack Compose, NotificationCompat, AndroidX Lifecycle, JUnit, Robolectric.

---

### Task 1: Add Localized Strings for Degraded Permissions Notification

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-ru/strings.xml`

**Step 1: Write string definitions in `values/strings.xml` and `values-ru/strings.xml`**
Add:
- `fgs_status_degraded_accessibility`
- `fgs_status_degraded_accessibility_disconnected`
- `fgs_status_degraded_overlay`
- `fgs_status_degraded_battery`
- `fgs_status_degraded_multiple`
- `fgs_missing_permissions_header`

**Step 2: Verify strings compile with Gradle check**
Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**
```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-ru/strings.xml
git commit -m "feat(strings): add localized strings for detailed degraded notification"
```

---

### Task 2: Update `FocusForegroundService` Notification Formatting & Deep Link Extra

**Files:**
- Modify: `app/src/main/kotlin/io/ronesec/android/platform/system/FocusForegroundService.kt`
- Test: `app/src/test/kotlin/io/ronesec/android/platform/system/T12_PermissionMonitorAndFgsTest.kt`

**Step 1: Write the failing tests in `T12_PermissionMonitorAndFgsTest.kt`**
- Test single missing accessibility permission text.
- Test single missing overlay permission text.
- Test single missing battery exemption text.
- Test accessibility disconnected text.
- Test multi-item degraded notification with `BigTextStyle`.
- Test `PendingIntent` contains `EXTRA_OPEN_PERMISSIONS == true` when degraded and false when active.

**Step 2: Run test to verify it fails**
Run: `./gradlew testDebugUnitTest --tests "io.ronesec.android.platform.system.T12_PermissionMonitorAndFgsTest"`
Expected: FAIL (missing methods / different string output)

**Step 3: Implement `buildNotification(snapshot: PermissionSnapshot)` in `FocusForegroundService.kt`**
- Inspect missing permissions:
  - Accessibility denied
  - Accessibility connected false (when granted)
  - Overlay denied
  - Battery exemption denied
- Build single-item or multi-item string.
- Set `BigTextStyle` for multiple missing items.
- Attach `MainActivity.EXTRA_OPEN_PERMISSIONS = true` to `activityIntent` when degraded.

**Step 4: Run test to verify it passes**
Run: `./gradlew testDebugUnitTest --tests "io.ronesec.android.platform.system.T12_PermissionMonitorAndFgsTest"`
Expected: PASS

**Step 5: Commit**
```bash
git add app/src/main/kotlin/io/ronesec/android/platform/system/FocusForegroundService.kt app/src/test/kotlin/io/ronesec/android/platform/system/T12_PermissionMonitorAndFgsTest.kt
git commit -m "feat(fgs): format detailed degraded notification and attach permissions extra"
```

---

### Task 3: Route Handling in `MainActivity` & `WattimNavHost` Route Guard

**Files:**
- Modify: `app/src/main/kotlin/io/ronesec/android/ui/MainActivity.kt`
- Modify: `app/src/main/kotlin/io/ronesec/android/ui/WattimNavHost.kt`
- Test: `app/src/test/kotlin/io/ronesec/android/ui/T16_NavigationAndOnboardingReachabilityTest.kt`

**Step 1: Write the failing tests in `T16_NavigationAndOnboardingReachabilityTest.kt`**
- Test that `AppRoute.Main(TerminalTab.CONFIG)` is permitted even when required permissions are missing.
- Test that launching `MainActivity` with `EXTRA_OPEN_PERMISSIONS` routes to `TerminalTab.CONFIG`.
- Test that switching tabs from `CONFIG` to `APPS` when required permissions are missing redirects to `AppRoute.Onboarding`.

**Step 2: Run test to verify it fails**
Run: `./gradlew testDebugUnitTest --tests "io.ronesec.android.ui.T16_NavigationAndOnboardingReachabilityTest"`
Expected: FAIL

**Step 3: Implement route handling & guard updates**
- In `MainActivity.kt`:
  - Declare `const val EXTRA_OPEN_PERMISSIONS = "io.ronesec.android.extra.OPEN_PERMISSIONS"`.
  - Add `permissionsRouteOrNull(): AppRoute?`.
  - Handle in `onCreate` and `onNewIntent`.
- In `WattimNavHost.kt`:
  - Permit `AppRoute.Main(TerminalTab.CONFIG)` as `initialRoute` and in `routeRequests`.
  - Guard `onTabSelected` in `MainShell` so navigating from `CONFIG` to `APPS`/`BLOCK`/`CODES` without permissions redirects to `AppRoute.Onboarding`.
  - Maintain auto-return to `Onboarding` only when current route is not `CONFIG` and not `Onboarding`.

**Step 4: Run test to verify it passes**
Run: `./gradlew testDebugUnitTest --tests "io.ronesec.android.ui.T16_NavigationAndOnboardingReachabilityTest"`
Expected: PASS

**Step 5: Commit**
```bash
git add app/src/main/kotlin/io/ronesec/android/ui/MainActivity.kt app/src/main/kotlin/io/ronesec/android/ui/WattimNavHost.kt app/src/test/kotlin/io/ronesec/android/ui/T16_NavigationAndOnboardingReachabilityTest.kt
git commit -m "feat(nav): allow CONFIG route when permissions missing and handle EXTRA_OPEN_PERMISSIONS"
```

---

### Task 4: Auto-scroll & Highlight in `ConfigScreen` and `PermissionStatusCard`

**Files:**
- Modify: `app/src/main/kotlin/io/ronesec/android/ui/config/ConfigScreen.kt`
- Modify: `app/src/main/kotlin/io/ronesec/android/ui/config/PermissionStatusCard.kt`
- Modify: `app/src/main/kotlin/io/ronesec/android/ui/MainShell.kt`
- Test: `app/src/test/kotlin/io/ronesec/android/ui/config/ConfigScreenTest.kt` (or existing config UI test)

**Step 1: Write failing UI test**
- Test that `PermissionStatusCard` displays highlighted border when required permissions are missing or focus requested.

**Step 2: Run test to verify it fails**
Run: `./gradlew testDebugUnitTest --tests "*Config*Test*"`
Expected: FAIL

**Step 3: Implement auto-scroll and highlight**
- In `ConfigScreen.kt`:
  - Add `shouldFocusPermissions: Boolean` parameter.
  - Use `BringIntoViewRequester` and `LaunchedEffect(shouldFocusPermissions)` to scroll `PermissionStatusCard` into view when focused.
  - Pass `isHighlighted = shouldFocusPermissions || !areRequiredPermissionsGranted` to `PermissionStatusCard`.
- In `PermissionStatusCard.kt`:
  - Add `isHighlighted: Boolean = false` parameter.
  - Render accent border `BorderStroke(1.5.dp, colors.accent)` when highlighted.
- In `MainShell.kt`:
  - Pass `shouldFocusPermissions = !permissionSnapshot.areRequiredPermissionsGranted` to `ConfigScreen`.

**Step 4: Run test to verify it passes**
Run: `./gradlew testDebugUnitTest --tests "*Config*Test*"`
Expected: PASS

**Step 5: Commit**
```bash
git add app/src/main/kotlin/io/ronesec/android/ui/config/ConfigScreen.kt app/src/main/kotlin/io/ronesec/android/ui/config/PermissionStatusCard.kt app/src/main/kotlin/io/ronesec/android/ui/MainShell.kt
git commit -m "feat(ui): auto-scroll and highlight PermissionStatusCard in ConfigScreen"
```

---

### Task 5: Full Regression & Integration Verification

**Files:**
- Run all project tests across modules.

**Step 1: Run full test suite**
Run: `./gradlew testDebugUnitTest`
Expected: ALL TESTS PASS

**Step 2: Run lint / verification**
Run: `./gradlew lintDebug` (or `./gradlew check -x lintVitalRelease`)
Expected: SUCCESS

**Step 3: Commit**
```bash
git commit --allow-empty -m "chore: verify full regression suite passes"
```
