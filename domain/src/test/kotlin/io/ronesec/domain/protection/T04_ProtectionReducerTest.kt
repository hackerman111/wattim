package io.ronesec.domain.protection

import io.ronesec.domain.model.AttemptOutcome
import io.ronesec.domain.model.CompiledBlockSession
import io.ronesec.domain.model.GlobalPause
import io.ronesec.domain.model.RuntimePolicySnapshot
import io.ronesec.domain.model.RuntimeState
import io.ronesec.domain.model.SessionId
import io.ronesec.domain.model.SessionPermit
import io.ronesec.domain.model.TargetConfig
import io.ronesec.domain.policy.AllowReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class T04_ProtectionReducerTest {

    private val targetPackage = "com.example.protectedapp"
    private val baseTarget = TargetConfig(
        packageName = targetPackage,
        displayName = "Target",
        durationMs = 8_000L,
        reinterventionMs = 300_000L
    )
    private val snapshot = RuntimePolicySnapshot(
        revision = 1L,
        targets = mapOf(targetPackage to baseTarget),
        activeGrants = emptyMap(),
        activeBlockSessions = emptyList(),
        activeSchedules = emptyList(),
        globalPause = GlobalPause.None
    )

    private var sessionCounter = 1L
    private val context = ReducerContext(
        nowWall = Instant.parse("2026-09-09T12:00:00Z"),
        nowElapsedMs = 500_000L,
        zoneId = ZoneId.of("UTC"),
        runtimeState = RuntimeState(snapshot),
        nextSessionId = { SessionId(100L, 1L, sessionCounter++) },
        nextAttemptId = { "att-$sessionCounter" }
    )

    @Test
    fun fullBreathingCycleToContinue_withSessionReinterventionDeadline() {
        // 1. Target entered while Idle
        val entryResult = ProtectionReducer.reduce(
            ProtectionState.Idle,
            ProtectionEvent.ForegroundCandidate(targetPackage, 1000L, 1L),
            context
        )
        assertTrue(entryResult.newState is ProtectionState.Intervening)
        val intervening = entryResult.newState as ProtectionState.Intervening
        assertEquals(InterveningSubstate.AwaitingAttachment, intervening.substate)
        assertTrue(entryResult.effects.any { it is ProtectionEffect.ShowIntervention })
        val audioEffect = entryResult.effects.filterIsInstance<ProtectionEffect.AcquireAudioLease>().single()
        assertEquals(targetPackage, audioEffect.packageName)

        val session = intervening.session

        // 2. Overlay Attached
        val attachResult = ProtectionReducer.reduce(
            intervening,
            ProtectionEvent.OverlayAttached(session.sessionId, session.cycle),
            entryResult.updatedRuntimeState.let { context.copy(runtimeState = it) }
        )
        assertTrue(attachResult.newState is ProtectionState.Intervening)
        val breathing = (attachResult.newState as ProtectionState.Intervening).substate
        assertTrue(breathing is InterveningSubstate.Breathing)
        assertEquals(context.nowElapsedMs + 8_000L, (breathing as InterveningSubstate.Breathing).deadlineElapsedMs)

        // 3. Deadline Reached (now = start + 8000ms)
        val deadlineContext = context.copy(
            nowElapsedMs = context.nowElapsedMs + 8_000L,
            runtimeState = attachResult.updatedRuntimeState
        )
        val completeResult = ProtectionReducer.reduce(
            attachResult.newState,
            ProtectionEvent.BreathingDeadlineReached(session.sessionId, session.cycle),
            deadlineContext
        )
        assertTrue(completeResult.newState is ProtectionState.Intervening)
        val completeSubstate = (completeResult.newState as ProtectionState.Intervening).substate
        assertTrue(completeSubstate is InterveningSubstate.Complete)
        assertTrue(completeResult.effects.any { it is ProtectionEffect.UpdateOverlayComplete })

        // 4. Action Continue
        val continueResult = ProtectionReducer.reduce(
            completeResult.newState,
            ProtectionEvent.ActionContinue(session.sessionId, session.cycle),
            deadlineContext.copy(runtimeState = completeResult.updatedRuntimeState)
        )
        assertTrue(continueResult.newState is ProtectionState.Granted)
        val granted = continueResult.newState as ProtectionState.Granted
        assertEquals(AllowReason.ACTIVE_SESSION_PERMIT, granted.reason)

        // Verifies CONTINUED outcome, session deadline, overlay dismissed, audio released
        val outcomeEffect = continueResult.effects.filterIsInstance<ProtectionEffect.CommitAttemptOutcome>().firstOrNull()
        assertNotNull(outcomeEffect)
        assertEquals(AttemptOutcome.CONTINUED, outcomeEffect!!.outcome)

        assertTrue(
            "Normal reintervention must not persist access across departure",
            continueResult.effects.none { it is ProtectionEffect.CommitAccessGrant }
        )
        assertEquals(
            deadlineContext.nowElapsedMs + baseTarget.reinterventionMs,
            continueResult.updatedRuntimeState.sessionPermits[targetPackage]?.expiresElapsedMs
        )

        assertTrue(continueResult.effects.any { it is ProtectionEffect.DismissOverlay })
        assertTrue(continueResult.effects.any { it is ProtectionEffect.ReleaseAudioLease })
    }

    @Test
    fun exit_persistsOverlayUntilDepartureConfirmed() {
        val entryResult = ProtectionReducer.reduce(
            ProtectionState.Idle,
            ProtectionEvent.ForegroundCandidate(targetPackage, 1000L, 1L),
            context
        )
        val intervening = entryResult.newState as ProtectionState.Intervening

        // User clicks Exit
        val exitResult = ProtectionReducer.reduce(
            intervening,
            ProtectionEvent.ActionExit(intervening.session.sessionId),
            context.copy(runtimeState = entryResult.updatedRuntimeState)
        )
        assertTrue(exitResult.newState is ProtectionState.Exiting)
        // Outcome committed as ABANDONED, SendToHome dispatched
        val outcomeEffect = exitResult.effects.filterIsInstance<ProtectionEffect.CommitAttemptOutcome>().firstOrNull()
        assertNotNull(outcomeEffect)
        assertEquals(AttemptOutcome.ABANDONED, outcomeEffect!!.outcome)
        assertTrue(exitResult.effects.any { it is ProtectionEffect.SendToHome })
        // ActionExit must clear uncommitted history delta so exponential backoff does not grow
        assertTrue(exitResult.updatedRuntimeState.uncommittedHistoryDeltas[targetPackage].isNullOrEmpty())

        // Overlay is NOT dismissed yet (retained until departure)
        assertTrue(exitResult.effects.none { it is ProtectionEffect.DismissOverlay })

        // Confirmed departure away from target
        val departResult = ProtectionReducer.reduce(
            exitResult.newState,
            ProtectionEvent.DepartureConfirmed(targetPackage),
            context.copy(runtimeState = exitResult.updatedRuntimeState)
        )
        assertTrue(departResult.newState is ProtectionState.Idle)
        // Now overlay is dismissed and audio released
        assertTrue(departResult.effects.any { it is ProtectionEffect.DismissOverlay })
        assertTrue(departResult.effects.any { it is ProtectionEffect.ReleaseAudioLease })
        // Last exit timestamp recorded
        assertNotNull(departResult.updatedRuntimeState.lastExitElapsedMs[targetPackage])
    }

    @Test
    fun overlayAttachFailure_interruptsProtectionWithoutSendingHome() {
        val entryResult = ProtectionReducer.reduce(
            ProtectionState.Idle,
            ProtectionEvent.ForegroundCandidate(targetPackage, 1000L, 1L),
            context
        )
        val intervening = entryResult.newState as ProtectionState.Intervening

        val failureResult = ProtectionReducer.reduce(
            intervening,
            ProtectionEvent.OverlayAttachFailed(
                sessionId = intervening.session.sessionId,
                cycle = intervening.session.cycle,
                errorType = "IllegalStateException"
            ),
            context.copy(runtimeState = entryResult.updatedRuntimeState)
        )

        assertTrue(failureResult.newState is ProtectionState.Unavailable)
        val outcome = failureResult.effects
            .filterIsInstance<ProtectionEffect.CommitAttemptOutcome>()
            .single()
        assertEquals(AttemptOutcome.INTERRUPTED, outcome.outcome)
        assertTrue(failureResult.effects.any { it is ProtectionEffect.DismissOverlay })
        assertTrue(failureResult.effects.any { it is ProtectionEffect.ReleaseAudioLease })
        assertTrue(failureResult.effects.any { it is ProtectionEffect.UpdateServiceSubscription })
        assertEquals(
            false,
            failureResult.effects
                .filterIsInstance<ProtectionEffect.SetProtectionOperational>()
                .single()
                .operational
        )
        assertTrue(failureResult.effects.none { it is ProtectionEffect.SendToHome })
    }

    @Test
    fun coherentReadyIsTheOnlyStartupEventThatMarksProtectionOperational() {
        val connected = ProtectionReducer.reduce(
            ProtectionState.Unavailable(),
            ProtectionEvent.ServiceConnected(generation = 1L),
            context
        )
        assertEquals(
            false,
            connected.effects
                .filterIsInstance<ProtectionEffect.SetProtectionOperational>()
                .single()
                .operational
        )

        val ready = ProtectionReducer.reduce(
            connected.newState,
            ProtectionEvent.CoherentReady(snapshot),
            context.copy(runtimeState = connected.updatedRuntimeState)
        )
        assertEquals(
            true,
            ready.effects
                .filterIsInstance<ProtectionEffect.SetProtectionOperational>()
                .single()
                .operational
        )

        val unavailable = ProtectionState.Unavailable("Overlay attachment failed")
        val staleReady = ProtectionReducer.reduce(
            unavailable,
            ProtectionEvent.CoherentReady(snapshot),
            context
        )
        assertEquals(unavailable, staleReady.newState)
        assertTrue(staleReady.effects.isEmpty())

        val entry = ProtectionReducer.reduce(
            ProtectionState.Idle,
            ProtectionEvent.ForegroundCandidate(targetPackage, 1000L, 1L),
            context
        )
        val activeIntervention = entry.newState as ProtectionState.Intervening
        val duplicateReady = ProtectionReducer.reduce(
            activeIntervention,
            ProtectionEvent.CoherentReady(snapshot),
            context.copy(runtimeState = entry.updatedRuntimeState)
        )
        assertEquals(activeIntervention, duplicateReady.newState)
        assertTrue(duplicateReady.effects.isEmpty())
    }

    @Test
    fun hardBlockOutcomeIsCommittedOnlyAfterMatchingOverlayAttachment() {
        val blockSnapshot = snapshot.copy(
            activeBlockSessions = listOf(
                CompiledBlockSession(
                    id = "block-1",
                    name = "Focus",
                    startTime = context.nowWall.minusSeconds(60),
                    endTime = context.nowWall.plusSeconds(60),
                    active = true,
                    targetPackages = setOf(targetPackage)
                )
            )
        )
        val entryResult = ProtectionReducer.reduce(
            ProtectionState.Idle,
            ProtectionEvent.ForegroundCandidate(targetPackage, 1000L, 1L),
            context.copy(runtimeState = RuntimeState(blockSnapshot))
        )
        val blocked = entryResult.newState as ProtectionState.Blocked

        val pendingAttempt = entryResult.effects.filterIsInstance<ProtectionEffect.RecordAttempt>().single()
        assertEquals(null, pendingAttempt.record.outcome)
        assertEquals(AttachmentStatus.AWAITING_ATTACHMENT, blocked.attachmentStatus)

        val attachedResult = ProtectionReducer.reduce(
            blocked,
            ProtectionEvent.OverlayAttached(blocked.session.sessionId, blocked.session.cycle),
            context.copy(runtimeState = entryResult.updatedRuntimeState)
        )

        assertEquals(
            AttachmentStatus.ATTACHED,
            (attachedResult.newState as ProtectionState.Blocked).attachmentStatus
        )
        val outcome = attachedResult.effects
            .filterIsInstance<ProtectionEffect.CommitAttemptOutcome>()
            .single()
        assertEquals(AttemptOutcome.BLOCKED, outcome.outcome)
    }

    @Test
    fun hardBlockExpiryReusesSessionWithoutRemovingProtectionSurface() {
        val blockSnapshot = snapshot.copy(
            activeBlockSessions = listOf(
                CompiledBlockSession(
                    id = "block-2",
                    name = "Focus",
                    startTime = context.nowWall.minusSeconds(60),
                    endTime = context.nowWall.plusSeconds(60),
                    active = true,
                    targetPackages = setOf(targetPackage)
                )
            )
        )
        val entryResult = ProtectionReducer.reduce(
            ProtectionState.Idle,
            ProtectionEvent.ForegroundCandidate(targetPackage, 1000L, 1L),
            context.copy(runtimeState = RuntimeState(blockSnapshot))
        )
        val blocked = entryResult.newState as ProtectionState.Blocked
        val attachedResult = ProtectionReducer.reduce(
            blocked,
            ProtectionEvent.OverlayAttached(blocked.session.sessionId, blocked.session.cycle),
            context.copy(runtimeState = entryResult.updatedRuntimeState)
        )

        val expiryResult = ProtectionReducer.reduce(
            attachedResult.newState,
            ProtectionEvent.TemporalBoundaryReached(context.nowWall.toEpochMilli()),
            context.copy(runtimeState = attachedResult.updatedRuntimeState.copy(snapshot = snapshot))
        )

        val intervening = expiryResult.newState as ProtectionState.Intervening
        assertEquals(blocked.session.sessionId, intervening.session.sessionId)
        assertEquals(blocked.session.cycle + 1, intervening.session.cycle)
        assertTrue(expiryResult.effects.any { it is ProtectionEffect.ShowIntervention })
        assertTrue(expiryResult.effects.none { it is ProtectionEffect.DismissOverlay })
        assertTrue(expiryResult.effects.none { it is ProtectionEffect.ReleaseAudioLease })
    }

    @Test
    fun policyAllowBeforeHardBlockAttachmentInterruptsPendingAttempt() {
        val blockSnapshot = snapshot.copy(
            activeBlockSessions = listOf(
                CompiledBlockSession(
                    id = "block-before-attach-allow",
                    name = "Focus",
                    startTime = context.nowWall.minusSeconds(60),
                    endTime = context.nowWall.plusSeconds(60),
                    active = true,
                    targetPackages = setOf(targetPackage)
                )
            )
        )
        val entryResult = ProtectionReducer.reduce(
            ProtectionState.Idle,
            ProtectionEvent.ForegroundCandidate(targetPackage, 1000L, 1L),
            context.copy(runtimeState = RuntimeState(blockSnapshot))
        )
        val blocked = entryResult.newState as ProtectionState.Blocked
        val pausedSnapshot = snapshot.copy(
            revision = snapshot.revision + 1,
            globalPause = GlobalPause.Indefinite
        )

        val policyResult = ProtectionReducer.reduce(
            blocked,
            ProtectionEvent.PolicyCommitted(pausedSnapshot.revision, pausedSnapshot),
            context.copy(runtimeState = entryResult.updatedRuntimeState)
        )

        assertTrue(policyResult.newState is ProtectionState.Granted)
        val outcome = policyResult.effects
            .filterIsInstance<ProtectionEffect.CommitAttemptOutcome>()
            .single()
        assertEquals(blocked.session.attemptId, outcome.attemptId)
        assertEquals(AttemptOutcome.INTERRUPTED, outcome.outcome)
        assertTrue(policyResult.effects.any { it is ProtectionEffect.DismissOverlay })
        assertTrue(policyResult.effects.any { it is ProtectionEffect.ReleaseAudioLease })
    }

    @Test
    fun policyInterventionBeforeHardBlockAttachmentKeepsAttemptIdentity() {
        val blockSnapshot = snapshot.copy(
            activeBlockSessions = listOf(
                CompiledBlockSession(
                    id = "block-before-attach-intervention",
                    name = "Focus",
                    startTime = context.nowWall.minusSeconds(60),
                    endTime = context.nowWall.plusSeconds(60),
                    active = true,
                    targetPackages = setOf(targetPackage)
                )
            )
        )
        val entryResult = ProtectionReducer.reduce(
            ProtectionState.Idle,
            ProtectionEvent.ForegroundCandidate(targetPackage, 1000L, 1L),
            context.copy(runtimeState = RuntimeState(blockSnapshot))
        )
        val blocked = entryResult.newState as ProtectionState.Blocked

        val policyResult = ProtectionReducer.reduce(
            blocked,
            ProtectionEvent.PolicyCommitted(snapshot.revision + 1, snapshot.copy(revision = snapshot.revision + 1)),
            context.copy(runtimeState = entryResult.updatedRuntimeState)
        )

        val intervening = policyResult.newState as ProtectionState.Intervening
        assertEquals(InterveningSubstate.AwaitingAttachment, intervening.substate)
        assertEquals(blocked.session.sessionId, intervening.session.sessionId)
        assertEquals(blocked.session.cycle, intervening.session.cycle)
        assertEquals(blocked.session.attemptId, intervening.session.attemptId)
        assertTrue(policyResult.effects.any { it is ProtectionEffect.ShowIntervention })
        assertTrue(policyResult.effects.none { it is ProtectionEffect.RecordAttempt })
        assertTrue(policyResult.effects.none { it is ProtectionEffect.AcquireAudioLease })

        val attachResult = ProtectionReducer.reduce(
            intervening,
            ProtectionEvent.OverlayAttached(intervening.session.sessionId, intervening.session.cycle),
            context.copy(runtimeState = policyResult.updatedRuntimeState)
        )
        assertTrue((attachResult.newState as ProtectionState.Intervening).substate is InterveningSubstate.Breathing)
    }

    @Test
    fun hardBlockAttachFailureReleasesOwnedResourcesWithoutSendingHome() {
        val blockSnapshot = snapshot.copy(
            activeBlockSessions = listOf(
                CompiledBlockSession(
                    id = "block-2",
                    name = "Focus",
                    startTime = context.nowWall.minusSeconds(60),
                    endTime = context.nowWall.plusSeconds(60),
                    active = true,
                    targetPackages = setOf(targetPackage)
                )
            )
        )
        val entryResult = ProtectionReducer.reduce(
            ProtectionState.Idle,
            ProtectionEvent.ForegroundCandidate(targetPackage, 1000L, 1L),
            context.copy(runtimeState = RuntimeState(blockSnapshot))
        )
        val blocked = entryResult.newState as ProtectionState.Blocked

        val failureResult = ProtectionReducer.reduce(
            blocked,
            ProtectionEvent.OverlayAttachFailed(
                blocked.session.sessionId,
                blocked.session.cycle,
                "BadTokenException"
            ),
            context.copy(runtimeState = entryResult.updatedRuntimeState)
        )

        assertTrue(failureResult.newState is ProtectionState.Unavailable)
        assertTrue(failureResult.effects.any { it is ProtectionEffect.ReleaseAudioLease })
        assertTrue(failureResult.effects.none { it is ProtectionEffect.SendToHome })
        val outcome = failureResult.effects
            .filterIsInstance<ProtectionEffect.CommitAttemptOutcome>()
            .single()
        assertEquals(AttemptOutcome.INTERRUPTED, outcome.outcome)
    }

    @Test
    fun screenOff_suspendsAndCleansResources() {
        val entryResult = ProtectionReducer.reduce(
            ProtectionState.Idle,
            ProtectionEvent.ForegroundCandidate(targetPackage, 1000L, 1L),
            context
        )

        val screenOffResult = ProtectionReducer.reduce(
            entryResult.newState,
            ProtectionEvent.ScreenOff,
            context.copy(runtimeState = entryResult.updatedRuntimeState)
        )
        assertTrue(screenOffResult.newState is ProtectionState.Suspended)
        assertTrue(screenOffResult.effects.any { it is ProtectionEffect.DismissOverlay })
        assertTrue(screenOffResult.effects.any { it is ProtectionEffect.ReleaseAudioLease })
        val outcome = screenOffResult.effects
            .filterIsInstance<ProtectionEffect.CommitAttemptOutcome>()
            .single()
        assertEquals(AttemptOutcome.INTERRUPTED, outcome.outcome)
    }

    @Test
    fun staleSessionAction_isRejected() {
        val entryResult = ProtectionReducer.reduce(
            ProtectionState.Idle,
            ProtectionEvent.ForegroundCandidate(targetPackage, 1000L, 1L),
            context
        )
        val intervening = entryResult.newState as ProtectionState.Intervening

        // Action with wrong session ID
        val wrongSessionId = SessionId(999L, 999L, 999L)
        val staleResult = ProtectionReducer.reduce(
            intervening,
            ProtectionEvent.ActionContinue(wrongSessionId, intervening.session.cycle),
            context.copy(runtimeState = entryResult.updatedRuntimeState)
        )
        // State remains unchanged, no effects dispatched
        assertEquals(intervening, staleResult.newState)
        assertTrue(staleResult.effects.isEmpty())
    }

    @Test
    fun policyCommitWhileIdleRefreshesSubscriptionWithNewEnabledTarget() {
        val telegramPackage = "org.telegram.messenger"
        val updatedSnapshot = snapshot.copy(
            revision = snapshot.revision + 1,
            targets = snapshot.targets + (telegramPackage to baseTarget.copy(
                packageName = telegramPackage,
                displayName = "Telegram"
            ))
        )

        val result = ProtectionReducer.reduce(
            ProtectionState.Idle,
            ProtectionEvent.PolicyCommitted(updatedSnapshot.revision, updatedSnapshot),
            context
        )

        val subscription = result.effects
            .filterIsInstance<ProtectionEffect.UpdateServiceSubscription>()
            .single()
        assertEquals(setOf(targetPackage, telegramPackage), subscription.targetPackages)
        assertEquals(false, subscription.trackAll)
    }

    @Test
    fun policyCommitDuringActiveTargetKeepsWideSubscription() {
        val entry = ProtectionReducer.reduce(
            ProtectionState.Idle,
            ProtectionEvent.ForegroundCandidate(targetPackage, 1_000L, 1L),
            context
        )
        val telegramPackage = "org.telegram.messenger"
        val updatedSnapshot = snapshot.copy(
            revision = snapshot.revision + 1,
            targets = snapshot.targets + (telegramPackage to baseTarget.copy(
                packageName = telegramPackage,
                displayName = "Telegram"
            ))
        )

        val result = ProtectionReducer.reduce(
            entry.newState,
            ProtectionEvent.PolicyCommitted(updatedSnapshot.revision, updatedSnapshot),
            context.copy(runtimeState = entry.updatedRuntimeState)
        )

        val subscription = result.effects
            .filterIsInstance<ProtectionEffect.UpdateServiceSubscription>()
            .single()
        assertEquals(setOf(targetPackage, telegramPackage), subscription.targetPackages)
        assertEquals(true, subscription.trackAll)
    }

    @Test
    fun disablingGrantedTargetClearsPermitAndRemovesItFromSubscription() {
        val session = ActiveSession(
            sessionId = SessionId(100L, 1L, 70L),
            packageName = targetPackage,
            cycle = 1,
            attemptId = "att-70"
        )
        val runtime = context.runtimeState.copy(
            sessionPermits = mapOf(
                targetPackage to SessionPermit(targetPackage, session.sessionId, expiresElapsedMs = null)
            )
        )
        val disabledSnapshot = snapshot.copy(
            revision = snapshot.revision + 1,
            targets = mapOf(targetPackage to baseTarget.copy(enabled = false))
        )

        val result = ProtectionReducer.reduce(
            ProtectionState.Granted(session, targetPackage, AllowReason.ACTIVE_SESSION_PERMIT),
            ProtectionEvent.PolicyCommitted(disabledSnapshot.revision, disabledSnapshot),
            context.copy(runtimeState = runtime)
        )

        val granted = result.newState as ProtectionState.Granted
        assertEquals(AllowReason.TARGET_DISABLED, granted.reason)
        assertEquals(null, granted.session)
        assertTrue(targetPackage !in result.updatedRuntimeState.sessionPermits)
        val subscription = result.effects
            .filterIsInstance<ProtectionEffect.UpdateServiceSubscription>()
            .single()
        assertTrue(subscription.targetPackages.isEmpty())
        assertEquals(false, subscription.trackAll)
    }

    @Test
    fun stalePolicyRevisionCannotRemoveNewTargetFromSubscription() {
        val telegramPackage = "org.telegram.messenger"
        val freshSnapshot = snapshot.copy(
            revision = snapshot.revision + 1,
            targets = snapshot.targets + (telegramPackage to baseTarget.copy(
                packageName = telegramPackage,
                displayName = "Telegram"
            ))
        )
        val fresh = ProtectionReducer.reduce(
            ProtectionState.Idle,
            ProtectionEvent.PolicyCommitted(freshSnapshot.revision, freshSnapshot),
            context
        )

        val stale = ProtectionReducer.reduce(
            fresh.newState,
            ProtectionEvent.PolicyCommitted(snapshot.revision, snapshot),
            context.copy(runtimeState = fresh.updatedRuntimeState)
        )

        assertEquals(fresh.updatedRuntimeState, stale.updatedRuntimeState)
        assertTrue(stale.effects.isEmpty())
    }
}
