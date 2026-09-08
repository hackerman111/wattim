package io.ronesec.android.domain.protection

import io.ronesec.android.domain.engine.RuleEngine
import io.ronesec.android.domain.engine.RuntimeState
import io.ronesec.android.domain.model.AllowReason
import io.ronesec.android.domain.model.AttemptOutcome
import io.ronesec.android.domain.model.Decision
import io.ronesec.android.domain.model.InterventionConfig
import java.time.Instant

data class Transition(
    val state: ProtectionState,
    val effects: List<ProtectionEffect>
)

object InterventionReducer {

    fun reduce(
        state: ProtectionState,
        event: ProtectionEvent,
        snapshot: RuntimeState,
        isPolicyReady: Boolean,
        appLabelResolver: (String) -> String = { it.substringAfterLast('.') },
        savedTimeTextResolver: (RuntimeState) -> String? = { null },
        ruleEngine: RuleEngine = RuleEngine(),
        nextSessionId: SessionId = SessionId.INITIAL
    ): Transition {
        return when (event) {
            is ProtectionEvent.ForegroundChanged -> handleForegroundChanged(
                state, event.packageName, event.timestamp, snapshot, isPolicyReady,
                appLabelResolver, savedTimeTextResolver, ruleEngine, nextSessionId
            )
            is ProtectionEvent.UserAction -> handleUserAction(
                state, event.sessionId, event.targetPackage, event.action, event.timestamp, snapshot
            )
            is ProtectionEvent.TemporalBoundaryReached -> handleBoundaryReached(
                state, event.sessionId, event.targetPackage, event.boundaryType, event.timestamp,
                snapshot, appLabelResolver, savedTimeTextResolver, ruleEngine
            )
            is ProtectionEvent.PolicySnapshotUpdated -> handlePolicyUpdated(
                state, snapshot, isPolicyReady, appLabelResolver, savedTimeTextResolver, ruleEngine
            )
            is ProtectionEvent.ScreenOff -> handleSessionInterruption(state, snapshot, isScreenOff = true)
            is ProtectionEvent.ServiceInterrupted,
            is ProtectionEvent.ServiceDestroyed -> handleSessionInterruption(state, snapshot, isScreenOff = false)
            is ProtectionEvent.ScreenOn -> {
                if (state is ProtectionState.Suspended) Transition(ProtectionState.Idle, emptyList())
                else Transition(state, emptyList())
            }
            is ProtectionEvent.TimeChanged,
            is ProtectionEvent.ServiceConnected -> Transition(state, emptyList())
        }
    }

    private fun handleForegroundChanged(
        state: ProtectionState,
        packageName: String,
        timestamp: Instant,
        snapshot: RuntimeState,
        isPolicyReady: Boolean,
        appLabelResolver: (String) -> String,
        savedTimeTextResolver: (RuntimeState) -> String?,
        ruleEngine: RuleEngine,
        nextSessionId: SessionId
    ): Transition {
        if (!isPolicyReady) return Transition(state, emptyList())

        val enabledTargets = snapshot.targets.filterValues { it.enabled }.keys

        // If currently Exiting via HOME
        if (state is ProtectionState.Exiting) {
            if (packageName == state.session.packageName) {
                // Lingering event from same package during home transition -> ignore
                return Transition(state, emptyList())
            }
            // Exit confirmed to launcher or other app
            val exitEffects = listOf(
                ProtectionEffect.DismissOverlay(state.session.id),
                ProtectionEffect.ReleaseAudio(state.session.id),
                ProtectionEffect.CancelBoundary(state.session.id),
                ProtectionEffect.PersistRevoke(state.session.packageName, state.session.id),
                ProtectionEffect.UpdateAdaptiveSubscription(enabledTargets, false)
            )
            val newTarget = snapshot.targets[packageName]
            return if (newTarget != null && newTarget.enabled) {
                val newSession = TargetSession(nextSessionId, packageName, timestamp)
                evaluateTarget(newSession, snapshot, ruleEngine, appLabelResolver, savedTimeTextResolver, timestamp, exitEffects + ProtectionEffect.UpdateAdaptiveSubscription(enabledTargets, true))
            } else {
                Transition(ProtectionState.Idle, exitEffects)
            }
        }

        // Active session in progress
        val currentSession = when (state) {
            is ProtectionState.Evaluating -> state.session
            is ProtectionState.Intervening -> state.session
            is ProtectionState.Granted -> state.session
            is ProtectionState.Blocked -> state.session
            else -> null
        }

        if (currentSession != null) {
            if (currentSession.packageName == packageName) {
                return Transition(state, emptyList())
            }
            // Switched away to different package
            val switchEffects = listOf(
                ProtectionEffect.CancelBoundary(currentSession.id),
                ProtectionEffect.DismissOverlay(currentSession.id),
                ProtectionEffect.ReleaseAudio(currentSession.id),
                ProtectionEffect.PersistRevoke(currentSession.packageName, currentSession.id),
                ProtectionEffect.UpdateAdaptiveSubscription(enabledTargets, false)
            )
            val newTarget = snapshot.targets[packageName]
            return if (newTarget != null && newTarget.enabled) {
                val newSession = TargetSession(nextSessionId, packageName, timestamp)
                evaluateTarget(newSession, snapshot, ruleEngine, appLabelResolver, savedTimeTextResolver, timestamp, switchEffects + ProtectionEffect.UpdateAdaptiveSubscription(enabledTargets, true))
            } else {
                Transition(ProtectionState.Idle, switchEffects)
            }
        }

        // State was Idle or Suspended
        val target = snapshot.targets[packageName]
        if (target == null || !target.enabled) {
            return Transition(ProtectionState.Idle, emptyList())
        }

        val newSession = TargetSession(nextSessionId, packageName, timestamp)
        val initialEffects = listOf(ProtectionEffect.UpdateAdaptiveSubscription(enabledTargets, true))
        return evaluateTarget(newSession, snapshot, ruleEngine, appLabelResolver, savedTimeTextResolver, timestamp, initialEffects)
    }

    private fun evaluateTarget(
        session: TargetSession,
        snapshot: RuntimeState,
        ruleEngine: RuleEngine,
        appLabelResolver: (String) -> String,
        savedTimeTextResolver: (RuntimeState) -> String?,
        timestamp: Instant,
        initialEffects: List<ProtectionEffect>
    ): Transition {
        val decision = ruleEngine.evaluate(
            packageName = session.packageName,
            now = timestamp,
            state = snapshot,
            sessionId = session.id
        )
        val effects = initialEffects.toMutableList()
        val target = snapshot.targets[session.packageName]
        val config = target?.intervention ?: InterventionConfig()

        return when (decision) {
            is Decision.Allow -> {
                effects.add(ProtectionEffect.DismissOverlay(session.id))
                effects.add(ProtectionEffect.ReleaseAudio(session.id))
                val permit = if (decision.reason == AllowReason.ACTIVE_TIMED_PERMIT) {
                    val grant = snapshot.activeGrants[session.packageName]
                    AccessPermit.Timed(grant?.expiresAt ?: timestamp.plusMillis(config.reinterventionMs ?: 0L))
                } else {
                    effects.add(ProtectionEffect.PersistGrant(session.packageName, config.reinterventionMs, session.id))
                    config.reinterventionMs?.let { delayMs ->
                        if (delayMs > 0L) {
                            effects.add(ProtectionEffect.ScheduleBoundary(session.id, session.packageName, BoundaryType.REINTERVENTION, delayMs))
                        }
                    }
                    AccessPermit.Session(session.id)
                }
                Transition(ProtectionState.Granted(session, permit), effects)
            }
            is Decision.Block -> {
                val label = appLabelResolver(session.packageName)
                effects.add(ProtectionEffect.AcquireAudio(session.id))
                effects.add(ProtectionEffect.ShowBlockOverlay(session.id, session.packageName, label, decision.until))
                Transition(ProtectionState.Blocked(session, decision.until ?: timestamp.plusSeconds(3600)), effects)
            }
            is Decision.Intervention -> {
                val label = appLabelResolver(session.packageName)
                val savedText = savedTimeTextResolver(snapshot)
                effects.add(ProtectionEffect.AcquireAudio(session.id))
                effects.add(ProtectionEffect.ShowInterventionOverlay(session.id, session.packageName, label, decision.config, savedText))
                Transition(ProtectionState.Intervening(session), effects)
            }
        }
    }

    private fun handleUserAction(
        state: ProtectionState,
        sessionId: SessionId,
        targetPackage: String,
        action: UserProtectionAction,
        timestamp: Instant,
        snapshot: RuntimeState
    ): Transition {
        val currentSession = when (state) {
            is ProtectionState.Intervening -> state.session
            is ProtectionState.Blocked -> state.session
            is ProtectionState.Evaluating -> state.session
            else -> null
        }
        if (currentSession == null || currentSession.id != sessionId) {
            return Transition(state, emptyList())
        }

        return when (action) {
            is UserProtectionAction.Close -> {
                val effects = listOf(
                    ProtectionEffect.PerformGlobalHome(sessionId),
                    ProtectionEffect.CancelBoundary(sessionId),
                    ProtectionEffect.PersistAttempt(targetPackage, AttemptOutcome.ABANDONED, timestamp)
                )
                Transition(ProtectionState.Exiting(currentSession), effects)
            }
            is UserProtectionAction.Continue -> {
                val target = snapshot.targets[targetPackage]
                val reintMs = target?.intervention?.reinterventionMs
                val effects = mutableListOf<ProtectionEffect>(
                    ProtectionEffect.DismissOverlay(sessionId),
                    ProtectionEffect.ReleaseAudio(sessionId),
                    ProtectionEffect.PersistGrant(targetPackage, reintMs, sessionId),
                    ProtectionEffect.PersistAttempt(targetPackage, AttemptOutcome.CONTINUED, timestamp)
                )
                reintMs?.let { delayMs ->
                    if (delayMs > 0L) {
                        effects.add(ProtectionEffect.ScheduleBoundary(sessionId, targetPackage, BoundaryType.REINTERVENTION, delayMs))
                    }
                }
                Transition(ProtectionState.Granted(currentSession, AccessPermit.Session(sessionId)), effects)
            }
            is UserProtectionAction.EmergencyAccess -> {
                val effects = mutableListOf<ProtectionEffect>(
                    ProtectionEffect.DismissOverlay(sessionId),
                    ProtectionEffect.ReleaseAudio(sessionId)
                )
                if (action.disableTarget) {
                    effects.add(ProtectionEffect.PersistTargetDisabled(targetPackage))
                    effects.add(ProtectionEffect.CancelBoundary(sessionId))
                    Transition(ProtectionState.Granted(currentSession, AccessPermit.Session(sessionId)), effects)
                } else {
                    effects.add(ProtectionEffect.PersistGrant(targetPackage, action.durationMs, sessionId))
                    effects.add(ProtectionEffect.PersistAttempt(targetPackage, AttemptOutcome.CONTINUED, timestamp))
                    action.durationMs?.let { delayMs ->
                        if (delayMs > 0L) {
                            effects.add(ProtectionEffect.ScheduleBoundary(sessionId, targetPackage, BoundaryType.TIMED_PERMIT_EXPIRY, delayMs))
                        }
                    }
                    val expiry = timestamp.plusMillis(action.durationMs ?: 0L)
                    Transition(ProtectionState.Granted(currentSession, AccessPermit.Timed(expiry)), effects)
                }
            }
        }
    }

    private fun handleBoundaryReached(
        state: ProtectionState,
        sessionId: SessionId,
        targetPackage: String,
        boundaryType: BoundaryType,
        timestamp: Instant,
        snapshot: RuntimeState,
        appLabelResolver: (String) -> String,
        savedTimeTextResolver: (RuntimeState) -> String?,
        ruleEngine: RuleEngine
    ): Transition {
        val currentSession = when (state) {
            is ProtectionState.Granted -> state.session
            is ProtectionState.Evaluating -> state.session
            else -> null
        }
        if (currentSession == null || currentSession.id != sessionId || currentSession.packageName != targetPackage) {
            return Transition(state, emptyList())
        }

        val target = snapshot.targets[targetPackage] ?: return Transition(state, emptyList())
        val label = appLabelResolver(targetPackage)
        val savedText = savedTimeTextResolver(snapshot)

        return when (boundaryType) {
            BoundaryType.REINTERVENTION -> {
                val pausedUntil = snapshot.protectionPausedUntil
                if (pausedUntil != null && (pausedUntil == -1L || timestamp.toEpochMilli() < pausedUntil)) {
                    return Transition(state, emptyList())
                }
                val decision = ruleEngine.evaluate(
                    packageName = targetPackage,
                    now = timestamp,
                    state = snapshot,
                    sessionId = null
                )
                if (decision is Decision.Block) {
                    val effects = listOf(
                        ProtectionEffect.AcquireAudio(sessionId),
                        ProtectionEffect.ShowBlockOverlay(sessionId, targetPackage, label, decision.until)
                    )
                    Transition(ProtectionState.Blocked(currentSession, decision.until ?: timestamp.plusSeconds(3600)), effects)
                } else {
                    val effects = listOf(
                        ProtectionEffect.AcquireAudio(sessionId),
                        ProtectionEffect.ShowInterventionOverlay(sessionId, targetPackage, label, target.intervention, savedText)
                    )
                    Transition(ProtectionState.Intervening(currentSession), effects)
                }
            }
            BoundaryType.TIMED_PERMIT_EXPIRY -> {
                val stateWithoutGrant = snapshot.copy(
                    activeGrants = snapshot.activeGrants - targetPackage,
                    activeSessionPermits = snapshot.activeSessionPermits - targetPackage
                )
                val decision = ruleEngine.evaluate(
                    packageName = targetPackage,
                    now = timestamp,
                    state = stateWithoutGrant,
                    sessionId = null
                )
                val effects = mutableListOf<ProtectionEffect>(ProtectionEffect.PersistRevoke(targetPackage, sessionId))
                when (decision) {
                    is Decision.Allow -> Transition(ProtectionState.Granted(currentSession, AccessPermit.Session(sessionId)), effects)
                    is Decision.Block -> {
                        effects.add(ProtectionEffect.AcquireAudio(sessionId))
                        effects.add(ProtectionEffect.ShowBlockOverlay(sessionId, targetPackage, label, decision.until))
                        Transition(ProtectionState.Blocked(currentSession, decision.until ?: timestamp.plusSeconds(3600)), effects)
                    }
                    is Decision.Intervention -> {
                        effects.add(ProtectionEffect.AcquireAudio(sessionId))
                        effects.add(ProtectionEffect.ShowInterventionOverlay(sessionId, targetPackage, label, decision.config, savedText))
                        Transition(ProtectionState.Intervening(currentSession), effects)
                    }
                }
            }
            BoundaryType.GLOBAL_PAUSE_EXPIRY,
            BoundaryType.SCHEDULE_BOUNDARY -> evaluateTarget(
                currentSession, snapshot, ruleEngine, appLabelResolver, savedTimeTextResolver, timestamp, emptyList()
            )
        }
    }

    private fun handlePolicyUpdated(
        state: ProtectionState,
        snapshot: RuntimeState,
        isPolicyReady: Boolean,
        appLabelResolver: (String) -> String,
        savedTimeTextResolver: (RuntimeState) -> String?,
        ruleEngine: RuleEngine
    ): Transition {
        val currentSession = when (state) {
            is ProtectionState.Evaluating -> state.session
            is ProtectionState.Intervening -> state.session
            is ProtectionState.Granted -> state.session
            is ProtectionState.Blocked -> state.session
            else -> null
        } ?: return Transition(state, emptyList())

        // Re-evaluate if active target app's policy changed while open
        val target = snapshot.targets[currentSession.packageName]
        if (target == null || !target.enabled) {
            val effects = listOf(
                ProtectionEffect.DismissOverlay(currentSession.id),
                ProtectionEffect.ReleaseAudio(currentSession.id),
                ProtectionEffect.CancelBoundary(currentSession.id)
            )
            return Transition(ProtectionState.Idle, effects)
        }
        return evaluateTarget(currentSession, snapshot, ruleEngine, appLabelResolver, savedTimeTextResolver, Instant.now(), emptyList())
    }

    private fun handleSessionInterruption(
        state: ProtectionState,
        snapshot: RuntimeState,
        isScreenOff: Boolean
    ): Transition {
        val currentSession = when (state) {
            is ProtectionState.Evaluating -> state.session
            is ProtectionState.Intervening -> state.session
            is ProtectionState.Granted -> state.session
            is ProtectionState.Blocked -> state.session
            is ProtectionState.Exiting -> state.session
            else -> null
        }
        val enabledTargets = snapshot.targets.filterValues { it.enabled }.keys
        val nextState = if (isScreenOff) ProtectionState.Suspended else ProtectionState.Idle

        if (currentSession == null) {
            return Transition(nextState, emptyList())
        }

        val effects = listOf(
            ProtectionEffect.CancelBoundary(currentSession.id),
            ProtectionEffect.DismissOverlay(currentSession.id),
            ProtectionEffect.ReleaseAudio(currentSession.id),
            ProtectionEffect.PersistRevoke(currentSession.packageName, currentSession.id),
            ProtectionEffect.UpdateAdaptiveSubscription(enabledTargets, false)
        )
        return Transition(nextState, effects)
    }
}
