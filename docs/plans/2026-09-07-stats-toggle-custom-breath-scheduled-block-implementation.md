# Stats Toggle, Custom Breath Duration & Scheduled Blocks Implementation Plan

> **For Antigravity:** REQUIRED SUB-SKILL: Load executing-plans to implement this plan task-by-task.

**Goal:** Allow users to toggle saved-time statistics on the intervention overlay, enter custom breath/inhale duration, and configure hard blocking schedules for apps.

**Architecture:** 
- Settings storage via Room DB `settings` table for `show_saved_time_stats`.
- `AppMonitorService` checks `show_saved_time_stats` before passing `savedTimeText` to overlay.
- `TargetSettingsScreen` adds direct seconds input, steppers, and presets for `durationMs`.
- `BlocksScreen` adds a terminal-styled dialog to create and edit `BlockSchedule` with time ranges, day selection, and app selection, persisting to `BlockDao`.

**Tech Stack:** Kotlin, Jetpack Compose, Room DB, Coroutines, StateFlow.

---

### Task 1: Toggle for Saved Time Statistics on Overlay

**Files:**
- Modify: `app/src/main/java/io/ronesec/android/ui/viewmodel/MainViewModel.kt`
- Modify: `app/src/main/java/io/ronesec/android/ui/screens/ConfigScreen.kt`
- Modify: `app/src/main/java/io/ronesec/android/service/AppMonitorService.kt`
- Test: `app/src/test/java/io/ronesec/android/domain/SettingsFlowTest.kt`

**Step 1: Write the test**
Verify setting `show_saved_time_stats` defaults to `"true"` and can be updated to `"false"`.

**Step 2: Run test to verify it fails**
Run: `./gradlew testDebugUnitTest --tests io.ronesec.android.domain.SettingsFlowTest`

**Step 3: Implement settings flow in MainViewModel and ConfigScreen**
- Expose `showSavedTimeOnOverlay: StateFlow<Boolean>` in `MainViewModel`.
- Add function `toggleShowSavedTimeOnOverlay(enabled: Boolean)`.
- In `ConfigScreen.kt`, add a card with `TerminalBadge` (ВКЛ / ВЫКЛ) to toggle `showSavedTimeOnOverlay`.
- In `AppMonitorService.kt`, query `repository.getSetting("show_saved_time_stats")` (default `true`), and only compute & pass `savedText` if enabled.

**Step 4: Run test to verify it passes**
Run: `./gradlew testDebugUnitTest --tests io.ronesec.android.domain.SettingsFlowTest`

---

### Task 2: Custom Breath Duration in TargetSettingsScreen

**Files:**
- Modify: `app/src/main/java/io/ronesec/android/ui/screens/TargetSettingsScreen.kt`
- Test: `app/src/test/java/io/ronesec/android/acceptance/AcceptanceCriteriaTest.kt`

**Step 1: Implement custom breath duration UI in TargetSettingsScreen**
- Add state `var durationInput by remember { mutableStateOf((target.intervention.durationMs / 1000L).toString()) }`.
- In the "ДЛИТЕЛЬНОСТЬ ПАУЗЫ" card:
  - Display current seconds with a `BasicTextField` with `fillMaxWidth()` and hint text.
  - Add steppers: `-5с`, `-1с`, `+1с`, `+5с`.
  - Add quick preset badges: `3с`, `5с`, `8с`, `10с`, `15с`, `20с`, `30с`.
  - In `onSave`, parse `durationInput.toLongOrNull() ?: 8L` coerced in `1L..120L`.

**Step 2: Run tests to verify**
Run: `./gradlew test`

---

### Task 3: Scheduled Blocking Management in BlocksScreen

**Files:**
- Modify: `app/src/main/java/io/ronesec/android/ui/screens/BlocksScreen.kt`
- Modify: `app/src/main/java/io/ronesec/android/ui/MainActivity.kt`
- Test: `app/src/test/java/io/ronesec/android/domain/RuleEngineTest.kt`

**Step 1: Write test for schedule evaluation in RuleEngineTest**
Test overnight schedules (e.g. 23:00 to 07:00) and daytime schedules (e.g. 09:00 to 18:00) with specific days of week.

**Step 2: Run test to verify it passes**
Run: `./gradlew testDebugUnitTest --tests io.ronesec.android.domain.RuleEngineTest`

**Step 3: Implement Schedule Creation Dialog and Schedule Cards in BlocksScreen**
- Add dialog state `showAddScheduleDialog`.
- Dialog features:
  - Text input for schedule name (e.g. "Работа", "Ночь").
  - Start time and End time inputs (hour/minute pickers or text inputs `09:00` -> `18:00`).
  - Days of week selector chips: Пн, Вт, Ср, Чт, Пт, Сб, Вс + quick chips ("Будни", "Выходные", "Все дни").
  - Apps picker: selectable list of target apps with checkboxes + "Выбрать все".
  - "СОЗДАТЬ" and "ОТМЕНА" buttons.
- In the schedule list:
  - Card showing schedule name, days, hours, and number of apps.
  - Active toggle badge (`АКТИВНО` / `ВЫКЛ`) calling `onSaveSchedule(schedule.copy(enabled = !schedule.enabled))`.
  - Delete button calling `onDeleteSchedule(schedule.id)`.

**Step 4: Run tests**
Run: `./gradlew test`

---

### Task 4: Release Build and Final Verification

**Files:**
- Build: `./gradlew assembleRelease`
- Copy: `wattim.apk`

**Step 1: Verify all 51+ Gradle tasks pass**
Run: `./gradlew test`

**Step 2: Build release APK**
Run: `./gradlew assembleRelease`
Verify size is ~2.3 MB.
