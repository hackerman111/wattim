# Design: Multi-Period Statistics & Reworked Untimed Breathing Animations

## Summary
1. **Multi-Period Statistics**: Expand the statistics engine and `StatsScreen` to support multiple analytical timeframes (`TODAY`, `WEEK` / 7 days, `MONTH` / 30 days, `ALL_TIME`), complete with per-app ranking and a daily activity trend chart for multi-day periods.
2. **Untimed Breathing Animations**: Rework all breathing animations into a unified untimed, mindful aesthetic (`revealsRemainingTime = false`).
   - `FILL_2` becomes `FILL` ("ЗАЛИВКА") with smooth wandering pseudo-random height.
   - Four new/reworked animations matching the same organic, non-countdown style:
     - `PULSE` ("ПУЛЬС"): smooth harmonic sine breathing orb with soft halo glow.
     - `WAVE` ("ВОЛНА"): multi-layered fluid harmonic ocean waves.
     - `ORBIT` ("ОРБИТА"): celestial constellation of particles with phase-shifted trails.
     - `RIPPLE` ("ДЗЕН"): concentric calm water ripples radiating smoothly from the center.
3. **Animation Selector UI**: Replace the single-column vertical button stack in `TargetSettingsScreen` with an ergonomic 2-column grid of interactive cards featuring live mini Canvas previews.

---

## 1. Domain & Data Architecture

### 1.1 Timeframe Domain Model
In `domain/src/main/kotlin/io/ronesec/domain/model/StatsPeriod.kt`:
```kotlin
enum class StatsPeriod {
    TODAY,
    WEEK,
    MONTH,
    ALL_TIME
}
```

### 1.2 Database Queries (`StatisticsDao`)
`open_attempts` stores every attempt with `timestamp` (epoch ms) and `outcome`.
We add time-bounded queries:
* `getSummary(startEpochMs: Long, endEpochMs: Long): PeriodSummaryRow`
* `getPerAppStats(startEpochMs: Long, endEpochMs: Long): List<PerAppStatRow>`
* `getDailyBreakdown(startEpochMs: Long, endEpochMs: Long): List<DailyStatRow>` where `DailyStatRow` contains `(dayStartMs: Long, totalAttempts: Int, closedCount: Int)`.

For `ALL_TIME`, `startEpochMs = 0L`.

### 1.3 Repository Layer (`StatisticsStore`)
* Encapsulates day and multi-day boundary calculations based on `wallClock.now()` and `wallClock.zoneId()`.
* Methods:
  - `getSummary(period: StatsPeriod, now: Instant, zoneId: ZoneId): PeriodSummary`
  - `getPerAppStats(period: StatsPeriod, now: Instant, zoneId: ZoneId): List<AppStatsRow>`
  - `getDailyActivity(period: StatsPeriod, now: Instant, zoneId: ZoneId): List<DailyActivityPoint>`

### 1.4 Backwards Compatibility for `AnimationMode`
In `TargetConfig.kt`:
```kotlin
enum class AnimationMode(
    val revealsRemainingTime: Boolean = false
) {
    FILL,
    PULSE,
    WAVE,
    ORBIT,
    RIPPLE
}
```
In `PolicyCompiler.kt`:
Safely deserialize legacy strings:
- `"FILL_2"` -> `AnimationMode.FILL`
- `"CIRCLE"` -> `AnimationMode.ORBIT`
- Unrecognized or null -> `AnimationMode.FILL`

---

## 2. Visuals & Breathing Geometry

### 2.1 Principles
* All 5 animations have `revealsRemainingTime = false`.
* No countdown text is shown during the breathing phase; only "ГОТОВО" (`BreathingPhase.COMPLETE`) is shown when time expires.
* All animations move continuously based on `elapsedMs`, without exposing remaining seconds.
* When `progress >= 1.0f`, each animation smoothly resolves to its complete state.
* When `reducedMotion = true`, each animation renders a calm, stationary composition.

### 2.2 Mathematical Specifications
1. **`FILL` (Wandering Fill)**:
   - Uses `BreathingGeometry.fillHeightFraction(elapsedMs, progress, reducedMotion)`.
   - Segments of 1,600ms, smoothstep interpolation between pseudo-random heights $[0.15, 0.85]$.
   - Fills to $1.0$ when complete.
2. **`PULSE` (Harmonic Breathing Orb)**:
   - Base sine wave `sin(2 * PI * elapsedMs / 4500ms)` simulating 4.5-second breathing cycle.
   - Outer and inner soft halos with accent colors.
3. **`WAVE` (Harmonic Ocean Waves)**:
   - Two overlayed harmonic waves moving across the screen with differing wavelengths and speeds.
   - Filled path below with smooth wave surface line.
4. **`ORBIT` (Celestial Constellation)**:
   - Constellation of 12 celestial nodes rotating around center with varying radii, speeds, and soft glowing tails.
5. **`RIPPLE` (Zen Water Rings)**:
   - Concentric circles radiating outward from center, fading out as radius expands.

---

## 3. UI & Interaction Design

### 3.1 `StatsScreen`
* **Period Selector**: Row of 4 chips (`Сегодня`, `7 дней`, `30 дней`, `Всё время`).
* **Period Summary Card**: Total attempts, avoided count, avoided percentage for the selected period.
* **Daily Trend Card**: Shown for `WEEK` and `MONTH` periods, displaying bar chart of daily activity.
* **Per-App Table**: Ordered by total attempts descending for the selected period.

### 3.2 `AnimationSelectorCard`
* 2-column grid of interactive cards.
* Each card includes:
  - Mini `BreathingCanvas` box (previewing animation with current theme accent).
  - Uppercase title label.
  - Distinct border accent when selected.
  - Touch target $\ge 48\text{dp}$.
