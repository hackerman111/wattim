package io.ronesec.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.ronesec.android.data.PolicyStore
import io.ronesec.android.data.StatisticsStore
import io.ronesec.android.platform.system.PackageCatalog
import io.ronesec.android.platform.audio.AudioDiagnostics
import io.ronesec.android.platform.system.PermissionMonitor
import io.ronesec.android.platform.system.SettingsIntentAdapter
import io.ronesec.android.ui.blocks.BlocksViewModel
import io.ronesec.android.ui.codes.CodesPanelState
import io.ronesec.android.ui.blocks.editor.ScheduleEditorScreen
import io.ronesec.android.ui.blocks.editor.ScheduleEditorViewModel
import io.ronesec.android.ui.config.ConfigViewModel
import io.ronesec.android.ui.designsystem.TerminalTab
import io.ronesec.android.ui.home.HomeViewModel
import io.ronesec.android.ui.onboarding.OnboardingScreen
import io.ronesec.android.ui.onboarding.OnboardingViewModel
import io.ronesec.android.ui.stats.StatsViewModel
import io.ronesec.android.ui.target.TargetSettingsScreen
import io.ronesec.android.ui.target.TargetSettingsViewModel
import io.ronesec.domain.model.WallClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow

@Composable
fun WattimNavHost(
    permissionMonitor: PermissionMonitor,
    settingsAdapter: SettingsIntentAdapter,
    initialRoute: AppRoute?,
    onStartFgs: () -> Unit,
    policyStore: PolicyStore? = null,
    statisticsStore: StatisticsStore? = null,
    packageCatalog: PackageCatalog? = null,
    wallClock: WallClock? = null,
    audioDiagnostics: AudioDiagnostics? = null,
    codesPanelState: CodesPanelState = CodesPanelState.Empty,
    routeRequests: Flow<AppRoute> = emptyFlow(),
    modifier: Modifier = Modifier,
    onRouteChange: (AppRoute) -> Unit = {}
) {
    val permissionSnapshot by permissionMonitor.statusFlow.collectAsState()

    var currentRoute by remember {
        val startingRoute = initialRoute ?: if (permissionSnapshot.areRequiredPermissionsGranted) {
            AppRoute.Main(TerminalTab.APPS)
        } else {
            AppRoute.Onboarding
        }
        mutableStateOf(startingRoute)
    }

    LaunchedEffect(currentRoute) {
        onRouteChange(currentRoute)
    }

    LaunchedEffect(routeRequests) {
        routeRequests.collect { requestedRoute ->
            currentRoute = requestedRoute
        }
    }

    // Auto-return to onboarding if required permissions are revoked while in Main or Detail (F21)
    LaunchedEffect(permissionSnapshot.areRequiredPermissionsGranted) {
        if (!permissionSnapshot.areRequiredPermissionsGranted && currentRoute !is AppRoute.Onboarding) {
            currentRoute = AppRoute.Onboarding
        }
    }

    val onboardingViewModel = remember {
        OnboardingViewModel(permissionMonitor, settingsAdapter)
    }
    val onboardingState by onboardingViewModel.uiState.collectAsState()

    val homeViewModel = remember(policyStore, statisticsStore, packageCatalog, wallClock) {
        if (policyStore != null && statisticsStore != null && packageCatalog != null && wallClock != null) {
            HomeViewModel(
                policyStore = policyStore,
                statisticsStore = statisticsStore,
                packageCatalog = packageCatalog,
                wallClock = wallClock
            )
        } else null
    }

    val blocksViewModel = remember(policyStore, wallClock) {
        if (policyStore != null && wallClock != null) {
            BlocksViewModel(
                policyStore = policyStore,
                wallClock = wallClock
            )
        } else null
    }

    val statsViewModel = remember(statisticsStore, policyStore, wallClock) {
        if (statisticsStore != null && policyStore != null && wallClock != null) {
            StatsViewModel(
                statisticsStore = statisticsStore,
                policyStore = policyStore,
                wallClock = wallClock
            )
        } else null
    }

    val configViewModel = remember(policyStore, permissionMonitor, settingsAdapter, audioDiagnostics) {
        ConfigViewModel(
            policyStore = policyStore,
            permissionMonitor = permissionMonitor,
            audioDiagnostics = audioDiagnostics,
            settingsIntentAdapter = settingsAdapter
        )
    }

    when (val route = currentRoute) {
        is AppRoute.Onboarding -> {
            OnboardingScreen(
                state = onboardingState,
                onEnableStep = { step -> onboardingViewModel.onEnablePermission(step) },
                onCheckStatus = { onboardingViewModel.onCheckStatus() },
                onOpenAppInfo = { onboardingViewModel.onOpenAppInfo() },
                onReadyContinue = {
                    onboardingViewModel.onCompleteReady()
                    onStartFgs()
                    currentRoute = AppRoute.Main(TerminalTab.APPS)
                },
                onDismissError = { onboardingViewModel.onDismissError() },
                modifier = modifier
            )
        }
        is AppRoute.Main -> {
            MainShell(
                currentTab = route.tab,
                onTabSelected = { newTab -> currentRoute = AppRoute.Main(newTab) },
                onOpenDetail = { pkg ->
                    currentRoute = AppRoute.Detail(
                        packageName = pkg,
                        originTab = route.tab,
                        canQuickLock = (route.tab == TerminalTab.BLOCK)
                    )
                },
                permissionSnapshot = permissionSnapshot,
                homeViewModel = homeViewModel,
                blocksViewModel = blocksViewModel,
                statsViewModel = statsViewModel,
                configViewModel = configViewModel,
                codesPanelState = codesPanelState,
                onOpenScheduleEditor = { scheduleId ->
                    currentRoute = AppRoute.ScheduleEditor(scheduleId)
                },
                modifier = modifier
            )
        }
        is AppRoute.Detail -> {
            if (policyStore != null) {
                val targetViewModel = remember(route.packageName) {
                    TargetSettingsViewModel(
                        packageName = route.packageName,
                        originTab = route.originTab,
                        canQuickLock = route.canQuickLock,
                        policyStore = policyStore,
                        wallClock = wallClock
                    )
                }
                val targetState by targetViewModel.uiState.collectAsState()
                TargetSettingsScreen(
                    state = targetState,
                    onBack = { currentRoute = AppRoute.Main(route.originTab) },
                    onPhraseChange = { targetViewModel.onPhraseChange(it) },
                    onAnimationChange = { targetViewModel.onAnimationChange(it) },
                    onDurationChange = { targetViewModel.onDurationChange(it) },
                    onReinterventionChoice = { targetViewModel.onReinterventionChoice(it) },
                    onCustomReinterventionChange = { min, sec -> targetViewModel.onCustomReinterventionChange(min, sec) },
                    onQuickReturnChange = { targetViewModel.onQuickReturnChange(it) },
                    onBackoffEnabledChange = { targetViewModel.onBackoffEnabledChange(it) },
                    onBackoffPercentChange = { targetViewModel.onBackoffPercentChange(it) },
                    onBackoffWindowChange = { targetViewModel.onBackoffWindowChange(it) },
                    onTwoStageUnlockChange = targetViewModel::onTwoStageUnlockChange,
                    onUnlockCodeLengthChange = targetViewModel::onUnlockCodeLengthChange,
                    onRequireEmergencyCodeChange = targetViewModel::onRequireEmergencyCodeChange,
                    onToggleRandomDuration = targetViewModel::onToggleRandomDuration,
                    onRandomMaxDurationChange = targetViewModel::onRandomMaxDurationChange,
                    onToggleEnabled = { targetViewModel.onToggleEnabled() },
                    onOpenPreview = { targetViewModel.onOpenPreview() },
                    onDismissPreview = { targetViewModel.onDismissPreview() },
                    onQuickLock = { targetViewModel.onQuickLock(it) },
                    onSave = { targetViewModel.onSave() },
                    onRemove = { targetViewModel.onRemove() },
                    modifier = modifier
                )
            } else {
                DetailShell(
                    packageName = route.packageName,
                    originTab = route.originTab,
                    onBack = { currentRoute = AppRoute.Main(route.originTab) },
                    modifier = modifier
                )
            }
        }
        is AppRoute.ScheduleEditor -> {
            if (policyStore != null) {
                val editorViewModel = remember(route.scheduleId) {
                    ScheduleEditorViewModel(
                        scheduleId = route.scheduleId,
                        policyStore = policyStore
                    )
                }
                val editorState by editorViewModel.uiState.collectAsState()
                ScheduleEditorScreen(
                    state = editorState,
                    onBack = { currentRoute = AppRoute.Main(TerminalTab.BLOCK) },
                    onNameChange = { editorViewModel.onNameChange(it) },
                    onTypeChange = { editorViewModel.onTypeChange(it) },
                    onToggleDay = { editorViewModel.onToggleDay(it) },
                    onStartTimeChange = { h, m -> editorViewModel.onStartTimeChange(h, m) },
                    onEndTimeChange = { h, m -> editorViewModel.onEndTimeChange(h, m) },
                    onApplyPreset = { editorViewModel.onApplyPreset(it) },
                    onToggleTarget = { editorViewModel.onToggleTarget(it) },
                    onSelectAllTargets = { editorViewModel.onSelectAllTargets() },
                    onSelectNoneTargets = { editorViewModel.onSelectNoneTargets() },
                    onSetDurationOverride = { pkg, dur -> editorViewModel.onSetDurationOverride(pkg, dur) },
                    onSetRepeatOverride = { pkg, rep -> editorViewModel.onSetRepeatOverride(pkg, rep) },
                    onSave = { editorViewModel.onSave() },
                    modifier = modifier
                )
            } else {
                currentRoute = AppRoute.Main(TerminalTab.BLOCK)
            }
        }
    }
}
