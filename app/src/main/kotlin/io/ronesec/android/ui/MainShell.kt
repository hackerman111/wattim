package io.ronesec.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.ronesec.android.R
import io.ronesec.android.platform.system.PermissionSnapshot
import io.ronesec.android.ui.designsystem.TerminalBottomNav
import io.ronesec.android.ui.designsystem.TerminalTab
import io.ronesec.android.ui.designsystem.WattimTheme
import io.ronesec.android.ui.home.HomeScreen
import io.ronesec.android.ui.blocks.BlocksScreen
import io.ronesec.android.ui.blocks.BlocksViewModel
import io.ronesec.android.ui.home.HomeViewModel
import io.ronesec.android.ui.stats.StatsScreen
import io.ronesec.android.ui.stats.StatsViewModel
import io.ronesec.android.ui.config.ConfigScreen
import io.ronesec.android.ui.config.ConfigViewModel

@Composable
fun MainShell(
    currentTab: TerminalTab,
    onTabSelected: (TerminalTab) -> Unit,
    onOpenDetail: (packageName: String) -> Unit,
    permissionSnapshot: PermissionSnapshot,
    homeViewModel: HomeViewModel? = null,
    blocksViewModel: BlocksViewModel? = null,
    statsViewModel: StatsViewModel? = null,
    configViewModel: ConfigViewModel? = null,
    onOpenScheduleEditor: ((Long?) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = WattimTheme.colors
    val tabLabels = mapOf(
        TerminalTab.APPS to stringResource(R.string.nav_apps),
        TerminalTab.BLOCK to stringResource(R.string.nav_block),
        TerminalTab.STATS to stringResource(R.string.nav_stats),
        TerminalTab.CONFIG to stringResource(R.string.nav_config)
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            TerminalBottomNav(
                selectedTab = currentTab,
                onTabSelected = onTabSelected,
                tabLabels = tabLabels
            )
        },
        containerColor = colors.background
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            when (currentTab) {
                TerminalTab.APPS -> {
                    if (homeViewModel != null) {
                        val homeUiState by homeViewModel.uiState.collectAsState()
                        HomeScreen(
                            uiState = homeUiState,
                            onVisible = { homeViewModel.onVisible() },
                            onInvisible = { homeViewModel.onInvisible() },
                            onOpenDetail = onOpenDetail,
                            onToggleEnabled = { homeViewModel.onToggleTarget(it) },
                            onSetPause = { homeViewModel.onSetGlobalPause(it) },
                            onResumePause = { homeViewModel.onResumeGlobalPause() },
                            onOpenAddApp = { homeViewModel.onOpenAddAppDialog() },
                            onDismissAddApp = { homeViewModel.onDismissAddAppDialog() },
                            onSearchQueryChange = { homeViewModel.onSearchQueryChange(it) },
                            onSelectAppToAdd = { homeViewModel.onSelectAppToAdd(it) }
                        )
                    } else {
                        AppsTabShell(
                            permissionSnapshot = permissionSnapshot,
                            onOpenDetail = onOpenDetail
                        )
                    }
                }
                TerminalTab.BLOCK -> {
                    if (blocksViewModel != null) {
                        val blocksUiState by blocksViewModel.uiState.collectAsState()
                        BlocksScreen(
                            uiState = blocksUiState,
                            onVisible = { blocksViewModel.onVisible() },
                            onInvisible = { blocksViewModel.onInvisible() },
                            onSelectDuration = { blocksViewModel.onSelectFocusDuration(it) },
                            onTogglePackage = { blocksViewModel.onTogglePackageForFocus(it) },
                            onSelectAll = { blocksViewModel.onSelectAllPackagesForFocus() },
                            onSelectNone = { blocksViewModel.onSelectNonePackagesForFocus() },
                            onStartFocus = { blocksViewModel.onStartQuickFocus() },
                            onStopSession = { blocksViewModel.onStopActiveSession(it) },
                            onOpenAddApp = { onTabSelected(TerminalTab.APPS) },
                            onOpenScheduleEditor = { scheduleId -> onOpenScheduleEditor?.invoke(scheduleId) },
                            onToggleSchedule = { id, enabled -> blocksViewModel.onToggleSchedule(id, enabled) },
                            onDeleteSchedule = { id -> blocksViewModel.onDeleteSchedule(id) }
                        )
                    } else {
                        BlocksTabShell(permissionSnapshot = permissionSnapshot)
                    }
                }
                TerminalTab.STATS -> {
                    if (statsViewModel != null) {
                        val statsUiState by statsViewModel.uiState.collectAsState()
                        StatsScreen(
                            uiState = statsUiState,
                            onVisible = { statsViewModel.onVisible() },
                            onInvisible = { statsViewModel.onInvisible() }
                        )
                    } else {
                        StatsTabShell(permissionSnapshot = permissionSnapshot)
                    }
                }
                TerminalTab.CONFIG -> {
                    if (configViewModel != null) {
                        val configUiState by configViewModel.uiState.collectAsState()
                        ConfigScreen(
                            uiState = configUiState,
                            onSelectTheme = { configViewModel.onSelectTheme(it) },
                            onSelectLanguage = { configViewModel.onSelectLanguage(it) },
                            onSelectSavedSessionMinutes = { configViewModel.onSelectSavedSessionMinutes(it) },
                            onToggleShowOverlayStats = { configViewModel.onToggleShowOverlayStats(it) },
                            onEnableAccessibility = { configViewModel.onEnableAccessibility() },
                            onEnableMediaControl = { configViewModel.onEnableMediaControl() },
                            onEnableOverlay = { configViewModel.onEnableOverlay() },
                            onEnableBattery = { configViewModel.onEnableBattery() },
                            onOpenAppInfo = { configViewModel.onOpenAppInfo() },
                            onDismissError = { configViewModel.onDismissError() },
                            onSetCustomEmergencyMinutes = { configViewModel.onSetCustomEmergencyMinutes(it) }
                        )
                    } else {
                        ConfigTabShell(permissionSnapshot = permissionSnapshot)
                    }
                }
            }
        }
    }
}
