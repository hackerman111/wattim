# Annoying Unlock and Enhanced Attention Check Failure Visibility Implementation Plan

> **For Antigravity:** REQUIRED SUB-SKILL: Load executing-plans to implement this plan task-by-task.

**Goal:** Add an "Annoying Unlock" mode where attention check typing randomly drops digits according to a user-configured chance (1–100%), and enhance failure visibility with a persistent 1-second red frame and error message upon timeouts and invalid codes.

**Architecture:**
- Domain: Add `annoyingUnlockEnabled` and `annoyingUnlockChancePercent` to `TargetConfig` and `EffectiveInterventionConfig`. In `CodeChallengeReducer`, introduce a 1-second `isExpired` holding phase upon timeout (`TemporalBoundaryReached`) before resetting breathing.
- Persistence: Room database schema bump to v7 with `MIGRATION_6_7`.
- UI: Implement digit-drop probability logic and 1-second timeout/error display in `AttentionCheckContent.kt`. Add settings controls (toggle, 1–100% input field, `-5%` / `+5%` stepper buttons) in `AttentionCheckCard.kt`.

**Tech Stack:** Kotlin 1.9 / 2.0, Jetpack Compose, Room (SQLite), Coroutines / Flow, Robolectric, JUnit4.

---

### Task 1: Domain Models & Policy Engine Updates

**Files:**
- Modify: `domain/src/main/kotlin/io/ronesec/domain/model/TargetConfig.kt`
- Modify: `domain/src/main/kotlin/io/ronesec/domain/policy/EffectiveInterventionConfig.kt`
- Modify: `domain/src/main/kotlin/io/ronesec/domain/policy/RuleEngine.kt`
- Test: `domain/src/test/kotlin/io/ronesec/domain/policy/TargetConfigAttentionCheckTest.kt`

**Step 1: Write the failing tests**
In `TargetConfigAttentionCheckTest.kt`, add tests verifying `annoyingUnlockChancePercent` validation in `TargetConfig` (valid range 1..100, throws `IllegalArgumentException` on 0 and 101).

**Step 2: Run test to verify it fails**
Run `./gradlew :domain:test --tests "io.ronesec.domain.policy.TargetConfigAttentionCheckTest"`
Expected: Compilation failure due to missing properties.

**Step 3: Implement minimal code in domain**
- Add `annoyingUnlockEnabled: Boolean = false` and `annoyingUnlockChancePercent: Int = 20` to `TargetConfig` with `init` validation and companion constants `MIN_ANNOYING_UNLOCK_CHANCE_PERCENT = 1`, `MAX_ANNOYING_UNLOCK_CHANCE_PERCENT = 100`, `DEFAULT_ANNOYING_UNLOCK_CHANCE_PERCENT = 20`.
- Add the fields to `EffectiveInterventionConfig`.
- Map the fields in `RuleEngine.kt` (both branches returning `Decision.Intervention`).

**Step 4: Run tests to verify they pass**
Run `./gradlew :domain:test --tests "io.ronesec.domain.policy.TargetConfigAttentionCheckTest"`
Expected: PASS.

**Step 5: Commit**
`git commit -m "feat(domain): add annoying unlock configuration to TargetConfig and RuleEngine"`

---

### Task 2: State Machine 1-Second Expiration Hold

**Files:**
- Modify: `domain/src/main/kotlin/io/ronesec/domain/protection/ProtectionState.kt`
- Modify: `domain/src/main/kotlin/io/ronesec/domain/protection/SessionCodes.kt`
- Modify: `domain/src/main/kotlin/io/ronesec/domain/protection/CodeChallengeReducer.kt`
- Test: `domain/src/test/kotlin/io/ronesec/domain/protection/AttentionCheckReducerTest.kt`

**Step 1: Write the failing test**
In `AttentionCheckReducerTest.kt`, add a test for timeout expiration:
- When `TemporalBoundaryReached` is received at deadline:
  - First step: substate becomes `AttentionCheck(isExpired = true)` with a 1000ms boundary scheduled.
  - Submitting code during `isExpired` is ignored.
  - Second step: when next boundary is reached 1000ms later, substate transitions to `Breathing` (startElapsedMs reset).

**Step 2: Run test to verify it fails**
Run `./gradlew :domain:test --tests "io.ronesec.domain.protection.AttentionCheckReducerTest"`
Expected: FAIL / compilation error.

**Step 3: Implement state machine two-phase timeout**
- Add `val isExpired: Boolean = false` to `InterveningSubstate.AttentionCheck` and `AttentionCheckUi`.
- In `SessionCodes.kt`, map `isExpired = substate.isExpired`.
- In `CodeChallengeReducer.kt`:
  - When `TemporalBoundaryReached` arrives at `deadlineElapsedMs`:
    - If `!substate.isExpired`, update to `substate.copy(isExpired = true, deadlineElapsedMs = context.nowElapsedMs + 1000L)` and schedule boundary `(1000L, deadlineElapsedMs)`.
    - If `substate.isExpired`, call `startBreathing(active, context)`.
  - In `SubmitAttentionCheckCode`, ignore if `substate.isExpired`.

**Step 4: Run test to verify it passes**
Run `./gradlew :domain:test --tests "io.ronesec.domain.protection.AttentionCheckReducerTest"`
Expected: PASS.

**Step 5: Commit**
`git commit -m "feat(domain): add 1-second expiration hold phase to attention check state machine"`

---

### Task 3: Room Database Migration (v6 -> v7) & Compiler

**Files:**
- Modify: `app/src/main/kotlin/io/ronesec/android/data/entity/TargetAppEntity.kt`
- Modify: `app/src/main/kotlin/io/ronesec/android/data/WattimDatabase.kt`
- Modify: `app/src/main/kotlin/io/ronesec/android/data/PolicyCompiler.kt`
- Create: `app/src/test/kotlin/io/ronesec/android/data/Migration6To7Test.kt`

**Step 1: Write the failing migration test**
In `Migration6To7Test.kt`, load schema `6.json`, insert row with version 6 columns, apply `MIGRATION_6_7`, verify `annoyingUnlockEnabled = false` and `annoyingUnlockChancePercent = 20`.

**Step 2: Run test to verify it fails**
Run `./gradlew testDebugUnitTest --tests "io.ronesec.android.data.Migration6To7Test"`
Expected: FAIL.

**Step 3: Implement Entity, Migration and PolicyCompiler updates**
- Add `@ColumnInfo(defaultValue = "0") val annoyingUnlockEnabled: Boolean = false` and `@ColumnInfo(defaultValue = "20") val annoyingUnlockChancePercent: Int = 20` to `TargetAppEntity`.
- Bump `WattimDatabase` version to 7 and register `MIGRATION_6_7`.
- Update `PolicyCompiler.toTargetConfig` and `toTargetEntity`.

**Step 4: Run test to verify it passes**
Run `./gradlew testDebugUnitTest --tests "io.ronesec.android.data.Migration6To7Test"`
Expected: PASS.

**Step 5: Commit**
`git commit -m "feat(data): add Room migration 6 to 7 for annoying unlock settings"`

---

### Task 4: Target Settings UI (ViewModel, Draft, AttentionCheckCard)

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-ru/strings.xml`
- Modify: `app/src/main/kotlin/io/ronesec/android/ui/target/TargetSettingsUiState.kt`
- Modify: `app/src/main/kotlin/io/ronesec/android/ui/target/TargetSettingsViewModel.kt`
- Modify: `app/src/main/kotlin/io/ronesec/android/ui/target/AttentionCheckCard.kt`
- Modify: `app/src/main/kotlin/io/ronesec/android/ui/target/TargetSettingsScreen.kt`
- Test: `app/src/test/kotlin/io/ronesec/android/ui/target/TargetSettingsViewModelTest.kt`

**Step 1: Write failing ViewModel test**
In `TargetSettingsViewModelTest.kt`, verify `toggleAnnoyingUnlock()` and `setAnnoyingUnlockChance(percent)` updates draft and saves to `TargetConfig`.

**Step 2: Run test to verify it fails**
Run `./gradlew testDebugUnitTest --tests "io.ronesec.android.ui.target.TargetSettingsViewModelTest"`
Expected: FAIL.

**Step 3: Implement UI and ViewModel updates**
- Add string resources for `target_attention_check_annoying_title` and `target_attention_check_annoying_desc` and chance labels.
- Update `TargetSettingsDraft` with `annoyingUnlockEnabled` and `annoyingUnlockChancePercent`.
- Add `toggleAnnoyingUnlock()` and `setAnnoyingUnlockChance(Int)` to `TargetSettingsViewModel.kt`, and map fields in `initDraft` and `onSave()`.
- Update `AttentionCheckCard.kt` with toggle and percentage text input + `-5%` / `+5%` stepper buttons.
- Connect in `TargetSettingsScreen.kt`.

**Step 4: Run test to verify it passes**
Run `./gradlew testDebugUnitTest --tests "io.ronesec.android.ui.target.TargetSettingsViewModelTest"`
Expected: PASS.

**Step 5: Commit**
`git commit -m "feat(ui): add annoying unlock controls to AttentionCheckCard and ViewModel"`

---

### Task 5: Attention Check Content (Digit Drop & 1-Second Error/Timeout UI)

**Files:**
- Modify: `app/src/main/kotlin/io/ronesec/android/ui/intervention/AttentionCheckContent.kt`
- Test: `app/src/test/kotlin/io/ronesec/android/ui/intervention/AttentionCheckContentTest.kt`

**Step 1: Write failing UI tests**
In `AttentionCheckContentTest.kt`:
1. Test that `attentionCheck.isExpired = true` renders `attention_check_timeout_message` and red border.
2. Test that `attentionCheck.hasError = true` renders `attention_check_error` and red border.
3. Test that when `annoyingUnlockEnabled = true` and `annoyingUnlockChancePercent = 100`, typed digits are dropped (not entered).

**Step 2: Run tests to verify they fail**
Run `./gradlew testDebugUnitTest --tests "io.ronesec.android.ui.intervention.AttentionCheckContentTest"`
Expected: FAIL.

**Step 3: Implement AttentionCheckContent logic**
- In `AttentionCheckContent`:
  - When `attentionCheck.isExpired == true`:
    - Draw border in `colors.error`.
    - Display `stringResource(R.string.attention_check_timeout_message)`.
    - Set timer display to `0.0s`.
    - Disable input (`enabled = false`).
  - When `attentionCheck.hasError == true`:
    - Maintain `isWrongCodeLocked` for 1000ms using `LaunchedEffect(attentionCheck.hasError)` with `delay(1000L)`.
    - Display `stringResource(R.string.attention_check_error)` and red border while active.
  - In `onValueChange`:
    - If new characters added and `config.annoyingUnlockEnabled`:
      - Filter new characters with random drop roll `Random.nextInt(100) < config.annoyingUnlockChancePercent`.

**Step 4: Run test to verify it passes**
Run `./gradlew testDebugUnitTest --tests "io.ronesec.android.ui.intervention.AttentionCheckContentTest"`
Expected: PASS.

**Step 5: Commit**
`git commit -m "feat(ui): implement digit drop and 1-second failure display in AttentionCheckContent"`

---

### Task 6: Full Verification and Integration Tests

**Step 1: Run all domain tests**
Run `./gradlew :domain:test`
Expected: ALL PASS.

**Step 2: Run all app unit tests**
Run `./gradlew testDebugUnitTest`
Expected: ALL PASS.

**Step 3: Run schema and build check**
Run `./gradlew compileDebugSources compileDebugUnitTestSources`
Expected: ALL PASS.
