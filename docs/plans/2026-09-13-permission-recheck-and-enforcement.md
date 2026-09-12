# Permission Re-check and Enforcement Implementation Plan

> **For Antigravity:** REQUIRED SUB-SKILL: Load executing-plans to implement this plan task-by-task.

**Goal:** Re-check core permissions on every app launch, resume, and manual re-check in Settings, automatically navigating to Onboarding when any required permission is revoked, and providing a manual re-check action in Settings.

**Architecture:** Reactive unidirectional state architecture using `PermissionMonitor.statusFlow`. `MainActivity` and `WattimNavHost` strictly enforce that `areRequiredPermissionsGranted` guards route resolution (including saved instance state restoration). `ConfigViewModel` exposes `onRefreshPermissions()`, which is wired to `PermissionStatusCard` in Settings.

**Tech Stack:** Kotlin, Jetpack Compose, Coroutines (Flow/StateFlow), AndroidX Navigation / custom routing, Robolectric unit tests.

---

### Task 1: Navigation and Route Guard in `WattimNavHost` and `MainActivity`

**Files:**
- Modify: `app/src/main/kotlin/io/ronesec/android/ui/WattimNavHost.kt`
- Modify: `app/src/main/kotlin/io/ronesec/android/ui/MainActivity.kt`
- Test: `app/src/test/kotlin/io/ronesec/android/ui/T16_NavigationAndOnboardingReachabilityTest.kt`

**Step 1: Write the failing tests**
Add unit tests to `T16_NavigationAndOnboardingReachabilityTest.kt` verifying:
1. When `areRequiredPermissionsGranted == false`, `startingRoute` resolution falls back to `AppRoute.Onboarding` even if `initialRoute` was provided as `AppRoute.Main`.
2. When `routeRequests` emits a route while `areRequiredPermissionsGranted == false`, it remains on or redirects to `AppRoute.Onboarding`.

**Step 2: Run test to verify it fails**
Run: `./gradlew :app:testDebugUnitTest --tests "io.ronesec.android.ui.T16_NavigationAndOnboardingReachabilityTest"`

**Step 3: Implement minimal code in `WattimNavHost.kt` and `MainActivity.kt`**
- In `WattimNavHost.kt`:
  Ensure `startingRoute` evaluates:
  ```kotlin
  val startingRoute = if (!permissionSnapshot.areRequiredPermissionsGranted) {
      AppRoute.Onboarding
  } else {
      initialRoute ?: AppRoute.Main(TerminalTab.APPS)
  }
  ```
  Ensure `routeRequests.collect` checks `permissionSnapshot.areRequiredPermissionsGranted` before navigating to a non-onboarding route.
- In `MainActivity.kt`:
  Ensure `permissionMonitor.refresh()` is called in `onCreate()` as well as in `onResume()`.

**Step 4: Run test to verify it passes**
Run: `./gradlew :app:testDebugUnitTest --tests "io.ronesec.android.ui.T16_NavigationAndOnboardingReachabilityTest"`
Expected: PASS

**Step 5: Commit**
```bash
git add app/src/main/kotlin/io/ronesec/android/ui/WattimNavHost.kt app/src/main/kotlin/io/ronesec/android/ui/MainActivity.kt app/src/test/kotlin/io/ronesec/android/ui/T16_NavigationAndOnboardingReachabilityTest.kt
git commit -m "feat(ui): enforce onboarding route guard on launch, resume, and route restoration"
```

---

### Task 2: Settings UI - Re-check Action in `PermissionStatusCard`, `ConfigScreen`, and `MainShell`

**Files:**
- Modify: `app/src/main/kotlin/io/ronesec/android/ui/config/PermissionStatusCard.kt`
- Modify: `app/src/main/kotlin/io/ronesec/android/ui/config/ConfigScreen.kt`
- Modify: `app/src/main/kotlin/io/ronesec/android/ui/MainShell.kt`
- Test: `app/src/test/kotlin/io/ronesec/android/ui/config/ConfigViewModelTest.kt`
- Test: `app/src/test/kotlin/io/ronesec/android/ui/T16_StatsAndConfigIntegrationTest.kt`

**Step 1: Write the failing tests**
In `ConfigViewModelTest.kt` and `T16_StatsAndConfigIntegrationTest.kt`:
- Verify `onRefreshPermissions()` updates permission statuses in `uiState`.
- Verify `PermissionStatusCard` contains the "CHECK STATUS" action button and invokes `onRefreshPermissions`.

**Step 2: Run test to verify it fails**
Run: `./gradlew :app:testDebugUnitTest --tests "io.ronesec.android.ui.config.ConfigViewModelTest"`

**Step 3: Implement minimal code in `PermissionStatusCard.kt`, `ConfigScreen.kt`, and `MainShell.kt`**
- In `PermissionStatusCard.kt`:
  - Add `onRefreshPermissions: () -> Unit` parameter.
  - In the card header row, place the title alongside a `TerminalButton(text = stringResource(R.string.action_check_status), onClick = onRefreshPermissions, variant = TerminalButtonVariant.SECONDARY)`.
- In `ConfigScreen.kt`:
  - Add `onRefreshPermissions: () -> Unit` parameter and pass it to `PermissionStatusCard`.
- In `MainShell.kt`:
  - Pass `onRefreshPermissions = { configViewModel.onRefreshPermissions() }` to `ConfigScreen`.

**Step 4: Run tests to verify they pass**
Run: `./gradlew :app:testDebugUnitTest --tests "io.ronesec.android.ui.config.ConfigViewModelTest"`
Run: `./gradlew :app:testDebugUnitTest --tests "io.ronesec.android.ui.T16_StatsAndConfigIntegrationTest"`
Expected: PASS

**Step 5: Commit**
```bash
git add app/src/main/kotlin/io/ronesec/android/ui/config/PermissionStatusCard.kt app/src/main/kotlin/io/ronesec/android/ui/config/ConfigScreen.kt app/src/main/kotlin/io/ronesec/android/ui/MainShell.kt app/src/test/kotlin/io/ronesec/android/ui/config/ConfigViewModelTest.kt app/src/test/kotlin/io/ronesec/android/ui/T16_StatsAndConfigIntegrationTest.kt
git commit -m "feat(ui): add check status action to PermissionStatusCard and wire to ConfigViewModel"
```

---

### Task 3: Full Regression and Verification

**Files:**
- None (verification only)

**Step 1: Run all unit tests**
Run: `./gradlew testDebugUnitTest`
Expected: ALL PASS

**Step 2: Verify git status and clean working tree**
Run: `git status`
Expected: Clean working tree
