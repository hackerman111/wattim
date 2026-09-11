package io.ronesec.android.ui.intervention

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import io.ronesec.android.platform.overlay.OverlayMode
import io.ronesec.android.platform.overlay.OverlayPresenter
import io.ronesec.android.platform.time.AndroidMonotonicClock
import io.ronesec.android.ui.designsystem.ThemeRegistry
import io.ronesec.android.ui.designsystem.WattimTheme
import io.ronesec.android.ui.locale.ProvideWattimLocale
import io.ronesec.domain.model.MonotonicClock

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
            is OverlayMode.Intervention -> key(mode.sessionId, mode.cycle) {
                SessionInterventionContent(
                    mode = mode,
                    uiState = uiState,
                    presenter = presenter,
                    monotonicClock = monotonicClock
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
