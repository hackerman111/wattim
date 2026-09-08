package io.ronesec.android.ui.screens.target

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ronesec.android.domain.util.TimeFormatUtils
import io.ronesec.android.ui.components.TerminalBadge
import io.ronesec.android.ui.components.TerminalButton
import io.ronesec.android.ui.i18n.LocalAppStrings
import io.ronesec.android.ui.theme.LocalAppPalette
import io.ronesec.android.ui.theme.TerminalFontFamily

@Composable
fun TargetCustomReinterventionSection(
    customMinutesInput: String,
    customSecondsInput: String,
    onCustomInputsChange: (minutes: String, seconds: String, totalMs: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val strings = LocalAppStrings.current
    val accent = palette.accent
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val curMins = customMinutesInput.filter { it.isDigit() }.toLongOrNull() ?: 0L
    val curSecs = customSecondsInput.filter { it.isDigit() }.toLongOrNull() ?: 0L
    val currentEffectiveMs = curMins * 60_000L + curSecs * 1000L
    val displayTime = strings.formatDuration(currentEffectiveMs)

    fun applyAdjustment(deltaMs: Long) {
        focusManager.clearFocus()
        val (newM, newS) = TimeFormatUtils.calculateAdjustedTime(
            customMinutesInput,
            customSecondsInput,
            deltaMs
        )
        val total = TimeFormatUtils.parseDuration(newM, newS)
        onCustomInputsChange(newM, newS, total)
    }

    fun applyPreset(presetMs: Long) {
        focusManager.clearFocus()
        val newM = (presetMs / 60_000L).toString()
        val newS = ((presetMs % 60_000L) / 1000L).toString()
        onCustomInputsChange(newM, newS, presetMs)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.surfaceElevated, RoundedCornerShape(6.dp))
            .border(1.dp, palette.border, RoundedCornerShape(6.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = strings.targetCustomInterval,
                fontFamily = TerminalFontFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.textSecondary
            )

            Text(
                text = displayTime,
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = accent
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CustomTimeColumn(
                label = strings.minutesLabel,
                value = customMinutesInput,
                maxDigits = 3,
                imeAction = ImeAction.Next,
                onValueChange = { input ->
                    val total = TimeFormatUtils.parseDuration(input, customSecondsInput)
                    onCustomInputsChange(input, customSecondsInput, total)
                },
                onActionTriggered = {
                    focusManager.moveFocus(FocusDirection.Next)
                },
                onBlur = {
                    val validM = if (customMinutesInput.isBlank()) "0" else customMinutesInput
                    val total = TimeFormatUtils.parseDuration(validM, customSecondsInput)
                    onCustomInputsChange(validM, customSecondsInput, total)
                },
                decrementText = "-1${strings.minutesShort}",
                incrementText = "+1${strings.minutesShort}",
                onDecrement = { applyAdjustment(-60_000L) },
                onIncrement = { applyAdjustment(60_000L) },
                modifier = Modifier.weight(1f)
            )

            CustomTimeColumn(
                label = strings.secondsLabel,
                value = customSecondsInput,
                maxDigits = 2,
                imeAction = ImeAction.Done,
                onValueChange = { input ->
                    val total = TimeFormatUtils.parseDuration(customMinutesInput, input)
                    onCustomInputsChange(customMinutesInput, input, total)
                },
                onActionTriggered = {
                    val validS = if (customSecondsInput.isBlank()) "0" else customSecondsInput
                    val total = TimeFormatUtils.parseDuration(customMinutesInput, validS)
                    onCustomInputsChange(customMinutesInput, validS, total)
                    focusManager.clearFocus()
                    keyboardController?.hide()
                },
                onBlur = {
                    val validS = if (customSecondsInput.isBlank()) "0" else customSecondsInput
                    val total = TimeFormatUtils.parseDuration(customMinutesInput, validS)
                    onCustomInputsChange(customMinutesInput, validS, total)
                },
                decrementText = "-15${strings.secondsUnitShort}",
                incrementText = "+15${strings.secondsUnitShort}",
                onDecrement = { applyAdjustment(-15_000L) },
                onIncrement = { applyAdjustment(15_000L) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = strings.targetQuickPresets,
            fontFamily = TerminalFontFamily,
            fontSize = 10.sp,
            color = palette.textSecondary,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "30${strings.secondsUnitShort}" to 30_000L,
                "45${strings.secondsUnitShort}" to 45_000L,
                "2${strings.minutesShort}" to 120_000L,
                "15${strings.minutesShort}" to 900_000L,
                "30${strings.minutesShort}" to 1800_000L
            ).forEach { (lbl, ms) ->
                TerminalBadge(
                    text = lbl,
                    isActive = currentEffectiveMs == ms,
                    modifier = Modifier.clickable { applyPreset(ms) }
                )
            }
        }
    }
}

@Composable
private fun CustomTimeColumn(
    label: String,
    value: String,
    maxDigits: Int,
    imeAction: ImeAction,
    onValueChange: (String) -> Unit,
    onActionTriggered: () -> Unit,
    onBlur: () -> Unit,
    decrementText: String,
    incrementText: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val accent = palette.accent

    Column(modifier = modifier) {
        Text(
            text = label,
            fontFamily = TerminalFontFamily,
            fontSize = 10.sp,
            color = palette.textSecondary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, palette.border, RoundedCornerShape(4.dp))
                .background(palette.surface, RoundedCornerShape(4.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            if (value.isEmpty()) {
                Text(
                    text = "0",
                    fontFamily = TerminalFontFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textSecondary.copy(alpha = 0.4f)
                )
            }
            BasicTextField(
                value = value,
                onValueChange = { input ->
                    val filtered = input.filter { it.isDigit() }.take(maxDigits)
                    onValueChange(filtered)
                },
                textStyle = TextStyle(
                    fontFamily = TerminalFontFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary
                ),
                cursorBrush = SolidColor(accent),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = imeAction
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        onBlur()
                        onActionTriggered()
                    },
                    onNext = {
                        onBlur()
                        onActionTriggered()
                    }
                ),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .onKeyEvent {
                        if (it.key == Key.Enter) {
                            onBlur()
                            onActionTriggered()
                            true
                        } else {
                            false
                        }
                    }
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TerminalButton(
                text = decrementText,
                onClick = onDecrement,
                modifier = Modifier.weight(1f)
            )
            TerminalButton(
                text = incrementText,
                onClick = onIncrement,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
