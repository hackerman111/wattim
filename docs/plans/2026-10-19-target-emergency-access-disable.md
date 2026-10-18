# Per-Target Emergency Access Toggle Implementation Plan

> **For Antigravity:** REQUIRED SUB-SKILL: Load executing-plans to implement this plan task-by-task.

**Goal:** Allow users to disable the emergency access bypass ("ЭКСТРЕННО" / "EMERGENCY") for specific target applications in their settings, hiding the emergency button on intervention and code-check screens.

**Architecture:** Add `emergencyAccessEnabled: Boolean = true` to `TargetConfig` and `TargetAppEntity`. Store in Room v9 with `MIGRATION_8_9`. Propagate through `RuleEngine` into `EffectiveInterventionConfig` so that `InterventionContent`, `CodeGateContent`, and `AttentionCheckContent` conditionally hide the emergency button and `OverlayPresenter` disallows emergency dialog opening when disabled.

**Tech Stack:** Kotlin, Jetpack Compose, Room, Kotlin Coroutines, Robolectric, JUnit 4.

---

### Task 1: Domain Model and RuleEngine (`domain`)

**Files:**
- Modify: `domain/src/main/kotlin/io/ronesec/domain/model/TargetConfig.kt`
- Modify: `domain/src/main/kotlin/io/ronesec/domain/policy/RuleEngine.kt`
- Test: `domain/src/test/kotlin/io/ronesec/domain/policy/RuleEngineTest.kt`

**Step 1: Write the failing test**
In `domain/src/test/kotlin/io/ronesec/domain/policy/RuleEngineTest.kt`, add:
```kotlin
    @Test
    fun interventionDecisionPropagatesEmergencyAccessEnabledFalse() {
        val target = TargetConfig(
            packageName = "com.example.not-emergency",
            displayName = "No Emergency",
            emergencyAccessEnabled = false
        )
        val snapshot = PolicySnapshot(
            schemaVersion = 1L,
            revision = 1L,
            targets = mapOf(target.packageName to target),
            schedules = emptyList(),
            globalPauseUntil = null,
            activeGrants = emptyMap()
        )
        val decision = RuleEngine.evaluate(
            packageName = target.packageName,
            snapshot = snapshot,
            now = Instant.EPOCH,
            dayOfWeek = DayOfWeek.MONDAY,
            timeOfDayMinutes = 600
        )
        assertTrue(decision is Decision.Intervention)
        val intervention = decision as Decision.Intervention
        assertFalse(intervention.config.emergencyAccessEnabled)
    }
```

**Step 2: Run test to verify it fails**
Run: `./gradlew :domain:test --tests "io.ronesec.domain.policy.RuleEngineTest.interventionDecisionPropagatesEmergencyAccessEnabledFalse"`
Expected: FAIL (unresolved reference: `emergencyAccessEnabled`).

**Step 3: Write minimal implementation**
1. In `TargetConfig.kt`:
   Add `val emergencyAccessEnabled: Boolean = true` with default `true`.
2. In `RuleEngine.kt`:
   Add `val emergencyAccessEnabled: Boolean = true` to `EffectiveInterventionConfig`.
   In `evaluateTarget(...)`: pass `emergencyAccessEnabled = target.emergencyAccessEnabled`.

**Step 4: Run test to verify it passes**
Run: `./gradlew :domain:test --tests "io.ronesec.domain.policy.RuleEngineTest"`
Expected: PASS.

---

### Task 2: Room Database v9 and Persistence (`app`)

**Files:**
- Modify: `app/src/main/kotlin/io/ronesec/android/data/entity/TargetAppEntity.kt`
- Modify: `app/src/main/kotlin/io/ronesec/android/data/WattimDatabase.kt`
- Modify: `app/src/main/kotlin/io/ronesec/android/data/PolicyStore.kt`
- Test: `app/src/test/kotlin/io/ronesec/android/data/TargetAppEntityMigrationTest.kt`

**Step 1: Write the failing test**
Add a migration test verifying `MIGRATION_8_9` correctly alters `target_apps` table and defaults `emergencyAccessEnabled` to 1.

**Step 2: Run test to verify it fails**
Run: `./gradlew :app:testDebugUnitTest --tests "*TargetAppEntityMigrationTest*"`
Expected: FAIL (`MIGRATION_8_9` not found).

**Step 3: Write minimal implementation**
1. In `TargetAppEntity.kt`:
   Add `@ColumnInfo(defaultValue = "1") val emergencyAccessEnabled: Boolean = true`.
2. In `WattimDatabase.kt`:
   Increment `version = 9`.
   Add `MIGRATION_8_9`:
   ```kotlin
   val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
       override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
           db.execSQL("ALTER TABLE target_apps ADD COLUMN emergencyAccessEnabled INTEGER NOT NULL DEFAULT 1")
       }
   }
   ```
   Add `MIGRATION_8_9` to `getInstance`.
3. In `PolicyStore.kt`:
   Map `emergencyAccessEnabled` in `toEntity()` and `toDomain()`.

**Step 4: Run test to verify it passes**
Run: `./gradlew :app:testDebugUnitTest --tests "*TargetAppEntityMigrationTest*"`
Expected: PASS.

---

### Task 3: Target Settings UI (`app`)

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-ru/strings.xml`
- Modify: `app/src/main/kotlin/io/ronesec/android/ui/target/TargetSettingsUiState.kt`
- Modify: `app/src/main/kotlin/io/ronesec/android/ui/target/TargetSettingsViewModel.kt`
- Modify: `app/src/main/kotlin/io/ronesec/android/ui/target/CodeSettingsCard.kt`
- Modify: `app/src/main/kotlin/io/ronesec/android/ui/target/TargetSettingsScreen.kt`
- Test: `app/src/test/kotlin/io/ronesec/android/ui/target/TargetSettingsViewModelTest.kt`

**Step 1: Write the failing test**
In `TargetSettingsViewModelTest.kt`, test toggling emergency access updates `uiState.emergencyAccessEnabled` and persists on save.

**Step 2: Run test to verify it fails**
Run: `./gradlew :app:testDebugUnitTest --tests "*TargetSettingsViewModelTest*"`
Expected: FAIL (`onToggleEmergencyAccess` unresolved).

**Step 3: Write minimal implementation**
1. Add string resources in `values/strings.xml` and `values-ru/strings.xml`:
   `target_emergency_access_title` and `target_emergency_access_desc`.
2. In `TargetSettingsUiState.kt`:
   Add `val emergencyAccessEnabled: Boolean = true`.
3. In `TargetSettingsViewModel.kt`:
   Add `fun onToggleEmergencyAccess()` updating state and saving via `policyStore`.
4. In `CodeSettingsCard.kt` / `TargetSettingsScreen.kt`:
   Add terminal toggle row for emergency access.

**Step 4: Run test to verify it passes**
Run: `./gradlew :app:testDebugUnitTest --tests "*TargetSettingsViewModelTest*"`
Expected: PASS.

---

### Task 4: Intervention & Overlay UI (`app`)

**Files:**
- Modify: `app/src/main/kotlin/io/ronesec/android/ui/intervention/InterventionContent.kt`
- Modify: `app/src/main/kotlin/io/ronesec/android/ui/intervention/CodeGateContent.kt`
- Modify: `app/src/main/kotlin/io/ronesec/android/ui/intervention/AttentionCheckContent.kt`
- Modify: `app/src/main/kotlin/io/ronesec/android/platform/overlay/OverlayPresenter.kt`
- Test: `app/src/test/kotlin/io/ronesec/android/ui/intervention/T14_InterventionContentAndTimelineTest.kt`

**Step 1: Write the failing test**
In `T14_InterventionContentAndTimelineTest.kt`, assert that when `emergencyAccessEnabled = false`, the emergency button is not displayed on `InterventionContent`.

**Step 2: Run test to verify it fails**
Run: `./gradlew :app:testDebugUnitTest --tests "*T14_InterventionContentAndTimelineTest*"`
Expected: FAIL.

**Step 3: Write minimal implementation**
1. In `InterventionContent.kt`:
   Check `emergencyAccessEnabled`. When false, render only the Exit button occupying full available width.
2. In `CodeGateContent.kt`:
   Check `emergencyAccessEnabled`. Omit emergency button when false.
3. In `AttentionCheckContent.kt`:
   Check `emergencyAccessEnabled`. Omit emergency button when false.
4. In `OverlayPresenter.kt`:
   Guard `openEmergencyDialog()`: do not open if `emergencyAccessEnabled == false`.

**Step 4: Run test to verify it passes**
Run: `./gradlew :app:testDebugUnitTest --tests "*T14_InterventionContentAndTimelineTest*"`
Expected: PASS.

---

### Task 5: Full Project Verification & Regression Suite

**Step 1: Run all unit tests**
Run: `./gradlew test`
Expected: ALL PASS.
