# Random Addition and Emergency Code Fuse Implementation Plan

> **For Antigravity:** REQUIRED SUB-SKILL: Load executing-plans to implement this plan task-by-task.

**Goal:** Decouple random delay into an additive jitter added on top of exponential backoff without multiplying it, and transform the emergency access code into an on-screen safety fuse in EmergencyDialog that unlocks all emergency actions.

**Architecture:** 
- In Domain: `RuleEngine` samples $\Delta_{random} \in [0, t_{random}]$ and adds it to the backoff delay $T_{backoff}$ calculated on $T_{base}$. `TargetConfig` removes the constraint that random max duration must be greater than base duration. `CodeChallengeReducer` guards `ActionEmergencyOnce`, `ActionEmergencyTimed`, and `ActionEmergencyForever` through `checkEmergency`.
- In UI: `EmergencyCodeLabel` is removed from intervention/breathing screens. `EmergencyDialog` prominently displays the fuse code when required, and disables all emergency entry actions ("ВОЙТИ РАЗОВО", presets 15m/30m/1h, custom minutes, forever) until the user inputs the matching code. `RandomDurationCard` updates labels and steppers for random addition up to 120s.

**Tech Stack:** Kotlin, Jetpack Compose, Coroutines, MVI/StateFlow, JUnit4.

---

### Task 1: Domain Logic — Random Addition & Backoff Calculation

**Files:**
- Modify: `domain/src/main/kotlin/io/ronesec/domain/model/TargetConfig.kt`
- Modify: `domain/src/main/kotlin/io/ronesec/domain/policy/RuleEngine.kt`
- Test: `domain/src/test/kotlin/io/ronesec/domain/policy/RandomDurationPolicyTest.kt`

**Step 1: Write failing tests in `RandomDurationPolicyTest.kt`**
Verify:
1. `TargetConfig` allows $t_{random} < \text{durationMs}$ (e.g. duration = 10s, random = 5s) and rejects $t_{random} < 0$.
2. `RuleEngine` samples $\Delta_{random}$ in $[0, t_{random}]$ and adds it to $T_{base}$.
3. When backoff is active ($N=1$, +50%), backoff applies to $T_{base}$ (10s -> 15s) and $\Delta_{random}$ (e.g. 3s) is added: total = 18s (not $(10+3)\times 1.5 = 19.5s$).

**Step 2: Run test to verify it fails**
Run: `./gradlew :domain:test --tests io.ronesec.domain.policy.RandomDurationPolicyTest`

**Step 3: Update `TargetConfig.kt` and `RuleEngine.kt`**
- In `TargetConfig.kt`:
  - `require(randomMaxDurationMs in 0L..MAX_DURATION_MS)`
  - Remove `require(randomMaxDurationMs >= durationMs)`.
- In `RuleEngine.kt`:
  - Calculate `val randomAdditionMs = if (target.randomDurationEnabled && target.randomMaxDurationMs > 0L) randomDurationProvider(0L, target.randomMaxDurationMs) else 0L`
  - Compute `val backoffDelayMs = Backoff.resolveEffectiveDurationMs(configuredBaseMs, target.growthConfig, priorEntryCount)`
  - `val effectiveDurationMs = (backoffDelayMs + randomAdditionMs).coerceIn(Backoff.MIN_DELAY_MS, Backoff.MAX_DELAY_MS)`
  - Set `baseDurationMs = configuredBaseMs` in `EffectiveInterventionConfig`.

**Step 4: Run test to verify it passes**
Run: `./gradlew :domain:test --tests io.ronesec.domain.policy.RandomDurationPolicyTest`

**Step 5: Commit**
```bash
git add domain/src/main/kotlin/io/ronesec/domain/model/TargetConfig.kt \
        domain/src/main/kotlin/io/ronesec/domain/policy/RuleEngine.kt \
        domain/src/test/kotlin/io/ronesec/domain/policy/RandomDurationPolicyTest.kt
git commit -m "feat(domain): treat random duration as additive jitter on top of exponential backoff"
```

---

### Task 2: UI & ViewModel for Random Addition

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-ru/strings.xml`
- Modify: `app/src/main/kotlin/io/ronesec/android/ui/target/TargetSettingsViewModel.kt`
- Modify: `app/src/main/kotlin/io/ronesec/android/ui/target/RandomDurationCard.kt`
- Test: `app/src/test/kotlin/io/ronesec/android/ui/target/TargetSettingsViewModelTest.kt`

**Step 1: Write failing test in `TargetSettingsViewModelTest.kt`**
Verify changing `durationSeconds` does not alter or clamp `randomMaxDurationSeconds`.

**Step 2: Run test to verify it fails**
Run: `./gradlew :app:testDebugUnitTest --tests io.ronesec.android.ui.target.TargetSettingsViewModelTest`

**Step 3: Implement changes in strings, ViewModel, and Card**
- `strings.xml` & `values-ru/strings.xml`:
  - Update `target_random_duration_title`, `target_random_duration_desc`, `target_random_max_duration_label`.
- `TargetSettingsViewModel.kt`:
  - In `onDurationChange`: remove coupling with `randomMaxDurationSeconds`.
  - In `onRandomMaxDurationChange`: clamp to `0..120`.
- `RandomDurationCard.kt`:
  - Display `+$maxDurationSeconds с`.
  - Buttons `-5s`, `-1s`, `+1s`, `+5s` operating on range `0..120`.

**Step 4: Run test to verify it passes**
Run: `./gradlew :app:testDebugUnitTest --tests io.ronesec.android.ui.target.TargetSettingsViewModelTest`

**Step 5: Commit**
```bash
git add app/src/main/res/values/strings.xml \
        app/src/main/res/values-ru/strings.xml \
        app/src/main/kotlin/io/ronesec/android/ui/target/TargetSettingsViewModel.kt \
        app/src/main/kotlin/io/ronesec/android/ui/target/RandomDurationCard.kt \
        app/src/test/kotlin/io/ronesec/android/ui/target/TargetSettingsViewModelTest.kt
git commit -m "feat(ui): update random duration card to represent independent additive jitter"
```

---

### Task 3: Domain Enforcement of Emergency Code Fuse

**Files:**
- Modify: `domain/src/main/kotlin/io/ronesec/domain/protection/CodeChallengeReducer.kt`
- Test: `domain/src/test/kotlin/io/ronesec/domain/protection/SessionCodesTest.kt`

**Step 1: Write failing test in `SessionCodesTest.kt`**
Verify:
1. `ActionEmergencyOnce` without code fails when `requireEmergencyCode == true`.
2. `ActionEmergencyOnce` with valid code succeeds and grants access.
3. `ActionEmergencyForever` without code fails when `requireEmergencyCode == true`.
4. `ActionEmergencyForever` with valid code succeeds and grants access.

**Step 2: Run test to verify it fails**
Run: `./gradlew :domain:test --tests io.ronesec.domain.protection.SessionCodesTest`

**Step 3: Update `CodeChallengeReducer.kt`**
In `CodeChallengeReducer.intercept`:
Route `ActionEmergencyOnce`, `ActionEmergencyTimed`, and `ActionEmergencyForever` to `checkEmergency(active, event.sessionId, event.cycle, event.code, context)`.

**Step 4: Run test to verify it passes**
Run: `./gradlew :domain:test --tests io.ronesec.domain.protection.SessionCodesTest`

**Step 5: Commit**
```bash
git add domain/src/main/kotlin/io/ronesec/domain/protection/CodeChallengeReducer.kt \
        domain/src/test/kotlin/io/ronesec/domain/protection/SessionCodesTest.kt
git commit -m "feat(domain): enforce emergency code on all emergency access actions"
```

---

### Task 4: Emergency Dialog Fuse UI & Clean Intervention Layout

**Files:**
- Modify: `app/src/main/kotlin/io/ronesec/android/ui/intervention/InterventionContent.kt`
- Modify: `app/src/main/kotlin/io/ronesec/android/ui/intervention/CodeGateContent.kt`
- Modify: `app/src/main/kotlin/io/ronesec/android/ui/intervention/EmergencyDialog.kt`
- Modify: `app/src/main/kotlin/io/ronesec/android/ui/intervention/SessionInterventionContent.kt`
- Test: `app/src/test/kotlin/io/ronesec/android/ui/intervention/CodeChallengeUiTest.kt`

**Step 1: Write failing UI test in `CodeChallengeUiTest.kt`**
Verify:
1. Emergency code is not shown in `InterventionContent` or `CodeGateContent`.
2. In `EmergencyDialog` with `requireCode == true`:
   - Code is displayed in the dialog.
   - Buttons "ВОЙТИ РАЗОВО" and presets are disabled when code is empty or mismatched.
   - Buttons become enabled once code matches.

**Step 2: Run test to verify it fails**
Run: `./gradlew :app:testDebugUnitTest --tests io.ronesec.android.ui.intervention.CodeChallengeUiTest`

**Step 3: Implement UI updates**
- Remove `EmergencyCodeLabel` from `InterventionContent.kt` and `CodeGateContent.kt`.
- In `EmergencyDialog.kt`:
  - Accept `emergencyCode: String?`.
  - Display code banner when `requireCode == true && emergencyCode != null`.
  - `isUnlocked = !requireCode || (code.isNotBlank() && code == emergencyCode)`.
  - Set `enabled = isUnlocked` on "ВОЙТИ РАЗОВО" and timed badges (with alpha `0.38f` when locked).
  - Forward `code` to `onEmergencyOnce(code)` and `onEmergencyForever(code)`.
- In `SessionInterventionContent.kt`:
  - Pass `emergencyCode = challenge?.emergencyCode`.
  - Dispatch events with `code`:
    `onEmergencyOnce = { code -> send(ProtectionEvent.ActionEmergencyOnce(mode.sessionId, mode.cycle, code)) }`
    `onEmergencyForever = { code -> send(ProtectionEvent.ActionEmergencyForever(mode.sessionId, mode.cycle, code)) }`

**Step 4: Run test to verify it passes**
Run: `./gradlew :app:testDebugUnitTest --tests io.ronesec.android.ui.intervention.CodeChallengeUiTest`

**Step 5: Commit**
```bash
git add app/src/main/kotlin/io/ronesec/android/ui/intervention/InterventionContent.kt \
        app/src/main/kotlin/io/ronesec/android/ui/intervention/CodeGateContent.kt \
        app/src/main/kotlin/io/ronesec/android/ui/intervention/EmergencyDialog.kt \
        app/src/main/kotlin/io/ronesec/android/ui/intervention/SessionInterventionContent.kt \
        app/src/test/kotlin/io/ronesec/android/ui/intervention/CodeChallengeUiTest.kt
git commit -m "feat(ui): implement emergency fuse in EmergencyDialog and clean intervention screen"
```

---

### Task 5: Full Verification & Integration Check

**Files:** None (verification step)

**Step 1: Run complete test suite**
Run: `./gradlew test`

**Step 2: Verify git status and clean working tree**
Run: `git status`
