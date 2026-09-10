package io.ronesec.android.ui.target

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ronesec.android.R
import io.ronesec.android.ui.designsystem.TerminalBadge
import io.ronesec.android.ui.designsystem.TerminalButton
import io.ronesec.android.ui.designsystem.TerminalCard
import io.ronesec.android.ui.designsystem.TerminalHelpCircle
import io.ronesec.android.ui.designsystem.WattimTheme

@Composable
fun InterventionDurationEditor(
    seconds: Int,
    onDurationChange: (Int) -> Unit,
    showContainer: Boolean = true,
    onHelpClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = WattimTheme.colors
    val dimensions = WattimTheme.dimensions
    val typography = WattimTheme.typography
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val presets = listOf(3, 5, 8, 10, 15, 20, 30)
    val shortUnit = stringResource(R.string.unit_sec).take(1).lowercase()
    var durationInput by remember { mutableStateOf(seconds.toString()) }

    LaunchedEffect(seconds) {
        if (durationInput.toIntOrNull() != seconds) {
            durationInput = seconds.toString()
        }
    }

    val content: @Composable () -> Unit = {
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
                    text = stringResource(R.string.target_pause_duration).uppercase(),
                    style = typography.labelSmall,
                    color = colors.textSecondary
                )
                if (onHelpClick != null) {
                    TerminalHelpCircle(
                        onClick = onHelpClick,
                        contentDescriptionText = stringResource(R.string.target_pause_duration)
                    )
                }
            }

            // Value input with keyboard
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, colors.border, RoundedCornerShape(4.dp))
                        .background(colors.surface, RoundedCornerShape(4.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
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
                            filtered.toIntOrNull()?.let { onDurationChange(it.coerceIn(1, 120)) }
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
                                    durationInput = seconds.toString()
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
                                        durationInput = seconds.toString()
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

            // Steppers row: -5s, -1s, +1s, +5s
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(-5, -1, 1, 5).forEach { delta ->
                    TerminalButton(
                        text = if (delta > 0) "+$delta$shortUnit" else "$delta$shortUnit",
                        onClick = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            val cur = durationInput.filter(Char::isDigit).toIntOrNull() ?: seconds
                            val next = (cur + delta).coerceIn(1, 120)
                            durationInput = next.toString()
                            onDurationChange(next)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Presets: 3s, 5s, 8s, 10s, 15s, 20s, 30s
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                presets.forEach { preset ->
                    val isSelected = (seconds == preset)
                    TerminalBadge(
                        text = "$preset$shortUnit",
                        isActive = isSelected,
                        modifier = Modifier.clickable {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            durationInput = preset.toString()
                            onDurationChange(preset)
                        }
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
