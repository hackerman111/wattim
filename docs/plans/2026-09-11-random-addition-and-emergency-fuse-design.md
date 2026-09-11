# Design Document: Random Addition and Emergency Code Fuse

**Date:** 2026-09-11  
**Status:** Approved

## 1. Problem Statement

1. **Conflict between Random Duration and Exponential Backoff:**
   In the previous implementation, when random duration was enabled, a value was sampled in $[T_{min}, T_{max}]$ and then multiplied by $(1 + r)^N$ during backoff calculations. This caused the random variation to exponentially inflate with every subsequent entry attempt, creating erratic delay spikes and conflicting with predictable exponential discipline.
   Furthermore, the user wants random duration to behave as an additive jitter $\Delta_{random} \in [0, t_{random}]$ rather than an upper ceiling bound that restricts base duration.

2. **Emergency Code Flow & Safety Fuse:**
   The emergency access code was previously displayed preemptively at the bottom of the intervention / breathing screen, distracting the user. In addition, only timed access (`ActionEmergencyTimed`) validated the code, while "Enter Once" (`ActionEmergencyOnce`) and "Forever" (`ActionEmergencyForever`) bypassed code checks.
   The emergency code should instead act as a safety fuse:
   - Hidden until the user presses "Emergency Access" (`ЭКСТРЕННЫЙ ВХОД`).
   - Displayed clearly inside `EmergencyDialog`.
   - The user must enter this code to unlock all emergency access actions ("Enter Once", 15m, 30m, 1h, custom minutes, and forever). The buttons must remain disabled until the correct code is entered.

---

## 2. Architecture and Data Flow

### 2.1 Random Addition Formula (`RuleEngine`)
- Target configuration stores $t_{random}$ (`randomMaxDurationMs`, non-negative, up to 120,000 ms).
- When `randomDurationEnabled == true` and $t_{random} > 0$:
  $$\Delta_{random} = \text{randomDurationProvider}(0, t_{random})$$
- Backoff is computed strictly on the configured base duration $T_{base}$:
  $$T_{backoff} = \text{Backoff.resolveEffectiveDurationMs}(T_{base}, \text{growthConfig}, N)$$
- The final effective delay is the sum:
  $$T_{effective} = \text{clamp}(T_{backoff} + \Delta_{random}, 1000\,\text{ms}, 300\,000\,\text{ms})$$
- When `randomDurationEnabled == false`: $\Delta_{random} = 0$, so $T_{effective} = T_{backoff}$.
- `EffectiveInterventionConfig.baseDurationMs` retains the pure $T_{base}$.

### 2.2 Target Settings & UI Controls
- `TargetConfig`:
  - Validates `randomMaxDurationMs in 0L..MAX_DURATION_MS`.
  - Removes the constraint `randomMaxDurationMs >= durationMs`.
- `TargetSettingsViewModel`:
  - Decouples `randomMaxDurationSeconds` from `durationSeconds`. Changing `durationSeconds` does not alter or clamp `randomMaxDurationSeconds`.
  - `onRandomMaxDurationChange(seconds)` clamps to `0..120`.
- `RandomDurationCard`:
  - Header: `target_random_duration_title` ("СЛУЧАЙНАЯ ДОБАВКА").
  - Description: `target_random_duration_desc` ("Случайная добавка к паузе от 0 до заданного значения при каждом запуске").
  - Label: `target_random_max_duration_label` ("СЛУЧАЙНАЯ ДОБАВКА (ДО)").
  - Value display: `+$seconds с` (or `+$seconds sec`).
  - Stepper controls: `-5s`, `-1s`, `+1s`, `+5s` operating on range `0..120`.

### 2.3 Emergency Code Fuse Mechanics
- **Intervention Screen & Code Gate Screen (`InterventionContent.kt`, `CodeGateContent.kt`):**
  - Remove `EmergencyCodeLabel` from the main intervention and code gate layouts.
- **Emergency Dialog (`EmergencyDialog.kt`):**
  - When `requireCode == true`:
    - Display the emergency code banner prominently inside the dialog:
      - Title/Label: `КОД ПРЕДОХРАНИТЕЛЯ: <CODE>`
      - Subtext: `Введите код выше для разблокировки`
    - `DigitCodeInput` (or numeric text input) with 10 digits.
    - Safety fuse state:
      `val isUnlocked = !requireCode || (code == emergencyCode)`
    - Buttons:
      - "ВОЙТИ РАЗОВО": `enabled = isUnlocked`
      - Timed options (15m, 30m, 1h, custom, forever): `enabled = isUnlocked` with dimmed alpha (0.38) when locked.
      - "ВЕРНУТЬСЯ К ДЫХАНИЮ": always active (`enabled = true`).
- **Domain Layer (`CodeChallengeReducer.kt`):**
  - Intercept and validate `ActionEmergencyOnce`, `ActionEmergencyTimed`, and `ActionEmergencyForever`.
  - All three trigger `checkEmergency(active, event.sessionId, event.cycle, event.code, context)`.
  - If code does not match or is missing when `requireEmergencyCode == true`, access is rejected and `emergencyError = true` is set on session state.

---

## 3. Verification Plan

1. **Unit Tests (Domain):**
   - `RandomDurationPolicyTest`:
     - Test validation allows $t_{random} < T_{base}$ and $t_{random} \ge 0$.
     - Test $\Delta_{random}$ sampled uniformly from $0$ to $t_{random}$ and added to $T_{base}$.
     - Test backoff applies to $T_{base}$ and $\Delta_{random}$ is added on top.
   - `SessionCodesTest`:
     - Test `ActionEmergencyOnce`, `ActionEmergencyTimed`, and `ActionEmergencyForever` all require the emergency code when `requireEmergencyCode == true`.
     - Test correct code grants access for each event.
     - Test invalid or omitted code sets error and denies grant.
2. **Unit Tests (UI / ViewModel):**
   - `TargetSettingsViewModelTest`:
     - Verify draft updates for random duration addition independently from base duration.
   - `EmergencyDialogTest` / `CodeChallengeUiTest`:
     - Verify fuse locked state (buttons disabled when code does not match).
     - Verify fuse unlocked state (buttons enabled when code matches).
     - Verify code is displayed inside dialog and not on parent intervention screen.
3. **Build & Integration:**
   - `./gradlew test` passes.
