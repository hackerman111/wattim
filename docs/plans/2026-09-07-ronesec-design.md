# ronesec — System Architecture & Design Specification

## 1. Overview & Objective

`ronesec` is a privacy-first, offline-only Android application inspired by One Sec. It intercepts attempts to open user-selected "protected" applications (e.g. social media, entertainment) and presents an immediate, unskippable full-screen mindfulness intervention (a breathing animation with a custom phrase). Only after the breathing cycle completes does the user decide whether to intentionally continue to the target application or return to the home screen.

## 2. Platform & System Contracts

- **Platform**: Android 10+ (API level 29+)
- **Application ID**: `io.ronesec.android`
- **Internet Permission**: None (100% offline, zero network access)
- **Key System Components**:
  - `AccessibilityService`: Ultra-fast foreground window monitoring (`TYPE_WINDOW_STATE_CHANGED`)
  - `SYSTEM_ALERT_WINDOW`: Full-screen hardware-accelerated overlay covering target application
  - `ForegroundService`: Persistent high OOM priority process maintaining runtime state
  - `Room / SQLite`: Local persistence for target apps, schedules, attempts, and configuration

## 3. Performance & Timing SLA

- **Event Detection to Overlay Display**:
  - Target: `< 100 ms`
  - Hard Maximum: `< 200 ms`
- **Breakdown**:
  - `AccessibilityService` event processing: `< 10 ms`
  - `RuleEngine` evaluation: `< 1 ms` (in-memory hot state)
  - `WindowManager` view attachment & frame dispatch: `< 40 ms`
- **Animation Refresh Rate**: Steady `60 FPS` (16.67 ms frame budget) via hardware-accelerated Canvas/Compose.

## 4. Architectural Layers

### 4.1 Domain Layer (Pure Kotlin, Zero Android SDK dependencies)
- `RuleEngine`: Pure decision-making unit.
  ```kotlin
  fun evaluate(packageName: String, now: Instant, state: RuntimeState): Decision
  ```
- `Decision`:
  - `Allow`
  - `Intervention(config: InterventionConfig)`
  - `Block(until: Instant?)`
- `InterventionConfig`:
  - `phrase: String` (1–80 chars, max 3 lines)
  - `animation: AnimationType` (`FILL`)
  - `durationMs: Long` (default 8,000 ms)
  - `reinterventionMs: Long?` (e.g. 300,000 ms = 5 min, or null)
  - `quickReturnGraceMs: Long` (default 60,000 ms)
- `AnimationEngine`:
  - `InterventionAnimation` interface
  - `FillAnimation`: Symmetrical `FastOutSlowIn` curve. First 50% = INHALE (0% -> 100% height), remaining 50% = EXHALE (100% -> 0% height).

### 4.2 System Integration Layer
- `AppMonitorService` (`AccessibilityService`):
  - Listens for `typeWindowStateChanged` and `typeWindowsChanged`.
  - Filters out system UI and launcher transitions.
  - Passes foreground events to `RuleEngine`.
  - Performs `performGlobalAction(GLOBAL_ACTION_HOME)` upon `[ CLOSE ]`.
- `OverlayController`:
  - Maintains `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY`.
  - Full-screen flags: `FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_NO_LIMITS | FLAG_NOT_TOUCH_MODAL | FLAG_HARDWARE_ACCELERATED`.
  - Guarantees 100% touch absorption: target app cannot be clicked through.
  - System back button is consumed/intercepted.
- `FocusForegroundService`:
  - Keeps process foregrounded with a low-importance notification channel.

### 4.3 UI & Presentation Layer (Jetpack Compose Neo-Terminal)
- **Theme Palette**:
  - Background: `#090B0D`
  - Surface: `#101316`
  - Primary text: `#E6E8E9`
  - Secondary text: `#737A80`
  - Borders: `#252A2E` (1px border, 4-6dp radius)
  - Accent: User-selectable (Default: Cyan `#00E5FF`, Green `#00E676`, Orange `#FF9100`, Violet `#D500F9`).
- **Typography**: Monospace (`FontFamily.Monospace`).
- **Screen Navigation**:
  - `[APPS]`: List of protected apps, daily mindfulness stats, quick app adder.
  - `APP SETTINGS`: Per-app phrase editing, duration slider, live animation preview, re-intervention intervals.
  - `BLOCK`: Hard Block (15m/30m/1h/2h focus mode) & recurring scheduled blocks.
  - `STATS`: Historical open attempts, avoided distractions count and avoidance rate.
  - `CONFIG`: Terminal theme accent color, watchdog permission status.

### 4.4 Data Layer (Room)
- Entities:
  - `TargetAppEntity`
  - `OpenAttemptEntity`
  - `AccessGrantEntity`
  - `BlockSessionEntity`
  - `BlockScheduleEntity`
  - `AppSettingEntity`
- Reactive updates via Kotlin Coroutines `Flow`.
- Thread-safe in-memory cache for `RuleEngine` to eliminate disk I/O from the critical latency path.

## 5. Acceptance Criteria Mapping

- **AC-01**: Overlay displays within < 200 ms (target < 100 ms).
- **AC-02**: Target app content is completely non-clickable under overlay.
- **AC-03**: Custom phrase per target application.
- **AC-04**: Animation selection (`Fill` implemented for MVP).
- **AC-05**: 8s duration: 4s rising fill (inhale), 4s receding fill (exhale).
- **AC-06**: `[ CONTINUE ]` button strictly does not exist in layout until animation completes.
- **AC-07**: `[ CLOSE ]` triggers `GLOBAL_ACTION_HOME` and records `ABANDONED`.
- **AC-08**: `[ CONTINUE ]` issues `AccessGrant` and unblocks target app.
- **AC-09**: Re-intervention occurs after grant expires (±1s accuracy).
- **AC-10**: Quick Return Grace avoids repeated interventions for brief app switches (< 60s).
- **AC-11**: Hard Block takes strict precedence over any active AccessGrant.
- **AC-12**: Boot receiver & persistence restore configuration after reboot.
- **AC-13**: Completely offline operation.
- **AC-14**: Zero tracking or telemetry.
