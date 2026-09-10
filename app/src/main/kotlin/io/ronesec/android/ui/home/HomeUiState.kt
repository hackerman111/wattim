package io.ronesec.android.ui.home

import io.ronesec.android.platform.system.PackageAppEntry
import io.ronesec.domain.model.AnimationMode
import io.ronesec.domain.model.TargetConfig

data class ProtectedAppRowItem(
    val index: Int,
    val isFirst: Boolean,
    val formattedIndex: String,
    val packageName: String,
    val displayName: String,
    val enabled: Boolean,
    val durationSeconds: Int,
    val animation: AnimationMode,
    val isPending: Boolean = false,
    val targetConfig: TargetConfig
)

data class TodayHeroState(
    val totalAttempts: Int = 0,
    val closedCount: Int = 0,
    val preventedPercent: Int = 0
)

sealed interface HomePauseState {
    data object Inactive : HomePauseState
    data class Active(
        val remainingMillis: Long?,
        val formattedRemaining: String,
        val isForever: Boolean
    ) : HomePauseState
}

data class AddAppDialogState(
    val isOpen: Boolean = false,
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val allCandidates: List<PackageAppEntry> = emptyList(),
    val filteredApps: List<PackageAppEntry> = emptyList(),
    val errorMessage: String? = null,
    val isAdding: Boolean = false
)

data class HomeUiState(
    val wallClockTime: String = "00:00",
    val protectedCount: Int = 0,
    val apps: List<ProtectedAppRowItem> = emptyList(),
    val todayHero: TodayHeroState = TodayHeroState(),
    val pauseState: HomePauseState = HomePauseState.Inactive,
    val addAppDialog: AddAppDialogState = AddAppDialogState(),
    val pendingTogglePackages: Set<String> = emptySet(),
    val errorNotification: String? = null
)
