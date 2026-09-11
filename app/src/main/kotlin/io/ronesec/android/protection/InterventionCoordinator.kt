package io.ronesec.android.protection

import io.ronesec.android.data.PolicyStore
import io.ronesec.android.platform.accessibility.EventIngress
import io.ronesec.domain.model.MonotonicClock
import io.ronesec.domain.model.RuntimePolicySnapshot
import io.ronesec.domain.model.RuntimeState
import io.ronesec.domain.model.SessionId
import io.ronesec.domain.model.WallClock
import io.ronesec.domain.codes.SecureSessionCodePort
import io.ronesec.domain.codes.SessionCodePort
import io.ronesec.domain.protection.ProtectionEvent
import io.ronesec.domain.protection.ProtectionReducer
import io.ronesec.domain.protection.ProtectionState
import io.ronesec.domain.protection.ReducerContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Orchestrator and single writer of protection, session, and runtime state.
 * Consumes events sequentially from EventIngress, invokes pure ProtectionReducer,
 * commits state updates in memory, and dispatches typed effects to EffectExecutor.
 * Satisfies I1, I2, I3, I4, F26, F29, F30, Section 3.1.
 */
class InterventionCoordinator(
    private val policyStore: PolicyStore,
    private val eventIngress: EventIngress,
    private val effectExecutor: EffectExecutor,
    val journal: ProtectionEventJournal,
    private val wallClock: WallClock,
    private val monotonicClock: MonotonicClock,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val processNonce: Long = System.currentTimeMillis(),
    private val sessionCodePort: SessionCodePort = SecureSessionCodePort
) {
    private val _protectionState = MutableStateFlow<ProtectionState>(ProtectionState.Loading())
    val protectionState: StateFlow<ProtectionState> = _protectionState.asStateFlow()

    private val _runtimeState = MutableStateFlow(RuntimeState(RuntimePolicySnapshot.EMPTY))
    val runtimeState: StateFlow<RuntimeState> = _runtimeState.asStateFlow()

    private var serviceGeneration: Long = 0L
    private var entryCounter: Long = 1L

    private var consumerJob: Job? = null
    private var policyCollectorJob: Job? = null

    fun start() {
        if (consumerJob?.isActive == true) return

        // Single serialized event consumer
        consumerJob = scope.launch(dispatcher) {
            while (isActive) {
                val event = eventIngress.receiveNextEvent()
                processEvent(event)
            }
        }

        // Observe policy snapshot readiness and commits
        policyCollectorJob = scope.launch(dispatcher) {
            policyStore.snapshotFlow.collect { snapshot ->
                if (snapshot != RuntimePolicySnapshot.EMPTY) {
                    if (_protectionState.value is ProtectionState.Loading) {
                        eventIngress.sendControlEvent(ProtectionEvent.CoherentReady(snapshot))
                    } else {
                        eventIngress.sendControlEvent(ProtectionEvent.PolicyCommitted(snapshot.revision, snapshot))
                    }
                }
            }
        }
    }

    fun stop() {
        consumerJob?.cancel()
        consumerJob = null
        policyCollectorJob?.cancel()
        policyCollectorJob = null
        processEvent(ProtectionEvent.ServiceDisconnected)
    }

    private fun processEvent(event: ProtectionEvent) {
        // Track and validate service generations
        if (event is ProtectionEvent.ServiceConnected) {
            serviceGeneration = event.generation
            eventIngress.setGeneration(serviceGeneration)
        } else if (event is ProtectionEvent.ScreenUnlocked &&
            event.generation != serviceGeneration
        ) {
            return
        } else if (event is ProtectionEvent.ResyncRequested &&
            event.generation != serviceGeneration
        ) {
            // Async foreground recovery is valid only for the exact live service generation.
            return
        }

        val currentState = _protectionState.value
        val currentRuntime = _runtimeState.value

        val context = ReducerContext(
            nowWall = wallClock.now(),
            nowElapsedMs = monotonicClock.elapsedRealtimeMs(),
            zoneId = wallClock.zoneId(),
            runtimeState = currentRuntime,
            nextSessionId = { SessionId(processNonce, serviceGeneration, entryCounter++) },
            nextAttemptId = { UUID.randomUUID().toString() },
            codePort = sessionCodePort
        )

        val result = ProtectionReducer.reduce(currentState, event, context)

        // Record in transition journal
        journal.record(
            timestampMs = context.nowWall.toEpochMilli(),
            eventType = event::class.simpleName ?: "Event",
            eventSummary = event.toString(),
            stateBefore = currentState::class.simpleName ?: "State",
            stateAfter = result.newState::class.simpleName ?: "State",
            effects = result.effects.map { it::class.simpleName ?: "Effect" },
            sessionId = extractSessionId(result.newState) ?: extractSessionId(currentState)
        )

        // Commit updated state in memory BEFORE executing effects
        _protectionState.value = result.newState
        _runtimeState.value = result.updatedRuntimeState

        // Dispatch effects
        effectExecutor.execute(result.effects)
    }

    private fun extractSessionId(state: ProtectionState): String? {
        return when (state) {
            is ProtectionState.Intervening -> state.session.sessionId.toString()
            is ProtectionState.Granted -> state.session?.sessionId?.toString()
            is ProtectionState.Exiting -> state.session?.sessionId?.toString()
            is ProtectionState.Evaluating -> state.session.sessionId.toString()
            is ProtectionState.Blocked -> state.session.sessionId.toString()
            else -> null
        }
    }

    // --- Ingress triggers ---

    fun onForegroundCandidate(candidate: ProtectionEvent.ForegroundCandidate): Boolean {
        return eventIngress.sendForegroundCandidate(candidate)
    }

    fun onServiceConnected(generation: Long) {
        eventIngress.sendControlEvent(ProtectionEvent.ServiceConnected(generation))
    }

    fun onScreenOff() {
        eventIngress.sendControlEvent(ProtectionEvent.ScreenOff)
    }

    fun onScreenOnLocked() {
        eventIngress.sendControlEvent(ProtectionEvent.ScreenOnLocked)
    }

    fun onScreenUnlocked() {
        eventIngress.sendControlEvent(ProtectionEvent.ScreenUnlocked(serviceGeneration))
    }

    fun onDepartureConfirmed(packageName: String) {
        eventIngress.sendControlEvent(ProtectionEvent.DepartureConfirmed(packageName))
    }

    fun onTemporalBoundaryReached(boundaryToken: Long) {
        eventIngress.sendControlEvent(ProtectionEvent.TemporalBoundaryReached(boundaryToken))
    }

    fun onOverlayAttached(sessionId: SessionId, cycle: Int) {
        eventIngress.sendControlEvent(ProtectionEvent.OverlayAttached(sessionId, cycle))
    }

    fun onOverlayDetached(sessionId: SessionId) {
        eventIngress.sendControlEvent(ProtectionEvent.OverlayDetached(sessionId))
    }

    fun onBreathingDeadlineReached(sessionId: SessionId, cycle: Int) {
        eventIngress.sendControlEvent(ProtectionEvent.BreathingDeadlineReached(sessionId, cycle))
    }

    fun onActionContinue(sessionId: SessionId, cycle: Int) {
        eventIngress.sendControlEvent(ProtectionEvent.ActionContinue(sessionId, cycle))
    }

    fun onCodePanelShown(sessionId: SessionId, cycle: Int, requestRevision: Long) {
        eventIngress.sendControlEvent(ProtectionEvent.CodePanelShown(sessionId, cycle, requestRevision))
    }

    fun onActionExit(sessionId: SessionId?) {
        eventIngress.sendControlEvent(ProtectionEvent.ActionExit(sessionId))
    }

    fun onActionCancel(sessionId: SessionId?) {
        eventIngress.sendControlEvent(ProtectionEvent.ActionCancel(sessionId))
    }

    fun onActionEmergencyOnce(sessionId: SessionId, cycle: Int) {
        eventIngress.sendControlEvent(ProtectionEvent.ActionEmergencyOnce(sessionId, cycle))
    }

    fun onActionEmergencyTimed(sessionId: SessionId, cycle: Int, durationMs: Long) {
        eventIngress.sendControlEvent(ProtectionEvent.ActionEmergencyTimed(sessionId, cycle, durationMs))
    }

    fun onActionEmergencyForever(sessionId: SessionId, cycle: Int) {
        eventIngress.sendControlEvent(ProtectionEvent.ActionEmergencyForever(sessionId, cycle))
    }

    val currentGeneration: Long
        get() = serviceGeneration
}
