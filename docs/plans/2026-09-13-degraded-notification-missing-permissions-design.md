# Design: Detailed Degraded Notification & Navigation to Permissions in Settings

## Context and Goals

When background protection becomes degraded (e.g. Accessibility revoked or disconnected, Overlay revoked, or Battery optimization exemption removed), the foreground service (`FocusForegroundService`) posts an ongoing notification.

Currently:
1. The notification displays a generic message (*"Защита приостановлена: требуются разрешения"* / *"Protection degraded: permissions required"*) without detailing which permission is missing.
2. Clicking the notification launches `MainActivity` with default flags and no target route. If the app was running in the background, the screen does not switch, or if required permissions are missing, the user is locked into Onboarding or APPS without clear guidance.
3. If accessibility is enabled in system settings but the accessibility service is disconnected, the user remains on the Apps tab without any indicator of why protection is degraded.

### User Requirements
1. **Informative Notification**: Explicitly state which permission is missing directly in the notification text (or list multiple missing permissions in `BigTextStyle`).
2. **Direct Navigation**: Tapping the notification opens the app directly at the **Settings** (`CONFIG`) tab, focusing/auto-scrolling to the **Permissions & Status** (`PermissionStatusCard`) card where the missing permission and its action button are clearly presented.

---

## 1. Informative Notification Formulation (`FocusForegroundService`)

### 1.1 State Inspection in `buildNotification`
- Instead of receiving a primitive `isDegraded: Boolean`, `buildNotification` receives `snapshot: PermissionSnapshot`.
- Identify missing requirements:
  - Accessibility permission: `accessibility != PermissionState.Granted`
  - Accessibility disconnected: `accessibility == PermissionState.Granted && !isAccessibilityConnected`
  - Overlay permission: `overlay != PermissionState.Granted`
  - Battery exemption: `batteryExemption != PermissionState.Granted`

### 1.2 Text Formatting & Localization
- **Single missing requirement**:
  - Content title: `R.string.app_name` ("wattim")
  - Content text:
    - Accessibility: *"Защита приостановлена: требуется «Специальные возможности»"* / *"Protection degraded: accessibility permission required"*
    - Accessibility disconnected: *"Защита приостановлена: служба специальных возможностей отключена"* / *"Protection degraded: accessibility service disconnected"*
    - Overlay: *"Защита приостановлена: требуется «Поверх других приложений»"* / *"Protection degraded: overlay permission required"*
    - Battery exemption: *"Защита приостановлена: требуется «Оптимизация батареи»"* / *"Protection degraded: battery optimization exemption required"*
- **Multiple missing requirements**:
  - Content text: *"Защита приостановлена: требуются разрешения (%d)"* / *"Protection degraded: %d permissions required"*
  - Big text style: A bulleted list of all missing permissions.

### 1.3 `PendingIntent` Routing Extras
- When degraded, the launch intent contains:
  ```kotlin
  putExtra(MainActivity.EXTRA_OPEN_PERMISSIONS, true)
  ```
- Use distinct `requestCode` (e.g. `NOTIFICATION_ID`) and `FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE` so extras are refreshed when the notification is re-posted.

---

## 2. Navigation & Route Guard (`MainActivity` & `WattimNavHost`)

### 2.1 `MainActivity` Deep Link Handling
- Define `EXTRA_OPEN_PERMISSIONS = "io.ronesec.android.extra.OPEN_PERMISSIONS"`.
- `intent.permissionsRouteOrNull()` helper:
  - Returns `AppRoute.Main(TerminalTab.CONFIG)` if `EXTRA_OPEN_PERMISSIONS` is true.
- In `onCreate`:
  - `restoredRoute` prioritizes `permissionsRouteOrNull()` -> `codesRouteOrNull()` -> `fromBundle(savedInstanceState)`.
- In `onNewIntent`:
  - Eagerly check `permissionsRouteOrNull()`, send to `routeRequests.trySend(...)`, and trigger `permissionMonitor.refresh()`.

### 2.2 `WattimNavHost` Route Guard Rules
- Allow `AppRoute.Main(TerminalTab.CONFIG)` even when `!permissionSnapshot.areRequiredPermissionsGranted`:
  - `initialRoute`: if `AppRoute.Main(TerminalTab.CONFIG)`, stay on `CONFIG`.
  - `routeRequests`: accept `AppRoute.Main(TerminalTab.CONFIG)` alongside `AppRoute.Onboarding`.
  - Auto-return to onboarding on revocation: only redirect if `currentRoute !is AppRoute.Onboarding && currentRoute != AppRoute.Main(TerminalTab.CONFIG)`.
  - In `MainShell`: if user selects `TerminalTab.APPS`, `TerminalTab.BLOCK`, or `TerminalTab.CODES` while `!areRequiredPermissionsGranted`, route them to `AppRoute.Onboarding`.

---

## 3. Auto-scroll and Visual Focus in `ConfigScreen`

### 3.1 Scroll & Highlight Coordination
- Add `shouldFocusPermissions: Boolean = false` parameter to `ConfigScreen` (derived from whether opened via permissions deep link or missing required permissions).
- Use Compose's `BringIntoViewRequester` or `scrollState.animateScrollTo(cardOffset)` to ensure `PermissionStatusCard` is brought into the viewport automatically upon arriving in `CONFIG`.
- When `shouldFocusPermissions` is true or missing permissions exist, style `PermissionStatusCard` with an accent highlight border (`WattimTheme.colors.accent`).

---

## 4. Testing & Verification

1. **Unit & Service Tests (`T12_PermissionMonitorAndFgsTest`)**:
   - Verify `buildNotification` content text when each individual permission is revoked.
   - Verify `buildNotification` content text when accessibility is granted but disconnected.
   - Verify `buildNotification` multi-item degraded text and `BigTextStyle`.
   - Verify `EXTRA_OPEN_PERMISSIONS` is set in the pending intent when degraded, and absent when active.
2. **Navigation Tests (`T16_NavigationAndOnboardingReachabilityTest`)**:
   - Verify `MainActivity.onNewIntent` and `onCreate` navigate to `TerminalTab.CONFIG` when `EXTRA_OPEN_PERMISSIONS` is passed.
   - Verify `WattimNavHost` permits `AppRoute.Main(TerminalTab.CONFIG)` even when `areRequiredPermissionsGranted == false`.
   - Verify switching away from `CONFIG` to protected tabs like `APPS` when permissions are missing redirects to `AppRoute.Onboarding`.
3. **UI / Compose Tests**:
   - Verify `PermissionStatusCard` displays proper states and actions for missing permissions.
