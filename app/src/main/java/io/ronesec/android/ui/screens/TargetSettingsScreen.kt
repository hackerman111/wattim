package io.ronesec.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ronesec.android.domain.model.TargetApp
import io.ronesec.android.domain.util.TimeFormatUtils
import io.ronesec.android.overlay.InterventionOverlayContent
import io.ronesec.android.ui.components.TerminalButton
import io.ronesec.android.ui.i18n.LocalAppStrings
import io.ronesec.android.ui.screens.target.TargetAnimationAndDurationCard
import io.ronesec.android.ui.screens.target.TargetExponentialGrowthCard
import io.ronesec.android.ui.screens.target.TargetPhraseCard
import io.ronesec.android.ui.screens.target.TargetQuickLockCard
import io.ronesec.android.ui.screens.target.TargetQuickReturnCard
import io.ronesec.android.ui.screens.target.TargetReinterventionCard
import io.ronesec.android.ui.screens.target.TargetStatusCard
import io.ronesec.android.ui.theme.LocalAppPalette
import io.ronesec.android.ui.theme.TerminalFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetSettingsScreen(
    target: TargetApp,
    onSave: (TargetApp) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
    onStartHardBlock: ((String, Int, Set<String>) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val strings = LocalAppStrings.current
    val accent = palette.accent
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var enabled by remember { mutableStateOf(target.enabled) }
    var phrase by remember { mutableStateOf(target.intervention.phrase) }
    var durationSeconds by remember { mutableFloatStateOf(target.intervention.durationMs / 1000f) }
    var durationInput by remember {
        mutableStateOf((target.intervention.durationMs / 1000L).coerceIn(1L, 120L).toString())
    }
    var selectedAnimation by remember { mutableStateOf(target.intervention.animation) }
    var remindAgainMs by remember { mutableStateOf(target.intervention.reinterventionMs) }
    val standardReinterventionPresets = listOf(60_000L, 180_000L, 300_000L, 600_000L)
    var isCustomReintervention by remember {
        mutableStateOf(remindAgainMs != null && remindAgainMs !in standardReinterventionPresets)
    }

    val initialCustomMs = target.intervention.reinterventionMs ?: 120_000L
    var customMinutesInput by remember {
        mutableStateOf((initialCustomMs / 60_000L).toString())
    }
    var customSecondsInput by remember {
        mutableStateOf(((initialCustomMs % 60_000L) / 1000L).toString())
    }

    var quickReturnGraceSec by remember { mutableLongStateOf(target.intervention.quickReturnGraceMs / 1000L) }

    var exponentialGrowthEnabled by remember { mutableStateOf(target.intervention.exponentialGrowthEnabled) }
    var growthPercent by remember { mutableIntStateOf(target.intervention.growthPercent) }
    var growthPeriodMinutes by remember { mutableIntStateOf(target.intervention.growthPeriodMinutes) }

    var showPreview by remember { mutableStateOf(false) }

    if (showPreview) {
        BasicAlertDialog(onDismissRequest = { showPreview = false }) {
            Box(modifier = Modifier.fillMaxSize()) {
                InterventionOverlayContent(
                    targetAppName = target.displayName,
                    config = target.intervention.copy(
                        phrase = phrase,
                        durationMs = (durationSeconds * 1000).toLong(),
                        animation = selectedAnimation,
                        exponentialGrowthEnabled = exponentialGrowthEnabled,
                        growthPercent = growthPercent,
                        growthPeriodMinutes = growthPeriodMinutes
                    ),
                    savedTimeText = strings.previewSavedTime,
                    onEmergencyAccess = { _, _ -> showPreview = false },
                    onClose = { showPreview = false },
                    onContinue = { showPreview = false }
                )
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Back header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onBack() }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "← ${target.displayName.uppercase()}",
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                letterSpacing = 0.15.sp,
                color = accent
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        TargetStatusCard(
            enabled = enabled,
            onEnabledChange = { enabled = it }
        )

        Spacer(modifier = Modifier.height(16.dp))

        TargetPhraseCard(
            phrase = phrase,
            onPhraseChange = { phrase = it }
        )

        Spacer(modifier = Modifier.height(16.dp))

        TargetAnimationAndDurationCard(
            selectedAnimation = selectedAnimation,
            onAnimationSelected = { selectedAnimation = it },
            durationSeconds = durationSeconds,
            durationInput = durationInput,
            onDurationInputChange = { input, seconds ->
                durationInput = input
                durationSeconds = seconds
            },
            onPreviewClick = { showPreview = true }
        )

        Spacer(modifier = Modifier.height(16.dp))

        TargetReinterventionCard(
            remindAgainMs = remindAgainMs,
            isCustomReintervention = isCustomReintervention,
            customMinutesInput = customMinutesInput,
            customSecondsInput = customSecondsInput,
            onRemindAgainChange = { remindAgainMs = it },
            onCustomModeChange = { isCustomReintervention = it },
            onCustomInputsChange = { mins, secs, totalMs ->
                customMinutesInput = mins
                customSecondsInput = secs
                remindAgainMs = totalMs
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        TargetQuickReturnCard(
            quickReturnGraceSec = quickReturnGraceSec,
            onQuickReturnGraceChange = { quickReturnGraceSec = it }
        )

        Spacer(modifier = Modifier.height(16.dp))

        TargetExponentialGrowthCard(
            enabled = exponentialGrowthEnabled,
            onEnabledChange = { exponentialGrowthEnabled = it },
            growthPercent = growthPercent,
            onGrowthPercentChange = { growthPercent = it },
            growthPeriodMinutes = growthPeriodMinutes,
            onGrowthPeriodMinutesChange = { growthPeriodMinutes = it },
            durationSeconds = durationSeconds
        )

        if (onStartHardBlock != null) {
            Spacer(modifier = Modifier.height(16.dp))
            TargetQuickLockCard(
                targetDisplayName = target.displayName,
                targetPackageName = target.packageName,
                onStartHardBlock = onStartHardBlock
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        TerminalButton(
            text = strings.saveChangesButton,
            onClick = {
                focusManager.clearFocus()
                keyboardController?.hide()
                val finalReinterventionMs = if (!isCustomReintervention) {
                    remindAgainMs
                } else {
                    TimeFormatUtils.parseDuration(customMinutesInput, customSecondsInput, minMs = 5_000L)
                }
                val parsedDuration = durationInput.filter { it.isDigit() }.toLongOrNull()
                    ?: (durationSeconds.toLong().coerceAtLeast(1L))
                val finalDurationMs = parsedDuration.coerceIn(1L, 120L) * 1000L
                val updated = target.copy(
                    enabled = enabled,
                    intervention = target.intervention.copy(
                        phrase = phrase.trim().ifEmpty { strings.targetDefaultPhrase },
                        durationMs = finalDurationMs,
                        animation = selectedAnimation,
                        reinterventionMs = finalReinterventionMs,
                        quickReturnGraceMs = quickReturnGraceSec * 1000L,
                        exponentialGrowthEnabled = exponentialGrowthEnabled,
                        growthPercent = growthPercent,
                        growthPeriodMinutes = growthPeriodMinutes
                    )
                )
                onSave(updated)
                onBack()
            },
            isPrimary = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        TerminalButton(
            text = strings.removeFromProtectionButton,
            onClick = {
                onDelete(target.packageName)
                onBack()
            },
            isPrimary = false,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
