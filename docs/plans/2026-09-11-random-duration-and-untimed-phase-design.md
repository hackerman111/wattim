# Design: Untimed Phase Text Removal and Configurable Random Duration Mode

## Context and Goals
1. **Untimed Intervention Polish**: For animations where the countdown timer is hidden (`revealsRemainingTime == false`, such as `WAVE` and `FILL_2`), remove the breathing instruction text ("ВДОХ" / "ВЫДОХ") during the breathing phase so that nothing clutters the visual breathing experience. Show "ГОТОВО" only upon completion (`COMPLETE` phase).
2. **Random Duration Mode**: Allow users to configure a random wait/intervention duration for target apps. The existing duration slider acts as the minimum duration ($T_{min}$), and when the "Random Duration" toggle is enabled, users can set a maximum duration ($T_{max} \ge T_{min}$, up to 120s). The intervention duration is uniformly sampled in discrete seconds $[T_{min}, T_{max}]$ on each entry attempt.
3. **Integration with `main`**: Branch `main` contains ongoing 2FA / code challenge development (`cc935ef`) and Room schema v3. Changes must be cleanly integrated, elevating the schema to v4, maintaining backwards compatibility and 100% passing tests across both feature sets.

---

## 1. Domain Model and Persistence (Room v4)

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
    val randomMaxDurationMs: Long = DEFAULT_DURATION_MS
) {
    init {
        ...
        require(randomMaxDurationMs in MIN_DURATION_MS..MAX_DURATION_MS) {
            "Random max duration must be between $MIN_DURATION_MS and $MAX_DURATION_MS ms, was $randomMaxDurationMs"
        }
        if (randomDurationEnabled) {
            require(randomMaxDurationMs >= durationMs) {
                "Random max duration ($randomMaxDurationMs ms) cannot be less than base duration ($durationMs ms)"
            }
        }
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
    @ColumnInfo(defaultValue = "8000") val randomMaxDurationMs: Long = 8_000L
)
```

In `app/src/main/kotlin/io/ronesec/android/data/WattimDatabase.kt`:
- Schema `version = 4`.
- `MIGRATION_3_4`:
  ```sql
  ALTER TABLE target_apps ADD COLUMN randomDurationEnabled INTEGER NOT NULL DEFAULT 0;
  ALTER TABLE target_apps ADD COLUMN randomMaxDurationMs INTEGER NOT NULL DEFAULT 8000;
  ```

---

## 2. Domain Logic (`RuleEngine`)

### 2.1 Effective Duration Sampling
When evaluating decisions for a target in `RuleEngine.evaluate`:
- If `target.randomDurationEnabled == true`:
  $$T_{min} = \text{baseDurationMs}$$
  $$T_{max} = \max(T_{min}, \text{target.randomMaxDurationMs})$$
  $$T_{sampled} = \text{randomDurationProvider}(T_{min}, T_{max})$$
  Default provider samples in 1-second steps:
  ```kotlin
  val randomDurationProvider: (Long, Long) -> Long = { min, max ->
      if (max > min) {
          val minSec = min / 1000L
          val maxSec = max / 1000L
          (minSec..maxSec).random() * 1000L
      } else min
  }
  ```
- Backoff calculation is then applied to $T_{sampled}$:
  $$\text{effectiveDurationMs} = \text{Backoff.resolveEffectiveDurationMs}(T_{sampled}, \text{target.growthConfig}, \text{priorEntryCount})$$

---

## 3. UI Changes

### 3.1 Intervention Overlay (`InterventionContent.kt`)
Phase label visibility is adjusted:
```kotlin
val showPhaseText = config.animation.revealsRemainingTime || progress.phase == BreathingPhase.COMPLETE

if (showPhaseText) {
    val phaseText = when (progress.phase) {
        BreathingPhase.INHALE -> stringResource(R.string.breathing_inhale)
        BreathingPhase.EXHALE -> stringResource(R.string.breathing_exhale)
        BreathingPhase.COMPLETE -> stringResource(R.string.breathing_complete)
    }

    Text(
        text = phaseText,
        style = typography.titleMedium,
        color = colors.accent,
        textAlign = TextAlign.Center
    )
}
```

### 3.2 Target Settings (`TargetSettingsScreen.kt`)
- Add `RandomDurationCard` underneath the duration editor:
  - Toggle switch for `randomDurationEnabled`.
  - Description explaining the randomized delay.
  - When enabled, display stepper/slider for `randomMaxDurationSeconds` with range $[T_{min}, 120]$.
  - Automatic clamp: if `durationSeconds` exceeds `randomMaxDurationSeconds`, adjust `randomMaxDurationSeconds = durationSeconds`.

---

## 4. Verification and Git Merge

1. Pre-merge: `git merge main` into working branch.
2. Implementation and test verification:
   - `RuleEngineTest`: Verify random range sampling and backoff integration.
   - `TargetConfigTest`: Verify range constraints and validation.
   - `InterventionContentTest`: Verify hidden phase text during inhale/exhale for untimed mode and display of COMPLETE.
   - `TargetSettingsViewModelTest`: Verify UI state mapping, clamping, and persistence.
   - Room migration test for 3 -> 4.
3. Verification command: `./gradlew testDebugUnitTest`.
4. Final merge: Checkout `main` and merge working branch.
