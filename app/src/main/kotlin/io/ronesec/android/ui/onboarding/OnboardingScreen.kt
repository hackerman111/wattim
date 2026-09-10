package io.ronesec.android.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ronesec.android.R
import io.ronesec.android.platform.system.OnboardingStep
import io.ronesec.android.ui.designsystem.TerminalBadge
import io.ronesec.android.ui.designsystem.TerminalButton
import io.ronesec.android.ui.designsystem.TerminalButtonVariant
import io.ronesec.android.ui.designsystem.TerminalCard
import io.ronesec.android.ui.designsystem.WattimTheme

@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onEnableStep: (OnboardingStep) -> Unit,
    onCheckStatus: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onReadyContinue: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = WattimTheme.colors
    val typography = WattimTheme.typography

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = stringResource(R.string.app_name).uppercase(),
            fontFamily = typography.bodyMedium.fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            letterSpacing = 0.2.sp,
            color = colors.accent
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = when (state.stepState) {
                is OnboardingStepState.Permission -> state.currentStepNumber.replace("/", " / ")
                OnboardingStepState.SystemReady -> stringResource(R.string.onboarding_ready_title)
            },
            fontFamily = typography.bodyMedium.fontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            letterSpacing = 0.15.sp,
            color = colors.textSecondary
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (state.errorMessage != null) {
            TerminalCard(isError = true, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = state.errorMessage,
                    style = typography.bodyMedium,
                    color = colors.error
                )
                Spacer(modifier = Modifier.height(8.dp))
                TerminalButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = onDismissError,
                    variant = TerminalButtonVariant.SECONDARY
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        when (val stepState = state.stepState) {
            is OnboardingStepState.Permission -> PermissionStepDetails(
                step = stepState.step,
                onOpenAppInfo = onOpenAppInfo
            )
            OnboardingStepState.SystemReady -> SystemReadyDetails()
        }

        Spacer(modifier = Modifier.height(24.dp))

        when (val stepState = state.stepState) {
            is OnboardingStepState.Permission -> {
                TerminalButton(
                    text = stringResource(stepState.step.actionLabelRes()),
                    onClick = { onEnableStep(stepState.step) },
                    variant = TerminalButtonVariant.PRIMARY,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                TerminalButton(
                    text = stringResource(R.string.action_check_status),
                    onClick = onCheckStatus,
                    variant = TerminalButtonVariant.SECONDARY,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            OnboardingStepState.SystemReady -> TerminalButton(
                text = stringResource(R.string.action_start_wattim),
                onClick = onReadyContinue,
                variant = TerminalButtonVariant.PRIMARY,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun PermissionStepDetails(
    step: OnboardingStep,
    onOpenAppInfo: () -> Unit
) {
    val colors = WattimTheme.colors
    val typography = WattimTheme.typography
    val titleRes = when (step) {
        OnboardingStep.Accessibility -> R.string.onboarding_step1_title
        OnboardingStep.Overlay -> R.string.onboarding_step2_title
        OnboardingStep.BatteryExemption -> R.string.onboarding_step3_title
    }
    val descriptionRes = when (step) {
        OnboardingStep.Accessibility -> R.string.onboarding_step1_desc
        OnboardingStep.Overlay -> R.string.onboarding_step2_desc
        OnboardingStep.BatteryExemption -> R.string.onboarding_step3_desc
    }

    Text(
        text = stringResource(titleRes),
        fontFamily = typography.bodyMedium.fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        color = colors.textPrimary
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = stringResource(descriptionRes),
        fontFamily = typography.bodyMedium.fontFamily,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        color = colors.textSecondary
    )

    if (step == OnboardingStep.Accessibility || step == OnboardingStep.Overlay) {
        Spacer(modifier = Modifier.height(16.dp))
        TerminalCard(
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.5f))
        ) {
            Text(
                text = stringResource(R.string.restricted_settings_title),
                fontFamily = typography.bodyMedium.fontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 0.1.sp,
                color = colors.accent,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Text(
                text = stringResource(R.string.restricted_settings_desc),
                fontFamily = typography.bodyMedium.fontFamily,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = colors.textSecondary,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            TerminalButton(
                text = stringResource(R.string.action_open_app_info),
                onClick = onOpenAppInfo,
                variant = TerminalButtonVariant.SECONDARY,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SystemReadyDetails() {
    TerminalCard(modifier = Modifier.fillMaxWidth()) {
        ReadyBadgeRow(
            label = stringResource(R.string.badge_accessibility),
            readyText = stringResource(R.string.badge_ready)
        )
        ReadyBadgeRow(
            label = stringResource(R.string.badge_overlay),
            readyText = stringResource(R.string.badge_ready)
        )
        ReadyBadgeRow(
            label = stringResource(R.string.badge_battery),
            readyText = stringResource(R.string.badge_ready)
        )
    }
}

@Composable
private fun ReadyBadgeRow(label: String, readyText: String) {
    val colors = WattimTheme.colors
    val typography = WattimTheme.typography

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = typography.bodyMedium,
            color = colors.textPrimary
        )
        TerminalBadge(text = readyText, isActive = true)
    }
}

private fun OnboardingStep.actionLabelRes(): Int = when (this) {
    OnboardingStep.Accessibility -> R.string.action_open_accessibility
    OnboardingStep.Overlay -> R.string.action_allow_overlay
    OnboardingStep.BatteryExemption -> R.string.action_disable_battery_optimization
}
