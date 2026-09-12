# Design: Annoying Unlock Mode and Enhanced Attention Check Failure Visibility

## Context and Goals
1. **Annoying Unlock Mode (Бесячая разблокировка)**:
   - When configured, each typed digit during an attention check has a user-configurable chance (1% to 100%, default 20%) of being dropped / ignored, simulating an annoying missed tap or screen touch failure.
   - Configurable per target application in `TargetSettingsScreen` inside the `AttentionCheckCard`.
   - Settings UI includes a toggle, a numeric text input field for percentage with 1..100 validation, and `-5%` / `+5%` quick-step adjustment buttons.
2. **Enhanced Attention Check Failure Visibility**:
   - When an attention check fails due to timeout:
     - The state machine enters an explicit 1000ms expiration phase (`isExpired = true`), scheduling a 1-second `TemporalBoundary`.
     - In the UI, the card border turns red, the countdown indicator stays at 0.0s, the message `attention_check_timeout_message` ("Время истекло! Начинаем заново...") is shown, and input is locked.
     - Once the 1000ms boundary elapses, `startBreathing` resets the breathing intervention from 0s.
   - When an incorrect code is submitted:
     - The UI locks error visibility for 1000ms: the red border and `attention_check_error` ("Неверный код") message remain visible for 1 second before reverting, and the input field is cleared.

---

## 1. Domain Models and Persistence

### 1.1 TargetConfig and EffectiveInterventionConfig
In `domain/src/main/kotlin/io/ronesec/domain/model/TargetConfig.kt`:
```kotlin
data class TargetConfig(
    ...
    val annoyingUnlockEnabled: Boolean = false,
    val annoyingUnlockChancePercent: Int = DEFAULT_ANNOYING_UNLOCK_CHANCE_PERCENT
) {
    init {
        ...
        require(annoyingUnlockChancePercent in MIN_ANNOYING_UNLOCK_CHANCE_PERCENT..MAX_ANNOYING_UNLOCK_CHANCE_PERCENT) {
            "Annoying unlock chance must be between $MIN_ANNOYING_UNLOCK_CHANCE_PERCENT and $MAX_ANNOYING_UNLOCK_CHANCE_PERCENT, was $annoyingUnlockChancePercent"
        }
    }

    companion object {
        ...
        const val MIN_ANNOYING_UNLOCK_CHANCE_PERCENT = 1
        const val MAX_ANNOYING_UNLOCK_CHANCE_PERCENT = 100
        const val DEFAULT_ANNOYING_UNLOCK_CHANCE_PERCENT = 20
    }
}
```
And in `EffectiveInterventionConfig.kt`:
```kotlin
data class EffectiveInterventionConfig(
    ...
    val annoyingUnlockEnabled: Boolean = false,
    val annoyingUnlockChancePercent: Int = 20
)
```

### 1.2 Room Entity and Migration 6 -> 7
In `app/src/main/kotlin/io/ronesec/android/data/entity/TargetAppEntity.kt`:
```kotlin
@ColumnInfo(defaultValue = "0") val annoyingUnlockEnabled: Boolean = false,
@ColumnInfo(defaultValue = "20") val annoyingUnlockChancePercent: Int = 20
```

In `app/src/main/kotlin/io/ronesec/android/data/WattimDatabase.kt`:
- Increase database version to 7:
```kotlin
@Database(
    entities = [TargetAppEntity::class, ...],
    version = 7,
    exportSchema = true
)
```
- Add migration:
```kotlin
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE target_apps ADD COLUMN annoyingUnlockEnabled INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE target_apps ADD COLUMN annoyingUnlockChancePercent INTEGER NOT NULL DEFAULT 20")
    }
}
```

---

## 2. State Machine & Domain Logic

### 2.1 InterveningSubstate & AttentionCheckUi
In `domain/src/main/kotlin/io/ronesec/domain/protection/ProtectionState.kt`:
```kotlin
data class AttentionCheck(
    val pausedElapsedProgressMs: Long,
    val durationMs: Long,
    val code: String,
    val deadlineElapsedMs: Long,
    val timeoutMs: Long,
    val remainingCheckOffsetsMs: List<Long> = emptyList(),
    val hasError: Boolean = false,
    val isExpired: Boolean = false
) : InterveningSubstate
```

In `domain/src/main/kotlin/io/ronesec/domain/protection/SessionCodes.kt`:
```kotlin
data class AttentionCheckUi(
    val active: Boolean,
    val code: String = "",
    val deadlineElapsedMs: Long = 0L,
    val timeoutMs: Long = 0L,
    val pausedElapsedProgressMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val hasError: Boolean = false,
    val isExpired: Boolean = false
)
```

### 2.2 CodeChallengeReducer
- On `TemporalBoundaryReached`:
  ```kotlin
  if (active.substate is InterveningSubstate.AttentionCheck) {
      if (!active.substate.isExpired && context.nowElapsedMs >= active.substate.deadlineElapsedMs) {
          val holdUntil = context.nowElapsedMs + 1_000L
          val expiredSubstate = active.substate.copy(
              isExpired = true,
              deadlineElapsedMs = holdUntil
          )
          return update(
              active.copy(substate = expiredSubstate),
              context,
              listOf(ProtectionEffect.ScheduleTemporalBoundary(1_000L, holdUntil))
          )
      } else if (active.substate.isExpired && context.nowElapsedMs >= active.substate.deadlineElapsedMs) {
          return startBreathing(active, context)
      }
  }
  ```
- On `SubmitAttentionCheckCode`:
  ```kotlin
  if (active.substate.isExpired) return unchanged()
  ```

---

## 3. UI and User Interaction

### 3.1 AttentionCheckContent
1. **Timeout display**:
   - When `attentionCheck.isExpired`:
     - Card border is `colors.error`.
     - Timer displays `0.0s` with error color.
     - Error text displays `stringResource(R.string.attention_check_timeout_message)`.
     - Input field `enabled = false`.
2. **Incorrect code display**:
   - When `attentionCheck.hasError`:
     - Holds error display for 1000ms (`isShowingWrongCode = true` with `delay(1000L)`).
     - Card border is `colors.error`.
     - Error text displays `stringResource(R.string.attention_check_error)`.
     - Input is cleared.
3. **Annoying Unlock digit drop**:
   - On typing into `BasicTextField`:
     - If new length > previous length (user entered digit(s)):
     - For each new digit, if `config.annoyingUnlockEnabled`:
       - Roll `Random.nextInt(100) < config.annoyingUnlockChancePercent`.
       - If roll is true: digit is dropped.
       - If roll is false: digit is kept.

### 3.2 AttentionCheckCard in Settings
- Toggle for "Бесячая разблокировка" (`R.string.target_attention_check_annoying_title`).
- Description: `R.string.target_attention_check_annoying_desc`.
- When enabled:
  - Percentage input row with numeric `BasicTextField` (showing value + "%").
  - `-5%` and `+5%` `TerminalButton`s clamped to `1..100`.

---

## 4. Verification Plan
- Pure JVM unit tests in `:domain` for `TargetConfig` validation and `AttentionCheckReducerTest` for two-phase timeout.
- Room database migration unit test `Migration6To7Test`.
- Compose tests in `AttentionCheckContentTest` verifying:
  - `isExpired` renders `attention_check_timeout_message` and error border.
  - Annoying unlock drops characters when enabled.
- Settings viewmodel tests in `TargetSettingsViewModelTest`.
