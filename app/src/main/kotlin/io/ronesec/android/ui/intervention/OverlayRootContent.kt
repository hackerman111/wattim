package io.ronesec.android.ui.intervention

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import io.ronesec.android.platform.overlay.OverlayMode
import io.ronesec.android.platform.overlay.OverlayPresenter
import io.ronesec.android.platform.time.AndroidMonotonicClock
import io.ronesec.android.ui.designsystem.ThemeRegistry
import io.ronesec.android.ui.designsystem.WattimTheme
import io.ronesec.android.ui.locale.ProvideWattimLocale
import io.ronesec.domain.breathing.BreathingTimeline
import io.ronesec.domain.model.MonotonicClock
import kotlinx.coroutines.isActive

/**
 * Root composition for WindowManager overlay.
 * Drives the high-frequency monotonic frame loop and renders InterventionContent or BlockContent
 * according to current OverlayMode.
 */
@Composable
fun OverlayRootContent(
    presenter: OverlayPresenter,
    monotonicClock: MonotonicClock = AndroidMonotonicClock()
) {
    val uiState by presenter.uiState.collectAsState()

    WattimTheme(colors = ThemeRegistry.getColors(uiState.themeId)) {
        ProvideWattimLocale(language = uiState.language) {
            when (val mode = uiState.mode) {
            is OverlayMode.Intervention -> {
                var progress by remember(mode.sessionId, mode.cycle) {
                    mutableStateOf(BreathingTimeline.calculate(0L, 0L, mode.config.durationMs))
                }
                var startElapsedMs by remember(mode.sessionId, mode.cycle) {
                    mutableLongStateOf(0L)
                }
                var deadlineEmitted by remember(mode.sessionId, mode.cycle) {
                    mutableStateOf(false)
                }

                LaunchedEffect(mode.sessionId, mode.cycle, mode.config.durationMs) {
                    startElapsedMs = monotonicClock.elapsedRealtimeMs()
                    while (isActive) {
                        withFrameNanos { _ ->
                            val now = monotonicClock.elapsedRealtimeMs()
                            val p = BreathingTimeline.calculate(startElapsedMs, now, mode.config.durationMs)
                            progress = p
                            if (p.isComplete && !deadlineEmitted) {
                                deadlineEmitted = true
                                presenter.onBreathingComplete(mode.sessionId, mode.cycle)
                            }
                        }
                    }
                }

                InterventionContent(
                    config = mode.config,
                    progress = progress,
                    showSavedBadge = uiState.showOverlayStats,
                    savedMinutes = uiState.allTimeSavedMinutes,
                    isEmergencyDialogOpen = uiState.isEmergencyDialogOpen,
                    onContinue = { presenter.onContinueClick() },
                    onExit = { presenter.onExitClick() },
                    onCancel = { presenter.onCancelClick() },
                    onEmergencyClick = { presenter.openEmergencyDialog() },
                    onDismissEmergency = { presenter.dismissEmergencyDialog() },
                    onEmergencyOnce = { presenter.onEmergencyOnce() },
                    onEmergencyTimed = { presenter.onEmergencyTimed(it) },
                    onEmergencyForever = { presenter.onEmergencyForever() },
                    customEmergencyMinutes = uiState.customEmergencyMinutes
                )
            }
            is OverlayMode.Block -> {
                BlockContent(
                    targetName = mode.packageName,
                    until = mode.until,
                    onExit = { presenter.onExitClick() }
                )
            }
            OverlayMode.None -> {
                Box(modifier = Modifier.fillMaxSize().background(WattimTheme.colors.background))
            }
        }
    }
}
}
