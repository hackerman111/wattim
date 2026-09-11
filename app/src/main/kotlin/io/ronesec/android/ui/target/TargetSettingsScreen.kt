package io.ronesec.android.ui.target

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ronesec.android.R
import io.ronesec.android.ui.designsystem.TerminalBadge
import io.ronesec.android.ui.designsystem.TerminalButton
import io.ronesec.android.ui.designsystem.TerminalButtonVariant
import io.ronesec.android.ui.designsystem.TerminalCard
import io.ronesec.android.ui.designsystem.TerminalHelpCircle
import io.ronesec.android.ui.designsystem.TerminalInfoDialog
import io.ronesec.android.ui.designsystem.TerminalInputField
import io.ronesec.android.ui.designsystem.WattimTheme
import io.ronesec.domain.model.AnimationMode

enum class TargetHelpTopic(val titleRes: Int, val descRes: Int) {
    PAUSE_DURATION(R.string.help_pause_duration_title, R.string.help_pause_duration_desc),
    REINTERVENTION(R.string.help_reintervention_title, R.string.help_reintervention_desc),
    QUICK_RETURN(R.string.help_quick_return_title, R.string.help_quick_return_desc),
    EXPONENTIAL_GROWTH(R.string.help_backoff_title, R.string.help_backoff_desc),
    QUICK_LOCK(R.string.help_quick_lock_title, R.string.help_quick_lock_desc)
}

@Composable
fun TargetSettingsScreen(
    state: TargetSettingsUiState,
    onBack: () -> Unit,
    onPhraseChange: (String) -> Unit,
    onAnimationChange: (AnimationMode) -> Unit,
    onDurationChange: (Int) -> Unit,
    onReinterventionChoice: (ReinterventionChoice) -> Unit,
    onCustomReinterventionChange: (minutes: Int, seconds: Int) -> Unit,
    onQuickReturnChange: (Long) -> Unit,
    onBackoffEnabledChange: (Boolean) -> Unit,
    onBackoffPercentChange: (Int) -> Unit,
    onBackoffWindowChange: (Long) -> Unit,
    onToggleEnabled: () -> Unit,
    onOpenPreview: () -> Unit,
    onDismissPreview: () -> Unit,
    onQuickLock: (Long) -> Unit = {},
    onSave: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    onTwoStageUnlockChange: (Boolean) -> Unit = {},
    onUnlockCodeLengthChange: (Int) -> Unit = {},
    onRequireEmergencyCodeChange: (Boolean) -> Unit = {},
    onToggleRandomDuration: () -> Unit = {},
    onRandomMaxDurationChange: (Int) -> Unit = {}
) {
    val colors = WattimTheme.colors
    val dimensions = WattimTheme.dimensions
    val typography = WattimTheme.typography

    // Handle back: Back discards draft (F58)
    BackHandler(onBack = onBack)

    // Navigate back upon successful save, remove, or quick lock (F58, F60)
    LaunchedEffect(state.isSaved, state.isRemoved, state.isQuickLocked) {
        if (state.isSaved || state.isRemoved || state.isQuickLocked) {
            onBack()
        }
    }

    val draft = state.draft
    var activeHelpTopic by remember { mutableStateOf<TargetHelpTopic?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(dimensions.space16)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(dimensions.space16)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onBack)
                .padding(vertical = dimensions.space8),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "← ${state.displayName.uppercase()}",
                style = typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                ),
                color = colors.accent
            )
        }

        TerminalCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.target_protection_status),
                        style = typography.labelSmall,
                        color = colors.textSecondary
                    )
                    Text(
                        text = stringResource(
                            if (draft.enabled) R.string.target_status_active
                            else R.string.target_status_disabled
                        ),
                        style = typography.titleMedium,
                        color = if (draft.enabled) colors.accent else colors.textSecondary
                    )
                }
                TerminalBadge(
                    text = stringResource(if (draft.enabled) R.string.status_on else R.string.status_off),
                    isActive = draft.enabled,
                    onClick = onToggleEnabled
                )
            }
        }

        // External change / stale warning (F58)
        if (state.isStale) {
            TerminalCard(modifier = Modifier.fillMaxWidth(), isError = true) {
                Text(
                    text = stringResource(R.string.status_stale_warning),
                    style = typography.bodyMedium,
                    color = colors.error
                )
            }
        }

        // Error message banner
        if (state.errorMessage != null && !state.isStale) {
            TerminalCard(modifier = Modifier.fillMaxWidth(), isError = true) {
                Text(
                    text = state.errorMessage,
                    style = typography.bodyMedium,
                    color = colors.error
                )
            }
        }

        // 1. Mindful Phrase (F50): Multiline <=80 chars, up to 3 lines
        TerminalCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.phrase_label),
                style = typography.labelSmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(bottom = dimensions.space8)
            )
            TerminalInputField(
                value = draft.phrase,
                onValueChange = onPhraseChange,
                placeholder = stringResource(R.string.phrase_hint),
                maxLength = 80,
                singleLine = false,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 2. Animation Mode Selection (F51)
        AnimationSelectorCard(
            selectedMode = draft.animation,
            onModeSelect = onAnimationChange
        )

        CodeSettingsCard(
            twoStageUnlock = draft.twoStageUnlock,
            unlockCodeLength = draft.unlockCodeLength,
            requireEmergencyCode = draft.requireEmergencyCode,
            onTwoStageUnlockChange = onTwoStageUnlockChange,
            onUnlockCodeLengthChange = onUnlockCodeLengthChange,
            onRequireEmergencyCodeChange = onRequireEmergencyCodeChange,
            enabled = !state.isLoading
        )

        // 3. Pause Duration Editor (F52): 1-120s, unit label, steppers, presets
        InterventionDurationEditor(
            seconds = draft.durationSeconds,
            onDurationChange = onDurationChange,
            onHelpClick = { activeHelpTopic = TargetHelpTopic.PAUSE_DURATION }
        )

        RandomDurationCard(
            enabled = draft.randomDurationEnabled,
            minDurationSeconds = draft.durationSeconds,
            maxDurationSeconds = draft.randomMaxDurationSeconds,
            onToggleEnabled = onToggleRandomDuration,
            onMaxDurationChange = onRandomMaxDurationChange,
            isInteractive = !state.isLoading
        )

        // 4. Preview Button (F53)
        TerminalButton(
            text = stringResource(R.string.action_preview),
            onClick = onOpenPreview,
            variant = TerminalButtonVariant.SECONDARY,
            modifier = Modifier.fillMaxWidth()
        )

        // 5. Re-intervention Configuration (F54): OFF/presets/custom
        ReinterventionEditor(
            choice = draft.reinterventionMode,
            customMinutes = draft.customReinterventionMinutes,
            customSeconds = draft.customReinterventionSeconds,
            onChoiceChange = onReinterventionChoice,
            onCustomChange = onCustomReinterventionChange,
            onHelpClick = { activeHelpTopic = TargetHelpTopic.REINTERVENTION }
        )

        // 6. Quick Return Grace (F55): 0/15s/30s/1m/2m/5m
        QuickReturnEditor(
            graceMs = draft.quickReturnGraceMs,
            onGraceChange = onQuickReturnChange,
            onHelpClick = { activeHelpTopic = TargetHelpTopic.QUICK_RETURN }
        )

        // 7. Growth Backoff Controls (F56): ON/OFF, 1-200%, rolling windows
        GrowthBackoffEditor(
            enabled = draft.backoffEnabled,
            percent = draft.backoffPercent,
            windowMs = draft.backoffWindowMs,
            onEnabledChange = onBackoffEnabledChange,
            onPercentChange = onBackoffPercentChange,
            onWindowChange = onBackoffWindowChange,
            onHelpClick = { activeHelpTopic = TargetHelpTopic.EXPONENTIAL_GROWTH }
        )

        // 8. Backoff Calculator (F57): Live first 10 delay projection
        if (draft.backoffEnabled) {
            BackoffCalculatorCard(
                delays = state.calculatedDelays,
                baseDurationSeconds = draft.durationSeconds,
                growthPercent = draft.backoffPercent,
                windowMs = draft.backoffWindowMs
            )
        }

        // Quick Lock Section (F60): Conditional 15m/30m/1h/2h single-app hard block and return
        if (state.canQuickLock) {
            QuickLockCard(
                onQuickLock = onQuickLock,
                enabled = !state.isLoading,
                onHelpClick = { activeHelpTopic = TargetHelpTopic.QUICK_LOCK }
            )
        }

        Spacer(modifier = Modifier.height(dimensions.space8))

        // 9. Bottom Actions: Save + Remove (F58)
        TerminalButton(
            text = stringResource(R.string.save_changes_button),
            onClick = onSave,
            variant = TerminalButtonVariant.PRIMARY,
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth()
        )

        TerminalButton(
            text = stringResource(R.string.remove_from_protection_button),
            onClick = onRemove,
            variant = TerminalButtonVariant.SECONDARY,
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth()
        )
    }

    // Preview Dialog (F53)
    if (state.isPreviewOpen) {
        TargetPreviewDialog(
            draft = draft,
            onDismiss = onDismissPreview
        )
    }

    // Feature explanation dialog
    activeHelpTopic?.let { topic ->
        TerminalInfoDialog(
            title = stringResource(topic.titleRes),
            description = stringResource(topic.descRes),
            onDismissRequest = { activeHelpTopic = null }
        )
    }
}

@Composable
private fun AnimationSelectorCard(
    selectedMode: AnimationMode,
    onModeSelect: (AnimationMode) -> Unit,
    showContainer: Boolean = true,
    modifier: Modifier = Modifier
) {
    val colors = WattimTheme.colors
    val dimensions = WattimTheme.dimensions
    val typography = WattimTheme.typography

    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(dimensions.space12)
        ) {
            Text(
                text = stringResource(R.string.animation_style_label).uppercase(),
                style = typography.labelSmall,
                color = colors.textSecondary
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    AnimationMode.FILL to stringResource(R.string.anim_fill),
                    AnimationMode.PULSE to stringResource(R.string.anim_pulse),
                    AnimationMode.CIRCLE to stringResource(R.string.anim_circle),
                    AnimationMode.WAVE to stringResource(R.string.anim_wave),
                    AnimationMode.FILL_2 to stringResource(R.string.anim_fill_2)
                ).forEach { (mode, label) ->
                    TerminalButton(
                        text = label,
                        onClick = { onModeSelect(mode) },
                        variant = if (selectedMode == mode) {
                            TerminalButtonVariant.PRIMARY
                        } else {
                            TerminalButtonVariant.SECONDARY
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
    if (showContainer) {
        TerminalCard(modifier = modifier.fillMaxWidth()) { content() }
    } else {
        content()
    }
}

@Composable
private fun QuickLockCard(
    onQuickLock: (Long) -> Unit,
    enabled: Boolean,
    onHelpClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = WattimTheme.colors
    val dimensions = WattimTheme.dimensions
    val typography = WattimTheme.typography

    TerminalCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(dimensions.space12)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.quick_lock_title).uppercase(),
                    style = typography.labelSmall,
                    color = colors.accent
                )
                if (onHelpClick != null) {
                    TerminalHelpCircle(
                        onClick = onHelpClick,
                        contentDescriptionText = stringResource(R.string.help_quick_lock_title)
                    )
                }
            }
            Text(
                text = stringResource(R.string.quick_lock_desc),
                style = typography.bodyMedium,
                color = colors.textSecondary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val durations = listOf(
                    stringResource(R.string.duration_15m) to 15 * 60 * 1000L,
                    stringResource(R.string.duration_30m) to 30 * 60 * 1000L,
                    stringResource(R.string.duration_1h) to 60 * 60 * 1000L,
                    stringResource(R.string.duration_2h) to 120 * 60 * 1000L
                )
                durations.forEach { (lbl, durMs) ->
                    TerminalButton(
                        text = lbl,
                        onClick = { if (enabled) onQuickLock(durMs) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
