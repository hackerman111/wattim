# Design: Permission Re-check and Enforcement on App Launch and in Settings

## Context and Goals
1. **Core Permission Enforcement on App Launch and Resume**:
   - The application requires three core permissions for protection:
     1. Accessibility Service (`AppMonitorService`)
     2. Overlay Permission (`Settings.canDrawOverlays`)
     3. Battery Optimization Exemption (`PowerManager.isIgnoringBatteryOptimizations`)
   - Other permissions, such as Notification permission (`POST_NOTIFICATIONS`) and Media Control (`NotificationListenerService`), are optional and do not block core protection.
   - On every app launch (`MainActivity.onCreate()`) and on return/resume (`MainActivity.onResume()`), permissions must be checked.
   - If any required permission is missing or revoked:
     - The app must immediately route to the initial setup / onboarding flow (`AppRoute.Onboarding`).
     - Even if the activity was restored from `savedInstanceState` or launched via a deep link intent, a missing required permission strictly redirects to Onboarding.
2. **Re-checking and Updating Permissions in Settings (`ConfigScreen`)**:
   - In the Settings screen (`PermissionStatusCard`), add an explicit action to re-check all permissions ("CHECK STATUS" / "ОБНОВИТЬ СТАТУС").
   - Wire `onRefreshPermissions()` from `ConfigViewModel` through `MainShell` and `ConfigScreen` into `PermissionStatusCard`.
   - For each permission item in the card:
     - If granted: display the active badge `ON`.
     - If not granted/revoked: display the `ENABLE` button which navigates to the appropriate system settings screen so the user can grant or update it.
   - If a re-check reveals that a required permission was revoked, the reactive flow automatically navigates to `AppRoute.Onboarding`.

---

## 1. Lifecycle and Navigation Enforcement

### 1.1 `MainActivity` Refresh
- In `MainActivity.onCreate()`:
  - Call `permissionMonitor.refresh()` eagerly before/around content setup to ensure the snapshot is fresh.
- In `MainActivity.onResume()`:
  - Retain existing call to `permissionMonitor.refresh()`.

### 1.2 `WattimNavHost` Route Guard
In `app/src/main/kotlin/io/ronesec/android/ui/WattimNavHost.kt`:
- Initial route resolution:
  ```kotlin
  var currentRoute by remember {
      val startingRoute = if (!permissionSnapshot.areRequiredPermissionsGranted) {
          AppRoute.Onboarding
      } else {
          initialRoute ?: AppRoute.Main(TerminalTab.APPS)
      }
      mutableStateOf(startingRoute)
  }
  ```
  This guarantees that even if `initialRoute` was provided (restored from `savedInstanceState` or `intent`), missing required permissions immediately force `AppRoute.Onboarding`.
- Route request handling:
  When a route is requested via `routeRequests`, if `!permissionSnapshot.areRequiredPermissionsGranted`, ignore or redirect to `AppRoute.Onboarding`.
- Retain reactive revocation guard:
  ```kotlin
  LaunchedEffect(permissionSnapshot.areRequiredPermissionsGranted) {
      if (!permissionSnapshot.areRequiredPermissionsGranted && currentRoute !is AppRoute.Onboarding) {
          currentRoute = AppRoute.Onboarding
      }
  }
  ```

---

## 2. Settings UI: Permission Status & Recheck

### 2.1 `PermissionStatusCard`
In `app/src/main/kotlin/io/ronesec/android/ui/config/PermissionStatusCard.kt`:
- Add `onRefreshPermissions: () -> Unit` parameter.
- Add a header action row with title `PERMISSIONS & STATUS` and a secondary button `CHECK STATUS` (`R.string.action_check_status`).
- Display permission items for Accessibility, Media Control, Overlay, and Battery Exemption.
- For each item, display `ON` badge if granted, or `ENABLE` button (`onEnable...`) to jump to the system settings if denied.

### 2.2 Wiring in `ConfigScreen` and `MainShell`
- `ConfigScreen`:
  - Add `onRefreshPermissions: () -> Unit` parameter.
  - Pass `onRefreshPermissions = onRefreshPermissions` to `PermissionStatusCard`.
- `MainShell`:
  - Pass `onRefreshPermissions = { configViewModel.onRefreshPermissions() }` to `ConfigScreen`.

---

## 3. Testing and Verification Plan

1. **`WattimNavHost` / Navigation Tests**:
   - Verify that when `areRequiredPermissionsGranted == false`, `WattimNavHost` always starts on `AppRoute.Onboarding`, even if `initialRoute` was set to `AppRoute.Main`.
   - Verify that revoking any of the three required permissions while in `AppRoute.Main` triggers transition to `AppRoute.Onboarding`.
   - Verify that re-granting all required permissions allows transition to `AppRoute.Main`.
2. **`ConfigViewModel` & UI Tests**:
   - Verify `onRefreshPermissions` triggers `permissionMonitor.refresh()`.
   - Verify `PermissionStatusCard` renders the `CHECK STATUS` button and invokes `onRefreshPermissions`.
   - Verify that clicking `ENABLE` for each missing permission launches the expected system intent.
