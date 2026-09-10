# Design: Feature Explanation Dialogs, Custom Emergency Duration, and Backoff Growth Fix

## Overview
This document specifies three enhancements to Wattim:
1. Interactive terminal-styled question mark badges (`TerminalHelpCircle`) on [`TargetSettingsScreen`](file:///home/papayka/Rust_project/wattim/app/src/main/kotlin/io/ronesec/android/ui/target/TargetSettingsScreen.kt) providing informative modals ([`TerminalInfoDialog`](file:///home/papayka/Rust_project/wattim/app/src/main/kotlin/io/ronesec/android/ui/target/TerminalInfoDialog.kt)) explaining core features.
2. Configuration of a custom duration for emergency access in [`ConfigScreen`](file:///home/papayka/Rust_project/wattim/app/src/main/kotlin/io/ronesec/android/ui/config/ConfigScreen.kt), dynamically adding an emergency badge in [`EmergencyDialog`](file:///home/papayka/Rust_project/wattim/app/src/main/kotlin/io/ronesec/android/ui/intervention/EmergencyDialog.kt) and allowing it to be removed to restore defaults.
3. Preventing exponential backoff delay growth when the user exits the breathing intervention (exiting via "ВЫХОД", "ОТМЕНА", or system Back button), ensuring only confirmed entries (`outcome = 'CONTINUED'`) increment backoff counts while preserving full statistics for the Stats screen.

---

## 1. Feature Explanations (`(?)` badges & `TerminalInfoDialog`)

### UI Components
- **`TerminalHelpCircle`**:
  - Diameter: 20dp. Touch target: 48×48dp for compliance with Android Accessibility Guidelines.
  - Shape: circular border with theme accent / border color, elevated surface background.
  - Text: monospace `?`, semi-bold, accent color.
  - Role: `Role.Button`, with semantic content description.
- **`TerminalInfoDialog`**:
  - Modal with 75% black scrim.
  - Themed container card with border.
  - Feature title (accent color, bold uppercase).
  - Feature description in Russian (concise, clear explanation).
  - Primary button: «ПОНЯТНО» dismissing the dialog.

### Feature Placements on `TargetSettingsScreen`
1. **Длительность паузы (Вдох и выдох)**:
   - *Explanation*: «Осознанная пауза перед открытием приложения. Она позволяет прервать автоматический импульс и решить, действительно ли вам нужно это приложение прямо сейчас. Таймер делится пополам на фазы вдоха и выдоха.»
2. **Повторное вмешательство**:
   - *Explanation*: «Ограничение времени непрерывной сессии. Если включено, экран дыхания снова появится через заданный интервал использования приложения, напоминая сделать паузу.»
3. **Быстрое возвращение**:
   - *Explanation*: «Время после выхода из приложения, в течение которого вы можете вернуться назад без повторного экрана дыхания (например, чтобы ответить на звонок или скопировать код подтверждения).»
4. **Экспоненциальный рост**:
   - *Explanation*: «Каждое повторное открытие приложения в течение скользящего окна увеличивает длительность дыхания на выбранный процент. Это мягко охлаждает привычку проверять приложение слишком часто.»
5. **Быстрая блокировка**:
   - *Explanation*: «Мгновенная жёсткая блокировка приложения на выбранный срок (15 мин, 30 мин, 1 час или 2 часа). Во время действия блокировки войти в приложение нельзя даже через паузу дыхания.»

---

## 2. Custom Emergency Access Duration

### Persistence & Data Flow
- **`AppSettingsEntity`**:
  - Add field: `val customEmergencyMinutes: Int? = null`.
- **`WattimDatabase`**:
  - Bump database version to 2.
  - Provide `MIGRATION_1_2`: `ALTER TABLE app_settings ADD COLUMN customEmergencyMinutes INTEGER DEFAULT NULL`.
- **`PresentationSettings`**:
  - Add field: `val customEmergencyMinutes: Int? = null`.
- **`PolicyStore`**:
  - Compile `customEmergencyMinutes` into `PresentationSettings`.
  - Add `suspend fun setCustomEmergencyMinutes(minutes: Int?): Result<Unit>`.

### General Settings Screen (`ConfigScreen`)
- Add **`EmergencyAccessConfigCard`**:
  - Displays current custom interval state.
  - Stepper controls (`[-]`, `[+]`) and text display in minutes (clamped between 1 and 720 minutes).
  - When unset: button «ДОБАВИТЬ В МЕНЮ» sets the custom duration.
  - When set: shows active interval and button «УБРАТЬ ИЗ МЕНЮ» (sets `customEmergencyMinutes = null` to revert to default presets).

### Emergency Dialog (`EmergencyDialog`)
- Consumes `customEmergencyMinutes: Int?` from `OverlayPresenter` / `PresentationSettings`.
- If `customEmergencyMinutes != null`:
  - Adds an extra `TerminalBadge` in the timed row: `"${customEmergencyMinutes}МИН"`.
  - Tapping it triggers `onEmergencyTimed(customEmergencyMinutes * 60 * 1000L)`.
- If `customEmergencyMinutes == null`:
  - Renders the default row only: `[15МИН] [30МИН] [1Ч] [НАВСЕГДА]`.

---

## 3. Exponential Backoff Growth Fix on Exit

### Root Cause
When an intervention starts, `ProtectionReducer` appends an entry to `uncommittedHistoryDeltas`, and an attempt record is created in `open_attempts` table.
When the user exits via "ВЫХОД" (Exit), "ОТМЕНА" (Cancel), or system Back:
1. `uncommittedHistoryDeltas` was retained in memory in `ProtectionReducer`.
2. `OpenAttemptDao.getAllRecentEntryTimestamps` and `getRecentEntryAttempts` queried:
   `SELECT packageName, timestamp FROM open_attempts WHERE kind = 'ENTRY' AND timestamp > :sinceTimestamp`
   This query included all entry attempts regardless of outcome (`ABANDONED` included).
Consequently, every exit inflated the count of prior entries $N$, increasing the exponential delay $T = T_{base} \times (1 + r/100)^N$ for future attempts.

### Solution
1. **`OpenAttemptDao.kt`**:
   Update `getAllRecentEntryTimestamps` and `getRecentEntryAttempts` to query only successful entries:
   ```sql
   SELECT packageName, timestamp FROM open_attempts
   WHERE kind = 'ENTRY' AND outcome = 'CONTINUED' AND timestamp > :sinceTimestamp
   ORDER BY timestamp ASC
   ```
   Attempts resulting in `outcome = 'ABANDONED'` or `outcome = 'BLOCKED'` or `outcome = 'INTERRUPTED'` are excluded from backoff calculations.
2. **`ProtectionReducer.kt`**:
   On `ProtectionEvent.ActionExit` and `ProtectionEvent.ActionCancel`:
   Remove the pending timestamp delta for `currentState.session.packageName` from `uncommittedHistoryDeltas`.
3. **Statistics Integrity**:
   `StatisticsDao` continues to aggregate all product-visible attempts (`CONTINUED`, `ABANDONED`, `BLOCKED`). Exiting during intervention still correctly records an avoided impulse and saved time in stats.

---

## Verification & Testing
1. **Unit Tests**:
   - `T03_BackoffHistoryRoomTest`: verify that `ABANDONED` entry does not increment backoff count, while `CONTINUED` entry does.
   - `T04_ProtectionReducerTest`: verify that `ActionExit` purges `uncommittedHistoryDeltas`.
   - `TargetSettingsUiTest`: verify clicking `TerminalHelpCircle` opens `TerminalInfoDialog` with correct text.
   - `ConfigScreenTest` / `EmergencyDialogTest`: verify custom emergency minutes setting, addition to dialog, and removal restoring default badges.
2. **Regression Run**: Full `./gradlew testDebugUnitTest` suite to verify existing guarantees.
