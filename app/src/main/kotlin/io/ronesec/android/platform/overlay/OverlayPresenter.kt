package io.ronesec.android.platform.overlay

import io.ronesec.android.data.PolicyStore
import io.ronesec.android.data.StatisticsStore
import io.ronesec.android.ui.designsystem.ThemeId
import io.ronesec.domain.model.SessionId
import io.ronesec.domain.protection.CodeChallengeUi
import io.ronesec.domain.protection.ProtectionEvent
import io.ronesec.domain.policy.EffectiveInterventionConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

sealed interface OverlayMode {
    object None : OverlayMode

    data class Intervention(
        val sessionId: SessionId,
        val cycle: Int,
        val config: EffectiveInterventionConfig,
        val isComplete: Boolean = false,
        val challenge: CodeChallengeUi? = null
    ) : OverlayMode

    data class Block(
        val packageName: String,
        val until: Instant?
    ) : OverlayMode
}

data class OverlayUiState(
    val mode: OverlayMode = OverlayMode.None,
    val themeId: ThemeId = ThemeId.NORD,
    val language: String = "AUTO",
    val showOverlayStats: Boolean = true,
    val allTimeSavedMinutes: Long = 0L,
    val isEmergencyDialogOpen: Boolean = false,
    val customEmergencyMinutes: Int? = null
)

interface OverlayActionDispatcher {
    fun onCodeEvent(event: ProtectionEvent) {}
    fun onContinue(sessionId: SessionId, cycle: Int)
    fun onExit(sessionId: SessionId?)
    fun onCancel(sessionId: SessionId?)
    fun onBreathingDeadlineReached(sessionId: SessionId, cycle: Int)
    fun onEmergencyOnce(sessionId: SessionId, cycle: Int)
    fun onEmergencyTimed(sessionId: SessionId, cycle: Int, durationMs: Long)
    fun onEmergencyForever(sessionId: SessionId, cycle: Int)
}

/**
 * Presenter joining coordinator state, presentation settings, and statistics.
 * Manages transient emergency dialog state and dispatches user actions back to coordinator.
 * Satisfies F33, F34, F37, Section 6, and Section 8.1.
 */
class OverlayPresenter(
    private val actionDispatcher: OverlayActionDispatcher,
    private val policyStore: PolicyStore?,
    private val statisticsStore: StatisticsStore?,
    private val scope: CoroutineScope,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) {
    private val _uiState = MutableStateFlow(OverlayUiState())
    val uiState: StateFlow<OverlayUiState> = _uiState.asStateFlow()

    init {
        // Observe presentation settings if policyStore is provided
        policyStore?.let { store ->
            scope.launch(mainDispatcher) {
                store.presentationSettings.collect { settings ->
                    _uiState.update { current ->
                        current.copy(
                            themeId = settings.themeId,
                            language = settings.language,
                            showOverlayStats = settings.showOverlayStats,
                            customEmergencyMinutes = settings.customEmergencyMinutes
                        )
                    }
                    // Refresh saved stats with updated multiplier/settings (F74)
                    statisticsStore?.let { stats ->
                        scope.launch(Dispatchers.IO) {
                            try {
                                val saved = stats.getAllTimeSavedLife()
                                _uiState.update { current ->
                                    current.copy(allTimeSavedMinutes = saved.totalSavedMinutes)
                                }
                            } catch (_: Exception) {
                                // Retain current on failure
                            }
                        }
                    }
                }
            }
        }

        // Load initial all-time saved stats asynchronously (non-blocking for overlay attach)
        if (policyStore == null) {
            statisticsStore?.let { stats ->
                scope.launch(Dispatchers.IO) {
                    try {
                        val saved = stats.getAllTimeSavedLife()
                        _uiState.update { current ->
                            current.copy(allTimeSavedMinutes = saved.totalSavedMinutes)
                        }
                    } catch (_: Exception) {
                        // Retain default 0L on failure
                    }
                }
            }
        }
    }

    fun showIntervention(sessionId: SessionId, cycle: Int, config: EffectiveInterventionConfig) {
        _uiState.update { current ->
            val isNewSession = when (val mode = current.mode) {
                is OverlayMode.Intervention -> mode.sessionId != sessionId
                else -> true
            }
            current.copy(
                mode = OverlayMode.Intervention(
                    sessionId = sessionId,
                    cycle = cycle,
                    config = config,
                    isComplete = false
                ),
                isEmergencyDialogOpen = if (isNewSession) false else current.isEmergencyDialogOpen
            )
        }
    }

    fun showBlock(packageName: String, until: Instant?) {
        _uiState.update { current ->
            current.copy(
                mode = OverlayMode.Block(packageName = packageName, until = until),
                isEmergencyDialogOpen = false
            )
        }
    }

    fun updateCodeChallenge(sessionId: SessionId, cycle: Int, snapshot: CodeChallengeUi) {
        _uiState.update { current ->
            val mode = current.mode
            if (mode is OverlayMode.Intervention && mode.sessionId == sessionId && mode.cycle == cycle) {
                current.copy(mode = mode.copy(challenge = snapshot))
            } else current
        }
    }

    fun dispatchCodeEvent(event: ProtectionEvent) {
        actionDispatcher.onCodeEvent(event)
    }

    fun updateOverlayComplete(sessionId: SessionId, cycle: Int) {
        _uiState.update { current ->
            when (val mode = current.mode) {
                is OverlayMode.Intervention -> {
                    if (mode.sessionId == sessionId && mode.cycle == cycle) {
                        current.copy(mode = mode.copy(isComplete = true))
                    } else {
                        current
                    }
                }
                else -> current
            }
        }
    }

    fun dismiss() {
        _uiState.update { current ->
            current.copy(mode = OverlayMode.None, isEmergencyDialogOpen = false)
        }
    }

    fun openEmergencyDialog() {
        _uiState.update { it.copy(isEmergencyDialogOpen = true) }
    }

    fun dismissEmergencyDialog() {
        _uiState.update { it.copy(isEmergencyDialogOpen = false) }
    }

    fun onContinueClick() {
        val current = _uiState.value.mode
        if (current is OverlayMode.Intervention && current.isComplete) {
            actionDispatcher.onContinue(current.sessionId, current.cycle)
        }
    }

    fun onExitClick() {
        val current = _uiState.value.mode
        val sessionId = when (current) {
            is OverlayMode.Intervention -> current.sessionId
            else -> null
        }
        actionDispatcher.onExit(sessionId)
    }

    fun onCancelClick() {
        val current = _uiState.value.mode
        val sessionId = when (current) {
            is OverlayMode.Intervention -> current.sessionId
            else -> null
        }
        actionDispatcher.onCancel(sessionId)
    }

    fun onBackExit() {
        // System Back remains overlay's Exit action even when emergency dialog is open
        onExitClick()
    }

    fun onBreathingComplete(sessionId: SessionId, cycle: Int) {
        val current = _uiState.value.mode
        if (current is OverlayMode.Intervention && current.sessionId == sessionId && current.cycle == cycle) {
            actionDispatcher.onBreathingDeadlineReached(sessionId, cycle)
        }
    }

    fun onEmergencyOnce() {
        val current = _uiState.value.mode
        if (current is OverlayMode.Intervention) {
            dismissEmergencyDialog()
            actionDispatcher.onEmergencyOnce(current.sessionId, current.cycle)
        }
    }

    fun onEmergencyTimed(durationMs: Long) {
        val current = _uiState.value.mode
        if (current is OverlayMode.Intervention) {
            dismissEmergencyDialog()
            actionDispatcher.onEmergencyTimed(current.sessionId, current.cycle, durationMs)
        }
    }

    fun onEmergencyForever() {
        val current = _uiState.value.mode
        if (current is OverlayMode.Intervention) {
            dismissEmergencyDialog()
            actionDispatcher.onEmergencyForever(current.sessionId, current.cycle)
        }
    }
}
