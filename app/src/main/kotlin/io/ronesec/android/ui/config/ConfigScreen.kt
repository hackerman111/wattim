package io.ronesec.android.ui.config

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ronesec.android.R
import io.ronesec.android.ui.designsystem.TerminalButton
import io.ronesec.android.ui.designsystem.TerminalButtonVariant
import io.ronesec.android.ui.designsystem.TerminalCard
import io.ronesec.android.ui.designsystem.ThemeId
import io.ronesec.android.ui.designsystem.WattimTheme

/**
 * Complete Configuration / Settings Screen (F72-F78).
 */
@Composable
fun ConfigScreen(
    uiState: ConfigUiState,
    onSelectTheme: (ThemeId) -> Unit,
    onSelectLanguage: (String) -> Unit,
    onSelectSavedSessionMinutes: (Int) -> Unit,
    onToggleShowOverlayStats: (Boolean) -> Unit,
    onEnableAccessibility: () -> Unit,
    onEnableMediaControl: () -> Unit,
    onEnableOverlay: () -> Unit,
    onEnableBattery: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onDismissError: () -> Unit,
    onSetCustomEmergencyMinutes: (Int?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = WattimTheme.colors
    val dimensions = WattimTheme.dimensions
    val typography = WattimTheme.typography

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_screen_title),
            fontFamily = typography.bodyMedium.fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            letterSpacing = 0.15.sp,
            color = colors.accent
        )

        // Error card if any
        if (uiState.errorMessage != null) {
            TerminalCard(
                isError = true,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(dimensions.space8)) {
                    Text(
                        text = uiState.errorMessage,
                        style = typography.bodyMedium,
                        color = colors.error
                    )
                    TerminalButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = onDismissError,
                        variant = TerminalButtonVariant.SECONDARY
                    )
                }
            }
        }

        // F72 Language selector
        LanguageSelectorCard(
            selectedLanguage = uiState.selectedLanguage,
            onSelectLanguage = onSelectLanguage
        )

        // F73 Theme selector
        ThemeSelectorCard(
            selectedTheme = uiState.selectedTheme,
            onSelectTheme = onSelectTheme
        )

        // F74 Saved-session duration
        SavedSessionDurationCard(
            selectedMinutes = uiState.savedSessionMinutes,
            onSelectMinutes = onSelectSavedSessionMinutes
        )

        // F75 Overlay statistics toggle
        OverlayStatsCard(
            showOverlayStats = uiState.showOverlayStats,
            onToggleShowOverlayStats = onToggleShowOverlayStats
        )

        // Custom emergency access duration
        EmergencyAccessConfigCard(
            customEmergencyMinutes = uiState.customEmergencyMinutes,
            onSetCustomMinutes = onSetCustomEmergencyMinutes
        )

        // F76 System permissions & status
        PermissionStatusCard(
            accessibilityState = uiState.accessibilityState,
            mediaControlState = uiState.mediaControlState,
            overlayState = uiState.overlayState,
            batteryState = uiState.batteryState,
            onEnableAccessibility = onEnableAccessibility,
            onEnableMediaControl = onEnableMediaControl,
            onEnableOverlay = onEnableOverlay,
            onEnableBattery = onEnableBattery
        )

        AudioDiagnosticsCard(state = uiState.audioDiagnostic)

        // F77 Restricted settings help
        RestrictedSettingsCard(
            onOpenAppInfo = onOpenAppInfo
        )

        // F78 Privacy & security
        PrivacyCard()
    }
}
