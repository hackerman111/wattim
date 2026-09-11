# Implementation Plan: Untimed Phase Text Removal, Random Duration Mode & Merge with Main

> **For Antigravity:** REQUIRED SUB-SKILL: Load executing-plans to implement this plan task-by-task.

**Goal:** Remove "ВДОХ" / "ВЫДОХ" phase text for untimed breathing animations, add configurable random duration mode with Room v4 migration, and merge with `main` preserving ongoing 2FA development.

**Architecture:** Merge `main` into working branch first. Update `InterventionContent` to conditionally display phase labels based on `revealsRemainingTime` and completion. Add `randomDurationEnabled` and `randomMaxDurationMs` to `TargetConfig` and `TargetAppEntity` with Room `MIGRATION_3_4`. Update `RuleEngine` to sample durations uniformly in seconds and compose with `Backoff`. Add UI controls in `TargetSettingsScreen`. Verify all tests and merge to `main`.

**Tech Stack:** Kotlin, Jetpack Compose, Room (v4), Coroutines, AndroidX Architecture Components.

---

### Task 1: Merge `main` into working branch

**Files:**
- Merge: `main` into `anti-scrol`

**Step 1: Run merge command**
```bash
git merge main
```

**Step 2: Resolve any conflict artifacts and verify status**
```bash
git status
```

**Step 3: Run existing unit tests to verify integration**
```bash
./gradlew testDebugUnitTest
```

---

### Task 2: Untimed Phase Text Removal

**Files:**
- Modify: `app/src/main/kotlin/io/ronesec/android/ui/intervention/InterventionContent.kt`
- Test: `app/src/test/kotlin/io/ronesec/android/ui/intervention/T14_InterventionContentAndTimelineTest.kt`

**Step 1: Write failing test**
In `T14_InterventionContentAndTimelineTest.kt`:
Verify that for `AnimationMode.WAVE` and `AnimationMode.FILL_2`, neither "ВДОХ" nor "ВЫДОХ" is present in `BreathingPhase.INHALE` or `BreathingPhase.EXHALE`, but "ГОТОВО" is present in `BreathingPhase.COMPLETE`.

**Step 2: Run test to verify it fails**
```bash
./gradlew app:testDebugUnitTest --tests "io.ronesec.android.ui.intervention.T14_InterventionContentAndTimelineTest"
```

**Step 3: Implement minimal code**
In `InterventionContent.kt`:
```kotlin
val showPhaseText = config.animation.revealsRemainingTime || progress.phase == BreathingPhase.COMPLETE

if (showPhaseText) {
    val phaseText = when (progress.phase) {
        BreathingPhase.INHALE -> stringResource(R.string.breathing_inhale)
        BreathingPhase.EXHALE -> stringResource(R.string.breathing_exhale)
        BreathingPhase.COMPLETE -> stringResource(R.string.breathing_complete)
    }

    Text(
        text = phaseText,
        style = typography.titleMedium,
        color = colors.accent,
        textAlign = TextAlign.Center
    )
}
```

**Step 4: Run test to verify it passes**
```bash
./gradlew app:testDebugUnitTest --tests "io.ronesec.android.ui.intervention.T14_InterventionContentAndTimelineTest"
```

**Step 5: Commit**
```bash
git add app/src/main/kotlin/io/ronesec/android/ui/intervention/InterventionContent.kt app/src/test/kotlin/io/ronesec/android/ui/intervention/T14_InterventionContentAndTimelineTest.kt
git commit -m "feat: hide inhale and exhale text during untimed animations"
```

---

### Task 3: Domain Model `TargetConfig` and `RuleEngine` Policy

**Files:**
- Modify: `domain/src/main/kotlin/io/ronesec/domain/model/TargetConfig.kt`
- Modify: `domain/src/main/kotlin/io/ronesec/domain/policy/RuleEngine.kt`
- Test: `domain/src/test/kotlin/io/ronesec/domain/policy/RandomDurationPolicyTest.kt`

**Step 1: Write failing unit test**
Create `RandomDurationPolicyTest.kt` verifying:
- Constraint validation on `TargetConfig`: `randomMaxDurationMs` within bounds, and `randomMaxDurationMs >= durationMs` when `randomDurationEnabled == true`.
- RuleEngine evaluation when `randomDurationEnabled == true`: samples duration within $[T_{min}, T_{max}]$.
- RuleEngine evaluation with Backoff: applies exponential growth to the sampled duration.

**Step 2: Run test to verify it fails**
```bash
./gradlew domain:testDebugUnitTest --tests "io.ronesec.domain.policy.RandomDurationPolicyTest"
```

**Step 3: Implement domain logic**
Add properties and validation to `TargetConfig.kt`. Add sampling logic with customizable `randomDurationProvider` to `RuleEngine.kt`.

**Step 4: Run test to verify it passes**
```bash
./gradlew domain:testDebugUnitTest --tests "io.ronesec.domain.policy.RandomDurationPolicyTest"
```

**Step 5: Commit**
```bash
git add domain/src/main/kotlin/io/ronesec/domain/model/TargetConfig.kt domain/src/main/kotlin/io/ronesec/domain/policy/RuleEngine.kt domain/src/test/kotlin/io/ronesec/domain/policy/RandomDurationPolicyTest.kt
git commit -m "feat: implement random duration mode in TargetConfig and RuleEngine"
```

---

### Task 4: Room Database Schema v4 and Migration

**Files:**
- Modify: `app/src/main/kotlin/io/ronesec/android/data/entity/TargetAppEntity.kt`
- Modify: `app/src/main/kotlin/io/ronesec/android/data/WattimDatabase.kt`
- Modify: `app/src/main/kotlin/io/ronesec/android/data/PolicyCompiler.kt`
- Test: `app/src/test/kotlin/io/ronesec/android/data/Migration3To4Test.kt`

**Step 1: Write failing test**
Create `Migration3To4Test.kt` verifying database migration from version 3 to version 4 with new columns `randomDurationEnabled` and `randomMaxDurationMs`.

**Step 2: Run test to verify it fails**
```bash
./gradlew app:testDebugUnitTest --tests "io.ronesec.android.data.Migration3To4Test"
```

**Step 3: Implement database migration and compiler mappings**
- Update `TargetAppEntity` with `@ColumnInfo(defaultValue = "0") val randomDurationEnabled` and `@ColumnInfo(defaultValue = "8000") val randomMaxDurationMs`.
- Add `MIGRATION_3_4` and update `WattimDatabase` version to 4.
- Update `PolicyCompiler` to map the new fields.

**Step 4: Run test to verify it passes**
```bash
./gradlew app:testDebugUnitTest --tests "io.ronesec.android.data.Migration3To4Test"
```

**Step 5: Commit**
```bash
git add app/src/main/kotlin/io/ronesec/android/data/ app/src/test/kotlin/io/ronesec/android/data/
git commit -m "feat: bump database to v4 and implement MIGRATION_3_4"
```

---

### Task 5: Target Settings UI for Random Duration

**Files:**
- Modify: `app/src/main/res/values/strings.xml` & `app/src/main/res/values-ru/strings.xml`
- Modify: `app/src/main/kotlin/io/ronesec/android/ui/target/TargetSettingsUiState.kt`
- Modify: `app/src/main/kotlin/io/ronesec/android/ui/target/TargetSettingsViewModel.kt`
- Modify: `app/src/main/kotlin/io/ronesec/android/ui/target/TargetSettingsScreen.kt`
- Test: `app/src/test/kotlin/io/ronesec/android/ui/target/TargetSettingsViewModelTest.kt`

**Step 1: Write failing test**
Add tests to `TargetSettingsViewModelTest.kt` verifying toggle and max duration editing in the ViewModel draft.

**Step 2: Run test to verify it fails**
```bash
./gradlew app:testDebugUnitTest --tests "io.ronesec.android.ui.target.TargetSettingsViewModelTest"
```

**Step 3: Implement UI and ViewModel changes**
- Add strings for random duration mode.
- Update `TargetSettingsDraft` and `TargetSettingsViewModel` with mutation functions and clamping.
- Add `RandomDurationCard` in `TargetSettingsScreen.kt`.

**Step 4: Run test to verify it passes**
```bash
./gradlew app:testDebugUnitTest --tests "io.ronesec.android.ui.target.TargetSettingsViewModelTest"
```

**Step 5: Commit**
```bash
git add app/src/main/res/ app/src/main/kotlin/io/ronesec/android/ui/target/ app/src/test/kotlin/io/ronesec/android/ui/target/
git commit -m "feat: add random duration settings card and ViewModel integration"
```

---

### Task 6: Full Verification and Merge into `main`

**Step 1: Run full test suite**
```bash
./gradlew testDebugUnitTest
```

**Step 2: Checkout `main` and merge `anti-scrol`**
```bash
git checkout main
git merge anti-scrol
```

**Step 3: Verify build and tests on `main`**
```bash
./gradlew testDebugUnitTest
```
