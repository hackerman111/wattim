# Schedule Minutes Implementation Plan

> **For Antigravity:** REQUIRED SUB-SKILL: Load executing-plans to implement this plan task-by-task.

**Goal:** Allow users to specify both hours and minutes in scheduled block creation with text fields and minute step controls (±5m, ±1m).

**Architecture:** Update `AddScheduleDialog` in `BlocksScreen.kt` to introduce dedicated text inputs for hours and minutes with focus navigation, paired with hour steppers (±1h) and minute steppers (±5m, ±1m). Synchronize with existing `startHour`, `startMinute`, `endHour`, `endMinute` state variables which already wire into `LocalTime.of(...)` and Room database.

**Tech Stack:** Jetpack Compose, Kotlin, Material3, Room, AndroidX.

---

### Task 1: Write Unit Test for Schedule Time Formatting and Range Behavior

**Files:**
- Test: `app/src/test/java/io/ronesec/android/domain/util/ScheduleTimeTest.kt`

**Step 1: Write the unit test**
Test schedule hour/minute bounds wrapping, step calculations (±1h, ±5m, ±1m), and formatting strings `HH:mm`.

**Step 2: Run test to verify it passes**
Run: `./gradlew testDebugUnitTest --tests "io.ronesec.android.domain.util.ScheduleTimeTest"`

---

### Task 2: Implement Time Input Fields and Steppers in `AddScheduleDialog`

**Files:**
- Modify: `app/src/main/java/io/ronesec/android/ui/screens/BlocksScreen.kt:390-540`

**Step 1: Implement text inputs and steppers**
- Add string states `startHourInput`, `startMinuteInput`, `endHourInput`, `endMinuteInput`.
- Wire `LocalFocusManager` and `LocalSoftwareKeyboardController`.
- Implement stacked cards for НАЧАЛО and КОНЕЦ with:
  - Text input for hours (0..23) with `[-1ч]` `[+1ч]`
  - Text input for minutes (0..59) with `[-5м]` `[-1м]` `[+1м]` `[+5м]`
  - KeyboardActions onDone and onNext, plus onKeyEvent for Key.Enter.
- Update quick range presets to update both numeric state and text field strings.

**Step 2: Run all unit tests**
Run: `./gradlew test`
Expected: BUILD SUCCESSFUL with 0 errors.

**Step 3: Assemble release build and verify APK**
Run: `./gradlew assembleRelease && cp app/build/outputs/apk/release/app-release.apk wattim.apk`
Expected: APK ~2.4 MB generated.
