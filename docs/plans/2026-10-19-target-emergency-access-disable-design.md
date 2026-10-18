# Design: Per-Target Disabling of Emergency Access

## Context and Goals
1. **User Goal**: Allow users to disable the emergency bypass option ("ЭКСТРЕННО" / "EMERGENCY") for specific target applications. When disabled, the user cannot skip breathing pauses or bypass verification codes for that app.
2. **Scope**: Per-target application setting (`emergencyAccessEnabled: Boolean`), enabled (`true`) by default for backward compatibility.
3. **Screen Behavior**:
   - In `InterventionContent`: during the waiting/breathing phase, if `emergencyAccessEnabled == false`, the emergency button is completely hidden, and the Exit button occupies the action row.
   - In `CodeGateContent` and `AttentionCheckContent`: the emergency button is omitted when disabled.
   - In `OverlayPresenter`: attempts to open the emergency dialog are blocked if emergency access is disabled in the active intervention configuration.
4. **Target Settings UI**: In `TargetSettingsScreen`, provide a dedicated toggle in terminal style: "ЭКСТРЕННЫЙ ВХОД" / "EMERGENCY ACCESS".
5. **Persistence & Lifecycle**:
   - Increment Room schema version from 8 to 9.
   - Add `MIGRATION_8_9` adding `emergencyAccessEnabled INTEGER NOT NULL DEFAULT 1` to `target_apps`.
   - Update `TargetConfig`, `TargetAppEntity`, `PolicyStore`, `RuleEngine`, and `EffectiveInterventionConfig`.

---

## 1. Architecture and Data Flow

```text
TargetSettingsScreen (Toggle on/off)
  -> TargetSettingsViewModel
  -> PolicyStore.saveTarget(target.copy(emergencyAccessEnabled = ...))
  -> Room target_apps table (MIGRATION_8_9)
  -> PolicyStore.currentSnapshot
  -> RuleEngine.evaluate(...)
  -> EffectiveInterventionConfig(..., emergencyAccessEnabled = ...)
  -> OverlayPresenter / OverlayMode.Intervention(config)
  -> InterventionContent / CodeGateContent / AttentionCheckContent (Hides Emergency button)
```

---

## 2. Detailed Changes

### 2.1 Domain Layer (`domain`)
- **`TargetConfig`**:
  Add `val emergencyAccessEnabled: Boolean = true` (default `true`).
- **`EffectiveInterventionConfig`**:
  Add `val emergencyAccessEnabled: Boolean = true`.
- **`RuleEngine`**:
  In `evaluateTarget(...)`:
  ```kotlin
  EffectiveInterventionConfig(
      ...
      emergencyAccessEnabled = target.emergencyAccessEnabled
  )
  ```

### 2.2 Data Layer (`app/src/main/kotlin/io/ronesec/android/data`)
- **`TargetAppEntity`**:
  Add `@ColumnInfo(defaultValue = "1") val emergencyAccessEnabled: Boolean = true`.
- **`WattimDatabase`**:
  Bump `version = 9`.
  Add `MIGRATION_8_9`:
  ```kotlin
  val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
      override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
          db.execSQL("ALTER TABLE target_apps ADD COLUMN emergencyAccessEnabled INTEGER NOT NULL DEFAULT 1")
      }
  }
  ```
  Register `MIGRATION_8_9` in `getInstance(...)`.
- **`PolicyStore`**:
  Map `emergencyAccessEnabled` in `toEntity()` and `toDomain()`.

### 2.3 UI Layer - Target Settings (`app/src/main/kotlin/io/ronesec/android/ui/target`)
- **`TargetSettingsUiState`**:
  Add `val emergencyAccessEnabled: Boolean = true`.
- **`TargetSettingsViewModel`**:
  Add `fun onToggleEmergencyAccess()` which updates `_uiState` and persists.
- **`TargetSettingsScreen`**:
  Add toggle row in `CodeSettingsCard` or dedicated section with terminal toggle styling:
  - Title: `R.string.target_emergency_access_title` ("ЭКСТРЕННЫЙ ВХОД" / "EMERGENCY ACCESS")
  - Description: `R.string.target_emergency_access_desc` ("Разрешить пропуск дыхательной паузы или ввода кода через экстренное меню" / "Allow skipping breathing pause or code check via emergency menu")
  - Toggle badge: `ВКЛ` / `ВЫКЛ`.

### 2.4 UI Layer - Overlay / Intervention (`app/src/main/kotlin/io/ronesec/android/ui/intervention` & `platform/overlay`)
- **`InterventionContent`**:
  When `!progress.isComplete`:
  - If `emergencyAccessEnabled`: show Exit + Emergency in row.
  - If `!emergencyAccessEnabled`: show Exit button full width (`widthIn(max = 400.dp)`).
- **`CodeGateContent`**:
  Only render `TerminalButton(text = stringResource(R.string.action_emergency), ...)` if `emergencyAccessEnabled == true`.
- **`AttentionCheckContent`**:
  Only render `TerminalButton(text = stringResource(R.string.action_emergency), ...)` if `emergencyAccessEnabled == true`; otherwise Exit button takes full width.
- **`OverlayPresenter`**:
  In `openEmergencyDialog()`:
  Check if `(mode as? OverlayMode.Intervention)?.config?.emergencyAccessEnabled == false`. If disabled, do not open dialog.

---

## 3. Verification Plan
1. **Database Migration Test**:
   Test `MIGRATION_8_9` upgrades Room database from version 8 to 9, preserving existing target apps and defaulting `emergencyAccessEnabled` to 1 (`true`).
2. **Domain & Rule Engine Test**:
   Verify `TargetConfig(emergencyAccessEnabled = false)` maps to `EffectiveInterventionConfig(emergencyAccessEnabled = false)` through `RuleEngine`.
3. **UI Overlay Test**:
   Verify `InterventionContent`, `CodeGateContent`, and `AttentionCheckContent` do not render the `action_emergency` button when `emergencyAccessEnabled = false`.
4. **Target Settings Integration Test**:
   Verify toggling emergency access updates the view model state and persists to database.
