package io.ronesec.android.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.ronesec.android.domain.model.TargetApp
import io.ronesec.android.service.FocusForegroundService
import io.ronesec.android.ui.components.NavDestination
import io.ronesec.android.ui.components.TerminalBottomNav
import io.ronesec.android.ui.screens.BlocksScreen
import io.ronesec.android.ui.screens.ConfigScreen
import io.ronesec.android.ui.screens.HomeScreen
import io.ronesec.android.ui.screens.OnboardingScreen
import io.ronesec.android.ui.screens.StatsScreen
import io.ronesec.android.ui.screens.TargetSettingsScreen
import io.ronesec.android.ui.theme.RonesecTheme
import io.ronesec.android.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FocusForegroundService.start(this)

        setContent {
            val currentAccent by viewModel.currentAccent.collectAsState()
            val permissionsGranted by viewModel.permissionsGranted.collectAsState()

            RonesecTheme(accent = currentAccent) {
                var forceShowMain by remember { mutableStateOf(false) }

                if (!permissionsGranted && !forceShowMain) {
                    OnboardingScreen(
                        onComplete = {
                            forceShowMain = true
                        }
                    )
                } else {
                    MainAppContent(viewModel = viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkPermissions()
    }
}

@Composable
fun MainAppContent(viewModel: MainViewModel) {
    val targets by viewModel.targets.collectAsState()
    val todayStats by viewModel.todayStats.collectAsState()
    val appStats by viewModel.appStats.collectAsState()
    val activeSessions by viewModel.activeBlockSessions.collectAsState()
    val schedules by viewModel.blockSchedules.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()
    val currentAccent by viewModel.currentAccent.collectAsState()

    var currentNav by remember { mutableStateOf(NavDestination.APPS) }
    var selectedTargetForEditing by remember { mutableStateOf<TargetApp?>(null) }

    Scaffold(
        bottomBar = {
            if (selectedTargetForEditing == null) {
                TerminalBottomNav(
                    currentDestination = currentNav,
                    onNavigate = { currentNav = it }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val currentEditing = selectedTargetForEditing
            if (currentEditing != null) {
                TargetSettingsScreen(
                    target = currentEditing,
                    onSave = { updated ->
                        viewModel.saveTarget(updated)
                        selectedTargetForEditing = null
                    },
                    onDelete = { pkg ->
                        viewModel.deleteTarget(pkg)
                        selectedTargetForEditing = null
                    },
                    onBack = { selectedTargetForEditing = null }
                )
            } else {
                when (currentNav) {
                    NavDestination.APPS -> {
                        HomeScreen(
                            targets = targets,
                            todayStats = todayStats,
                            installedApps = installedApps,
                            onSelectTarget = { target ->
                                selectedTargetForEditing = target
                            },
                            onToggleTarget = { target, enabled ->
                                viewModel.toggleTarget(target, enabled)
                            },
                            onAddApp = { pkg, label ->
                                viewModel.addTargetFromPackage(pkg, label)
                            }
                        )
                    }

                    NavDestination.BLOCK -> {
                        BlocksScreen(
                            activeSessions = activeSessions,
                            schedules = schedules,
                            targets = targets,
                            onStartHardBlock = { name, mins, pkgs ->
                                viewModel.startHardBlock(name, mins, pkgs)
                            },
                            onStopHardBlock = { id ->
                                viewModel.stopHardBlock(id)
                            },
                            onSaveSchedule = { sched ->
                                viewModel.saveSchedule(sched)
                            },
                            onDeleteSchedule = { id ->
                                viewModel.deleteSchedule(id)
                            }
                        )
                    }

                    NavDestination.STATS -> {
                        StatsScreen(
                            todayStats = todayStats,
                            appStats = appStats
                        )
                    }

                    NavDestination.CONFIG -> {
                        ConfigScreen(
                            currentAccent = currentAccent,
                            onSelectAccent = { acc ->
                                viewModel.setAccent(acc)
                            }
                        )
                    }
                }
            }
        }
    }
}
