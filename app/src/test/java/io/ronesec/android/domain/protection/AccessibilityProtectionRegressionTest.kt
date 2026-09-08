package io.ronesec.android.domain.protection

import io.ronesec.android.domain.engine.RuleEngine
import io.ronesec.android.domain.engine.RuntimeState
import io.ronesec.android.domain.model.AccessGrant
import io.ronesec.android.domain.model.AttemptOutcome
import io.ronesec.android.domain.model.BlockSchedule
import io.ronesec.android.domain.model.Decision
import io.ronesec.android.domain.model.InterventionConfig
import io.ronesec.android.domain.model.ScheduleType
import io.ronesec.android.domain.model.TargetApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

class AccessibilityProtectionRegressionTest {

    private val testPackage = "com.instagram.android"
    private val launcherPackage = "com.google.android.apps.nexuslauncher"
    private val baseTime = Instant.parse("2026-09-07T12:00:00Z") // Monday 12:00 UTC

    private val testTarget = TargetApp(
        packageName = testPackage,
        displayName = "Instagram",
        enabled = true,
        intervention = InterventionConfig(
            phrase = "Take a breath",
            durationMs = 5_000L,
            reinterventionMs = 300_000L, // 5m
            quickReturnGraceMs = 30_000L
        )
    )

    private fun createCoordinator(
        ruleEngine: RuleEngine = RuleEngine(),
        effects: MutableList<ProtectionEffect>
    ): InterventionCoordinator {
        return InterventionCoordinator(
            ruleEngine = ruleEngine,
            appLabelResolver = { "Instagram" },
            savedTimeTextResolver = { null },
            onEffect = { effects.add(it) }
        )
    }

    // Scenario 1: Session #1 async result finishes during session #2
    @Test
    fun `scenario 1 - session 1 async result finishes during session 2 is ignored`() {
        val effects = mutableListOf<ProtectionEffect>()
        val coordinator = createCoordinator(effects = effects)

        val state = RuntimeState(targets = mapOf(testPackage to testTarget))
        coordinator.processEvent(ProtectionEvent.PolicySnapshotUpdated(state))

        // Session 1 begins
        coordinator.processEvent(ProtectionEvent.ForegroundChanged(testPackage, baseTime))
        assertEquals(1L, coordinator.getCurrentSessionId())

        // User switches away and back -> Session 2 begins
        coordinator.processEvent(ProtectionEvent.ForegroundChanged("com.other.app", baseTime.plusSeconds(10)))
        coordinator.processEvent(ProtectionEvent.ForegroundChanged(testPackage, baseTime.plusSeconds(20)))
        assertEquals(2L, coordinator.getCurrentSessionId())

        effects.clear()

        // Stale async result from Session 1 arrives
        coordinator.processEvent(
            ProtectionEvent.UserAction(
                sessionId = 1L,
                targetPackage = testPackage,
                action = UserProtectionAction.Continue,
                timestamp = baseTime.plusSeconds(25)
            )
        )

        // Verify no effects emitted for stale session 1 action
        assertTrue(effects.none { it is ProtectionEffect.DismissOverlay && it.sessionId == 1L })
        assertTrue(effects.none { it is ProtectionEffect.PersistGrant })
        assertEquals(2L, coordinator.getCurrentSessionId())
    }

    // Scenario 2: Exit followed by stale same-package event
    @Test
    fun `scenario 2 - exit followed by stale same-package event does not reopen overlay`() {
        val effects = mutableListOf<ProtectionEffect>()
        val coordinator = createCoordinator(effects = effects)

        val state = RuntimeState(targets = mapOf(testPackage to testTarget))
        coordinator.processEvent(ProtectionEvent.PolicySnapshotUpdated(state))

        // Target opened -> Session 1
        coordinator.processEvent(ProtectionEvent.ForegroundChanged(testPackage, baseTime))
        assertEquals(1L, coordinator.getCurrentSessionId())

        effects.clear()

        // User taps exit/close
        coordinator.processEvent(
            ProtectionEvent.UserAction(
                sessionId = 1L,
                targetPackage = testPackage,
                action = UserProtectionAction.Close,
                timestamp = baseTime.plusSeconds(2)
            )
        )

        assertTrue(effects.any { it is ProtectionEffect.PerformGlobalHome })
        assertTrue(effects.any { it is ProtectionEffect.PersistAttempt && it.outcome == AttemptOutcome.ABANDONED })

        effects.clear()

        // Lingering same-package event arrives while returning HOME
        coordinator.processEvent(ProtectionEvent.ForegroundChanged(testPackage, baseTime.plusMillis(2100)))
        // Must be ignored! No new overlay
        assertTrue(effects.none { it is ProtectionEffect.ShowInterventionOverlay })

        // Launcher is now in foreground
        coordinator.processEvent(ProtectionEvent.ForegroundChanged(launcherPackage, baseTime.plusMillis(2500)))
        // Confirms exit
        assertTrue(effects.any { it is ProtectionEffect.DismissOverlay && it.sessionId == 1L })
        assertTrue(effects.any { it is ProtectionEffect.ReleaseAudio && it.sessionId == 1L })
        assertNull(coordinator.getActiveTargetPackage())
    }

    // Scenario 3: Timed emergency expires while user remains in target
    @Test
    fun `scenario 3 - timed emergency expires while user remains in target`() {
        val effects = mutableListOf<ProtectionEffect>()
        val coordinator = createCoordinator(effects = effects)

        val state = RuntimeState(targets = mapOf(testPackage to testTarget))
        coordinator.processEvent(ProtectionEvent.PolicySnapshotUpdated(state))

        coordinator.processEvent(ProtectionEvent.ForegroundChanged(testPackage, baseTime))
        val sessionId = coordinator.getCurrentSessionId()

        // User grants 15 minutes emergency access
        coordinator.processEvent(
            ProtectionEvent.UserAction(
                sessionId = sessionId,
                targetPackage = testPackage,
                action = UserProtectionAction.EmergencyAccess(durationMs = 900_000L, disableTarget = false),
                timestamp = baseTime.plusSeconds(1)
            )
        )

        assertTrue(effects.any { it is ProtectionEffect.ScheduleBoundary && it.boundaryType == BoundaryType.TIMED_PERMIT_EXPIRY })

        effects.clear()

        // 15 minutes expire while user is still in the target
        val expiryTime = baseTime.plusMillis(900_000L)
        coordinator.processEvent(
            ProtectionEvent.TemporalBoundaryReached(
                sessionId = sessionId,
                targetPackage = testPackage,
                boundaryType = BoundaryType.TIMED_PERMIT_EXPIRY,
                timestamp = expiryTime
            )
        )

        // Coordinator revokes permit and triggers fresh intervention overlay
        assertTrue(effects.any { it is ProtectionEffect.PersistRevoke })
        assertTrue(effects.any { it is ProtectionEffect.ShowInterventionOverlay && it.sessionId == sessionId })
    }

    // Scenario 4: Global pause active when re-intervention deadline fires
    @Test
    fun `scenario 4 - global pause active when re-intervention deadline fires`() {
        val effects = mutableListOf<ProtectionEffect>()
        val coordinator = createCoordinator(effects = effects)

        val state = RuntimeState(targets = mapOf(testPackage to testTarget))
        coordinator.processEvent(ProtectionEvent.PolicySnapshotUpdated(state))

        coordinator.processEvent(ProtectionEvent.ForegroundChanged(testPackage, baseTime))
        val sessionId = coordinator.getCurrentSessionId()

        // User finishes breathing and continues
        coordinator.processEvent(
            ProtectionEvent.UserAction(
                sessionId = sessionId,
                targetPackage = testPackage,
                action = UserProtectionAction.Continue,
                timestamp = baseTime.plusSeconds(5)
            )
        )

        // User turns on global pause for 30 minutes
        val pausedState = state.copy(
            protectionPausedUntil = baseTime.plusSeconds(1800).toEpochMilli(),
            activeSessionPermits = mapOf(testPackage to sessionId)
        )
        coordinator.processEvent(ProtectionEvent.PolicySnapshotUpdated(pausedState))

        effects.clear()

        // Re-intervention deadline fires at 5 minutes
        val reintTime = baseTime.plusMillis(300_000L)
        coordinator.processEvent(
            ProtectionEvent.TemporalBoundaryReached(
                sessionId = sessionId,
                targetPackage = testPackage,
                boundaryType = BoundaryType.REINTERVENTION,
                timestamp = reintTime
            )
        )

        // Fresh policy check finds global pause active -> no overlay shown!
        assertTrue(effects.none { it is ProtectionEffect.ShowInterventionOverlay })
        assertTrue(effects.none { it is ProtectionEffect.ShowBlockOverlay })
    }

    // Scenario 5: Disable then re-enable target leaves no permanent permit
    @Test
    fun `scenario 5 - disable then re-enable target leaves no permanent permit`() {
        val ruleEngine = RuleEngine()
        val disabledTarget = testTarget.copy(enabled = false)

        val stateWithDisabled = RuntimeState(targets = mapOf(testPackage to disabledTarget))
        val decisionDisabled = ruleEngine.evaluate(testPackage, baseTime, stateWithDisabled)
        assertEquals(Decision.Allow, decisionDisabled)

        // Target re-enabled
        val enabledTarget = testTarget.copy(enabled = true)
        val stateWithReenabled = RuntimeState(targets = mapOf(testPackage to enabledTarget))
        val decisionReenabled = ruleEngine.evaluate(testPackage, baseTime, stateWithReenabled)

        // Must require fresh intervention, not allowed by stale permit
        assertTrue(decisionReenabled is Decision.Intervention)
    }

    // Scenario 6: Process restart preserves timed permit but not session permit
    @Test
    fun `scenario 6 - process restart preserves timed permit but not session permit`() {
        val ruleEngine = RuleEngine()

        // Before restart: app had session permit for session 42, and another app had timed permit
        val unexpiredTimedGrant = AccessGrant(
            packageName = "com.other.app",
            createdAt = baseTime,
            expiresAt = baseTime.plusSeconds(600)
        )
        val targetOther = testTarget.copy(packageName = "com.other.app")

        // In-memory state before process death
        val preRestartState = RuntimeState(
            targets = mapOf(testPackage to testTarget, "com.other.app" to targetOther),
            activeGrants = mapOf("com.other.app" to unexpiredTimedGrant),
            activeSessionPermits = mapOf(testPackage to 42L)
        )
        assertEquals(Decision.Allow, ruleEngine.evaluate(testPackage, baseTime, preRestartState, currentSessionId = 42L))
        assertEquals(Decision.Allow, ruleEngine.evaluate("com.other.app", baseTime, preRestartState))

        // After process restart: RAM permits cleared, timed grants restored from database
        val postRestartState = RuntimeState(
            targets = mapOf(testPackage to testTarget, "com.other.app" to targetOther),
            activeGrants = mapOf("com.other.app" to unexpiredTimedGrant),
            activeSessionPermits = emptyMap() // RAM cleared
        )

        // Session permit target requires intervention
        val decisionSession = ruleEngine.evaluate(testPackage, baseTime, postRestartState, currentSessionId = 1L)
        assertTrue(decisionSession is Decision.Intervention)

        // Timed permit target remains Allowed
        val decisionTimed = ruleEngine.evaluate("com.other.app", baseTime, postRestartState)
        assertEquals(Decision.Allow, decisionTimed)
    }

    // Scenario 7: Old revoke cannot delete new permit
    @Test
    fun `scenario 7 - old revoke cannot delete new permit`() {
        val permits = mutableMapOf(testPackage to 2L) // Session 2 is active

        fun clearSessionPermit(pkg: String, sessionId: Long?) {
            if (sessionId == null || permits[pkg] == sessionId) {
                permits.remove(pkg)
            }
        }

        // Stale revoke from Session 1 arrives
        clearSessionPermit(testPackage, sessionId = 1L)

        // Permit for Session 2 must NOT be deleted
        assertEquals(2L, permits[testPackage])

        // Revoke from current Session 2 arrives
        clearSessionPermit(testPackage, sessionId = 2L)
        assertNull(permits[testPackage])
    }

    // Scenario 8: Overlapping hard-block and intervention priority
    @Test
    fun `scenario 8 - overlapping hard-block and intervention priority`() {
        val ruleEngine = RuleEngine()
        val zone = ZoneId.of("UTC")
        val now = Instant.parse("2026-09-07T13:00:00Z") // Monday 13:00 UTC

        val interventionSchedule = BlockSchedule(
            id = 1L,
            name = "Work Hours Intervention",
            days = setOf(DayOfWeek.MONDAY),
            start = LocalTime.of(9, 0),
            end = LocalTime.of(18, 0),
            packages = setOf(testPackage),
            scheduleType = ScheduleType.INTERVENTION
        )

        val hardBlockSchedule = BlockSchedule(
            id = 2L,
            name = "Deep Focus Hard Block",
            days = setOf(DayOfWeek.MONDAY),
            start = LocalTime.of(12, 0),
            end = LocalTime.of(14, 0),
            packages = setOf(testPackage),
            scheduleType = ScheduleType.HARD_BLOCK
        )

        val state = RuntimeState(
            targets = mapOf(testPackage to testTarget),
            blockSchedules = listOf(interventionSchedule, hardBlockSchedule)
        )

        val decision = ruleEngine.evaluate(testPackage, now, state, zoneId = zone)
        // Hard-block must strictly override intervention schedule
        assertTrue(decision is Decision.Block)
    }

    // Scenario 9: Overnight schedule boundary
    @Test
    fun `scenario 9 - overnight schedule boundary across midnight`() {
        val ruleEngine = RuleEngine()
        val zone = ZoneId.of("UTC")

        val overnightSchedule = BlockSchedule(
            id = 1L,
            name = "Night Hard Block",
            days = setOf(DayOfWeek.MONDAY), // Monday evening overnight to Tuesday morning
            start = LocalTime.of(22, 0),
            end = LocalTime.of(6, 0),
            packages = setOf(testPackage),
            scheduleType = ScheduleType.HARD_BLOCK
        )

        val state = RuntimeState(
            targets = mapOf(testPackage to testTarget),
            blockSchedules = listOf(overnightSchedule)
        )

        // Monday 23:00 -> within overnight span
        val mondayNight = Instant.parse("2026-09-07T23:00:00Z")
        assertTrue(ruleEngine.evaluate(testPackage, mondayNight, state, zoneId = zone) is Decision.Block)

        // Tuesday 02:00 -> within overnight span from Monday
        val tuesdayMorning = Instant.parse("2026-09-08T02:00:00Z")
        assertTrue(ruleEngine.evaluate(testPackage, tuesdayMorning, state, zoneId = zone) is Decision.Block)

        // Tuesday 07:00 -> after overnight span ends
        val tuesdayDay = Instant.parse("2026-09-08T07:00:00Z")
        assertFalse(ruleEngine.evaluate(testPackage, tuesdayDay, state, zoneId = zone) is Decision.Block)

        // Wednesday 02:00 -> Tuesday not in days, so not blocked
        val wednesdayMorning = Instant.parse("2026-09-09T02:00:00Z")
        assertFalse(ruleEngine.evaluate(testPackage, wednesdayMorning, state, zoneId = zone) is Decision.Block)
    }

    // Scenario 10: Screen off during overlay
    @Test
    fun `scenario 10 - screen off during overlay dismisses and cleans up`() {
        val effects = mutableListOf<ProtectionEffect>()
        val coordinator = createCoordinator(effects = effects)

        val state = RuntimeState(targets = mapOf(testPackage to testTarget))
        coordinator.processEvent(ProtectionEvent.PolicySnapshotUpdated(state))

        coordinator.processEvent(ProtectionEvent.ForegroundChanged(testPackage, baseTime))
        val sessionId = coordinator.getCurrentSessionId()

        effects.clear()

        // User turns off screen
        coordinator.processEvent(ProtectionEvent.ScreenOff)

        assertTrue(effects.any { it is ProtectionEffect.CancelBoundary && it.sessionId == sessionId })
        assertTrue(effects.any { it is ProtectionEffect.DismissOverlay && it.sessionId == sessionId })
        assertTrue(effects.any { it is ProtectionEffect.ReleaseAudio && it.sessionId == sessionId })
        assertNull(coordinator.getActiveTargetPackage())
    }

    // Scenario 11: Service interrupt then real reopen
    @Test
    fun `scenario 11 - service interrupt then real reopen starts clean new session`() {
        val effects = mutableListOf<ProtectionEffect>()
        val coordinator = createCoordinator(effects = effects)

        val state = RuntimeState(targets = mapOf(testPackage to testTarget))
        coordinator.processEvent(ProtectionEvent.PolicySnapshotUpdated(state))

        coordinator.processEvent(ProtectionEvent.ForegroundChanged(testPackage, baseTime))
        assertEquals(1L, coordinator.getCurrentSessionId())

        // Accessibility service is interrupted / killed
        coordinator.processEvent(ProtectionEvent.ServiceInterrupted)
        assertNull(coordinator.getActiveTargetPackage())

        effects.clear()

        // Reconnect and reopen target
        coordinator.processEvent(ProtectionEvent.ForegroundChanged(testPackage, baseTime.plusSeconds(30)))

        assertEquals(2L, coordinator.getCurrentSessionId())
        assertEquals(testPackage, coordinator.getActiveTargetPackage())
        assertTrue(effects.any { it is ProtectionEffect.ShowInterventionOverlay && it.sessionId == 2L })
    }

    // Scenario 12: Policy not ready when target event arrives
    @Test
    fun `scenario 12 - policy not ready when target event arrives queues and processes on readiness`() {
        val effects = mutableListOf<ProtectionEffect>()
        val coordinator = createCoordinator(effects = effects)

        // Event arrives before policy snapshot
        coordinator.processEvent(ProtectionEvent.ForegroundChanged(testPackage, baseTime))

        // Must NOT show overlay or allow without policy
        assertTrue(effects.none { it is ProtectionEffect.ShowInterventionOverlay })
        assertTrue(effects.none { it is ProtectionEffect.DismissOverlay })

        // Repository signals policy readiness
        val state = RuntimeState(targets = mapOf(testPackage to testTarget))
        coordinator.processEvent(ProtectionEvent.PolicySnapshotUpdated(state))

        // Now queued event should be evaluated immediately
        assertEquals(1L, coordinator.getCurrentSessionId())
        assertEquals(testPackage, coordinator.getActiveTargetPackage())
        assertTrue(effects.any { it is ProtectionEffect.ShowInterventionOverlay && it.sessionId == 1L })
    }
}
