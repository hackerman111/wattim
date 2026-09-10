# Design: Untimed Random Fill Animation Mode (FILL_2)

## Context and Goal
In Wattim, breathing intervention animations serve as a friction mechanism to prevent mindless app opens. The existing `WAVE` animation introduced an untimed experience where the countdown timer is hidden (`revealsRemainingTime = false`), letting the user focus on the breathing rhythm rather than clock-watching.

The goal is to create an analog: `AnimationMode.FILL_2` ("ЗАЛИВКА 2" / "FILL 2"), which:
1. Is untimed (`revealsRemainingTime = false`, hiding the countdown timer text during intervention).
2. Uses the visual appearance of a fill (horizontal line with filled bottom rectangle).
3. Moves non-monotonically, smoothly wandering both up and down to pseudo-random heights over time.
4. Smoothly reaches 100% (full screen) when the session completes (`COMPLETE` phase).

---

## 1. Domain Model and Localization

### 1.1 AnimationMode Enum
In `domain/src/main/kotlin/io/ronesec/domain/model/TargetConfig.kt`:
```kotlin
enum class AnimationMode(
    val revealsRemainingTime: Boolean = true
) {
    FILL,
    PULSE,
    CIRCLE,
    WAVE(revealsRemainingTime = false),
    FILL_2(revealsRemainingTime = false)
}
```
* `revealsRemainingTime = false` ensures `InterventionContent` automatically hides the countdown timer `progress.formattedCountdown`.
* Stored in Room/DataStore as string `"FILL_2"` via standard `AnimationMode.valueOf()` without needing schema migrations.

### 1.2 Localization
* `app/src/main/res/values/strings.xml`:
  ```xml
  <string name="anim_fill_2">FILL 2</string>
  ```
* `app/src/main/res/values-ru/strings.xml`:
  ```xml
  <string name="anim_fill_2">ЗАЛИВКА 2</string>
  ```

---

## 2. Mathematical Model and Rendering

### 2.1 Pseudo-random Smooth Wander in `BreathingGeometry`
In `app/src/main/kotlin/io/ronesec/android/ui/designsystem/BreathingVisuals.kt`:
* **Segment Duration**: `FILL_2_SEGMENT_MS = 1_600L` (every 1.6s it interpolates towards a new target height).
* **Height Range**: $[0.15, 0.85]$ of the screen height.
* **Deterministic Hash Function**:
  ```kotlin
  fun fill2TargetHeight(segmentIndex: Long): Float {
      var x = segmentIndex * 0x9E3779B97F4A7C15L
      x = (x xor (x ushr 30)) * 0xBF58476D1CE4E5B9L
      x = (x xor (x ushr 27)) * 0x94D049BB133111EBL
      val norm = ((x xor (x ushr 31)) and 0x7FFFFFFFL).toFloat() / 0x7FFFFFFFL.toFloat()
      return 0.15f + norm * 0.70f
  }
  ```
* **Smoothstep Interpolation**:
  ```kotlin
  fun fill2HeightFraction(elapsedMs: Long, progress: Float, reducedMotion: Boolean): Float {
      if (reducedMotion) return 0.5f
      if (progress >= 1.0f) return 1.0f

      val currentSegment = (elapsedMs.coerceAtLeast(0L) / FILL_2_SEGMENT_MS)
      val t = (elapsedMs.coerceAtLeast(0L) % FILL_2_SEGMENT_MS).toFloat() / FILL_2_SEGMENT_MS.toFloat()
      val smoothT = t * t * (3f - 2f * t)

      val hStart = fill2TargetHeight(currentSegment)
      val hEnd = fill2TargetHeight(currentSegment + 1L)

      return hStart + (hEnd - hStart) * smoothT
  }
  ```

### 2.2 Visual Rendering
* Function: `DrawScope.drawRandomFill(fraction: Float, colors: WattimColors)`:
  * Fills rectangle from `size.height * (1f - fraction)` to bottom with `colors.accent.copy(alpha = BreathingGeometry.FILL_ALPHA)`.
  * Draws top divider stroke line with `colors.accent` and width `2f`.

---

## 3. UI Integration and Testing

### 3.1 UI Components
* **BreathingCanvas**: routes `AnimationMode.FILL_2` to `drawRandomFill(BreathingGeometry.fill2HeightFraction(effectiveElapsed, effectiveProgress, reducedMotion), colors)`.
* **TargetSettingsScreen**: adds `AnimationMode.FILL_2 to stringResource(R.string.anim_fill_2)` in `AnimationSelectorCard`.
* **ProtectedAppsSection**: maps `AnimationMode.FILL_2 -> stringResource(R.string.anim_fill_2)`.
* **ComponentGallery**: adds preview entry for `AnimationMode.FILL_2`.

### 3.2 Verification & Testing
1. **Unit Tests**:
   * Verify height boundaries $[0.15, 0.85]$ for various elapsed times.
   * Verify non-monotonicity (values both rise and fall over consecutive segments).
   * Verify `1.0f` on completion (`progress >= 1.0f`).
   * Verify `0.5f` on `reducedMotion = true`.
2. **Compose & UI Tests**:
   * Verify countdown timer is hidden for `AnimationMode.FILL_2`.
   * Verify matrix rendering across all 6 themes.
   * Verify setting persistence in `TargetSettingsViewModelTest`.
