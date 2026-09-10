package io.ronesec.android.ui.blocks.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.ronesec.android.R
import io.ronesec.android.ui.designsystem.TerminalCard
import io.ronesec.android.ui.designsystem.WattimTheme
import io.ronesec.domain.model.ScheduleOverride

@Composable
fun AppInterventionOverrideCard(
    packageName: String,
    displayName: String,
    override: ScheduleOverride?,
    onSetDurationOverride: (packageName: String, durationMs: Long?) -> Unit,
    onSetRepeatOverride: (packageName: String, repeatMs: Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = WattimTheme.colors
    val dimensions = WattimTheme.dimensions
    val typography = WattimTheme.typography
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val isDurationCustom = override?.durationMs != null
    val currentDurationSec = ((override?.durationMs ?: 8000L) / 1000L).toInt().coerceIn(1, 120)

    var durationInput by remember { mutableStateOf(currentDurationSec.toString()) }

    LaunchedEffect(currentDurationSec) {
        if (durationInput.toIntOrNull() != currentDurationSec) {
            durationInput = currentDurationSec.toString()
        }
    }

    val isRepeatCustom = override?.reinterventionMs != null
    val currentRepeatMs = override?.reinterventionMs ?: 300_000L

    val durationPresets = listOf(3, 5, 8, 10, 15, 20, 30)
    val repeatOptions = listOf(
        Pair(0L, stringResource(R.string.status_off)),
        Pair(60_000L, "1M"),
        Pair(180_000L, "3M"),
        Pair(300_000L, "5M"),
        Pair(600_000L, "10M")
    )

    TerminalCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(dimensions.space12)
        ) {
            // App name header
            Text(
                text = displayName.uppercase(),
                style = typography.titleMedium,
                color = colors.accent
            )

            // Duration Override Section (F67)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(dimensions.space8)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.override_duration_label).uppercase(),
                        style = typography.labelSmall,
                        color = colors.textSecondary
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(dimensions.space4)) {
                        OverrideModeChip(
                            text = stringResource(R.string.override_inherit),
                            isSelected = !isDurationCustom,
                            onClick = { onSetDurationOverride(packageName, null) }
                        )
                        OverrideModeChip(
                            text = stringResource(R.string.override_custom),
                            isSelected = isDurationCustom,
                            onClick = { onSetDurationOverride(packageName, currentDurationSec * 1000L) }
                        )
                    }
                }

                if (isDurationCustom) {
                    // Duration text input with keyboard + unit
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(dimensions.space8),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .border(BorderStroke(dimensions.borderWidth, colors.border), RoundedCornerShape(dimensions.buttonRadius))
                                .background(colors.surface, RoundedCornerShape(dimensions.buttonRadius))
                                .padding(horizontal = dimensions.space12, vertical = dimensions.space8)
                        ) {
                            if (durationInput.isEmpty()) {
                                Text(
                                    text = "8",
                                    fontFamily = typography.bodyMedium.fontFamily,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textSecondary.copy(alpha = 0.4f)
                                )
                            }
                            BasicTextField(
                                value = durationInput,
                                onValueChange = { input ->
                                    val filtered = input.filter(Char::isDigit).take(3)
                                    durationInput = filtered
                                    filtered.toIntOrNull()?.let {
                                        val clamped = it.coerceIn(1, 120)
                                        onSetDurationOverride(packageName, clamped * 1000L)
                                    }
                                },
                                textStyle = TextStyle(
                                    fontFamily = typography.bodyMedium.fontFamily,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                ),
                                cursorBrush = SolidColor(colors.accent),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        if (durationInput.isBlank()) {
                                            durationInput = currentDurationSec.toString()
                                        }
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                    }
                                ),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onKeyEvent {
                                        if (it.key == Key.Enter) {
                                            if (durationInput.isBlank()) {
                                                durationInput = currentDurationSec.toString()
                                            }
                                            focusManager.clearFocus()
                                            keyboardController?.hide()
                                            true
                                        } else {
                                            false
                                        }
                                    }
                            )
                        }

                        Text(
                            text = stringResource(R.string.unit_sec).uppercase(),
                            fontFamily = typography.bodyMedium.fontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = colors.accent
                        )
                    }

                    // Steppers: -5, -1, +1, +5
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(dimensions.space8, Alignment.CenterHorizontally)
                    ) {
                        listOf(-5, -1, 1, 5).forEach { delta ->
                            val text = if (delta > 0) "+$delta" else "$delta"
                            OverrideStepperButton(text) {
                                val newSec = (currentDurationSec + delta).coerceIn(1, 120)
                                durationInput = newSec.toString()
                                onSetDurationOverride(packageName, newSec * 1000L)
                            }
                        }
                    }

                    // Presets
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(dimensions.space4, Alignment.CenterHorizontally)
                    ) {
                        durationPresets.forEach { preset ->
                            OverrideModeChip(
                                text = preset.toString(),
                                isSelected = (currentDurationSec == preset),
                                onClick = {
                                    durationInput = preset.toString()
                                    onSetDurationOverride(packageName, preset * 1000L)
                                }
                            )
                        }
                    }
                }
            }

            // Repeat Override Section (F67)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(dimensions.space8)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.override_repeat_label).uppercase(),
                        style = typography.labelSmall,
                        color = colors.textSecondary
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(dimensions.space4)) {
                        OverrideModeChip(
                            text = stringResource(R.string.override_inherit),
                            isSelected = !isRepeatCustom,
                            onClick = { onSetRepeatOverride(packageName, null) }
                        )
                        OverrideModeChip(
                            text = stringResource(R.string.override_custom),
                            isSelected = isRepeatCustom,
                            onClick = { onSetRepeatOverride(packageName, currentRepeatMs) }
                        )
                    }
                }

                if (isRepeatCustom) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(dimensions.space4, Alignment.CenterHorizontally)
                    ) {
                        repeatOptions.forEach { (ms, label) ->
                            OverrideModeChip(
                                text = label,
                                isSelected = (currentRepeatMs == ms),
                                onClick = { onSetRepeatOverride(packageName, ms) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverrideModeChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = WattimTheme.colors
    val dimensions = WattimTheme.dimensions
    val typography = WattimTheme.typography
    val shape = RoundedCornerShape(dimensions.buttonRadius)

    val borderColor = if (isSelected) colors.accent else colors.border
    val bgColor = if (isSelected) colors.accent.copy(alpha = 0.15f) else colors.surface

    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = dimensions.minTouchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .border(BorderStroke(dimensions.borderWidth, borderColor), shape)
            .background(bgColor, shape)
            .padding(horizontal = dimensions.space8, vertical = dimensions.space8),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = typography.labelSmall,
            color = if (isSelected) colors.accent else colors.textPrimary
        )
    }
}

@Composable
private fun OverrideStepperButton(
    text: String,
    onClick: () -> Unit
) {
    val colors = WattimTheme.colors
    val dimensions = WattimTheme.dimensions
    val typography = WattimTheme.typography
    val shape = RoundedCornerShape(dimensions.buttonRadius)

    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = dimensions.minTouchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .border(BorderStroke(dimensions.borderWidth, colors.border), shape)
            .background(colors.surface, shape)
            .padding(horizontal = dimensions.space8, vertical = dimensions.space8),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = typography.button,
            color = colors.textPrimary
        )
    }
}
