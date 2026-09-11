# Random Attention Check Count Implementation Plan

> **For Antigravity:** REQUIRED SUB-SKILL: Load executing-plans to implement this plan task-by-task.

**Goal:** Allow users to configure a random number of attention checks within a bounded range `[min, max]` (1..5) for each target application.

**Architecture:** Extend `TargetConfig` and `TargetAppEntity` with `attentionCheckRandomCountEnabled`, `attentionCheckMinCount`, and `attentionCheckMaxCount`. Perform Room database upgrade from v5 to v6 via `MIGRATION_5_6`. Update `RuleEngine` to sample the effective check count during intervention decision making. Update `AttentionCheckCard`, `TargetSettingsScreen`, and `TargetSettingsViewModel` with controls and steppers for the random check range.

**Tech Stack:** Kotlin, Jetpack Compose, Room DB, JUnit4, AndroidX StateFlow.

---

### Task 1: Domain Model (`TargetConfig`) Updates & Unit Tests

**Files:**
- Modify: `domain/src/main/kotlin/io/ronesec/domain/model/TargetConfig.kt`
- Test: `domain/src/test/kotlin/io/ronesec/domain/policy/TargetConfigAttentionCheckTest.kt`

**Step 1: Write failing tests for validation of min/max and random count**
Add tests asserting:
- `attentionCheckRandomCountEnabled` defaults to `false`, `attentionCheckMinCount` to `1`, `attentionCheckMaxCount` to `1`.
- `attentionCheckMinCount` < 1 or > 5 throws `IllegalArgumentException`.
- `attentionCheckMaxCount` < 1 or > 5 throws `IllegalArgumentException`.
- When `attentionCheckRandomCountEnabled == true` and `minCount > maxCount`, throws `IllegalArgumentException`.

**Step 2: Run tests to verify failure**
Run: `./gradlew :domain:test --tests io.ronesec.domain.policy.TargetConfigAttentionCheckTest`

**Step 3: Update `TargetConfig`**
Add fields:
- `val attentionCheckRandomCountEnabled: Boolean = false`
- `val attentionCheckMinCount: Int = 1`
- `val attentionCheckMaxCount: Int = 1`
Add validation in `init { ... }`.

**Step 4: Run tests to verify pass**
Run: `./gradlew :domain:test --tests io.ronesec.domain.policy.TargetConfigAttentionCheckTest`

**Step 5: Commit**
```bash
git add domain/src/main/kotlin/io/ronesec/domain/model/TargetConfig.kt domain/src/test/kotlin/io/ronesec/domain/policy/TargetConfigAttentionCheckTest.kt
git commit -m "feat(domain): add random attention check count fields and validation to TargetConfig"
```

---

### Task 2: Policy Evaluation in `RuleEngine` & Unit Tests

**Files:**
- Modify: `domain/src/main/kotlin/io/ronesec/domain/policy/RuleEngine.kt`
- Test: `domain/src/test/kotlin/io/ronesec/domain/policy/RandomAttentionCheckPolicyTest.kt`

**Step 1: Write failing tests in `RandomAttentionCheckPolicyTest`**
- Test with `randomAttentionCheckCountProvider = { min, _ -> min }` verifies `decision.config.attentionCheckCount == min`.
- Test with `randomAttentionCheckCountProvider = { _, max -> max }` verifies `decision.config.attentionCheckCount == max`.
- Test with `attentionCheckRandomCountEnabled == false` verifies `decision.config.attentionCheckCount == attentionCheckCount`.

**Step 2: Run tests to verify failure**
Run: `./gradlew :domain:test --tests io.ronesec.domain.policy.RandomAttentionCheckPolicyTest`

**Step 3: Implement sampling logic in `RuleEngine`**
- Add `randomAttentionCheckCountProvider: (min: Int, max: Int) -> Int = { min, max -> if (max > min) java.util.concurrent.ThreadLocalRandom.current().nextInt(min, max + 1) else min }` parameter to `RuleEngine.evaluate`.
- Compute `resolvedAttentionCheckCount` in both scheduled and standard intervention decisions.
- Pass `attentionCheckCount = resolvedAttentionCheckCount` into `EffectiveInterventionConfig`.

**Step 4: Run tests to verify pass**
Run: `./gradlew :domain:test --tests io.ronesec.domain.policy.RandomAttentionCheckPolicyTest`

**Step 5: Commit**
```bash
git add domain/src/main/kotlin/io/ronesec/domain/policy/RuleEngine.kt domain/src/test/kotlin/io/ronesec/domain/policy/RandomAttentionCheckPolicyTest.kt
git commit -m "feat(domain): implement random attention check count sampling in RuleEngine"
```

---

### Task 3: Room Migration `MIGRATION_5_6` and Entity Updates

**Files:**
- Modify: `app/src/main/kotlin/io/ronesec/android/data/entity/TargetAppEntity.kt`
- Modify: `app/src/main/kotlin/io/ronesec/android/data/WattimDatabase.kt`
- Modify: `app/src/main/kotlin/io/ronesec/android/data/PolicyCompiler.kt`
- Test: `app/src/test/kotlin/io/ronesec/android/data/Migration5To6Test.kt`

**Step 1: Write failing test in `Migration5To6Test`**
- Create database at version 5, insert target apps with attention check columns.
- Apply `MIGRATION_5_6`.
- Verify new columns exist with defaults (`attentionCheckRandomCountEnabled = 0`, `attentionCheckMinCount = 1`, `attentionCheckMaxCount = 1`).

**Step 2: Run test to verify failure**
Run: `./gradlew :app:testDebugUnitTest --tests io.ronesec.android.data.Migration5To6Test`

**Step 3: Implement Entity, Database migration and `PolicyCompiler`**
- Add `@ColumnInfo` fields to `TargetAppEntity`.
- Bump `WattimDatabase.version = 6` and add `MIGRATION_5_6`.
- Update `PolicyCompiler` mapping to/from `TargetConfig`.

**Step 4: Run tests to verify pass**
Run: `./gradlew :app:testDebugUnitTest --tests io.ronesec.android.data.Migration5To6Test`

**Step 5: Commit**
```bash
git add app/src/main/kotlin/io/ronesec/android/data/ app/src/test/kotlin/io/ronesec/android/data/Migration5To6Test.kt
git commit -m "feat(data): add Room migration 5 to 6 for random attention check count"
```

---

### Task 4: Localization & String Resources

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-ru/strings.xml`

**Step 1: Add new strings**
- `target_attention_check_random_title`
- `target_attention_check_random_desc`
- `target_attention_check_min_count_label`
- `target_attention_check_max_count_label`

**Step 2: Verify resources compile**
Run: `./gradlew :app:processDebugResources`

**Step 3: Commit**
```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-ru/strings.xml
git commit -m "feat(ui): add string resources for random attention checks"
```

---

### Task 5: UI Controls in `AttentionCheckCard`, `TargetSettingsScreen`, & `TargetSettingsViewModel`

**Files:**
- Modify: `app/src/main/kotlin/io/ronesec/android/ui/target/AttentionCheckCard.kt`
- Modify: `app/src/main/kotlin/io/ronesec/android/ui/target/TargetSettingsUiState.kt`
- Modify: `app/src/main/kotlin/io/ronesec/android/ui/target/TargetSettingsViewModel.kt`
- Modify: `app/src/main/kotlin/io/ronesec/android/ui/target/TargetSettingsScreen.kt`
- Modify: `app/src/main/kotlin/io/ronesec/android/ui/WattimNavHost.kt`
- Test: `app/src/test/kotlin/io/ronesec/android/ui/target/TargetSettingsViewModelTest.kt`

**Step 1: Write failing ViewModel test**
- Test toggling `attentionCheckRandomCountEnabled`.
- Test adjusting `attentionCheckMinCount` (clamped to 1..maxCount).
- Test adjusting `attentionCheckMaxCount` (clamped to minCount..5).
- Test that saving draft writes these 3 fields to `PolicyStore`.

**Step 2: Run test to verify failure**
Run: `./gradlew :app:testDebugUnitTest --tests io.ronesec.android.ui.target.TargetSettingsViewModelTest`

**Step 3: Implement UI and ViewModel**
- Add fields to `TargetSettingsDraft`.
- Add handlers `onToggleAttentionCheckRandomCount`, `onAttentionCheckMinCountChange`, `onAttentionCheckMaxCountChange` in `TargetSettingsViewModel`.
- Update `AttentionCheckCard` to render the random toggle and min/max steppers.
- Connect in `TargetSettingsScreen` and `WattimNavHost`.

**Step 4: Run test to verify pass**
Run: `./gradlew :app:testDebugUnitTest --tests io.ronesec.android.ui.target.TargetSettingsViewModelTest`

**Step 5: Commit**
```bash
git add app/src/main/kotlin/io/ronesec/android/ui/ app/src/test/kotlin/io/ronesec/android/ui/
git commit -m "feat(ui): implement random attention check count controls and ViewModel wiring"
```

---

### Task 6: Full Verification & Schema Acceptance

**Step 1: Run all tests in both modules**
Run: `./gradlew test`

**Step 2: Verify git status is clean and all tests pass**
Run: `git status`
