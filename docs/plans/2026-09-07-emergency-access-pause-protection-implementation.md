# Emergency Access & Pause Protection Implementation Plan

> **For Antigravity:** REQUIRED SUB-SKILL: Load executing-plans to implement this plan task-by-task.

**Goal:** Implement an "Emergency Access" bypass in the intervention overlay with a confirmation dialog ("Are you sure?") allowing immediate entry or pausing protection for the app (15m/30m/60m/indefinitely), and add global protection pause controls on the Home screen.

**Architecture:** Update `RuntimeState` and `RuleEngine` to recognize global and per-app paused protection windows; add repository and settings methods for pausing/resuming protection; update `InterventionOverlayView` and `OverlayController` to present an emergency confirmation modal and handle bypass actions; and update `HomeScreen` to display pause status, countdown, and pause/resume controls.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Room Database, Kotlinx Coroutines, Android AccessibilityService.

---

### Task 1: Domain & Repository Pause Protection Support
**Files:**
- Modify: `app/src/main/java/io/ronesec/android/domain/engine/RuntimeState.kt`
- Modify: `app/src/main/java/io/ronesec/android/domain/engine/RuleEngine.kt`
- Modify: `app/src/main/java/io/ronesec/android/data/repository/RonesecRepository.kt`
- Modify: `app/src/test/java/io/ronesec/android/domain/RuleEngineTest.kt`

**Step 1: Write Unit Test in RuleEngineTest**
- Test that `protectionPausedUntil` in `RuntimeState` grants `Decision.Allow` when unexpired.
- Test that `protectionPausedUntil == -1L` (indefinite pause) grants `Decision.Allow`.
- Test that expired `protectionPausedUntil` falls back to normal rules.

**Step 2: Implement RuntimeState & RuleEngine Updates**
- In `RuntimeState.kt`:
  - Add `val protectionPausedUntil: Long? = null`.
- In `RuleEngine.kt`:
  - At the very top of `evaluate(...)`:
    ```kotlin
    val pausedUntil = state.protectionPausedUntil
    if (pausedUntil != null) {
        if (pausedUntil == -1L || now.toEpochMilli() < pausedUntil) {
            return Decision.Allow
        }
    }
    ```
- In `RonesecRepository.kt`:
  - Include `protection_paused_until` in hot runtime state.
  - Add methods:
    - `suspend fun pauseProtection(durationMinutes: Int)` (if durationMinutes == -1, stores -1, else stores `System.currentTimeMillis() + durationMinutes * 60_000L`).
    - `suspend fun resumeProtection()`
    - `fun getProtectionPausedUntilFlow(): Flow<Long?>`

**Step 3: Run tests to verify they pass**
- `./gradlew testDebugUnitTest --tests io.ronesec.android.domain.RuleEngineTest`

**Step 4: Commit**
- `git add . && git commit -m "feat(domain): add protection pausing logic to RuntimeState and RuleEngine"`

---

### Task 2: Emergency Access in Intervention Overlay & Controller
**Files:**
- Modify: `app/src/main/java/io/ronesec/android/overlay/InterventionOverlayView.kt`
- Modify: `app/src/main/java/io/ronesec/android/overlay/OverlayController.kt`
- Modify: `app/src/main/java/io/ronesec/android/service/AppMonitorService.kt`

**Step 1: Add Emergency UI to InterventionOverlayView**
- In `InterventionOverlayContent`:
  - Add parameter `onEmergencyAccess: (durationMs: Long?, disableTarget: Boolean) -> Unit`.
  - State: `var showEmergencyConfirmDialog by remember { mutableStateOf(false) }`.
  - Under "ВЫЙТИ" button:
    - Add clickable text button: `"⚡ ЭКСТРЕННЫЙ ВХОД"`.
  - When `showEmergencyConfirmDialog`:
    - Display a confirmation `BasicAlertDialog`:
      - Title: "ВЫ УВЕРЕНЫ?"
      - Text: "Вы действительно хотите пропустить паузу осознанности и войти в приложение?"
      - Action: "ВОЙТИ РАЗОВО" -> `onEmergencyAccess(null, false)`.
      - Section: "ОТКЛЮЧИТЬ ЗАЩИТУ ДЛЯ ПРИЛОЖЕНИЯ:"
        - Chips: "15м" -> `onEmergencyAccess(15 * 60_000L, false)`
        - "30м" -> `onEmergencyAccess(30 * 60_000L, false)`
        - "60м" -> `onEmergencyAccess(60 * 60_000L, false)`
        - "До включения" -> `onEmergencyAccess(null, true)`
      - Action: "ВЕРНУТЬСЯ К ДЫХАНИЮ" -> `showEmergencyConfirmDialog = false`.

**Step 2: Update OverlayController and AppMonitorService**
- In `OverlayController.showIntervention`:
  - Accept `onEmergencyAccess: (durationMs: Long?, disableTarget: Boolean) -> Unit`.
- In `AppMonitorService.kt`:
  - Pass `onEmergencyAccess` callback:
    - Dismiss overlay.
    - If `disableTarget`:
      `repository.setTargetEnabled(rawPackage, false)`
      `repository.grantAccess(rawPackage, null)`
    - Else:
      `val grantDuration = durationMs ?: target.intervention.reinterventionMs`
      `repository.grantAccess(rawPackage, grantDuration)`
    - Record attempt as `CONTINUED`.

**Step 3: Verification**
- `./gradlew compileDebugKotlin`

**Step 4: Commit**
- `git add . && git commit -m "feat(overlay): add emergency bypass with confirmation and temporary app unblock"`

---

### Task 3: Global Protection Pause Controls on HomeScreen
**Files:**
- Modify: `app/src/main/java/io/ronesec/android/ui/screens/HomeScreen.kt`
- Modify: `app/src/main/java/io/ronesec/android/ui/viewmodel/MainViewModel.kt`

**Step 1: Expose Pause State in MainViewModel**
- Collect `getProtectionPausedUntilFlow()` into `StateFlow<Long?>`.
- Add methods: `pauseProtection(durationMinutes: Int)` and `resumeProtection()`.

**Step 2: Add Protection Status Card on HomeScreen**
- If not paused:
  - Card "ПАУЗА ЗАЩИТЫ":
    - Chips: `15м`, `30м`, `1ч`, `До включения`.
- If paused:
  - Warning card "ЗАЩИТА ПРИОСТАНОВЛЕНА":
    - Countdown timer (e.g. "Осталось: 24:12" or "До ручного включения").
    - Button "ВОЗОБНОВИТЬ ЗАЩИТУ".

**Step 3: Verification**
- `./gradlew test`
- `./gradlew assembleRelease`

**Step 4: Commit**
- `git add . && git commit -m "feat(ui): add global protection pause controls and countdown on HomeScreen"`
