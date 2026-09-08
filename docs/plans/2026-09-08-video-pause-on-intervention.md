# Video Pause on Intervention Implementation Plan

> **For Antigravity:** REQUIRED SUB-SKILL: Load executing-plans to implement this plan task-by-task.

**Goal:** Ensure video and audio playback in background media apps (YouTube, TikTok, Instagram Reels, VK, Telegram, Chrome) are reliably paused when Wattim's intervention overlay appears.

**Architecture:** 
- `SystemAudioGuard` requests `AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE` to forbid background audio ducking.
- Staggered pause pulses ($t=0$, $150$ms, $400$ms, $800$ms, $1200$ms) handle players that initialize with a delay.
- `OnAudioFocusChangeListener` re-requests exclusive focus and pauses playback if a background app attempts to steal audio focus during an active intervention session.
- Clean cancellation and symmetric resource release on exit/continue/screen-off.

**Tech Stack:** Kotlin, Coroutines (`kotlinx.coroutines`), Android `AudioManager` & `AudioFocusRequest`, JUnit 4.

---

### Task 1: Enhance `AudioGuard` and `SystemAudioGuard` with Staggered Pulses & Focus Watchdog

**Files:**
- Modify: `app/src/main/java/io/ronesec/android/domain/protection/AudioGuard.kt`
- Modify: `app/src/main/java/io/ronesec/android/service/AppMonitorService.kt:62`

**Step 1: Write the failing test**
Create `app/src/test/java/io/ronesec/android/domain/protection/AudioGuardTest.kt` asserting that `SystemAudioGuard` requests exclusive audio focus, schedules staggered pauses, and properly releases on cancellation.

**Step 2: Run test to verify it fails**
Run: `./gradlew testDebugUnitTest --tests io.ronesec.android.domain.protection.AudioGuardTest`

**Step 3: Implement enhanced `SystemAudioGuard`**
- In `SystemAudioGuard`, accept `scope: CoroutineScope = CoroutineScope(Dispatchers.Main)`.
- Use `AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE`.
- In `acquire()`, launch a coroutine job executing `dispatchMediaPause()` at $0$ms, $150$ms, $400$ms, $800$ms, and $1200$ms.
- Listen for `AUDIOFOCUS_LOSS_TRANSIENT` and `AUDIOFOCUS_LOSS` in `OnAudioFocusChangeListener`; if `activeSessionId == sessionId`, re-assert exclusive focus and send `KEYCODE_MEDIA_PAUSE`.
- In `release()`, cancel the job, abandon focus, and reset `activeSessionId`.

**Step 4: Run test to verify it passes**
Run: `./gradlew testDebugUnitTest --tests io.ronesec.android.domain.protection.AudioGuardTest`
Expected: PASS

**Step 5: Commit**
```bash
git add app/src/main/java/io/ronesec/android/domain/protection/AudioGuard.kt app/src/main/java/io/ronesec/android/service/AppMonitorService.kt app/src/test/java/io/ronesec/android/domain/protection/AudioGuardTest.kt
git commit -m "feat(audio): add staggered media pause and focus watchdog to AudioGuard"
```

---

### Task 2: Regression and Integration Verification

**Files:**
- Test: All unit tests (`./gradlew test`)
- Test: Browser extension tests (`node tests/run-all.mjs`)
- Build: `./gradlew assembleDebug`

**Step 1: Run full test suite**
Run: `./gradlew test && node tests/run-all.mjs`
Expected: 100% tests pass.

**Step 2: Verify APK compilation**
Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.
