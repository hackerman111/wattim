# Untimed Random Fill Animation (FILL_2) Implementation Plan

> **For Antigravity:** REQUIRED SUB-SKILL: Load executing-plans to implement this plan task-by-task.

**Goal:** Implement a new untimed breathing intervention animation mode `AnimationMode.FILL_2` ("ЗАЛИВКА 2" / "FILL 2") which hides the countdown timer, draws a horizontal fill rising from the bottom that smoothly wanders up and down between pseudo-random heights, and fills to 100% upon completion.

**Architecture:** Extend domain `AnimationMode` with `FILL_2(revealsRemainingTime = false)`. In `BreathingGeometry`, implement deterministic pseudo-random height generation and smoothstep interpolation between time segments based on `elapsedMs`. Add `drawRandomFill` in `BreathingVisuals`, wire into `BreathingCanvas`, add localization and UI selector buttons, and update unit and Compose tests.

**Tech Stack:** Kotlin, Jetpack Compose, Room (string enum storage), JUnit 4, Compose UI Testing.

---

### Task 1: Domain Model and Localization

**Files:**
- Modify: `domain/src/main/kotlin/io/ronesec/domain/model/TargetConfig.kt:3-10`
- Modify: `app/src/main/res/values/strings.xml:80-86`
- Modify: `app/src/main/res/values-ru/strings.xml:80-86`

**Step 1: Write test or verify domain compilation**
Run: `./gradlew :domain:test`
Expected: BUILD SUCCESSFUL

**Step 2: Update TargetConfig.kt**
Add `FILL_2(revealsRemainingTime = false)` to `AnimationMode`.

**Step 3: Update strings.xml and strings.xml (ru)**
Add `<string name="anim_fill_2">FILL 2</string>` and `<string name="anim_fill_2">ЗАЛИВКА 2</string>`.

**Step 4: Run domain tests**
Run: `./gradlew :domain:test`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**
```bash
git add domain/src/main/kotlin/io/ronesec/domain/model/TargetConfig.kt app/src/main/res/values/strings.xml app/src/main/res/values-ru/strings.xml
git commit -m "feat: add FILL_2 to AnimationMode and string resources"
```

---

### Task 2: Math Model in BreathingGeometry & Canvas Rendering

**Files:**
- Modify: `app/src/main/kotlin/io/ronesec/android/ui/designsystem/BreathingVisuals.kt`
- Test: `app/src/test/kotlin/io/ronesec/android/ui/designsystem/T18_AllThemeVisualStateMatrixTest.kt`

**Step 1: Write failing unit tests in T18_AllThemeVisualStateMatrixTest.kt**
Add tests:
- `fill_2 height stays within bounds during breathing and varies over time`
- `fill_2 height reaches 1_0 on completion`
- `fill_2 height is fixed at 0_5 on reduced motion`

**Step 2: Run test to verify it fails**
Run: `./gradlew :app:testDebugUnitTest --tests "io.ronesec.android.ui.designsystem.T18_AllThemeVisualStateMatrixTest"`
Expected: FAIL (unresolved reference `fill2HeightFraction` / `FILL_2`)

**Step 3: Implement BreathingGeometry math and drawRandomFill in BreathingVisuals.kt**
- Add `FILL_2_SEGMENT_MS = 1_600L`
- Add `fill2TargetHeight(segmentIndex: Long): Float`
- Add `fill2HeightFraction(elapsedMs: Long, progress: Float, reducedMotion: Boolean): Float`
- Add `DrawScope.drawRandomFill(fraction: Float, colors: WattimColors)`
- In `BreathingCanvas`, branch `AnimationMode.FILL_2 -> drawRandomFill(BreathingGeometry.fill2HeightFraction(effectiveElapsed, effectiveProgress, reducedMotion), colors)`

**Step 4: Run tests to verify they pass**
Run: `./gradlew :app:testDebugUnitTest --tests "io.ronesec.android.ui.designsystem.T18_AllThemeVisualStateMatrixTest"`
Expected: PASS

**Step 5: Commit**
```bash
git add app/src/main/kotlin/io/ronesec/android/ui/designsystem/BreathingVisuals.kt app/src/test/kotlin/io/ronesec/android/ui/designsystem/T18_AllThemeVisualStateMatrixTest.kt
git commit -m "feat: implement random fill math and rendering in BreathingVisuals"
```

---

### Task 3: UI Integration (Target Settings, Home Badges, Component Gallery)

**Files:**
- Modify: `app/src/main/kotlin/io/ronesec/android/ui/target/TargetSettingsScreen.kt:310-318`
- Modify: `app/src/main/kotlin/io/ronesec/android/ui/home/ProtectedAppsSection.kt:113-120`
- Modify: `app/src/main/kotlin/io/ronesec/android/ui/designsystem/gallery/ComponentGallery.kt:168-175`

**Step 1: Update UI screens**
- In `TargetSettingsScreen.kt`: add `AnimationMode.FILL_2 to stringResource(R.string.anim_fill_2)` in `AnimationSelectorCard`.
- In `ProtectedAppsSection.kt`: add `AnimationMode.FILL_2 -> stringResource(R.string.anim_fill_2)`.
- In `ComponentGallery.kt`: add `BreathingCanvas(style = AnimationMode.FILL_2, progress = 0.5f, elapsedMs = 1200L)` preview.

**Step 2: Run build to verify compilation**
Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**
```bash
git add app/src/main/kotlin/io/ronesec/android/ui/target/TargetSettingsScreen.kt app/src/main/kotlin/io/ronesec/android/ui/home/ProtectedAppsSection.kt app/src/main/kotlin/io/ronesec/android/ui/designsystem/gallery/ComponentGallery.kt
git commit -m "feat: integrate FILL_2 into target settings, home screen, and gallery"
```

---

### Task 4: UI & ViewModel Tests

**Files:**
- Modify: `app/src/test/kotlin/io/ronesec/android/ui/intervention/T14_InterventionContentAndTimelineTest.kt`
- Modify: `app/src/test/kotlin/io/ronesec/android/ui/target/TargetSettingsViewModelTest.kt`

**Step 1: Add tests**
- In `T14_InterventionContentAndTimelineTest.kt`: add `fill_2 mode hides remaining time while preserving breathing actions`.
- In `TargetSettingsViewModelTest.kt`: verify changing animation to `AnimationMode.FILL_2` updates draft and persists.

**Step 2: Run targeted test suite**
Run: `./gradlew :app:testDebugUnitTest --tests "io.ronesec.android.ui.intervention.T14_InterventionContentAndTimelineTest" --tests "io.ronesec.android.ui.target.TargetSettingsViewModelTest"`
Expected: PASS

**Step 3: Commit**
```bash
git add app/src/test/kotlin/io/ronesec/android/ui/intervention/T14_InterventionContentAndTimelineTest.kt app/src/test/kotlin/io/ronesec/android/ui/target/TargetSettingsViewModelTest.kt
git commit -m "test: add unit and UI tests for FILL_2 animation mode"
```

---

### Task 5: Documentation and Full Verification

**Files:**
- Modify: `README.md`
- Modify: `README_RU.md`

**Step 1: Update documentation**
Add `FILL 2` / `ЗАЛИВКА 2` to the list of breathing animation styles in README and README_RU.

**Step 2: Run all unit and regression tests**
Run: `./gradlew test`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**
```bash
git add README.md README_RU.md
git commit -m "docs: document FILL_2 animation mode in README"
```
