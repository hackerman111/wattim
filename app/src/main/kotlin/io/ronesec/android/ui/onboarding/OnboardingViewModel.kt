package io.ronesec.android.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ronesec.android.platform.system.OnboardingStep
import io.ronesec.android.platform.system.PermissionMonitor
import io.ronesec.android.platform.system.PermissionSnapshot
import io.ronesec.android.platform.system.SettingsIntentAdapter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface OnboardingStepState {
    data class Permission(val step: OnboardingStep) : OnboardingStepState
    data object SystemReady : OnboardingStepState
}

data class OnboardingUiState(
    val stepState: OnboardingStepState = OnboardingStepState.Permission(OnboardingStep.Accessibility),
    val permissionSnapshot: PermissionSnapshot = PermissionSnapshot(),
    val errorMessage: String? = null,
    val isReadyAcknowledged: Boolean = false
) {
    val currentStepNumber: String
        get() = when (val s = stepState) {
            is OnboardingStepState.Permission -> s.step.stepNumber
            OnboardingStepState.SystemReady -> "READY"
        }
}

class OnboardingViewModel(
    private val permissionMonitor: PermissionMonitor,
    private val settingsIntentAdapter: SettingsIntentAdapter? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        createInitialState(permissionMonitor.statusFlow.value)
    )
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            permissionMonitor.statusFlow.collect { snapshot ->
                updateFromSnapshot(snapshot)
            }
        }
    }

    private fun createInitialState(snapshot: PermissionSnapshot): OnboardingUiState {
        val stepState = if (snapshot.areRequiredPermissionsGranted) {
            OnboardingStepState.SystemReady
        } else {
            val missing = snapshot.firstMissingStep ?: OnboardingStep.Accessibility
            OnboardingStepState.Permission(missing)
        }
        return OnboardingUiState(
            stepState = stepState,
            permissionSnapshot = snapshot
        )
    }

    private fun updateFromSnapshot(snapshot: PermissionSnapshot) {
        _uiState.update { current ->
            val nextStepState = if (snapshot.areRequiredPermissionsGranted) {
                OnboardingStepState.SystemReady
            } else {
                val missing = snapshot.firstMissingStep ?: OnboardingStep.Accessibility
                OnboardingStepState.Permission(missing)
            }
            current.copy(
                permissionSnapshot = snapshot,
                stepState = nextStepState
            )
        }
    }

    fun onCheckStatus() {
        _uiState.update { it.copy(errorMessage = null) }
        permissionMonitor.refresh()
    }

    fun onEnablePermission(step: OnboardingStep) {
        val adapter = settingsIntentAdapter ?: return
        _uiState.update { it.copy(errorMessage = null) }
        val intent = when (step) {
            OnboardingStep.Accessibility -> adapter.createAccessibilitySettingsIntent()
            OnboardingStep.Overlay -> adapter.createOverlaySettingsIntent()
            OnboardingStep.BatteryExemption -> adapter.createBatteryOptimizationIntent()
        }
        val result = adapter.launchSafely(intent)
        if (result.isFailure) {
            _uiState.update {
                it.copy(errorMessage = result.exceptionOrNull()?.localizedMessage ?: "Unable to open system settings")
            }
        }
    }

    fun onOpenAppInfo() {
        val adapter = settingsIntentAdapter ?: return
        _uiState.update { it.copy(errorMessage = null) }
        val intent = adapter.createAppDetailsSettingsIntent()
        val result = adapter.launchSafely(intent)
        if (result.isFailure) {
            _uiState.update {
                it.copy(errorMessage = result.exceptionOrNull()?.localizedMessage ?: "Unable to open app info")
            }
        }
    }

    fun onDismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun onCompleteReady() {
        _uiState.update { it.copy(isReadyAcknowledged = true) }
    }
}
