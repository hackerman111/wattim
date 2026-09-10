package io.ronesec.android.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ronesec.android.data.PolicyStore
import io.ronesec.android.data.StatisticsStore
import io.ronesec.android.platform.system.PackageAppEntry
import io.ronesec.android.platform.system.PackageCatalog
import io.ronesec.domain.model.GlobalPause
import io.ronesec.domain.model.TargetConfig
import io.ronesec.domain.model.WallClock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class HomeViewModel(
    private val policyStore: PolicyStore,
    private val statisticsStore: StatisticsStore,
    private val packageCatalog: PackageCatalog,
    private val wallClock: WallClock,
    private val coroutineScope: CoroutineScope? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val scope: CoroutineScope
        get() = coroutineScope ?: viewModelScope

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var tickerJob: Job? = null
    private var lastRecordedDate: LocalDate? = null
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    init {
        // Observe policy snapshot for targets and global pause
        scope.launch {
            policyStore.snapshotFlow.collect { snapshot ->
                val sortedTargets = snapshot.targets.values
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName.ifBlank { it.packageName } })

                val rowItems = sortedTargets.mapIndexed { index, target ->
                    val rowNum = index + 1
                    ProtectedAppRowItem(
                        index = rowNum,
                        isFirst = (rowNum == 1),
                        formattedIndex = "%02d".format(rowNum),
                        packageName = target.packageName,
                        displayName = target.displayName.ifBlank { target.packageName },
                        enabled = target.enabled,
                        durationSeconds = (target.durationMs / 1000L).toInt(),
                        animation = target.animation,
                        isPending = target.packageName in _uiState.value.pendingTogglePackages,
                        targetConfig = target
                    )
                }

                val currentPause = computePauseState(snapshot.globalPause)

                _uiState.update { current ->
                    current.copy(
                        protectedCount = rowItems.size,
                        apps = rowItems,
                        pauseState = currentPause
                    )
                }
            }
        }

        // Initial fetch of today's statistics
        refreshTodayStatistics()
        updateWallClockTime()
    }

    fun onVisible() {
        if (tickerJob?.isActive == true) return
        updateWallClockTime()
        refreshTodayStatistics()

        tickerJob = scope.launch {
            while (isActive) {
                delay(1000L)
                tickClockAndPause()
            }
        }
    }

    fun onInvisible() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun tickClockAndPause() {
        val now = wallClock.now()
        val localDate = now.atZone(wallClock.zoneId()).toLocalDate()

        // Check day change
        if (lastRecordedDate != null && localDate != lastRecordedDate) {
            refreshTodayStatistics()
        }
        lastRecordedDate = localDate

        val formattedTime = now.atZone(wallClock.zoneId()).format(timeFormatter)
        val pauseState = computePauseState(policyStore.currentSnapshot.globalPause)

        _uiState.update { current ->
            current.copy(
                wallClockTime = formattedTime,
                pauseState = pauseState
            )
        }
    }

    private fun updateWallClockTime() {
        val now = wallClock.now()
        lastRecordedDate = now.atZone(wallClock.zoneId()).toLocalDate()
        val formattedTime = now.atZone(wallClock.zoneId()).format(timeFormatter)
        _uiState.update { it.copy(wallClockTime = formattedTime) }
    }

    fun refreshTodayStatistics() {
        scope.launch(ioDispatcher) {
            val summary = statisticsStore.getTodaySummary(
                nowWall = wallClock.now(),
                zoneId = wallClock.zoneId()
            )
            _uiState.update { current ->
                current.copy(
                    todayHero = TodayHeroState(
                        totalAttempts = summary.totalAttempts,
                        closedCount = summary.closedCount,
                        preventedPercent = summary.avoidedPercent
                    )
                )
            }
        }
    }

    private fun computePauseState(globalPause: GlobalPause): HomePauseState {
        return when (globalPause) {
            is GlobalPause.None -> HomePauseState.Inactive
            is GlobalPause.Indefinite -> HomePauseState.Active(
                remainingMillis = null,
                formattedRemaining = "FOREVER",
                isForever = true
            )
            is GlobalPause.Until -> {
                val nowMs = wallClock.now().toEpochMilli()
                val remMs = globalPause.until.toEpochMilli() - nowMs
                if (remMs <= 0) {
                    // Expired pause
                    HomePauseState.Inactive
                } else {
                    val totalSeconds = (remMs + 999L) / 1000L
                    val minutes = totalSeconds / 60
                    val seconds = totalSeconds % 60
                    HomePauseState.Active(
                        remainingMillis = remMs,
                        formattedRemaining = "%02d:%02d".format(minutes, seconds),
                        isForever = false
                    )
                }
            }
        }
    }

    fun onToggleTarget(packageName: String) {
        val target = _uiState.value.apps.find { it.packageName == packageName } ?: return
        val newEnabled = !target.enabled

        _uiState.update { it.copy(pendingTogglePackages = it.pendingTogglePackages + packageName) }

        scope.launch(ioDispatcher) {
            val result = policyStore.toggleTarget(packageName, newEnabled)
            _uiState.update { current ->
                current.copy(
                    pendingTogglePackages = current.pendingTogglePackages - packageName,
                    errorNotification = if (result.isFailure) "Failed to toggle $packageName" else null
                )
            }
        }
    }

    fun onSetGlobalPause(durationMillis: Long?) {
        scope.launch(ioDispatcher) {
            val pause = if (durationMillis == null) {
                GlobalPause.Indefinite
            } else {
                GlobalPause.Until(wallClock.now().plusMillis(durationMillis))
            }
            policyStore.setGlobalPause(pause)
        }
    }

    fun onResumeGlobalPause() {
        scope.launch(ioDispatcher) {
            policyStore.resumeGlobalPause()
        }
    }

    fun onOpenAddAppDialog() {
        val existingTargets = _uiState.value.apps.map { it.packageName }.toSet()
        _uiState.update { current ->
            current.copy(
                addAppDialog = current.addAppDialog.copy(
                    isOpen = true,
                    isLoading = true,
                    errorMessage = null
                )
            )
        }

        scope.launch(ioDispatcher) {
            try {
                val candidates = packageCatalog.getLaunchableApps(existingTargets)
                _uiState.update { current ->
                    val query = current.addAppDialog.searchQuery
                    val filtered = filterCandidates(candidates, query)
                    current.copy(
                        addAppDialog = current.addAppDialog.copy(
                            isLoading = false,
                            allCandidates = candidates,
                            filteredApps = filtered
                        )
                    )
                }
            } catch (e: Exception) {
                _uiState.update { current ->
                    current.copy(
                        addAppDialog = current.addAppDialog.copy(
                            isLoading = false,
                            errorMessage = e.message ?: "Failed to scan packages"
                        )
                    )
                }
            }
        }
    }

    fun onDismissAddAppDialog() {
        _uiState.update { current ->
            current.copy(
                addAppDialog = current.addAppDialog.copy(isOpen = false, errorMessage = null)
            )
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { current ->
            val filtered = filterCandidates(current.addAppDialog.allCandidates, query)
            current.copy(
                addAppDialog = current.addAppDialog.copy(
                    searchQuery = query,
                    filteredApps = filtered
                )
            )
        }
    }

    fun onSelectAppToAdd(entry: PackageAppEntry) {
        _uiState.update { current ->
            current.copy(addAppDialog = current.addAppDialog.copy(isAdding = true))
        }

        scope.launch(ioDispatcher) {
            val defaultConfig = TargetConfig(
                packageName = entry.packageName,
                displayName = entry.label
            )
            val result = policyStore.saveTarget(defaultConfig)
            if (result.isSuccess) {
                _uiState.update { current ->
                    current.copy(
                        addAppDialog = current.addAppDialog.copy(
                            isOpen = false,
                            isAdding = false,
                            errorMessage = null
                        )
                    )
                }
            } else {
                _uiState.update { current ->
                    current.copy(
                        addAppDialog = current.addAppDialog.copy(
                            isAdding = false,
                            errorMessage = result.exceptionOrNull()?.message ?: "Failed to add application"
                        )
                    )
                }
            }
        }
    }

    private fun filterCandidates(candidates: List<PackageAppEntry>, query: String): List<PackageAppEntry> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return candidates
        return candidates.filter {
            it.label.contains(trimmed, ignoreCase = true) ||
            it.packageName.contains(trimmed, ignoreCase = true)
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorNotification = null) }
    }
}
