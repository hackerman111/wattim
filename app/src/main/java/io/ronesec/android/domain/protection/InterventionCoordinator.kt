package io.ronesec.android.domain.protection

import io.ronesec.android.domain.engine.RuleEngine
import io.ronesec.android.domain.engine.RuntimeState
import io.ronesec.android.domain.model.AttemptOutcome
import io.ronesec.android.domain.model.Decision
import io.ronesec.android.domain.model.InterventionConfig
import java.time.Instant

class InterventionCoordinator(
    private val ruleEngine: RuleEngine = RuleEngine(),
    private val appLabelResolver: (String) -> String = { it.substringAfterLast('.') },
    private val savedTimeTextResolver: (RuntimeState) -> String? = { null },
    private val onEffect: (ProtectionEffect) -> Unit
) {
    private var currentSessionId: Long = 0L
    private var activeTargetPackage: String? = null
    private var activeSessionCycle: Int = 1
    private var isExitingViaHome: Boolean = false

    private var latestSnapshot: RuntimeState = RuntimeState()
    private var isPolicyReady: Boolean = false
    private var pendingForegroundEvent: ProtectionEvent.ForegroundChanged? = null

    fun processEvent(event: ProtectionEvent) {
        when (event) {
            is ProtectionEvent.PolicySnapshotUpdated -> handlePolicyUpdated(event.snapshot)
            is ProtectionEvent.ForegroundChanged -> handleForegroundChanged(event.packageName, event.timestamp)
            is ProtectionEvent.TemporalBoundaryReached -> handleBoundaryReached(event.sessionId, event.targetPackage, event.boundaryType, event.timestamp)
            is ProtectionEvent.UserAction -> handleUserAction(event.sessionId, event.targetPackage, event.action, event.timestamp)
            is ProtectionEvent.ScreenOff -> handleLifecycleInterruption()
            is ProtectionEvent.ServiceInterrupted -> handleLifecycleInterruption()
        }
    }

    private fun handlePolicyUpdated(snapshot: RuntimeState) {
        latestSnapshot = snapshot
        val wasReady = isPolicyReady
        isPolicyReady = true

        val enabledTargets = snapshot.targets.filterValues { it.enabled }.keys
        onEffect(ProtectionEffect.UpdateAdaptiveSubscription(enabledTargets, activeTargetPackage != null))

        if (!wasReady) {
            pendingForegroundEvent?.let { pending ->
                pendingForegroundEvent = null
                handleForegroundChanged(pending.packageName, pending.timestamp)
            }
        }
    }

    private fun handleForegroundChanged(packageName: String, timestamp: Instant) {
        if (!isPolicyReady) {
            pendingForegroundEvent = ProtectionEvent.ForegroundChanged(packageName, timestamp)
            return
        }

        // If currently exiting via home and a lingering same-package event arrives, ignore it
        if (isExitingViaHome && packageName == activeTargetPackage) {
            return
        }

        // If exiting to HOME / another app, confirm the exit
        if (isExitingViaHome && packageName != activeTargetPackage) {
            val closingSession = currentSessionId
            onEffect(ProtectionEffect.DismissOverlay(closingSession))
            onEffect(ProtectionEffect.ReleaseAudio(closingSession))
            onEffect(ProtectionEffect.CancelBoundary(closingSession))
            activeTargetPackage?.let {
                onEffect(ProtectionEffect.PersistRevoke(it, closingSession))
            }
            isExitingViaHome = false
            activeTargetPackage = null
            val enabledTargets = latestSnapshot.targets.filterValues { it.enabled }.keys
            onEffect(ProtectionEffect.UpdateAdaptiveSubscription(enabledTargets, false))
        }

        // Switching away from active target to another package
        if (activeTargetPackage != null && activeTargetPackage != packageName) {
            val previousSession = currentSessionId
            onEffect(ProtectionEffect.CancelBoundary(previousSession))
            onEffect(ProtectionEffect.DismissOverlay(previousSession))
            onEffect(ProtectionEffect.ReleaseAudio(previousSession))
            activeTargetPackage = null
            val enabledTargets = latestSnapshot.targets.filterValues { it.enabled }.keys
            onEffect(ProtectionEffect.UpdateAdaptiveSubscription(enabledTargets, false))
        }

        val target = latestSnapshot.targets[packageName]
        if (target == null || !target.enabled) {
            return
        }

        // Enter new target session
        val newSessionId = ++currentSessionId
        activeTargetPackage = packageName
        activeSessionCycle = 1
        isExitingViaHome = false

        val enabledTargets = latestSnapshot.targets.filterValues { it.enabled }.keys
        onEffect(ProtectionEffect.UpdateAdaptiveSubscription(enabledTargets, true))

        evaluateTarget(newSessionId, packageName, target.intervention, timestamp)
    }

    private fun evaluateTarget(sessionId: Long, packageName: String, config: InterventionConfig, timestamp: Instant) {
        val decision = ruleEngine.evaluate(
            packageName = packageName,
            now = timestamp,
            state = latestSnapshot,
            currentSessionId = sessionId
        )

        when (decision) {
            is Decision.Allow -> {
                onEffect(ProtectionEffect.DismissOverlay(sessionId))
                onEffect(ProtectionEffect.ReleaseAudio(sessionId))
                onEffect(ProtectionEffect.PersistGrant(packageName, config.reinterventionMs, sessionId))
                config.reinterventionMs?.let { delayMs ->
                    if (delayMs > 0L) {
                        onEffect(ProtectionEffect.ScheduleBoundary(sessionId, packageName, BoundaryType.REINTERVENTION, delayMs))
                    }
                }
            }
            is Decision.Block -> {
                val label = appLabelResolver(packageName)
                onEffect(ProtectionEffect.AcquireAudio(sessionId))
                onEffect(ProtectionEffect.ShowBlockOverlay(sessionId, packageName, label, decision.until))
            }
            is Decision.Intervention -> {
                val label = appLabelResolver(packageName)
                val savedText = savedTimeTextResolver(latestSnapshot)
                onEffect(ProtectionEffect.AcquireAudio(sessionId))
                onEffect(ProtectionEffect.ShowInterventionOverlay(sessionId, packageName, label, decision.config, savedText))
            }
        }
    }

    private fun handleUserAction(sessionId: Long, targetPackage: String, action: UserProtectionAction, timestamp: Instant) {
        if (sessionId != currentSessionId) return

        when (action) {
            is UserProtectionAction.Close -> {
                isExitingViaHome = true
                onEffect(ProtectionEffect.PerformGlobalHome(sessionId))
                onEffect(ProtectionEffect.CancelBoundary(sessionId))
                onEffect(ProtectionEffect.PersistAttempt(targetPackage, AttemptOutcome.ABANDONED, timestamp))
            }
            is UserProtectionAction.Continue -> {
                onEffect(ProtectionEffect.DismissOverlay(sessionId))
                onEffect(ProtectionEffect.ReleaseAudio(sessionId))
                val target = latestSnapshot.targets[targetPackage]
                val reintMs = target?.intervention?.reinterventionMs
                onEffect(ProtectionEffect.PersistGrant(targetPackage, reintMs, sessionId))
                onEffect(ProtectionEffect.PersistAttempt(targetPackage, AttemptOutcome.CONTINUED, timestamp))
                reintMs?.let { delayMs ->
                    if (delayMs > 0L) {
                        onEffect(ProtectionEffect.ScheduleBoundary(sessionId, targetPackage, BoundaryType.REINTERVENTION, delayMs))
                    }
                }
            }
            is UserProtectionAction.EmergencyAccess -> {
                onEffect(ProtectionEffect.DismissOverlay(sessionId))
                onEffect(ProtectionEffect.ReleaseAudio(sessionId))
                if (action.disableTarget) {
                    onEffect(ProtectionEffect.PersistTargetDisabled(targetPackage))
                    onEffect(ProtectionEffect.CancelBoundary(sessionId))
                } else {
                    onEffect(ProtectionEffect.PersistGrant(targetPackage, action.durationMs, sessionId))
                    onEffect(ProtectionEffect.PersistAttempt(targetPackage, AttemptOutcome.CONTINUED, timestamp))
                    action.durationMs?.let { delayMs ->
                        if (delayMs > 0L) {
                            onEffect(ProtectionEffect.ScheduleBoundary(sessionId, targetPackage, BoundaryType.TIMED_PERMIT_EXPIRY, delayMs))
                        }
                    }
                }
            }
        }
    }

    private fun handleBoundaryReached(sessionId: Long, targetPackage: String, boundaryType: BoundaryType, timestamp: Instant) {
        if (sessionId != currentSessionId || targetPackage != activeTargetPackage) return

        val target = latestSnapshot.targets[targetPackage] ?: return
        val label = appLabelResolver(targetPackage)
        val savedText = savedTimeTextResolver(latestSnapshot)

        when (boundaryType) {
            BoundaryType.REINTERVENTION -> {
                val pausedUntil = latestSnapshot.protectionPausedUntil
                if (pausedUntil != null && (pausedUntil == -1L || timestamp.toEpochMilli() < pausedUntil)) {
                    return
                }
                val decision = ruleEngine.evaluate(
                    packageName = targetPackage,
                    now = timestamp,
                    state = latestSnapshot,
                    currentSessionId = null
                )
                if (decision is Decision.Block) {
                    onEffect(ProtectionEffect.AcquireAudio(sessionId))
                    onEffect(ProtectionEffect.ShowBlockOverlay(sessionId, targetPackage, label, decision.until))
                    return
                }

                activeSessionCycle++
                val nextConfig = target.intervention
                onEffect(ProtectionEffect.AcquireAudio(sessionId))
                onEffect(ProtectionEffect.ShowInterventionOverlay(sessionId, targetPackage, label, nextConfig, savedText))
            }
            BoundaryType.TIMED_PERMIT_EXPIRY -> {
                onEffect(ProtectionEffect.PersistRevoke(targetPackage, sessionId))
                val stateWithoutGrant = latestSnapshot.copy(
                    activeGrants = latestSnapshot.activeGrants - targetPackage,
                    activeSessionPermits = latestSnapshot.activeSessionPermits - targetPackage
                )
                val decision = ruleEngine.evaluate(
                    packageName = targetPackage,
                    now = timestamp,
                    state = stateWithoutGrant,
                    currentSessionId = null
                )
                when (decision) {
                    is Decision.Allow -> Unit
                    is Decision.Block -> {
                        onEffect(ProtectionEffect.AcquireAudio(sessionId))
                        onEffect(ProtectionEffect.ShowBlockOverlay(sessionId, targetPackage, label, decision.until))
                    }
                    is Decision.Intervention -> {
                        onEffect(ProtectionEffect.AcquireAudio(sessionId))
                        onEffect(ProtectionEffect.ShowInterventionOverlay(sessionId, targetPackage, label, decision.config, savedText))
                    }
                }
            }
            BoundaryType.GLOBAL_PAUSE_EXPIRY, BoundaryType.SCHEDULE_BOUNDARY -> {
                evaluateTarget(sessionId, targetPackage, target.intervention, timestamp)
            }
        }
    }

    private fun handleLifecycleInterruption() {
        val sessionToCancel = currentSessionId
        onEffect(ProtectionEffect.CancelBoundary(sessionToCancel))
        onEffect(ProtectionEffect.DismissOverlay(sessionToCancel))
        onEffect(ProtectionEffect.ReleaseAudio(sessionToCancel))
        activeTargetPackage?.let {
            onEffect(ProtectionEffect.PersistRevoke(it, sessionToCancel))
        }
        activeTargetPackage = null
        isExitingViaHome = false
        val enabledTargets = latestSnapshot.targets.filterValues { it.enabled }.keys
        onEffect(ProtectionEffect.UpdateAdaptiveSubscription(enabledTargets, false))
    }

    fun getCurrentSessionId(): Long = currentSessionId
    fun getActiveTargetPackage(): String? = activeTargetPackage
}
