# Schedule Editing, App Intervention Overrides, Exponential Growth, Fast Exit & Edge-to-Edge Implementation Plan

> **For Antigravity:** REQUIRED SUB-SKILL: Load executing-plans to implement this plan task-by-task.

**Goal:** Implement full schedule editing, per-app intervention overrides within schedules, exponential wait time growth based on launches per rolling window with a 10-launch preview calculator, immediate exit button during breathing phases, and display cutout edge-to-edge support for bezel-less screens.

**Architecture:** Extend Room entities (`BlockScheduleEntity`, `TargetAppEntity`, `OpenAttemptDao`) with fallback migration and JSON serialization for schedule overrides; enhance `RuleEngine` to evaluate schedule intervention overrides and calculate exponential duration scaling; update `OverlayController` layout parameters with `LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES` and compose insets; implement schedule edit mode with per-app parameter controls in `BlocksScreen`; add exponential growth controls and preview table in `TargetSettingsScreen`; and provide an immediate "ВЫЙТИ" button during inhale/exhale in `InterventionOverlayView`.

**Tech Stack:** Kotlin 1.9+, Android Jetpack Compose Material 3, Room Database, Kotlinx Coroutines, AndroidX WindowManager & WindowInsets.

---

### Task 1: Domain Models & Room Database Migration
**Files:**
- Modify: `app/src/main/java/io/ronesec/android/domain/model/BlockSchedule.kt`
- Modify: `app/src/main/java/io/ronesec/android/domain/model/InterventionConfig.kt`
- Modify: `app/src/main/java/io/ronesec/android/domain/model/TargetApp.kt`
- Modify: `app/src/main/java/io/ronesec/android/data/local/entity/BlockScheduleEntity.kt`
- Modify: `app/src/main/java/io/ronesec/android/data/local/entity/TargetAppEntity.kt`
- Modify: `app/src/main/java/io/ronesec/android/data/local/dao/OpenAttemptDao.kt`
- Modify: `app/src/main/java/io/ronesec/android/data/local/AppDatabase.kt`
- Modify: `app/src/main/java/io/ronesec/android/data/repository/RonesecRepository.kt`

**Step 1: Write Unit Test for Models & Overrides Serialization**
- Create `app/src/test/java/io/ronesec/android/domain/ScheduleOverrideTest.kt`:
  - Test serialization/deserialization of `ScheduleAppOverride`.
  - Test `TargetApp` defaults for `exponentialGrowthEnabled`, `growthPercent`, `growthPeriodMinutes`.

**Step 2: Run test to verify it fails**
- Command: `./gradlew test --tests io.ronesec.android.domain.ScheduleOverrideTest`

**Step 3: Update Domain Models & Entities**
- In `BlockSchedule.kt`:
  - Add `enum class ScheduleType { HARD_BLOCK, INTERVENTION }`
  - Add `data class ScheduleAppOverride(val durationMs: Long? = null, val reinterventionMs: Long? = null)`
  - Update `BlockSchedule` with `scheduleType: ScheduleType = ScheduleType.HARD_BLOCK` and `appOverrides: Map<String, ScheduleAppOverride> = emptyMap()`.
- In `InterventionConfig.kt`:
  - Add `exponentialGrowthEnabled: Boolean = false`, `growthPercent: Int = 20`, `growthPeriodMinutes: Int = 60`.
- In `BlockScheduleEntity.kt`:
  - Add columns `scheduleType: String = "HARD_BLOCK"` and `appOverridesJson: String? = null`.
  - Provide helpers to convert `appOverridesJson` to/from `Map<String, ScheduleAppOverride>`.
- In `TargetAppEntity.kt`:
  - Add columns `exponentialGrowthEnabled: Boolean = false`, `growthPercent: Int = 20`, `growthPeriodMinutes: Int = 60`.
- In `OpenAttemptDao.kt`:
  - Add `@Query("SELECT COUNT(*) FROM open_attempts WHERE packageName = :packageName AND timestamp >= :sinceTimestamp") suspend fun countAttemptsByPackageSince(packageName: String, sinceTimestamp: Long): Int`.
- In `AppDatabase.kt`:
  - Bump DB version to 2.
- In `RonesecRepository.kt`:
  - Expose `getRecentAttemptsCount(packageName: String, periodMinutes: Int): Int`.

**Step 4: Run test to verify it passes**
- Command: `./gradlew test --tests io.ronesec.android.domain.ScheduleOverrideTest`

**Step 5: Commit**
- Command: `git add . && git commit -m "feat(data): extend BlockSchedule and TargetApp models with overrides and exponential growth"`

---

### Task 2: RuleEngine Logic & Exponential Duration Calculation
**Files:**
- Modify: `app/src/main/java/io/ronesec/android/domain/engine/RuleEngine.kt`
- Modify: `app/src/main/java/io/ronesec/android/service/AppMonitorService.kt`
- Modify: `app/src/test/java/io/ronesec/android/domain/RuleEngineTest.kt`

**Step 1: Write Unit Test for RuleEngine Schedule Overrides & Exponential Growth**
- In `RuleEngineTest.kt`:
  - Test that active `HARD_BLOCK` schedule yields `Decision.Block`.
  - Test that active `INTERVENTION` schedule overrides `durationMs` and `reinterventionMs`.
  - Test that `exponentialGrowthEnabled` calculates $T = T_{\text{base}} \times (1 + p/100)^N$ correctly.

**Step 2: Run test to verify it fails**
- Command: `./gradlew test --tests io.ronesec.android.domain.RuleEngineTest`

**Step 3: Implement RuleEngine Logic**
- In `RuleEngine.kt`:
  - Update `evaluate(packageName, now, state, zoneId, recentAttemptsCount)`:
    - If `activeSchedule` is found:
      - If `scheduleType == ScheduleType.HARD_BLOCK`: return `Decision.Block(until = todayEnd)`.
      - If `scheduleType == ScheduleType.INTERVENTION`: check active `AccessGrant` and `quickReturnGraceMs`. If neither allows access, build `InterventionConfig` with overrides from `activeSchedule.appOverrides[packageName]`.
    - Apply exponential growth calculation if `exponentialGrowthEnabled` is true and `recentAttemptsCount > 0`.
- In `AppMonitorService.kt`:
  - Before calling `ruleEngine.evaluate(...)`, query `recentAttemptsCount` via `repository.getRecentAttemptsCount(rawPackage, target.intervention.growthPeriodMinutes)`.

**Step 4: Run test to verify it passes**
- Command: `./gradlew test --tests io.ronesec.android.domain.RuleEngineTest`

**Step 5: Commit**
- Command: `git add . && git commit -m "feat(engine): implement schedule intervention overrides and exponential backoff in RuleEngine"`

---

### Task 3: Fast Exit Button & True Edge-to-Edge Bezel-less Display
**Files:**
- Modify: `app/src/main/java/io/ronesec/android/overlay/OverlayController.kt`
- Modify: `app/src/main/java/io/ronesec/android/overlay/InterventionOverlayView.kt`

**Step 1: Write Unit/UI Verification for Fast Exit State**
- Verify that `onClose` callback can be triggered during `AnimationPhase.INHALE` or `AnimationPhase.EXHALE`.

**Step 2: Update OverlayController with Display Cutout Mode**
- In `OverlayController.kt`:
  - In `createLayoutParams()`:
    - Add `WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS`.
    - If `Build.VERSION.SDK_INT >= Build.VERSION_CODES.P`:
      `layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES`.
    - On `ComposeView`: set window system UI / insets flags to make system status bar and nav bar fully transparent and drawing under cutouts.

**Step 3: Update InterventionOverlayContent**
- In `InterventionOverlayView.kt`:
  - During `AnimationPhase.INHALE` and `AnimationPhase.EXHALE`: show full-width secondary button `"ВЫЙТИ"`, calling `onClose()`.
  - During `AnimationPhase.COMPLETE`: show `"ЗАКРЫТЬ"` and `"ПРОДОЛЖИТЬ"` buttons side-by-side.
  - Apply `statusBarsPadding()` and `navigationBarsPadding()` to the inner UI Column, while keeping the background Canvas unpadded (`fillMaxSize()`) so breath animations completely flood the bezel-less screen edges.

**Step 4: Verify with build**
- Command: `./gradlew compileDebugKotlin`

**Step 5: Commit**
- Command: `git add . && git commit -m "feat(overlay): add immediate exit button during breathing and display cutout edge-to-edge support"`

---

### Task 4: TargetSettingsScreen Exponential Growth & 10-Step Calculator
**Files:**
- Modify: `app/src/main/java/io/ronesec/android/ui/screens/TargetSettingsScreen.kt`

**Step 1: Implement UI Card for Exponential Growth**
- In `TargetSettingsScreen.kt`:
  - Add state variables: `exponentialGrowthEnabled`, `growthPercent`, `growthPeriodMinutes`.
  - Add "ЭКСПОНЕНЦИАЛЬНЫЙ РОСТ" Card:
    - Toggle `ВКЛ / ВЫКЛ`.
    - Percentage stepper (`-5%`, `-1%`, `+1%`, `+5%`) and preset chips (`10%`, `20%`, `30%`, `50%`).
    - Period selector chips (`15м`, `30м`, `1ч`, `2ч`, `24ч`).
    - Interactive 10-launch precalculation table:
      - Shows launches 1 to 10 with formula $T_k = T_{\text{base}} \times (1 + p/100)^k$ formatted in seconds (e.g. `1-е открытие: 12.0с`, `5-е открытие: 24.9с`, `10-е открытие: 61.9с`).
  - Ensure saving in `onSave(target.copy(...))`.

**Step 2: Verify with build**
- Command: `./gradlew compileDebugKotlin`

**Step 3: Commit**
- Command: `git add . && git commit -m "feat(ui): add exponential growth configuration and 10-step preview calculator to TargetSettingsScreen"`

---

### Task 5: BlocksScreen Schedule Editing & Per-App Intervention Overrides
**Files:**
- Modify: `app/src/main/java/io/ronesec/android/ui/screens/BlocksScreen.kt`

**Step 1: Enhance Schedule Card with "РЕДАКТИРОВАТЬ" Button**
- Add "РЕДАКТИРОВАТЬ" badge/button on each schedule card in `BlocksScreen`.
- Clicking the card or button opens `ScheduleEditorDialog` with the target `BlockSchedule`.

**Step 2: Upgrade Dialog to `ScheduleEditorDialog`**
- Support editing existing schedule:
  - Header: `"РЕДАКТИРОВАНИЕ РАСПИСАНИЯ"` vs `"НОВОЕ РАСПИСАНИЕ БЛОКИРОВКИ"`.
  - Schedule Type Selector: `"ПОЛНАЯ БЛОКИРОВКА"` vs `"ОСОБЫЕ ИНТЕРВЕНЦИИ"`.
  - If `"ОСОБЫЕ ИНТЕРВЕНЦИИ"` is selected:
    - Under each selected app, render custom override controls:
      - Pause Duration: stepper (`-5с`, `-1с`, `+1с`, `+5с`) and input field (e.g. 15s).
      - Repeat Interval: chips (`Выкл`, `1м`, `3м`, `5м`, `10м`).
  - Pre-populate all fields from existing schedule if editing.
  - On save: updates existing schedule preserving `id`.

**Step 3: Verify with build & unit tests**
- Command: `./gradlew test`

**Step 4: Commit**
- Command: `git add . && git commit -m "feat(ui): add schedule editing dialog and per-app intervention overrides in BlocksScreen"`

---

### Task 6: Full Verification & Release APK Build
**Files:**
- All modified files

**Step 1: Run Full Test Suite**
- Command: `./gradlew test`

**Step 2: Build Release APK**
- Command: `./gradlew assembleRelease`

**Step 3: Verify APK Artifact**
- Command: `ls -lh app/build/outputs/apk/release/app-release-unsigned.apk`
- Commit final changes and verification notes.
