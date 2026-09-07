package io.ronesec.android.acceptance

import io.ronesec.android.domain.animation.FillAnimation
import io.ronesec.android.domain.engine.RuleEngine
import io.ronesec.android.domain.engine.RuntimeState
import io.ronesec.android.domain.model.AccessGrant
import io.ronesec.android.domain.model.AnimationPhase
import io.ronesec.android.domain.model.AnimationType
import io.ronesec.android.domain.model.AttemptOutcome
import io.ronesec.android.domain.model.BlockSession
import io.ronesec.android.domain.model.Decision
import io.ronesec.android.domain.model.InterventionConfig
import io.ronesec.android.domain.model.TargetApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant

class AcceptanceCriteriaTest {

    private val ruleEngine = RuleEngine()
    private val now = Instant.parse("2026-09-07T12:00:00Z")

    // AC-03: Фразу пользователь может задать отдельно для каждого приложения
    @Test
    fun `AC-03 Custom phrase per target application`() {
        val instaConfig = InterventionConfig(
            phrase = "Зачем вы сюда зашли?",
            animation = AnimationType.FILL
        )
        val youtubeConfig = InterventionConfig(
            phrase = "Что именно вы собираетесь посмотреть?",
            animation = AnimationType.FILL
        )

        val insta = TargetApp("com.instagram.android", "Instagram", true, instaConfig)
        val youtube = TargetApp("com.google.android.youtube", "YouTube", true, youtubeConfig)

        val state = RuntimeState(targets = mapOf(insta.packageName to insta, youtube.packageName to youtube))

        val decisionInsta = ruleEngine.evaluate(insta.packageName, now, state) as Decision.Intervention
        val decisionYt = ruleEngine.evaluate(youtube.packageName, now, state) as Decision.Intervention

        assertEquals("Зачем вы сюда зашли?", decisionInsta.config.phrase)
        assertEquals("Что именно вы собираетесь посмотреть?", decisionYt.config.phrase)
        assertNotEquals(decisionInsta.config.phrase, decisionYt.config.phrase)
    }

    // AC-04: Пользователь может выбрать animation (Fill для MVP)
    @Test
    fun `AC-04 Animation selection is Fill`() {
        val config = InterventionConfig(animation = AnimationType.FILL)
        assertEquals(AnimationType.FILL, config.animation)
    }

    // AC-05: При duration = 8 sec первые 4с экран заполняется, следующие 4с освобождается
    @Test
    fun `AC-05 8 second Fill animation 4s inhale and 4s exhale`() {
        val fill = FillAnimation()
        val startMs = 1_000_000L
        fill.start(durationMs = 8_000L, startTimeMs = startMs)

        // At 0s: 0%
        assertEquals(0.0f, fill.progress(startMs), 0.001f)
        assertEquals(AnimationPhase.INHALE, fill.phase(startMs))

        // At 2s: 50%
        assertEquals(0.5f, fill.progress(startMs + 2_000L), 0.001f)
        assertEquals(AnimationPhase.INHALE, fill.phase(startMs + 2_000L))

        // At 4s: 100%
        assertEquals(1.0f, fill.progress(startMs + 4_000L), 0.001f)
        assertEquals(AnimationPhase.EXHALE, fill.phase(startMs + 4_000L))

        // At 6s: 50%
        assertEquals(0.5f, fill.progress(startMs + 6_000L), 0.001f)
        assertEquals(AnimationPhase.EXHALE, fill.phase(startMs + 6_000L))

        // At 8s: 0% and COMPLETE
        assertEquals(0.0f, fill.progress(startMs + 8_000L), 0.001f)
        assertEquals(AnimationPhase.COMPLETE, fill.phase(startMs + 8_000L))
        assertTrue(fill.isFinished(startMs + 8_000L))
    }

    // AC-06: CONTINUE появляется только после завершения полного animation cycle
    @Test
    fun `AC-06 CONTINUE is inaccessible prior to COMPLETE`() {
        val fill = FillAnimation()
        val startMs = 1_000_000L
        fill.start(durationMs = 8_000L, startTimeMs = startMs)

        // During inhale & exhale, animation is NOT finished
        for (elapsed in 0L until 8_000L step 500L) {
            assertFalse("Animation should not be finished at ${elapsed}ms", fill.isFinished(startMs + elapsed))
            assertNotEquals(AnimationPhase.COMPLETE, fill.phase(startMs + elapsed))
        }

        // Only at or past 8000ms is it finished
        assertTrue(fill.isFinished(startMs + 8_000L))
        assertEquals(AnimationPhase.COMPLETE, fill.phase(startMs + 8_000L))
    }

    // AC-08: После CONTINUE target app становится доступно
    @Test
    fun `AC-08 AccessGrant unblocks target app`() {
        val target = TargetApp("com.instagram.android", "Instagram", true, InterventionConfig())
        val grant = AccessGrant(target.packageName, now, now.plusSeconds(300))
        val state = RuntimeState(
            targets = mapOf(target.packageName to target),
            activeGrants = mapOf(target.packageName to grant)
        )

        val decision = ruleEngine.evaluate(target.packageName, now.plusSeconds(10), state)
        assertEquals(Decision.Allow, decision)
    }

    // AC-09: При re-intervention = 5 min следующий intervention появляется через 5 минут
    @Test
    fun `AC-09 Re-intervention occurs after grant expires`() {
        val target = TargetApp(
            "com.instagram.android",
            "Instagram",
            true,
            InterventionConfig(reinterventionMs = 300_000L)
        )
        val grant = AccessGrant(target.packageName, now, now.plusMillis(300_000L))
        val state = RuntimeState(
            targets = mapOf(target.packageName to target),
            activeGrants = mapOf(target.packageName to grant)
        )

        // 4 min 59 sec: Still allowed
        assertEquals(Decision.Allow, ruleEngine.evaluate(target.packageName, now.plusSeconds(299), state))

        // 5 min + 1 sec: Grant expired -> Intervention required again
        val decision = ruleEngine.evaluate(target.packageName, now.plusSeconds(301), state)
        assertTrue(decision is Decision.Intervention)
    }

    // AC-10: Quick Return Grace не показывает новый intervention при кратком переключении
    @Test
    fun `AC-10 Quick Return Grace avoids repeated interventions`() {
        val target = TargetApp(
            "com.instagram.android",
            "Instagram",
            true,
            InterventionConfig(quickReturnGraceMs = 60_000L)
        )
        // User exited at 12:00:00
        val state = RuntimeState(
            targets = mapOf(target.packageName to target),
            lastExitTimes = mapOf(target.packageName to now)
        )

        // Returned 20s later: Allowed
        assertEquals(Decision.Allow, ruleEngine.evaluate(target.packageName, now.plusSeconds(20), state))

        // Returned 65s later: Intervention required
        val decision = ruleEngine.evaluate(target.packageName, now.plusSeconds(65), state)
        assertTrue(decision is Decision.Intervention)
    }

    // AC-11: Hard Block имеет больший приоритет чем AccessGrant
    @Test
    fun `AC-11 Hard Block overrides active AccessGrant`() {
        val target = TargetApp("com.instagram.android", "Instagram", true, InterventionConfig())
        val grant = AccessGrant(target.packageName, now, now.plusSeconds(3600))
        val hardBlock = BlockSession(
            id = 1,
            name = "FOCUS SESSION",
            startTime = now,
            endTime = now.plusSeconds(1800),
            active = true,
            packages = setOf(target.packageName)
        )
        val state = RuntimeState(
            targets = mapOf(target.packageName to target),
            activeGrants = mapOf(target.packageName to grant),
            activeBlockSessions = listOf(hardBlock)
        )

        val decision = ruleEngine.evaluate(target.packageName, now.plusSeconds(60), state)
        assertTrue(decision is Decision.Block)
    }

    // Emergency Access: Разовый вход или временное снятие защиты разрешает доступ к приложению
    @Test
    fun `Emergency Access with 15 minute pause unblocks target app`() {
        val target = TargetApp("com.instagram.android", "Instagram", true, InterventionConfig())
        val emergencyPauseMs = 15 * 60_000L
        val grant = AccessGrant(target.packageName, now, now.plusMillis(emergencyPauseMs))
        val state = RuntimeState(
            targets = mapOf(target.packageName to target),
            activeGrants = mapOf(target.packageName to grant)
        )

        // 10 minutes in: Still allowed under 15m emergency grant
        val decision = ruleEngine.evaluate(target.packageName, now.plusSeconds(600), state)
        assertEquals(Decision.Allow, decision)

        // 16 minutes in: Emergency grant expired -> requires intervention again
        val expiredDecision = ruleEngine.evaluate(target.packageName, now.plusSeconds(960), state)
        assertTrue(expiredDecision is Decision.Intervention)
    }

    @Test
    fun `Emergency Access disabling target permanently allows access`() {
        val target = TargetApp("com.instagram.android", "Instagram", false, InterventionConfig())
        val state = RuntimeState(
            targets = mapOf(target.packageName to target)
        )

        val decision = ruleEngine.evaluate(target.packageName, now.plusSeconds(100), state)
        assertEquals(Decision.Allow, decision)
    }

    // AC-13 & AC-14: 100% offline, zero telemetry, NO INTERNET permission in Manifest
    @Test
    fun `AC-13 and AC-14 AndroidManifest does not contain INTERNET permission`() {
        val manifestFile = File("src/main/AndroidManifest.xml")
        if (manifestFile.exists()) {
            val content = manifestFile.readText()
            assertFalse(
                "AndroidManifest must not declare INTERNET permission tag",
                content.contains("<uses-permission android:name=\"android.permission.INTERNET\"")
            )
        }
    }
}
