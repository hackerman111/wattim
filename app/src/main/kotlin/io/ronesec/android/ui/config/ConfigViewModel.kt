package io.ronesec.android.ui.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ronesec.android.data.PolicyStore
import io.ronesec.android.platform.audio.AudioDiagnostics
import io.ronesec.android.platform.system.PermissionMonitor
import io.ronesec.android.platform.system.SettingsIntentAdapter
import io.ronesec.android.ui.designsystem.ThemeId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel managing settings mutation, permission status, and system navigation (F72-F78).
 */
class ConfigViewModel(
    private val policyStore: PolicyStore?,
    private val permissionMonitor: PermissionMonitor,
    private val audioDiagnostics: AudioDiagnostics? = null,
    private val settingsIntentAdapter: SettingsIntentAdapter? = null,
    private val coroutineScope: CoroutineScope? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val scope: CoroutineScope
        get() = coroutineScope ?: viewModelScope

    private val _uiState = MutableStateFlow(ConfigUiState())
    val uiState: StateFlow<ConfigUiState> = _uiState.asStateFlow()

    init {
        policyStore?.let { store ->
            scope.launch(ioDispatcher) {
                store.presentationSettings.collect { settings ->
                    _uiState.update { current ->
                        current.copy(
                            selectedTheme = settings.themeId,
                            selectedLanguage = settings.language,
                            savedSessionMinutes = settings.savedSessionMinutes,
                            showOverlayStats = settings.showOverlayStats,
                            customEmergencyMinutes = settings.customEmergencyMinutes
                        )
                    }
                }
            }
        }

        scope.launch(ioDispatcher) {
            permissionMonitor.statusFlow.collect { snapshot ->
                _uiState.update { current ->
                    current.copy(
                        accessibilityState = snapshot.accessibility,
                        mediaControlState = snapshot.mediaControl,
                        overlayState = snapshot.overlay,
                        batteryState = snapshot.batteryExemption
                    )
                }
            }
        }

        audioDiagnostics?.let { diagnostics ->
            scope.launch {
                diagnostics.state.collect { state ->
                    _uiState.update { it.copy(audioDiagnostic = state) }
                }
            }
        }
    }

    fun onSelectTheme(themeId: ThemeId) {
        val store = policyStore ?: return
        scope.launch(ioDispatcher) {
            val result = store.setTheme(themeId)
            if (result.isFailure) {
                _uiState.update { it.copy(errorMessage = "Failed to update theme") }
            }
        }
    }

    fun onSelectLanguage(language: String) {
        val store = policyStore ?: return
        scope.launch(ioDispatcher) {
            val result = store.setLanguage(language)
            if (result.isFailure) {
                _uiState.update { it.copy(errorMessage = "Failed to update language") }
            }
        }
    }

    fun onSelectSavedSessionMinutes(minutes: Int) {
        val store = policyStore ?: return
        scope.launch(ioDispatcher) {
            val result = store.setSavedSessionMinutes(minutes)
            if (result.isFailure) {
                _uiState.update { it.copy(errorMessage = "Failed to update saved session duration") }
            }
        }
    }

    fun onToggleShowOverlayStats(show: Boolean) {
        val store = policyStore ?: return
        scope.launch(ioDispatcher) {
            val result = store.setShowOverlayStats(show)
            if (result.isFailure) {
                _uiState.update { it.copy(errorMessage = "Failed to update overlay statistics toggle") }
            }
        }
    }

    fun onSetCustomEmergencyMinutes(minutes: Int?) {
        val store = policyStore ?: return
        scope.launch(ioDispatcher) {
            val result = store.setCustomEmergencyMinutes(minutes)
            if (result.isFailure) {
                _uiState.update { it.copy(errorMessage = "Failed to update custom emergency interval") }
            }
        }
    }

    fun onEnableAccessibility() {
        val adapter = settingsIntentAdapter ?: return
        _uiState.update { it.copy(errorMessage = null) }
        val intent = adapter.createAccessibilitySettingsIntent()
        val result = adapter.launchSafely(intent)
        if (result.isFailure) {
            _uiState.update { it.copy(errorMessage = "Unable to open accessibility settings") }
        }
    }

    fun onEnableMediaControl() {
        val adapter = settingsIntentAdapter ?: return
        _uiState.update { it.copy(errorMessage = null) }
        val result = adapter.launchSafely(adapter.createMediaControlSettingsIntent())
        if (result.isFailure) {
            _uiState.update { it.copy(errorMessage = "Unable to open media control settings") }
        }
    }

    fun onEnableOverlay() {
        val adapter = settingsIntentAdapter ?: return
        _uiState.update { it.copy(errorMessage = null) }
        val intent = adapter.createOverlaySettingsIntent()
        val result = adapter.launchSafely(intent)
        if (result.isFailure) {
            _uiState.update { it.copy(errorMessage = "Unable to open overlay settings") }
        }
    }

    fun onEnableBattery() {
        val adapter = settingsIntentAdapter ?: return
        _uiState.update { it.copy(errorMessage = null) }
        val intent = adapter.createBatteryOptimizationIntent()
        val result = adapter.launchSafely(intent)
        if (result.isFailure) {
            _uiState.update { it.copy(errorMessage = "Unable to open battery optimization settings") }
        }
    }

    fun onOpenAppInfo() {
        val adapter = settingsIntentAdapter ?: return
        _uiState.update { it.copy(errorMessage = null) }
        val intent = adapter.createAppDetailsSettingsIntent()
        val result = adapter.launchSafely(intent)
        if (result.isFailure) {
            _uiState.update { it.copy(errorMessage = "Unable to open app details settings") }
        }
    }

    fun onRefreshPermissions() {
        permissionMonitor.refresh()
    }

    fun onDismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
