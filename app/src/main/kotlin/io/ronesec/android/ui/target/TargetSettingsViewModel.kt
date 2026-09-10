package io.ronesec.android.ui.target

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ronesec.android.data.PolicyStore
import io.ronesec.android.ui.designsystem.TerminalTab
import io.ronesec.domain.model.AnimationMode
import io.ronesec.domain.model.BackoffConfig
import io.ronesec.domain.model.TargetConfig
import io.ronesec.domain.model.WallClock
import io.ronesec.domain.policy.Backoff
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

class TargetSettingsViewModel(
    private val packageName: String,
    private val originTab: TerminalTab = TerminalTab.APPS,
    private val canQuickLock: Boolean = false,
    private val policyStore: PolicyStore,
    private val wallClock: WallClock? = null,
    private val savedStateHandle: SavedStateHandle? = null,
    private val coroutineScope: CoroutineScope? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val scope: CoroutineScope
        get() = coroutineScope ?: viewModelScope

    private val _uiState = MutableStateFlow(
        TargetSettingsUiState(
            packageName = packageName,
            originTab = originTab,
            canQuickLock = canQuickLock
        )
    )
    val uiState: StateFlow<TargetSettingsUiState> = _uiState.asStateFlow()

    init {
        loadInitialTarget()
    }

    private fun loadInitialTarget() {
        val existing = policyStore.currentSnapshot.targets[packageName]
        val displayName = existing?.displayName?.ifBlank { packageName } ?: packageName
        val durationSec = existing?.let { (it.durationMs / 1000L).toInt() } ?: 8
        val (reintChoice, reintCustom) = existing?.let {
            ReinterventionChoice.fromDurationMs(it.reinterventionMs)
        } ?: Pair(ReinterventionChoice.MIN_5, Pair(5, 0))

        val initialDraft = TargetSettingsDraft(
            packageName = packageName,
            displayName = displayName,
            enabled = existing?.enabled ?: true,
            phrase = existing?.phrase ?: TargetConfig.DEFAULT_PHRASE,
            animation = existing?.animation ?: AnimationMode.FILL,
            durationSeconds = durationSec,
            reinterventionMode = reintChoice,
            customReinterventionMinutes = reintCustom.first,
            customReinterventionSeconds = reintCustom.second,
            quickReturnGraceMs = existing?.quickReturnGraceMs ?: 0L,
            backoffEnabled = existing?.growthConfig?.enabled ?: false,
            backoffPercent = existing?.growthConfig?.percent ?: 20,
            backoffWindowMs = existing?.growthConfig?.windowMs ?: 3600_000L,
            baseRowVersion = existing?.rowVersion ?: 1L,
            twoStageUnlock = existing?.twoStageUnlock ?: false,
            unlockCodeLength = existing?.unlockCodeLength ?: 4,
            requireEmergencyCode = existing?.requireEmergencyCode ?: false
        )

        val delays = Backoff.calculateFirstTen(
            baseDurationMs = initialDraft.durationSeconds * 1000L,
            growthPercent = initialDraft.backoffPercent
        )

        _uiState.update { current ->
            current.copy(
                displayName = displayName,
                draft = initialDraft,
                calculatedDelays = delays
            )
        }
    }

    fun onPhraseChange(phrase: String) {
        val limited = if (phrase.length > 80) phrase.take(80) else phrase
        updateDraft { it.copy(phrase = limited) }
    }

    fun onAnimationChange(animation: AnimationMode) {
        updateDraft { it.copy(animation = animation) }
    }

    fun onDurationChange(seconds: Int) {
        val clamped = seconds.coerceIn(1, 120)
        updateDraft { it.copy(durationSeconds = clamped) }
        recomputeDelays()
    }

    fun onReinterventionChoice(choice: ReinterventionChoice) {
        updateDraft { it.copy(reinterventionMode = choice) }
    }

    fun onCustomReinterventionChange(minutes: Int, seconds: Int) {
        val totalSec = (minutes.coerceAtLeast(0) * 60) + seconds.coerceAtLeast(0)
        val normM = totalSec / 60
        val normS = totalSec % 60
        updateDraft {
            it.copy(
                reinterventionMode = ReinterventionChoice.CUSTOM,
                customReinterventionMinutes = normM,
                customReinterventionSeconds = normS
            )
        }
    }

    fun onQuickReturnChange(graceMs: Long) {
        updateDraft { it.copy(quickReturnGraceMs = graceMs.coerceAtLeast(0L)) }
    }

    fun onBackoffEnabledChange(enabled: Boolean) {
        updateDraft { it.copy(backoffEnabled = enabled) }
    }

    fun onBackoffPercentChange(percent: Int) {
        val clamped = percent.coerceIn(1, 200)
        updateDraft { it.copy(backoffPercent = clamped) }
        recomputeDelays()
    }

    fun onBackoffWindowChange(windowMs: Long) {
        updateDraft { it.copy(backoffWindowMs = windowMs) }
    }

    fun onToggleEnabled() {
        updateDraft { it.copy(enabled = !it.enabled) }
    }

    fun onTwoStageUnlockChange(enabled: Boolean) {
        updateDraft { it.copy(twoStageUnlock = enabled) }
    }

    fun onUnlockCodeLengthChange(length: Int) {
        updateDraft { it.copy(unlockCodeLength = length.coerceIn(1, 10)) }
    }

    fun onRequireEmergencyCodeChange(enabled: Boolean) {
        updateDraft { it.copy(requireEmergencyCode = enabled) }
    }

    fun onOpenPreview() {
        _uiState.update { it.copy(isPreviewOpen = true) }
    }

    fun onDismissPreview() {
        _uiState.update { it.copy(isPreviewOpen = false) }
    }

    private fun recomputeDelays() {
        val draft = _uiState.value.draft
        val delays = Backoff.calculateFirstTen(
            baseDurationMs = draft.durationSeconds * 1000L,
            growthPercent = draft.backoffPercent
        )
        _uiState.update { it.copy(calculatedDelays = delays) }
    }

    private fun updateDraft(transform: (TargetSettingsDraft) -> TargetSettingsDraft) {
        _uiState.update { current ->
            val updated = transform(current.draft)
            current.copy(draft = updated, errorMessage = null)
        }
    }

    fun onSave() {
        val draft = _uiState.value.draft
        val reintMs = when (draft.reinterventionMode) {
            ReinterventionChoice.OFF -> 0L
            ReinterventionChoice.CUSTOM -> {
                (draft.customReinterventionMinutes * 60L + draft.customReinterventionSeconds) * 1000L
            }
            else -> draft.reinterventionMode.durationMs ?: 0L
        }

        val targetConfig = TargetConfig(
            packageName = draft.packageName,
            displayName = draft.displayName,
            enabled = draft.enabled,
            phrase = draft.phrase.ifBlank { TargetConfig.DEFAULT_PHRASE },
            animation = draft.animation,
            durationMs = draft.durationSeconds * 1000L,
            reinterventionMs = reintMs,
            quickReturnGraceMs = draft.quickReturnGraceMs,
            growthConfig = BackoffConfig(
                enabled = draft.backoffEnabled,
                percent = draft.backoffPercent,
                windowMs = draft.backoffWindowMs
            ),
            rowVersion = draft.baseRowVersion,
            twoStageUnlock = draft.twoStageUnlock,
            unlockCodeLength = draft.unlockCodeLength,
            requireEmergencyCode = draft.requireEmergencyCode
        )

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        scope.launch(ioDispatcher) {
            val result = policyStore.saveTarget(targetConfig)
            if (result.isSuccess) {
                _uiState.update { it.copy(isLoading = false, isSaved = true) }
            } else {
                val error = result.exceptionOrNull()
                val isConflict = error?.message?.contains("Stale edit") == true
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        isStale = isConflict,
                        errorMessage = if (isConflict) "Settings modified externally" else (error?.message ?: "Failed to save")
                    )
                }
            }
        }
    }

    fun onRemove() {
        // Immediate removal without confirmation dialog (F58)
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        scope.launch(ioDispatcher) {
            val result = policyStore.removeTarget(packageName)
            if (result.isSuccess) {
                _uiState.update { it.copy(isLoading = false, isRemoved = true) }
            } else {
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Failed to remove"
                    )
                }
            }
        }
    }

    fun onQuickLock(durationMs: Long) {
        if (!canQuickLock) return
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        scope.launch(ioDispatcher) {
            val clock = wallClock ?: object : WallClock {
                override fun now(): Instant = Instant.now()
                override fun zoneId(): java.time.ZoneId = java.time.ZoneId.systemDefault()
            }
            val now = clock.now()
            val end = now.plusMillis(durationMs)
            val displayName = _uiState.value.displayName.ifBlank { packageName }
            val result = policyStore.createBlockSession(
                name = "Quick Lock: $displayName",
                startTime = now,
                endTime = end,
                targetPackages = setOf(packageName)
            )
            if (result.isSuccess) {
                _uiState.update { it.copy(isLoading = false, isQuickLocked = true) }
            } else {
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Failed to quick lock"
                    )
                }
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
