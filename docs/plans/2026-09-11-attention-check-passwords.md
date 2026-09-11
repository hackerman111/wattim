# Implementation Plan: Configurable Attention Check Passwords During Waiting (Intervention)

> **For Antigravity:** REQUIRED SUB-SKILL: Load executing-plans to implement this plan task-by-task.

**Goal:** Implement configurable attention check passwords (CAPTCHA-style reaction tests) during the intervention waiting/breathing animation, with a mini-countdown timer and complete session reset on failure or timeout.

**Architecture:**
1. In `domain`:
   - Extend `TargetConfig` with attention check properties (`attentionChecksEnabled`, `attentionCheckCount`, `attentionCheckCodeLength`, `attentionCheckTimeoutMs`).
   - Propagate to `EffectiveInterventionConfig`.
   - Add `InterveningSubstate.AttentionCheck` to `ProtectionState`.
   - Add event `SubmitAttentionCheckCode`.
   - Implement attention check logic in `CodeChallengeReducer` / `AttentionCheckReducer`: checkpoint scheduling within $[0.15 \cdot D, 0.85 \cdot D]$, pausing breathing on checkpoint boundary, monotonic resume on correct code submission (`startElapsedMs = now - pausedMs`), and reset to 0 with fresh checkpoints on timeout.
   - Pure JVM domain unit tests (`AttentionCheckReducerTest`).
2. In `app` data:
   - Update `TargetAppEntity` with attention check columns.
   - Bump `WattimDatabase` to version 5 with `MIGRATION_4_5`.
   - Update `PolicyCompiler` mapping.
   - Migration test `Migration4To5Test`.
3. In `app` UI:
   - Add `AttentionCheckContent` overlay composable with bold monospace code, animated mini-timer bar, digit input, error indication, SOS and exit actions.
   - Integrate with `SessionInterventionContent` and `OverlayPresenter`.
   - Add `AttentionCheckCard` in `TargetSettingsScreen` with reactive state in `TargetSettingsViewModel`.
   - Add localized strings (RU & EN).

**Tech Stack:** Kotlin, Jetpack Compose, Room (v5), Coroutines, AndroidX Architecture Components.

---

### Task 1: Domain Model Extensions & Validation

**Files:**
- Modify: `domain/src/main/kotlin/io/ronesec/domain/model/TargetConfig.kt`
- Modify: `domain/src/main/kotlin/io/ronesec/domain/policy/EffectiveInterventionConfig.kt` (or wherever defined)
- Test: `domain/src/test/kotlin/io/ronesec/domain/policy/TargetConfigAttentionCheckTest.kt`

**Step 1: Write failing unit test**
Create `TargetConfigAttentionCheckTest.kt` verifying:
- Defaults: `attentionChecksEnabled = false`, `attentionCheckCount = 1`, `attentionCheckCodeLength = 4`, `attentionCheckTimeoutMs = 5_000L`.
- Validation errors on out-of-range count (<1 or >5), length (<3 or >8), timeout (<3000ms or >30000ms) when enabled.

**Step 2: Run test to verify it fails**
```bash
./gradlew domain:test --tests "io.ronesec.domain.policy.TargetConfigAttentionCheckTest"
```

**Step 3: Implement minimal code**
Add properties and validation in `TargetConfig.kt` and `EffectiveInterventionConfig`.

**Step 4: Run test to verify it passes**
```bash
./gradlew domain:test --tests "io.ronesec.domain.policy.TargetConfigAttentionCheckTest"
```

**Step 5: Commit**
```bash
git add domain/src/main/kotlin/io/ronesec/domain/model/TargetConfig.kt domain/src/test/kotlin/io/ronesec/domain/policy/TargetConfigAttentionCheckTest.kt
git commit -m "feat(domain): add attention check fields and validation to TargetConfig"
```

---

### Task 2: Domain Attention Check State Machine & Reducer

**Files:**
- Modify: `domain/src/main/kotlin/io/ronesec/domain/protection/ProtectionState.kt`
- Modify: `domain/src/main/kotlin/io/ronesec/domain/protection/ProtectionEvent.kt`
- Modify: `domain/src/main/kotlin/io/ronesec/domain/protection/SessionCodes.kt`
- Modify: `domain/src/main/kotlin/io/ronesec/domain/protection/CodeChallengeReducer.kt`
- Modify: `domain/src/main/kotlin/io/ronesec/domain/protection/ProtectionReducer.kt`
- Test: `domain/src/test/kotlin/io/ronesec/domain/protection/AttentionCheckReducerTest.kt`

**Step 1: Write failing unit tests**
In `AttentionCheckReducerTest.kt`:
- Test 1: When breathing starts with attention checks enabled (e.g. count=2), checkpoints are scheduled between 15% and 85% of duration.
- Test 2: Reaching checkpoint transitions substate to `AttentionCheck`, recording `pausedElapsedProgressMs`.
- Test 3: Submitting valid code resumes `Breathing` substate with adjusted `startElapsedMs = nowElapsedMs - pausedElapsedProgressMs`.
- Test 4: Submitting invalid code sets `hasError = true`.
- Test 5: Temporal boundary reaching deadline resets breathing progress to 0 and regenerates checkpoints.
- Test 6: Exit action is handled cleanly while in `AttentionCheck`.

**Step 2: Run tests to verify they fail**
```bash
./gradlew domain:test --tests "io.ronesec.domain.protection.AttentionCheckReducerTest"
```

**Step 3: Implement domain state machine logic**
Add `InterveningSubstate.AttentionCheck`, event `SubmitAttentionCheckCode`, and checkpoint schedule handling in reducer.

**Step 4: Run tests to verify they pass**
```bash
./gradlew domain:test --tests "io.ronesec.domain.protection.AttentionCheckReducerTest"
```

**Step 5: Commit**
```bash
git add domain/src/main/kotlin/io/ronesec/domain/protection/ domain/src/test/kotlin/io/ronesec/domain/protection/AttentionCheckReducerTest.kt
git commit -m "feat(domain): implement attention check state machine and reducer transitions"
```

---

### Task 3: Room Database Schema v5 and Migration

**Files:**
- Modify: `app/src/main/kotlin/io/ronesec/android/data/entity/TargetAppEntity.kt`
- Modify: `app/src/main/kotlin/io/ronesec/android/data/WattimDatabase.kt`
- Modify: `app/src/main/kotlin/io/ronesec/android/data/PolicyCompiler.kt`
- Test: `app/src/test/kotlin/io/ronesec/android/data/Migration4To5Test.kt`

**Step 1: Write failing migration test**
In `Migration4To5Test.kt`:
- Create schema v4 database with sample target app.
- Run `MIGRATION_4_5`.
- Validate that columns `attentionChecksEnabled`, `attentionCheckCount`, `attentionCheckCodeLength`, `attentionCheckTimeoutMs` exist with default values (0, 1, 4, 5000).

**Step 2: Run test to verify it fails**
```bash
./gradlew app:testDebugUnitTest --tests "io.ronesec.android.data.Migration4To5Test"
```

**Step 3: Implement entity, database version bump, and migration**
Update `TargetAppEntity`, `WattimDatabase` (version 5, `MIGRATION_4_5`), and `PolicyCompiler`.

**Step 4: Run test to verify it passes**
```bash
./gradlew app:testDebugUnitTest --tests "io.ronesec.android.data.Migration4To5Test"
```

**Step 5: Commit**
```bash
git add app/src/main/kotlin/io/ronesec/android/data/ app/src/test/kotlin/io/ronesec/android/data/Migration4To5Test.kt
git commit -m "feat(data): add Room migration 4 to 5 for attention check settings"
```

---

### Task 4: Target Settings UI (`AttentionCheckCard`)

**Files:**
- Create: `app/src/main/kotlin/io/ronesec/android/ui/target/AttentionCheckCard.kt`
- Modify: `app/src/main/kotlin/io/ronesec/android/ui/target/TargetSettingsUiState.kt`
- Modify: `app/src/main/kotlin/io/ronesec/android/ui/target/TargetSettingsViewModel.kt`
- Modify: `app/src/main/kotlin/io/ronesec/android/ui/target/TargetSettingsScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-ru/strings.xml`
- Test: `app/src/test/kotlin/io/ronesec/android/ui/target/TargetSettingsViewModelTest.kt`

**Step 1: Write failing ViewModel test**
In `TargetSettingsViewModelTest.kt`:
- Verify loading target loads `attentionChecksEnabled`, `attentionCheckCount`, `attentionCheckCodeLength`, `attentionCheckTimeoutMs`.
- Verify updating these fields updates state and persists to database.

**Step 2: Run test to verify it fails**
```bash
./gradlew app:testDebugUnitTest --tests "io.ronesec.android.ui.target.TargetSettingsViewModelTest"
```

**Step 3: Implement AttentionCheckCard and ViewModel integration**
- Create `AttentionCheckCard.kt` with toggle and stepper/slider controls for count (1..5), code length (3..8), timeout (3..30s).
- Add strings in `values/strings.xml` and `values-ru/strings.xml`.
- Wire into `TargetSettingsScreen.kt` and `TargetSettingsViewModel.kt`.

**Step 4: Run test to verify it passes**
```bash
./gradlew app:testDebugUnitTest --tests "io.ronesec.android.ui.target.TargetSettingsViewModelTest"
```

**Step 5: Commit**
```bash
git add app/src/main/kotlin/io/ronesec/android/ui/target/ app/src/main/res/ app/src/test/kotlin/io/ronesec/android/ui/target/
git commit -m "feat(ui): add AttentionCheckCard in TargetSettingsScreen with persistence"
```

---

### Task 5: Intervention Overlay Attention Check Content

**Files:**
- Create: `app/src/main/kotlin/io/ronesec/android/ui/intervention/AttentionCheckContent.kt`
- Modify: `app/src/main/kotlin/io/ronesec/android/ui/intervention/SessionInterventionContent.kt`
- Modify: `app/src/main/kotlin/io/ronesec/android/platform/overlay/OverlayPresenter.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-ru/strings.xml`
- Test: `app/src/test/kotlin/io/ronesec/android/ui/intervention/AttentionCheckContentTest.kt`

**Step 1: Write test for AttentionCheckContent**
Verify rendering of attention prompt, code, remaining timer calculation, and submit event dispatch.

**Step 2: Run test to verify it fails**
```bash
./gradlew app:testDebugUnitTest --tests "io.ronesec.android.ui.intervention.AttentionCheckContentTest"
```

**Step 3: Implement AttentionCheckContent and integrate into overlay**
- Create `AttentionCheckContent`: monospace code display, timer progress bar, auto-submitting digit input, error indication, and exit/SOS buttons.
- Connect into `SessionInterventionContent.kt` when `mode.challenge` or substate indicates `AttentionCheck`.

**Step 4: Run test to verify it passes**
```bash
./gradlew app:testDebugUnitTest --tests "io.ronesec.android.ui.intervention.AttentionCheckContentTest"
```

**Step 5: Commit**
```bash
git add app/src/main/kotlin/io/ronesec/android/ui/intervention/ app/src/test/kotlin/io/ronesec/android/ui/intervention/
git commit -m "feat(ui): implement AttentionCheckContent in intervention overlay"
```

---

### Task 6: Full Verification and Build

**Step 1: Run all unit tests**
```bash
./gradlew testDebugUnitTest
```
Expected: All tests in `:domain` and `:app` pass.

**Step 2: Build debug APK**
```bash
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL. (Verified: BUILD SUCCESSFUL in 3s)

**Step 3: Commit and summarize**
All 6 tasks completed and verified with 100% test pass rate across domain and app modules.

