# Design: Random Attention Check Count Configuration

## Context and Goals
1. **Random Attention Check Count (Случайное число проверок на внимательность)**: Allow users to specify whether the number of attention checks during an intervention should be randomly sampled within a configurable range `[min, max]`.
2. **Bounds & Constraints**:
   - Both minimum and maximum must fall within `[1..5]` (`MIN_ATTENTION_CHECK_COUNT` to `MAX_ATTENTION_CHECK_COUNT`).
   - When random check count is enabled, `minCount <= maxCount` is strictly enforced.
3. **Session Stability**:
   - The concrete count is sampled when `RuleEngine` makes the intervention decision (`Decision.Intervention`), preserving a predictable, immutable contract inside `EffectiveInterventionConfig` for the lifetime of that intervention session.
   - If an attention check fails or times out, the session resets breathing to 0 seconds and re-distributes checkpoints for the same sampled session count.
4. **Target Settings UI**:
   - Toggle switch in `AttentionCheckCard`: "Случайное число проверок" / "Random check count".
   - When disabled: single stepper for `attentionCheckCount` (1..5).
   - When enabled: two steppers — "Минимум проверок" (1..max) and "Максимум проверок" (min..5).
5. **Persistence**:
   - Room database schema upgraded from v5 to v6 with `MIGRATION_5_6`.
   - Preserves all existing user target app data.

---

## 1. Domain Models and Validation

### 1.1 `TargetConfig` (`domain/.../model/TargetConfig.kt`)
Add the following properties:
- `attentionCheckRandomCountEnabled: Boolean = false`
- `attentionCheckMinCount: Int = 1`
- `attentionCheckMaxCount: Int = 1`

Validation invariants:
```kotlin
require(attentionCheckCount in MIN_ATTENTION_CHECK_COUNT..MAX_ATTENTION_CHECK_COUNT) {
    "Attention check count must be between $MIN_ATTENTION_CHECK_COUNT and $MAX_ATTENTION_CHECK_COUNT, was $attentionCheckCount"
}
require(attentionCheckMinCount in MIN_ATTENTION_CHECK_COUNT..MAX_ATTENTION_CHECK_COUNT) {
    "Attention check min count must be between $MIN_ATTENTION_CHECK_COUNT and $MAX_ATTENTION_CHECK_COUNT, was $attentionCheckMinCount"
}
require(attentionCheckMaxCount in MIN_ATTENTION_CHECK_COUNT..MAX_ATTENTION_CHECK_COUNT) {
    "Attention check max count must be between $MIN_ATTENTION_CHECK_COUNT and $MAX_ATTENTION_CHECK_COUNT, was $attentionCheckMaxCount"
}
if (attentionCheckRandomCountEnabled) {
    require(attentionCheckMinCount <= attentionCheckMaxCount) {
        "Attention check min count ($attentionCheckMinCount) must be <= max count ($attentionCheckMaxCount)"
    }
}
```

---

## 2. Policy Evaluation and Engine

### 2.1 `RuleEngine` (`domain/.../policy/RuleEngine.kt`)
Injectable provider for sampling check counts:
```kotlin
randomAttentionCheckCountProvider: (min: Int, max: Int) -> Int = { min, max ->
    if (max > min) java.util.concurrent.ThreadLocalRandom.current().nextInt(min, max + 1) else min
}
```

Evaluation resolution:
```kotlin
val resolvedAttentionCheckCount = if (target.attentionChecksEnabled && target.attentionCheckRandomCountEnabled) {
    randomAttentionCheckCountProvider(target.attentionCheckMinCount, target.attentionCheckMaxCount)
        .coerceIn(target.attentionCheckMinCount, target.attentionCheckMaxCount)
} else {
    target.attentionCheckCount
}
```
Assign `resolvedAttentionCheckCount` to `EffectiveInterventionConfig.attentionCheckCount`.

---

## 3. Room Persistence and Migrations

### 3.1 `TargetAppEntity` (`app/.../data/entity/TargetAppEntity.kt`)
Add new columns:
```kotlin
@ColumnInfo(defaultValue = "0") val attentionCheckRandomCountEnabled: Boolean = false,
@ColumnInfo(defaultValue = "1") val attentionCheckMinCount: Int = 1,
@ColumnInfo(defaultValue = "1") val attentionCheckMaxCount: Int = 1,
```

### 3.2 `WattimDatabase` (`app/.../data/WattimDatabase.kt`)
- Bump `version = 6`.
- Add `MIGRATION_5_6`:
```kotlin
val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE target_apps ADD COLUMN attentionCheckRandomCountEnabled INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE target_apps ADD COLUMN attentionCheckMinCount INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE target_apps ADD COLUMN attentionCheckMaxCount INTEGER NOT NULL DEFAULT 1")
    }
}
```
- Update `PolicyCompiler` to map these 3 fields between `TargetAppEntity` and `TargetConfig`.

---

## 4. UI Layer

### 4.1 `AttentionCheckCard` (`app/.../ui/target/AttentionCheckCard.kt`)
Parameters expanded to accept:
- `randomCountEnabled: Boolean`
- `minCount: Int`
- `maxCount: Int`
- `onToggleRandomCount: () -> Unit`
- `onMinCountChange: (Int) -> Unit`
- `onMaxCountChange: (Int) -> Unit`

Rendering:
- When attention checks enabled:
  - Toggle for random count.
  - If random count disabled: single stepper for `attentionCheckCount` (1..5).
  - If random count enabled:
    - Stepper for `minCount` (1..maxCount).
    - Stepper for `maxCount` (minCount..5).
  - Steppers for code length and timeout.

### 4.2 `TargetSettingsViewModel` and `TargetSettingsUiState`
- Add draft state fields `attentionCheckRandomCountEnabled`, `attentionCheckMinCount`, `attentionCheckMaxCount`.
- Add event handlers:
  - `onToggleAttentionCheckRandomCount()`
  - `onAttentionCheckMinCountChange(min: Int)`
  - `onAttentionCheckMaxCountChange(max: Int)`
- When saving, persist all fields into `PolicyStore`.

---

## 5. Testing Plan
- `TargetConfigAttentionCheckTest`: tests validation bounds (1..5, min <= max).
- `RuleEngineTest` / `RandomAttentionCheckPolicyTest`: tests sampling logic and provider overrides.
- `Migration5To6Test`: verifies SQLite schema migration and default values.
- `TargetSettingsViewModelTest`: verifies UI state mutations, clamping, and save persistence.
