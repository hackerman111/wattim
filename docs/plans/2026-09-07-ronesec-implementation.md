# ronesec Implementation Plan

> **For Antigravity:** REQUIRED SUB-SKILL: Load executing-plans to implement this plan task-by-task.

**Goal:** Build `ronesec` — an unskippable mindfulness intervention and app-blocker for Android 10+ with Neo-Terminal UI, < 100ms interception speed via AccessibilityService + WindowManager overlay, Room persistence, and zero telemetry.

**Architecture:** Pure Kotlin `RuleEngine` and `AnimationEngine` (Domain Layer) completely decoupled from Android SDK. A high-priority `ForegroundService` and `AccessibilityService` detect foreground transitions and trigger a pre-warmed `WindowManager` Compose overlay with 60 FPS `Fill` breathing animation. Room database handles offline persistence with a thread-safe in-memory cache for sub-millisecond rule evaluation.

**Tech Stack:** Kotlin 2.0+, Jetpack Compose (BOM 2024.09+), AndroidX Lifecycle & Navigation, Room ORM with KSP, Coroutines & Flow, Gradle Kotlin DSL.

---

### Task 1: Gradle Build Configuration & Project Scaffolding

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `app/build.gradle.kts`
- Create: `app/proguard-rules.pro`
- Create: `app/src/main/AndroidManifest.xml`

**Step 1: Create root and app build scripts**
Set up Android Gradle Plugin, Kotlin, Compose, KSP, and Room dependencies. Ensure `android.permission.INTERNET` is omitted. Configure API minSdk 29 (Android 10), targetSdk 35.

**Step 2: Verify project structure**
Ensure Gradle wrapper and files are valid and project parses properly.

**Step 3: Commit**
```bash
git add settings.gradle.kts build.gradle.kts gradle.properties app/build.gradle.kts app/proguard-rules.pro app/src/main/AndroidManifest.xml
git commit -m "chore: setup gradle build scripts, dependencies, and manifest"
```

---

### Task 2: Domain Layer — Models, Enums & Animation Engine

**Files:**
- Create: `app/src/main/java/io/ronesec/android/domain/model/AnimationType.kt`
- Create: `app/src/main/java/io/ronesec/android/domain/model/AnimationPhase.kt`
- Create: `app/src/main/java/io/ronesec/android/domain/model/InterventionConfig.kt`
- Create: `app/src/main/java/io/ronesec/android/domain/model/Decision.kt`
- Create: `app/src/main/java/io/ronesec/android/domain/model/AccessGrant.kt`
- Create: `app/src/main/java/io/ronesec/android/domain/model/TargetApp.kt`
- Create: `app/src/main/java/io/ronesec/android/domain/model/BlockSession.kt`
- Create: `app/src/main/java/io/ronesec/android/domain/model/BlockSchedule.kt`
- Create: `app/src/main/java/io/ronesec/android/domain/model/AttemptOutcome.kt`
- Create: `app/src/main/java/io/ronesec/android/domain/model/OpenAttempt.kt`
- Create: `app/src/main/java/io/ronesec/android/domain/animation/InterventionAnimation.kt`
- Create: `app/src/main/java/io/ronesec/android/domain/animation/FillAnimation.kt`
- Test: `app/src/test/java/io/ronesec/android/domain/FillAnimationTest.kt`

**Step 1: Write failing unit tests for FillAnimation**
Verify 50% inhale rising, 50% exhale falling, phase transitions (`INHALE`, `EXHALE`, `COMPLETE`), and bounds checking.

**Step 2: Implement Domain Models and FillAnimation**
Implement mathematical formulas:
- Inhale ($t \le 0.5$): $progress = t \times 2$
- Exhale ($t > 0.5$): $progress = (1 - t) \times 2$
- Phase mapping and FastOutSlowIn easing.

**Step 3: Run unit tests to verify pass**

**Step 4: Commit**
```bash
git add app/src/main/java/io/ronesec/android/domain/ app/src/test/java/io/ronesec/android/domain/FillAnimationTest.kt
git commit -m "feat(domain): implement core models and FillAnimation engine with tests"
```

---

### Task 3: Domain Layer — RuleEngine & Decision Logic

**Files:**
- Create: `app/src/main/java/io/ronesec/android/domain/engine/RuntimeState.kt`
- Create: `app/src/main/java/io/ronesec/android/domain/engine/RuleEngine.kt`
- Test: `app/src/test/java/io/ronesec/android/domain/RuleEngineTest.kt`

**Step 1: Write failing unit tests for RuleEngine**
Cover all key scenarios:
1. Unknown or disabled package -> `Decision.Allow`
2. Hard block active -> `Decision.Block(until)`
3. Hard block overriding existing AccessGrant (AC-11)
4. Scheduled block active -> `Decision.Block`
5. Active unexpired AccessGrant -> `Decision.Allow`
6. Within Quick Return Grace (< 60s) -> `Decision.Allow`
7. Expired grace or first launch -> `Decision.Intervention`
8. Re-intervention expiration after grant elapsed -> `Decision.Intervention` (AC-09)

**Step 2: Implement RuleEngine**
Pure Kotlin evaluation logic executing in < 1 ms without external dependencies.

**Step 3: Run unit tests to verify pass**

**Step 4: Commit**
```bash
git add app/src/main/java/io/ronesec/android/domain/engine/ app/src/test/java/io/ronesec/android/domain/RuleEngineTest.kt
git commit -m "feat(domain): implement RuleEngine decision evaluation with comprehensive tests"
```

---

### Task 4: Data Layer — Room Database & Local Repository

**Files:**
- Create: `app/src/main/java/io/ronesec/android/data/local/entity/TargetAppEntity.kt`
- Create: `app/src/main/java/io/ronesec/android/data/local/entity/OpenAttemptEntity.kt`
- Create: `app/src/main/java/io/ronesec/android/data/local/entity/AccessGrantEntity.kt`
- Create: `app/src/main/java/io/ronesec/android/data/local/entity/BlockSessionEntity.kt`
- Create: `app/src/main/java/io/ronesec/android/data/local/entity/BlockScheduleEntity.kt`
- Create: `app/src/main/java/io/ronesec/android/data/local/entity/AppSettingEntity.kt`
- Create: `app/src/main/java/io/ronesec/android/data/local/dao/TargetAppDao.kt`
- Create: `app/src/main/java/io/ronesec/android/data/local/dao/OpenAttemptDao.kt`
- Create: `app/src/main/java/io/ronesec/android/data/local/dao/AccessGrantDao.kt`
- Create: `app/src/main/java/io/ronesec/android/data/local/dao/BlockDao.kt`
- Create: `app/src/main/java/io/ronesec/android/data/local/dao/SettingsDao.kt`
- Create: `app/src/main/java/io/ronesec/android/data/local/AppDatabase.kt`
- Create: `app/src/main/java/io/ronesec/android/data/repository/RonesecRepository.kt`

**Step 1: Implement entities, DAOs, and Room database**
Configure TypeConverters for `Instant`, `AnimationType`, `AttemptOutcome`, and day lists.

**Step 2: Implement RonesecRepository with in-memory cache**
Sync target apps, active blocks, and grants in memory for instant synchronous access by `RuleEngine`.

**Step 3: Commit**
```bash
git add app/src/main/java/io/ronesec/android/data/
git commit -m "feat(data): implement Room database, entities, DAOs, and repository"
```

---

### Task 5: Neo-Terminal Design System & Base Components (Jetpack Compose)

**Files:**
- Create: `app/src/main/java/io/ronesec/android/ui/theme/Color.kt`
- Create: `app/src/main/java/io/ronesec/android/ui/theme/Theme.kt`
- Create: `app/src/main/java/io/ronesec/android/ui/theme/Typography.kt`
- Create: `app/src/main/java/io/ronesec/android/ui/components/TerminalButton.kt`
- Create: `app/src/main/java/io/ronesec/android/ui/components/TerminalCard.kt`
- Create: `app/src/main/java/io/ronesec/android/ui/components/TerminalBadge.kt`
- Create: `app/src/main/java/io/ronesec/android/ui/components/TerminalInput.kt`
- Create: `app/src/main/java/io/ronesec/android/ui/components/TerminalNav.kt`

**Step 1: Implement Neo-Terminal color palette and typography**
Define `#090B0D` background, `#101316` surface, `#E6E8E9` primary text, `#737A80` secondary text, `#252A2E` border, and accent colors (Cyan `#00E5FF`, Green `#00E676`, Orange `#FF9100`, Violet `#D500F9`). Monospace typography hierarchy.

**Step 2: Implement reusable terminal UI widgets**
Clean 1px borders, 4-6dp rounded corners, uppercase mono labels, interactive touch states.

**Step 3: Commit**
```bash
git add app/src/main/java/io/ronesec/android/ui/theme/ app/src/main/java/io/ronesec/android/ui/components/
git commit -m "feat(ui): implement Neo-Terminal theme and foundational UI components"
```

---

### Task 6: Intervention Overlay & WindowManager Controller

**Files:**
- Create: `app/src/main/java/io/ronesec/android/overlay/InterventionOverlayView.kt`
- Create: `app/src/main/java/io/ronesec/android/overlay/BlockOverlayView.kt`
- Create: `app/src/main/java/io/ronesec/android/overlay/OverlayController.kt`

**Step 1: Implement InterventionOverlayView**
- Target app title (monospace uppercase)
- Phase label (`INHALE` / `EXHALE`) & countdown timer
- Phrase displayed statically centered (1–80 chars, max 3 lines)
- Hardware-accelerated 60 FPS Fill canvas (rises from bottom on inhale, recedes on exhale)
- AC-06 strict guarantee: `[ CONTINUE ]` button does not exist until animation completes
- `[ CLOSE ]` button triggers Home action and records `ABANDONED`
- `[ CONTINUE ]` grants access and closes overlay

**Step 2: Implement BlockOverlayView**
Displays `BLOCKED`, focus timer countdown, and prevents access.

**Step 3: Implement OverlayController**
Manages `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY`, full-screen flags, touch absorption, and back button interception.

**Step 4: Commit**
```bash
git add app/src/main/java/io/ronesec/android/overlay/
git commit -m "feat(overlay): implement WindowManager overlay controller and intervention UI"
```

---

### Task 7: Services & System Integration (Accessibility, Foreground Service, Watchdog)

**Files:**
- Create: `app/src/main/res/xml/accessibility_service_config.xml`
- Create: `app/src/main/java/io/ronesec/android/service/AppMonitorService.kt`
- Create: `app/src/main/java/io/ronesec/android/service/FocusForegroundService.kt`
- Create: `app/src/main/java/io/ronesec/android/receiver/BootReceiver.kt`
- Create: `app/src/main/java/io/ronesec/android/util/PermissionHelper.kt`

**Step 1: Implement accessibility configuration and AppMonitorService**
Listen for `TYPE_WINDOW_STATE_CHANGED`, query `RuleEngine`, trigger `OverlayController`, and handle `GLOBAL_ACTION_HOME`.

**Step 2: Implement FocusForegroundService & BootReceiver**
Sticky foreground service with "Protection active · X apps" notification channel. BootReceiver restores service on startup.

**Step 3: Implement PermissionHelper**
Check and request:
1. Accessibility Service enabled
2. Can draw overlays (`Settings.canDrawOverlays`)
3. Battery optimization whitelisting

**Step 4: Commit**
```bash
git add app/src/main/res/xml/ app/src/main/java/io/ronesec/android/service/ app/src/main/java/io/ronesec/android/receiver/ app/src/main/java/io/ronesec/android/util/
git commit -m "feat(system): implement AppMonitorService, FocusForegroundService, and BootReceiver"
```

---

### Task 8: Application Screens & ViewModels

**Files:**
- Create: `app/src/main/java/io/ronesec/android/ui/screens/HomeScreen.kt`
- Create: `app/src/main/java/io/ronesec/android/ui/screens/TargetSettingsScreen.kt`
- Create: `app/src/main/java/io/ronesec/android/ui/screens/BlocksScreen.kt`
- Create: `app/src/main/java/io/ronesec/android/ui/screens/StatsScreen.kt`
- Create: `app/src/main/java/io/ronesec/android/ui/screens/ConfigScreen.kt`
- Create: `app/src/main/java/io/ronesec/android/ui/screens/OnboardingScreen.kt`
- Create: `app/src/main/java/io/ronesec/android/ui/screens/AddAppDialog.kt`
- Create: `app/src/main/java/io/ronesec/android/ui/viewmodel/MainViewModel.kt`
- Create: `app/src/main/java/io/ronesec/android/ui/MainActivity.kt`

**Step 1: Implement MainViewModel**
State flows for protected apps, statistics, active hard blocks, permission watchdog status, and accent color.

**Step 2: Implement Screens**
- `HomeScreen`: Header, protected apps list (`> 01 Instagram ACTIVE`), today stats (`OPEN`, `CLOSED`, `% AVOIDED`), `[ + ADD APP ]` dialog.
- `TargetSettingsScreen`: Status toggle, phrase editor (1-80 chars), duration stepper, remind again picker, quick return grace, **live real-time animation preview**.
- `BlocksScreen`: Quick Hard Block buttons (15m/30m/1h/2h) & scheduled recurring blocks.
- `StatsScreen`: Daily and per-app stats table.
- `ConfigScreen`: Accent color selector (Cyan, Green, Orange, Violet) and permission status tiles.
- `OnboardingScreen`: 3-step setup with `[ OPEN SETTINGS ]` actions.

**Step 3: Connect MainActivity with Navigation**

**Step 4: Commit**
```bash
git add app/src/main/java/io/ronesec/android/ui/
git commit -m "feat(ui): implement all main application screens, dialogs, and navigation"
```

---

### Task 9: Verification & Acceptance Tests

**Files:**
- Create: `app/src/test/java/io/ronesec/android/acceptance/AcceptanceCriteriaTest.kt`

**Step 1: Implement automated verification tests**
- Verify AC-01 to AC-14 logic directly in unit and scenario tests.
- Verify timing calculations, grace period boundaries, and unskippable button transitions.

**Step 2: Execute test suite and verify clean passes**

**Step 3: Commit**
```bash
git add app/src/test/java/io/ronesec/android/acceptance/
git commit -m "test: add comprehensive acceptance criteria test suite"
```
