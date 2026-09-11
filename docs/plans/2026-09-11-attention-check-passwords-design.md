# Design: Configurable Attention Check Passwords During Waiting (Intervention)

## Context and Goals
1. **Attention Check (Проверка внимания)**: To prevent mindless or passive waiting during the breathing intervention, allow users to configure random attention checks during the wait/breathing cycle.
2. **Random Triggering**: At randomly distributed checkpoints during the breathing animation (between 15% and 85% of total breathing duration), the session pauses, displays a prompt showing a randomly generated numeric code/password of configurable length (e.g. 4 digits), and starts a mini-countdown timer (e.g. 5 seconds).
3. **Success vs. Failure**:
   - **Success**: Entering the correct code unpauses the session; breathing resumes from the exact millisecond where it stopped. If further checkpoints remain, they are scheduled; otherwise, breathing proceeds to completion.
   - **Failure/Timeout**: If the mini-timer expires without the correct code, or the user fails to provide the correct code before time runs out, the entire breathing process resets from the beginning (progress = 0s) with a fresh set of randomized checkpoints.
4. **Configurability**: Each target app can be independently configured in `TargetSettingsScreen`:
   - `attentionChecksEnabled: Boolean` (Toggle)
   - `attentionCheckCount: Int` (1..5 checks per intervention)
   - `attentionCheckCodeLength: Int` (3..8 digits)
   - `attentionCheckTimeoutMs: Long` (3..30 seconds)
5. **Persistence & Lifecycle**: Room database schema bumped to v5 with `MIGRATION_4_5`. Pure JVM domain state machine in `domain` module, ensuring lifecycle safety, process resumption resilience, and pure testability.

---

## 1. Domain Models and Persistence (Room v5)

### 1.1 TargetConfig
In `domain/src/main/kotlin/io/ronesec/domain/model/TargetConfig.kt`:
```kotlin
data class TargetConfig(
    val packageName: String,
    val displayName: String,
    val enabled: Boolean = true,
    val phrase: String = DEFAULT_PHRASE,
    val animation: AnimationMode = AnimationMode.FILL,
    val durationMs: Long = DEFAULT_DURATION_MS,
    val reinterventionMs: Long = DEFAULT_REINTERVENTION_MS,
    val quickReturnGraceMs: Long = DEFAULT_GRACE_MS,
    val growthConfig: BackoffConfig = BackoffConfig(),
    val rowVersion: Long = 1L,
    val twoStageUnlock: Boolean = false,
    val unlockCodeLength: Int = 4,
    val requireEmergencyCode: Boolean = false,
    val randomDurationEnabled: Boolean = false,
    val randomMaxDurationMs: Long = DEFAULT_DURATION_MS,
    val attentionChecksEnabled: Boolean = false,
    val attentionCheckCount: Int = 1,
    val attentionCheckCodeLength: Int = 4,
    val attentionCheckTimeoutMs: Long = 5_000L
) {
    init {
        ...
        require(attentionCheckCount in MIN_ATTENTION_CHECK_COUNT..MAX_ATTENTION_CHECK_COUNT) {
            "Attention check count must be in $MIN_ATTENTION_CHECK_COUNT..$MAX_ATTENTION_CHECK_COUNT, was $attentionCheckCount"
        }
        require(attentionCheckCodeLength in MIN_ATTENTION_CHECK_CODE_LENGTH..MAX_ATTENTION_CHECK_CODE_LENGTH) {
            "Attention check code length must be in $MIN_ATTENTION_CHECK_CODE_LENGTH..$MAX_ATTENTION_CHECK_CODE_LENGTH, was $attentionCheckCodeLength"
        }
        require(attentionCheckTimeoutMs in MIN_ATTENTION_CHECK_TIMEOUT_MS..MAX_ATTENTION_CHECK_TIMEOUT_MS) {
            "Attention check timeout must be in $MIN_ATTENTION_CHECK_TIMEOUT_MS..$MAX_ATTENTION_CHECK_TIMEOUT_MS ms, was $attentionCheckTimeoutMs"
        }
    }

    companion object {
        ...
        const val MIN_ATTENTION_CHECK_COUNT = 1
        const val MAX_ATTENTION_CHECK_COUNT = 5
        const val MIN_ATTENTION_CHECK_CODE_LENGTH = 3
        const val MAX_ATTENTION_CHECK_CODE_LENGTH = 8
        const val MIN_ATTENTION_CHECK_TIMEOUT_MS = 3_000L
        const val MAX_ATTENTION_CHECK_TIMEOUT_MS = 30_000L
    }
}
```

### 1.2 Room Entity and Migration
In `app/src/main/kotlin/io/ronesec/android/data/entity/TargetAppEntity.kt`:
```kotlin
@Entity(tableName = "target_apps")
data class TargetAppEntity(
    @PrimaryKey
    val packageName: String,
    val displayName: String,
    val enabled: Boolean = true,
    val phrase: String = "Сделай глубокий вдох и выдох",
    val animation: String = "FILL",
    val durationMs: Long = 8_000L,
    val reinterventionMs: Long = 300_000L,
    val quickReturnGraceMs: Long = 0L,
    val growthEnabled: Boolean = false,
    val growthPercent: Int = 20,
    val growthWindowMs: Long = 3_600_000L,
    val rowVersion: Long = 1L,
    @ColumnInfo(defaultValue = "0") val twoStageUnlock: Boolean = false,
    @ColumnInfo(defaultValue = "4") val unlockCodeLength: Int = 4,
    @ColumnInfo(defaultValue = "0") val requireEmergencyCode: Boolean = false,
    @ColumnInfo(defaultValue = "0") val randomDurationEnabled: Boolean = false,
    @ColumnInfo(defaultValue = "8000") val randomMaxDurationMs: Long = 8_000L,
    @ColumnInfo(defaultValue = "0") val attentionChecksEnabled: Boolean = false,
    @ColumnInfo(defaultValue = "1") val attentionCheckCount: Int = 1,
    @ColumnInfo(defaultValue = "4") val attentionCheckCodeLength: Int = 4,
    @ColumnInfo(defaultValue = "5000") val attentionCheckTimeoutMs: Long = 5_000L
)
```

In `app/src/main/kotlin/io/ronesec/android/data/WattimDatabase.kt`:
- Schema `version = 5`.
- `MIGRATION_4_5`:
  ```sql
  ALTER TABLE target_apps ADD COLUMN attentionChecksEnabled INTEGER NOT NULL DEFAULT 0;
  ALTER TABLE target_apps ADD COLUMN attentionCheckCount INTEGER NOT NULL DEFAULT 1;
  ALTER TABLE target_apps ADD COLUMN attentionCheckCodeLength INTEGER NOT NULL DEFAULT 4;
  ALTER TABLE target_apps ADD COLUMN attentionCheckTimeoutMs INTEGER NOT NULL DEFAULT 5000;
  ```

---

## 2. Domain Logic and Reducer

### 2.1 Generating Attention Check Schedule
When breathing begins (`Breathing` substate):
- If `effectiveConfig.attentionChecksEnabled == true` and `effectiveConfig.attentionCheckCount > 0`:
  Sample $K$ sorted millisecond offsets $O = [o_1, o_2, \dots, o_K]$ in $[0.15 \cdot D, 0.85 \cdot D]$, where $D = \text{durationMs}$, with minimum separation between checks (e.g. at least 1 second or $(0.7 \cdot D) / (K + 1)$).
- Store remaining offsets in `Breathing` state or `ActiveSession`.
- Schedule first temporal boundary at $t_1 = \text{startElapsedMs} + o_1$.

### 2.2 Substate Transition: `InterveningSubstate.AttentionCheck`
```kotlin
data class AttentionCheck(
    val pausedElapsedProgressMs: Long,
    val durationMs: Long,
    val code: String,
    val deadlineElapsedMs: Long,
    val timeoutMs: Long,
    val remainingCheckOffsetsMs: List<Long>,
    val hasError: Boolean = false
) : InterveningSubstate
```

- When $t_1$ is reached:
  - Transition to `AttentionCheck`.
  - Pause point: `pausedElapsedProgressMs = nowElapsedMs - startElapsedMs`.
  - `code = context.codePort.generate(codeLength)`.
  - `deadlineElapsedMs = nowElapsedMs + timeoutMs`.
  - Schedule temporal boundary for `timeoutMs`.

### 2.3 Events in `AttentionCheck`
- **`SubmitAttentionCheckCode(sessionId, cycle, code)`**:
  - If `code == active.code`:
    - Code is correct! Resume breathing:
      `newStartElapsedMs = nowElapsedMs - pausedElapsedProgressMs`.
      If more check offsets exist, schedule next checkpoint; else schedule deadline for `newStartElapsedMs + durationMs`.
  - If `code != active.code`:
    - Update state with `hasError = true`. User can backspace and retype before deadline.
- **`TemporalBoundaryReached`**:
  - If `nowElapsedMs >= deadlineElapsedMs`:
    - Timeout expired!
    - Reset breathing session to beginning:
      `newStartElapsedMs = nowElapsedMs`, progress restarts from 0, new check offsets are generated.
- **`ActionExit`, `ActionCancel`, `ActionEmergency*`**:
  - Handled cleanly in all substates.

---

## 3. UI Implementation

### 3.1 TargetSettingsScreen
- `AttentionCheckCard`:
  - Switch for `attentionChecksEnabled`.
  - Number of checks stepper (1..5).
  - Code length stepper (3..8 digits).
  - Timeout stepper / slider (3..30 seconds).

### 3.2 Overlay Attention Check Content
- `AttentionCheckContent`:
  - Renders over paused breathing background.
  - Title: "ПРОВЕРКА ВНИМАНИЯ".
  - Monospace code prompt: `8 4 1 9`.
  - Linear countdown timer bar draining down and textual countdown (e.g. "4.2 с").
  - Numeric code input field with auto-submit when full length is typed.
  - Error state with red accent if incorrect code submitted.
  - Buttons: "Выход" and "SOS".

---

## 4. Verification Plan
1. Unit tests:
   - `AttentionCheckTimelineTest`
   - `ProtectionReducerAttentionCheckTest`
   - `TargetConfigValidationTest`
   - `Migration4To5Test`
   - `TargetSettingsViewModelTest`
2. `./gradlew testDebugUnitTest` to ensure 100% test pass rate.
3. `./gradlew assembleDebug` to verify compilation.
