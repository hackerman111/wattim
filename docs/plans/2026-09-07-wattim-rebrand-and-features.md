# wattim Rebrand, Themes, Saved Time Stats, Animations & Size Optimization Implementation Plan

> **For Antigravity:** REQUIRED SUB-SKILL: Load executing-plans to implement this plan task-by-task.

**Goal:** Rebrand the application to 'wattim' with custom launcher icons, remove bracket symbols from UI, add 6 complete color themes, track saved life time with stats & overlay banner, add 2 new mindful 60-FPS animations, localize the app to Russian, and shrink APK size from ~44-57MB to ~2-4MB.

**Architecture:** MVI/MVVM with Jetpack Compose, Room database persistence for settings and stats, dynamic theme palette provided via CompositionLocal, custom Canvas-based 60 FPS animation renderers, and R8/resource shrinking for compact APK packaging.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Room DB, Coroutines/Flow, AndroidX Canvas, R8 Proguard.

---

### Task 1: Binary Size Reduction & Build Configuration
**Files:**
- Modify: app/build.gradle.kts
- Modify: app/proguard-rules.pro

1. Remove implementation("androidx.compose.material:material-icons-extended").
2. Configure isMinifyEnabled = true, isShrinkResources = true in buildTypes.release.
3. Verify proguard rules retain Room entities and Compose layouts.

### Task 2: Rebranding to 'wattim' & Launcher Icon Generation
**Files:**
- Modify: app/src/main/res/values/strings.xml
- Modify: app/src/main/AndroidManifest.xml
- Create: app/src/main/res/mipmap-mdpi/ic_launcher.png, ic_launcher_round.png
- Create: app/src/main/res/mipmap-hdpi/ic_launcher.png, ic_launcher_round.png
- Create: app/src/main/res/mipmap-xhdpi/ic_launcher.png, ic_launcher_round.png
- Create: app/src/main/res/mipmap-xxhdpi/ic_launcher.png, ic_launcher_round.png
- Create: app/src/main/res/mipmap-xxxhdpi/ic_launcher.png, ic_launcher_round.png

1. Generate standard mipmap icons from root wattim.png using PIL.
2. Update application label to "wattim" and icon references in AndroidManifest.xml.

### Task 3: Square Bracket Removal [ ]
**Files:**
- Modify: app/src/main/java/io/ronesec/android/ui/components/TerminalButton.kt
- Modify: app/src/main/java/io/ronesec/android/ui/components/TerminalNav.kt
- Modify: app/src/main/java/io/ronesec/android/ui/screens/AddAppDialog.kt

1. Change [ $text ] to clean $text.
2. Remove [${destination.label}] brackets from navigation bar tabs.
3. Remove [ ADD ] brackets.

### Task 4: Color Themes System (Nord, Catppuccin, Dracula, Gruvbox, Tokyo Night, Cyber Terminal)
**Files:**
- Modify: app/src/main/java/io/ronesec/android/ui/theme/Color.kt
- Modify: app/src/main/java/io/ronesec/android/ui/theme/Theme.kt
- Modify: app/src/main/java/io/ronesec/android/ui/screens/ConfigScreen.kt
- Modify: app/src/main/java/io/ronesec/android/ui/viewmodel/MainViewModel.kt
- Modify: app/src/main/java/io/ronesec/android/service/AppMonitorService.kt

1. Define AppTheme enum and AppPalette data class with full color sets.
2. Store theme name in Room DB (app_theme).
3. Replace hardcoded TerminalBackground with LocalAppPalette.current.background across UI screens.

### Task 5: Detailed Saved Life Time Statistics
**Files:**
- Modify: app/src/main/java/io/ronesec/android/data/local/dao/OpenAttemptDao.kt
- Modify: app/src/main/java/io/ronesec/android/data/repository/RonesecRepository.kt
- Modify: app/src/main/java/io/ronesec/android/ui/viewmodel/MainViewModel.kt
- Modify: app/src/main/java/io/ronesec/android/ui/screens/StatsScreen.kt

1. Calculate total avoided sessions (all-time & today).
2. Calculate saved minutes with configurable average session duration (default 7 min).
3. Human-readable Russian formatting ("2 дн. 4 ч. жизни", "45 мин.").
4. Render beautiful cards on StatsScreen.

### Task 6: Motivating Saved Time on Intervention Overlay & 2 New Mindful Animations
**Files:**
- Modify: app/src/main/java/io/ronesec/android/domain/model/AnimationType.kt
- Modify: app/src/main/java/io/ronesec/android/overlay/InterventionOverlayView.kt
- Modify: app/src/main/java/io/ronesec/android/overlay/OverlayController.kt
- Modify: app/src/main/java/io/ronesec/android/service/AppMonitorService.kt
- Modify: app/src/main/java/io/ronesec/android/ui/screens/TargetSettingsScreen.kt

1. Implement PULSE (Breathing Sphere with glowing concentric aura).
2. Implement ORBIT (Zen Orbit with orbiting particles that expand and contract with breath).
3. Display real-time motivating banner: "🌱 Вы сберегли уже 2 дня 4 часа жизни" on the intervention screen.

### Task 7: Full Russian Localization
**Files:**
- Modify: app/src/main/java/io/ronesec/android/ui/screens/HomeScreen.kt
- Modify: app/src/main/java/io/ronesec/android/ui/screens/BlocksScreen.kt
- Modify: app/src/main/java/io/ronesec/android/ui/screens/TargetSettingsScreen.kt
- Modify: app/src/main/java/io/ronesec/android/ui/screens/ConfigScreen.kt
- Modify: app/src/main/java/io/ronesec/android/ui/screens/OnboardingScreen.kt
- Modify: app/src/main/java/io/ronesec/android/overlay/BlockOverlayView.kt
- Modify: app/src/main/java/io/ronesec/android/overlay/InterventionOverlayView.kt

1. Localize all strings, buttons, descriptions, and statuses into Russian.

### Task 8: Verification & Size Measurement
1. Run ./gradlew test to ensure zero regressions.
2. Build debug and release APKs.
3. Measure APK size reduction from 57MB to < 5MB.
