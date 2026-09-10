# Feature Explanations, Custom Emergency Interval, and Backoff Growth Fix Implementation Plan

> **For Antigravity:** REQUIRED SUB-SKILL: Load executing-plans to implement this plan task-by-task.

**Goal:** Provide question mark explanation dialogs for core features in Target Settings, allow configuring and resetting a custom emergency access duration in General Settings, and fix exponential backoff so exiting an intervention does not grow the delay timer.

**Architecture:**
1. Room query modification in `OpenAttemptDao` to count only `outcome = 'CONTINUED'` for backoff history, with uncommitted delta clearance on exit in `ProtectionReducer`.
2. Database schema evolution (Room v1 -> v2) on `AppSettingsEntity` with `MIGRATION_1_2` to persist `customEmergencyMinutes`, wired via `PolicyStore.presentationSettings` to `ConfigScreen` and `EmergencyDialog`.
3. Terminal design-system components `TerminalHelpCircle` (48dp touch target) and `TerminalInfoDialog` integrated into `TargetSettingsScreen`.

**Tech Stack:** Kotlin, Jetpack Compose, Android Room Database, StateFlow / Coroutines, JUnit 4, Robolectric.

---

### Task 1: Exponential Backoff Growth Fix on Exit

**Files:**
- Modify: `app/src/main/kotlin/io/ronesec/android/data/dao/OpenAttemptDao.kt:28-33`
- Modify: `domain/src/main/kotlin/io/ronesec/domain/protection/ProtectionReducer.kt:486-508`
- Test: `app/src/test/kotlin/io/ronesec/android/data/T03_BackoffHistoryRoomTest.kt`
- Test: `domain/src/test/kotlin/io/ronesec/domain/protection/T04_ProtectionReducerTest.kt`

**Step 1: Write the failing tests**
1. In `T03_BackoffHistoryRoomTest.kt`, add test `abandonedEntryAttemptsAreExcludedFromBackoffHistory`:
   Insert one `CONTINUED` attempt and one `ABANDONED` attempt. Assert that `getAllRecentEntryTimestamps` returns only 1 timestamp (the `CONTINUED` one), not 2.
2. In `T04_ProtectionReducerTest.kt`, add test `actionExitClearsUncommittedHistoryDelta`:
   Verify that after receiving `ActionExit`, the returned `updatedRuntimeState.uncommittedHistoryDeltas` has no deltas for the target package.

**Step 2: Run tests to verify they fail**
Run: `./gradlew :app:testDebugUnitTest --tests "io.ronesec.android.data.T03_BackoffHistoryRoomTest" && ./gradlew :domain:test --tests "io.ronesec.domain.protection.T04_ProtectionReducerTest"`
Expected: FAIL (2 timestamps returned instead of 1; uncommitted delta not cleared).

**Step 3: Write minimal implementation**
1. In `OpenAttemptDao.kt`:
   ```kotlin
   @Query("SELECT * FROM open_attempts WHERE packageName = :packageName AND kind = 'ENTRY' AND outcome = 'CONTINUED' AND timestamp > :sinceTimestamp ORDER BY timestamp ASC")
   suspend fun getRecentEntryAttempts(packageName: String, sinceTimestamp: Long): List<OpenAttemptEntity>

   @Query("SELECT packageName, timestamp FROM open_attempts WHERE kind = 'ENTRY' AND outcome = 'CONTINUED' AND timestamp > :sinceTimestamp ORDER BY timestamp ASC")
   suspend fun getAllRecentEntryTimestamps(sinceTimestamp: Long): List<PackageTimestamp>
   ```
2. In `ProtectionReducer.kt`:
   On `ProtectionEvent.ActionExit` and `ProtectionEvent.ActionCancel`:
   ```kotlin
   val updatedDeltas = context.runtimeState.uncommittedHistoryDeltas - currentState.session.packageName
   val updatedRuntime = context.runtimeState.copy(uncommittedHistoryDeltas = updatedDeltas)
   ```

**Step 4: Run tests to verify they pass**
Run: `./gradlew :app:testDebugUnitTest --tests "io.ronesec.android.data.T03_BackoffHistoryRoomTest" && ./gradlew :domain:test --tests "io.ronesec.domain.protection.T04_ProtectionReducerTest"`
Expected: PASS

**Step 5: Commit**
```bash
git add app/src/main/kotlin/io/ronesec/android/data/dao/OpenAttemptDao.kt domain/src/main/kotlin/io/ronesec/domain/protection/ProtectionReducer.kt app/src/test/kotlin/io/ronesec/android/data/T03_BackoffHistoryRoomTest.kt domain/src/test/kotlin/io/ronesec/domain/protection/T04_ProtectionReducerTest.kt
git commit -m "fix(backoff): exclude abandoned attempts from exponential backoff history"
```

---

### Task 2: Custom Emergency Duration Data Layer (Room & PolicyStore)

**Files:**
- Modify: `app/src/main/kotlin/io/ronesec/android/data/entity/AppSettingsEntity.kt:6-18`
- Modify: `app/src/main/kotlin/io/ronesec/android/data/WattimDatabase.kt:26-76`
- Modify: `app/src/main/kotlin/io/ronesec/android/data/PresentationSettings.kt:5-10`
- Modify: `app/src/main/kotlin/io/ronesec/android/data/PolicyStore.kt:105-125,270-300`
- Test: `app/src/test/kotlin/io/ronesec/android/data/PolicyStoreTest.kt`

**Step 1: Write the failing test**
In `app/src/test/kotlin/io/ronesec/android/data/PolicyStoreTest.kt`, test setting `setCustomEmergencyMinutes(45)` updates `presentationSettings.value.customEmergencyMinutes == 45`, and setting `null` updates it back to `null`.

**Step 2: Run test to verify it fails**
Run: `./gradlew :app:testDebugUnitTest --tests "io.ronesec.android.data.PolicyStoreTest"`
Expected: Compilation failure or assertion failure (missing method / field).

**Step 3: Write minimal implementation**
1. In `AppSettingsEntity.kt`:
   Add `val customEmergencyMinutes: Int? = null`.
2. In `WattimDatabase.kt`:
   Bump version to 2, exportSchema = false or update schema. Add migration:
   ```kotlin
   val MIGRATION_1_2 = object : Migration(1, 2) {
       override fun migrate(db: SupportSQLiteDatabase) {
           db.execSQL("ALTER TABLE app_settings ADD COLUMN customEmergencyMinutes INTEGER DEFAULT NULL")
       }
   }
   ```
   Add `.addMigrations(MIGRATION_1_2)` and `.fallbackToDestructiveMigration()` for testing convenience.
3. In `PresentationSettings.kt`:
   Add `val customEmergencyMinutes: Int? = null`.
4. In `PolicyStore.kt`:
   Populate `customEmergencyMinutes = settings?.customEmergencyMinutes` in `getSnapshot()`.
   Add `suspend fun setCustomEmergencyMinutes(minutes: Int?): Result<Unit>`.

**Step 4: Run test to verify it passes**
Run: `./gradlew :app:testDebugUnitTest --tests "io.ronesec.android.data.PolicyStoreTest"`
Expected: PASS

**Step 5: Commit**
```bash
git add app/src/main/kotlin/io/ronesec/android/data/entity/AppSettingsEntity.kt app/src/main/kotlin/io/ronesec/android/data/WattimDatabase.kt app/src/main/kotlin/io/ronesec/android/data/PresentationSettings.kt app/src/main/kotlin/io/ronesec/android/data/PolicyStore.kt app/src/test/kotlin/io/ronesec/android/data/PolicyStoreTest.kt
git commit -m "feat(settings): add customEmergencyMinutes persistence and presentation stream"
```

---

### Task 3: Custom Emergency Duration UI in ConfigScreen & EmergencyDialog

**Files:**
- Create: `app/src/main/kotlin/io/ronesec/android/ui/config/EmergencyAccessConfigCard.kt`
- Modify: `app/src/main/kotlin/io/ronesec/android/ui/config/ConfigUiState.kt`
- Modify: `app/src/main/kotlin/io/ronesec/android/ui/config/ConfigViewModel.kt`
- Modify: `app/src/main/kotlin/io/ronesec/android/ui/config/ConfigScreen.kt`
- Modify: `app/src/main/kotlin/io/ronesec/android/platform/overlay/OverlayPresenter.kt`
- Modify: `app/src/main/kotlin/io/ronesec/android/ui/intervention/EmergencyDialog.kt`
- Modify: `app/src/main/kotlin/io/ronesec/android/ui/intervention/InterventionContent.kt`
- Modify: `app/src/main/kotlin/io/ronesec/android/ui/intervention/OverlayRootContent.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-ru/strings.xml`
- Test: `app/src/test/kotlin/io/ronesec/android/ui/intervention/T15_EmergencyDialogAndAccessTest.kt`

**Step 1: Write the failing tests**
In `T15_EmergencyDialogAndAccessTest.kt`, assert:
1. When `customEmergencyMinutes = 45`, badge `"45МИН"` is present and clicking it dispatches `45 * 60 * 1000L`.
2. When `customEmergencyMinutes = null`, badge `"45МИН"` is absent and only standard badges exist.

**Step 2: Run test to verify it fails**
Run: `./gradlew :app:testDebugUnitTest --tests "io.ronesec.android.ui.intervention.T15_EmergencyDialogAndAccessTest"`
Expected: FAIL

**Step 3: Write minimal implementation**
1. Strings for emergency access card in Russian and English.
2. `EmergencyAccessConfigCard.kt` in `io.ronesec.android.ui.config`.
3. Integrate into `ConfigViewModel` and `ConfigScreen`.
4. Pass `customEmergencyMinutes` from `PresentationSettings` -> `OverlayPresenter` -> `OverlayUiState` -> `InterventionContent` -> `EmergencyDialog`.
5. In `EmergencyDialog.kt`, render the custom badge if `customEmergencyMinutes != null`.

**Step 4: Run test to verify it passes**
Run: `./gradlew :app:testDebugUnitTest --tests "io.ronesec.android.ui.intervention.T15_EmergencyDialogAndAccessTest"`
Expected: PASS

**Step 5: Commit**
```bash
git add app/src/main/kotlin/io/ronesec/android/ui/config/EmergencyAccessConfigCard.kt app/src/main/kotlin/io/ronesec/android/ui/config/ConfigUiState.kt app/src/main/kotlin/io/ronesec/android/ui/config/ConfigViewModel.kt app/src/main/kotlin/io/ronesec/android/ui/config/ConfigScreen.kt app/src/main/kotlin/io/ronesec/android/platform/overlay/OverlayPresenter.kt app/src/main/kotlin/io/ronesec/android/ui/intervention/EmergencyDialog.kt app/src/main/kotlin/io/ronesec/android/ui/intervention/InterventionContent.kt app/src/main/kotlin/io/ronesec/android/ui/intervention/OverlayRootContent.kt app/src/main/res/values/strings.xml app/src/main/res/values-ru/strings.xml app/src/test/kotlin/io/ronesec/android/ui/intervention/T15_EmergencyDialogAndAccessTest.kt
git commit -m "feat(emergency): support custom emergency interval configuration and dynamic badge"
```

---

### Task 4: Feature Explanations Question Badges (`(?)`) & Info Dialog

**Files:**
- Create: `app/src/main/kotlin/io/ronesec/android/ui/designsystem/TerminalHelpCircle.kt`
- Create: `app/src/main/kotlin/io/ronesec/android/ui/designsystem/TerminalInfoDialog.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-ru/strings.xml`
- Modify: `app/src/main/kotlin/io/ronesec/android/ui/target/TargetSettingsScreen.kt`
- Test: `app/src/test/kotlin/io/ronesec/android/ui/target/TargetSettingsUiTest.kt`

**Step 1: Write the failing test**
In `TargetSettingsUiTest.kt`, verify that clicking the help circle next to "ДЛИТЕЛЬНОСТЬ ПАУЗЫ" displays the dialog containing the pause explanation, and clicking "ПОНЯТНО" closes it.

**Step 2: Run test to verify it fails**
Run: `./gradlew :app:testDebugUnitTest --tests "io.ronesec.android.ui.target.TargetSettingsUiTest"`
Expected: FAIL

**Step 3: Write minimal implementation**
1. Implement `TerminalHelpCircle.kt` (20dp diameter, 48dp touch target, `?` text).
2. Implement `TerminalInfoDialog.kt` (75% black scrim, title, text, "ПОНЯТНО" button).
3. Add strings for all 5 features in English and Russian.
4. Integrate `TerminalHelpCircle` next to headers in `TargetSettingsScreen.kt` with a rememberable dialog state (`var activeHelpTopic by remember { mutableStateOf<HelpTopic?>(null) }`).

**Step 4: Run test to verify it passes**
Run: `./gradlew :app:testDebugUnitTest --tests "io.ronesec.android.ui.target.TargetSettingsUiTest"`
Expected: PASS

**Step 5: Commit**
```bash
git add app/src/main/kotlin/io/ronesec/android/ui/designsystem/TerminalHelpCircle.kt app/src/main/kotlin/io/ronesec/android/ui/designsystem/TerminalInfoDialog.kt app/src/main/res/values/strings.xml app/src/main/res/values-ru/strings.xml app/src/main/kotlin/io/ronesec/android/ui/target/TargetSettingsScreen.kt app/src/test/kotlin/io/ronesec/android/ui/target/TargetSettingsUiTest.kt
git commit -m "feat(ui): add help question badges and info dialogs to TargetSettingsScreen"
```

---

### Task 5: Full Regression Testing & Verification

**Files:**
- Test: whole test suite

**Step 1: Run comprehensive tests**
Run: `./gradlew testDebugUnitTest`
Expected: ALL PASS

**Step 2: Final commit if any adjustments needed**
