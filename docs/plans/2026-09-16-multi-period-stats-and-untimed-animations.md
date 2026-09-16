# Multi-Period Statistics & Untimed Animations Implementation Plan

> **For Antigravity:** REQUIRED SUB-SKILL: Load executing-plans to implement this plan task-by-task.

**Goal:** Implement multi-period detailed statistics collection (Day, Week, Month, All Time) with daily activity charts, and rework all breathing animations into an untimed, non-countdown style with a 2-column preview selector grid.

**Architecture:** 
- Domain: Update `AnimationMode` (`FILL`, `PULSE`, `WAVE`, `ORBIT`, `RIPPLE` with `revealsRemainingTime = false`) and add `StatsPeriod`.
- Database & Repository: Enhance `StatisticsDao` and `StatisticsStore` to support arbitrary date windows, local-timezone daily aggregation, and period summaries.
- Visuals: Rework `BreathingVisuals.kt` and `BreathingGeometry` to render smooth continuous canvas animations without countdown cues.
- UI: Implement multi-period selector chips and daily activity bars in `StatsScreen.kt`; replace single-column button stack with a 2-column interactive grid of live preview cards in `TargetSettingsScreen.kt`.

**Tech Stack:** Kotlin, Jetpack Compose, Canvas DrawScope, Room Database, StateFlow, Coroutines, JUnit4.

---

### Task 1: Domain Models & Backwards Compatibility
Update `AnimationMode`, add `StatsPeriod`, and handle backwards compatibility in `PolicyCompiler`.

**Files:**
- Modify: `domain/src/main/kotlin/io/ronesec/domain/model/TargetConfig.kt`
- Create: `domain/src/main/kotlin/io/ronesec/domain/model/StatsPeriod.kt`
- Modify: `app/src/main/kotlin/io/ronesec/android/data/PolicyCompiler.kt`
- Test: `app/src/test/kotlin/io/ronesec/android/data/PolicyCompilerAnimationTest.kt`

**Step 1: Write the failing test for legacy animation deserialization**
Create `app/src/test/kotlin/io/ronesec/android/data/PolicyCompilerAnimationTest.kt`:
Verify that `"FILL_2"` decodes to `AnimationMode.FILL`, `"CIRCLE"` decodes to `AnimationMode.ORBIT`, and all modes have `revealsRemainingTime = false`.

**Step 2: Run test to verify it fails**
Run: `./gradlew testDebugUnitTest --tests "io.ronesec.android.data.PolicyCompilerAnimationTest"`
Expected: FAIL (missing `ORBIT`, `RIPPLE`, `StatsPeriod`).

**Step 3: Update domain models and compiler**
- In `TargetConfig.kt`: Set `AnimationMode` values: `FILL`, `PULSE`, `WAVE`, `ORBIT`, `RIPPLE`, with `revealsRemainingTime = false` default.
- In `StatsPeriod.kt`: Create enum `StatsPeriod { TODAY, WEEK, MONTH, ALL_TIME }`.
- In `PolicyCompiler.kt`: Map `"FILL_2"` to `AnimationMode.FILL`, `"CIRCLE"` to `AnimationMode.ORBIT`.

**Step 4: Run test to verify it passes**
Run: `./gradlew testDebugUnitTest --tests "io.ronesec.android.data.PolicyCompilerAnimationTest"`
Expected: PASS.

**Step 5: Commit**
```bash
git add domain/src/main/kotlin/io/ronesec/domain/model/TargetConfig.kt domain/src/main/kotlin/io/ronesec/domain/model/StatsPeriod.kt app/src/main/kotlin/io/ronesec/android/data/PolicyCompiler.kt app/src/test/kotlin/io/ronesec/android/data/PolicyCompilerAnimationTest.kt
git commit -m "feat(domain): define untimed AnimationMode and StatsPeriod with legacy compat"
```

---

### Task 2: Multi-Period Statistics Engine & DAO
Enhance `StatisticsDao` and `StatisticsStore` to compute period summaries, app breakdowns, and timezone-aware daily activity points.

**Files:**
- Modify: `app/src/main/kotlin/io/ronesec/android/data/dao/StatisticsDao.kt`
- Modify: `app/src/main/kotlin/io/ronesec/android/data/StatisticsStore.kt`
- Test: `app/src/test/kotlin/io/ronesec/android/data/T20_StatisticsStoreTest.kt`

**Step 1: Write tests for multi-period and daily trend calculation**
Add tests in `T20_StatisticsStoreTest.kt`:
- Verify `getPeriodSummary` for `WEEK` and `MONTH` correctly aggregates attempts and avoided percentages.
- Verify `getDailyActivity` returns chronological daily points with correct `totalOpenings` and `totalClosed` mapped to local dates.

**Step 2: Run test to verify it fails**
Run: `./gradlew testDebugUnitTest --tests "io.ronesec.android.data.T20_StatisticsStoreTest"`
Expected: FAIL (unresolved references).

**Step 3: Implement data access & repository logic**
- In `StatisticsDao.kt`: Add `getAttemptEvents(startEpochMs: Long, endEpochMs: Long): List<AttemptEventRow>`.
- In `StatisticsStore.kt`:
  - Implement `getPeriodBounds(period: StatsPeriod, now: Instant, zoneId: ZoneId): Pair<Long, Long>`.
  - Implement `getPeriodSummary(period, now, zoneId)`.
  - Implement `getPerAppStats(period, now, zoneId)`.
  - Implement `getDailyActivity(period, now, zoneId)`.

**Step 4: Run test to verify it passes**
Run: `./gradlew testDebugUnitTest --tests "io.ronesec.android.data.T20_StatisticsStoreTest"`
Expected: PASS.

**Step 5: Commit**
```bash
git add app/src/main/kotlin/io/ronesec/android/data/dao/StatisticsDao.kt app/src/main/kotlin/io/ronesec/android/data/StatisticsStore.kt app/src/test/kotlin/io/ronesec/android/data/T20_StatisticsStoreTest.kt
git commit -m "feat(stats): add multi-period summaries and daily activity calculation"
```

---

### Task 3: Stats UI State & StatsViewModel
Update `StatsUiState` and `StatsViewModel` to support switching periods and loading trend data.

**Files:**
- Modify: `app/src/main/kotlin/io/ronesec/android/ui/stats/StatsUiState.kt`
- Modify: `app/src/main/kotlin/io/ronesec/android/ui/stats/StatsViewModel.kt`
- Test: `app/src/test/kotlin/io/ronesec/android/ui/stats/StatsViewModelTest.kt`

**Step 1: Write tests for period selection in ViewModel**
In `StatsViewModelTest.kt`:
- Verify initial period is `StatsPeriod.TODAY`.
- Verify calling `selectPeriod(StatsPeriod.WEEK)` updates state with week's data and daily activity.
- Verify duration formatting respects selected period.

**Step 2: Run test to verify it fails**
Run: `./gradlew testDebugUnitTest --tests "io.ronesec.android.ui.stats.StatsViewModelTest"`
Expected: FAIL.

**Step 3: Update StatsUiState and StatsViewModel**
- In `StatsUiState.kt`: Add `selectedPeriod: StatsPeriod`, `dailyActivity: List<DailyActivityPoint>`, `periodSavedDuration: String`, `periodTotalAttempts: Int`, `periodClosedCount: Int`, `periodAvoidedPercent: Int`.
- In `StatsViewModel.kt`: Add `onPeriodSelected(period: StatsPeriod)`, load period-specific stats and daily trend.

**Step 4: Run test to verify it passes**
Run: `./gradlew testDebugUnitTest --tests "io.ronesec.android.ui.stats.StatsViewModelTest"`
Expected: PASS.

**Step 5: Commit**
```bash
git add app/src/main/kotlin/io/ronesec/android/ui/stats/StatsUiState.kt app/src/main/kotlin/io/ronesec/android/ui/stats/StatsViewModel.kt app/src/test/kotlin/io/ronesec/android/ui/stats/StatsViewModelTest.kt
git commit -m "feat(stats): wire StatsPeriod selection and daily activity into StatsViewModel"
```

---

### Task 4: Stats Screen UI with Period Chips & Daily Activity Chart
Update `StatsScreen.kt` with a segmented period selector and a daily activity bar chart.

**Files:**
- Create: `app/src/main/kotlin/io/ronesec/android/ui/stats/DailyTrendCard.kt`
- Create: `app/src/main/kotlin/io/ronesec/android/ui/stats/PeriodSelectorRow.kt`
- Modify: `app/src/main/kotlin/io/ronesec/android/ui/stats/StatsScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-ru/strings.xml`
- Test: `app/src/test/kotlin/io/ronesec/android/ui/T16_StatsAndConfigIntegrationTest.kt`

**Step 1: Write integration test for period switching on StatsScreen**
In `T16_StatsAndConfigIntegrationTest.kt`:
- Verify all period labels render.
- Verify period selection updates the table and summary.

**Step 2: Run test to verify it fails**
Run: `./gradlew testDebugUnitTest --tests "io.ronesec.android.ui.T16_StatsAndConfigIntegrationTest"`
Expected: FAIL.

**Step 3: Implement period chips, trend chart, and string resources**
- Add string resources in `strings.xml` and `strings.xml (ru)` for periods and chart labels.
- Create `PeriodSelectorRow.kt` using `TerminalButton` chips.
- Create `DailyTrendCard.kt` rendering minimal canvas bars for openings and prevented attempts.
- Integrate into `StatsScreen.kt`.

**Step 4: Run test to verify it passes**
Run: `./gradlew testDebugUnitTest --tests "io.ronesec.android.ui.T16_StatsAndConfigIntegrationTest"`
Expected: PASS.

**Step 5: Commit**
```bash
git add app/src/main/kotlin/io/ronesec/android/ui/stats/ app/src/main/res/values/strings.xml app/src/main/res/values-ru/strings.xml app/src/test/kotlin/io/ronesec/android/ui/T16_StatsAndConfigIntegrationTest.kt
git commit -m "feat(ui): add PeriodSelectorRow and DailyTrendCard to StatsScreen"
```

---

### Task 5: Rework Breathing Geometry & Visuals (Untimed Style)
Implement the 5 beautiful untimed animations in `BreathingVisuals.kt`:
1. `FILL`: smooth wandering fill (formerly FILL_2).
2. `PULSE`: harmonic sine breathing orb with double halo.
3. `WAVE`: multi-layered fluid waves.
4. `ORBIT`: celestial constellation of orbiting nodes.
5. `RIPPLE`: Zen concentric expanding water ripples.

**Files:**
- Modify: `app/src/main/kotlin/io/ronesec/android/ui/designsystem/BreathingVisuals.kt`
- Test: `app/src/test/kotlin/io/ronesec/android/ui/designsystem/BreathingVisualsTest.kt`
- Modify: `app/src/test/kotlin/io/ronesec/android/ui/designsystem/T18_AllThemeVisualStateMatrixTest.kt`

**Step 1: Write unit tests for geometry and animation functions**
In `BreathingVisualsTest.kt`:
- Test height fractions for `FILL` (wandering $[0.15, 0.85]$, reaches $1.0$ when progress $\ge 1.0$).
- Test pulse radius and alpha calculation across elapsed time.
- Test wave geometry and ripple expansion.
- Test `reducedMotion = true` returns stationary values for all 5 styles.

**Step 2: Run test to verify it fails**
Run: `./gradlew testDebugUnitTest --tests "io.ronesec.android.ui.designsystem.BreathingVisualsTest"`
Expected: FAIL.

**Step 3: Implement new BreathingGeometry and canvas drawing functions**
- Update `BreathingGeometry` with mathematics for `PULSE`, `WAVE`, `ORBIT`, `RIPPLE`, and `FILL`.
- Implement `drawPulse`, `drawCyclicWave`, `drawOrbit`, `drawZenRipple`, `drawFill`.
- Connect all branches in `BreathingCanvas`.

**Step 4: Run test to verify it passes**
Run: `./gradlew testDebugUnitTest --tests "io.ronesec.android.ui.designsystem.BreathingVisualsTest"`
Run: `./gradlew testDebugUnitTest --tests "io.ronesec.android.ui.designsystem.T18_AllThemeVisualStateMatrixTest"`
Expected: PASS.

**Step 5: Commit**
```bash
git add app/src/main/kotlin/io/ronesec/android/ui/designsystem/BreathingVisuals.kt app/src/test/kotlin/io/ronesec/android/ui/designsystem/BreathingVisualsTest.kt app/src/test/kotlin/io/ronesec/android/ui/designsystem/T18_AllThemeVisualStateMatrixTest.kt
git commit -m "feat(visuals): rework breathing animations to untimed organic style"
```

---

### Task 6: 2-Column Animation Selector Grid with Live Previews
Replace single-column buttons with a 2-column grid of interactive preview cards in `TargetSettingsScreen.kt`.

**Files:**
- Modify: `app/src/main/kotlin/io/ronesec/android/ui/target/TargetSettingsScreen.kt`
- Modify: `app/src/main/kotlin/io/ronesec/android/ui/home/ProtectedAppsSection.kt`
- Modify: `app/src/main/kotlin/io/ronesec/android/ui/designsystem/gallery/ComponentGallery.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-ru/strings.xml`
- Test: `app/src/test/kotlin/io/ronesec/android/ui/target/TargetSettingsViewModelTest.kt`
- Test: `app/src/test/kotlin/io/ronesec/android/ui/intervention/T14_InterventionContentAndTimelineTest.kt`

**Step 1: Write UI tests for 2-column animation selection**
Update `TargetSettingsViewModelTest.kt` and `T14_InterventionContentAndTimelineTest.kt` to test selection and rendering of each of the 5 modes.

**Step 2: Run tests to verify they pass/fail accordingly**
Run: `./gradlew testDebugUnitTest --tests "io.ronesec.android.ui.target.TargetSettingsViewModelTest"`
Expected: FAIL or compilation errors on legacy modes.

**Step 3: Implement 2-column AnimationSelectorCard and wire up preview boxes**
- Update strings (`anim_fill`: "ЗАЛИВКА", `anim_pulse`: "ПУЛЬС", `anim_wave`: "ВОЛНА", `anim_orbit`: "ОРБИТА", `anim_ripple`: "ДЗЕН").
- In `TargetSettingsScreen.kt`: implement 2-column grid (`AnimationCard` with mini `BreathingCanvas` box + title + selection border).
- Update `ProtectedAppsSection.kt` and `ComponentGallery.kt`.

**Step 4: Run test to verify it passes**
Run: `./gradlew testDebugUnitTest`
Expected: ALL PASS.

**Step 5: Commit**
```bash
git add app/src/main/kotlin/io/ronesec/android/ui/target/TargetSettingsScreen.kt app/src/main/kotlin/io/ronesec/android/ui/home/ProtectedAppsSection.kt app/src/main/kotlin/io/ronesec/android/ui/designsystem/gallery/ComponentGallery.kt app/src/main/res/values/strings.xml app/src/main/res/values-ru/strings.xml app/src/test/
git commit -m "feat(ui): implement 2-column grid animation selector with live previews"
```

---

### Task 7: Full Verification & Regression Testing
Run all unit, integration, and lint checks across domain and app modules.

**Files:**
- All modified files

**Step 1: Run full test suite**
Run: `./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL with 0 failures.

**Step 2: Run Android lint and compile verification**
Run: `./gradlew compileDebugKotlin compileDebugUnitTestKotlin`
Expected: BUILD SUCCESSFUL.

**Step 3: Verification commit if any fixes were needed**
```bash
git commit --allow-empty -m "chore: final verification for multi-period stats and untimed animations"
```
