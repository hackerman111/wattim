package io.ronesec.android.domain.protection

import io.ronesec.android.domain.engine.RuleEngine
import io.ronesec.android.domain.engine.RuntimeState

class InterventionCoordinator(
    private val ruleEngine: RuleEngine = RuleEngine(),
    private val appLabelResolver: (String) -> String = { it.substringAfterLast('.') },
    private val savedTimeTextResolver: (RuntimeState) -> String? = { null },
    private val onEffect: (ProtectionEffect) -> Unit
) {
    private var currentState: ProtectionState = ProtectionState.Idle
    private var currentSessionId: SessionId = SessionId.NONE
    private var latestSnapshot: RuntimeState = RuntimeState()
    private var isPolicyReady: Boolean = false
    private var pendingForegroundEvent: ProtectionEvent.ForegroundChanged? = null

    fun processEvent(event: ProtectionEvent) {
        if (event is ProtectionEvent.PolicySnapshotUpdated) {
            latestSnapshot = event.snapshot
            val wasReady = isPolicyReady
            isPolicyReady = true

            val enabledTargets = event.snapshot.targets.filterValues { it.enabled }.keys
            onEffect(ProtectionEffect.UpdateAdaptiveSubscription(enabledTargets, getActiveTargetPackage() != null))

            val pending = pendingForegroundEvent
            if (!wasReady && pending != null) {
                pendingForegroundEvent = null
                processEvent(pending)
            } else if (currentState !is ProtectionState.Idle && currentState !is ProtectionState.Suspended) {
                // Re-evaluate active session with new policy
                dispatchToReducer(event)
            }
            return
        }

        if (!isPolicyReady && event is ProtectionEvent.ForegroundChanged) {
            pendingForegroundEvent = event
            return
        }

        dispatchToReducer(event)
    }

    private fun dispatchToReducer(event: ProtectionEvent) {
        val nextId = currentSessionId.next()
        val transition = InterventionReducer.reduce(
            state = currentState,
            event = event,
            snapshot = latestSnapshot,
            isPolicyReady = isPolicyReady,
            appLabelResolver = appLabelResolver,
            savedTimeTextResolver = savedTimeTextResolver,
            ruleEngine = ruleEngine,
            nextSessionId = nextId
        )

        currentState = transition.state
        when (val state = transition.state) {
            is ProtectionState.Evaluating -> currentSessionId = state.session.id
            is ProtectionState.Intervening -> currentSessionId = state.session.id
            is ProtectionState.Granted -> currentSessionId = state.session.id
            is ProtectionState.Blocked -> currentSessionId = state.session.id
            is ProtectionState.Exiting -> currentSessionId = state.session.id
            is ProtectionState.Idle,
            is ProtectionState.Suspended -> Unit
        }

        for (effect in transition.effects) {
            onEffect(effect)
        }
    }

    fun getCurrentSessionId(): Long = currentSessionId.value
    fun getCurrentSession(): SessionId = currentSessionId
    fun getCurrentState(): ProtectionState = currentState

    fun getActiveTargetPackage(): String? = when (val state = currentState) {
        is ProtectionState.Evaluating -> state.session.packageName
        is ProtectionState.Intervening -> state.session.packageName
        is ProtectionState.Granted -> state.session.packageName
        is ProtectionState.Blocked -> state.session.packageName
        is ProtectionState.Exiting -> state.session.packageName
        is ProtectionState.Idle,
        is ProtectionState.Suspended -> null
    }
}
