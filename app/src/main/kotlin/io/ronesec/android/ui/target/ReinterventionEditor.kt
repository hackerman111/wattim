package io.ronesec.android.ui.target

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
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
fun ReinterventionEditor(
    choice: ReinterventionChoice,
    customMinutes: Int,
    customSeconds: Int,
    onChoiceChange: (ReinterventionChoice) -> Unit,
    onCustomChange: (minutes: Int, seconds: Int) -> Unit,
    onHelpClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = WattimTheme.colors
    val dimensions = WattimTheme.dimensions
    val typography = WattimTheme.typography

    val choices = listOf(
        Pair(ReinterventionChoice.OFF, stringResource(R.string.status_off)),
        Pair(ReinterventionChoice.MIN_1, "1M"),
        Pair(ReinterventionChoice.MIN_3, "3M"),
        Pair(ReinterventionChoice.MIN_5, "5M"),
        Pair(ReinterventionChoice.MIN_10, "10M"),
        Pair(ReinterventionChoice.CUSTOM, stringResource(R.string.reintervention_custom))
    )

    fun adjustCustom(deltaMinutes: Int, deltaSeconds: Int) {
        val totalSec = (customMinutes * 60 + customSeconds + deltaMinutes * 60 + deltaSeconds).coerceAtLeast(0)
        val m = totalSec / 60
        val s = totalSec % 60
        onCustomChange(m, s)
    }

    val explanationText = when (choice) {
        ReinterventionChoice.OFF -> stringResource(R.string.reintervention_explanation_off)
        ReinterventionChoice.MIN_1 -> stringResource(R.string.reintervention_explanation, "1m")
        ReinterventionChoice.MIN_3 -> stringResource(R.string.reintervention_explanation, "3m")
        ReinterventionChoice.MIN_5 -> stringResource(R.string.reintervention_explanation, "5m")
        ReinterventionChoice.MIN_10 -> stringResource(R.string.reintervention_explanation, "10m")
        ReinterventionChoice.CUSTOM -> {
            val durStr = if (customMinutes > 0 && customSeconds > 0) {
                "${customMinutes}m ${customSeconds}s"
            } else if (customMinutes > 0) {
                "${customMinutes}m"
            } else {
                "${customSeconds}s"
            }
            stringResource(R.string.reintervention_explanation, durStr)
        }
    }

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
                    text = stringResource(R.string.reintervention_label).uppercase(),
                    style = typography.labelSmall,
                    color = colors.textSecondary
                )
                if (onHelpClick != null) {
                    TerminalHelpCircle(
                        onClick = onHelpClick,
                        contentDescriptionText = stringResource(R.string.reintervention_label)
                    )
                }
            }

            // Preset choices row: OFF, 1m, 3m, 5m, 10m, CUSTOM
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                choices.forEach { (c, label) ->
                    val isSelected = (choice == c)
                    TerminalBadge(
                        text = label,
                        isActive = isSelected,
                        modifier = Modifier.clickable { onChoiceChange(c) }
                    )
                }
            }

            // Custom editor controls (only visible if CUSTOM chosen)
            if (choice == ReinterventionChoice.CUSTOM) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surfaceElevated, RoundedCornerShape(6.dp))
                        .border(1.dp, colors.border, RoundedCornerShape(6.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.reintervention_custom).uppercase(),
                            style = typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textSecondary
                        )

                        Text(
                            text = "%02d:%02d".format(customMinutes, customSeconds),
                            style = typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.accent
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CustomTimeColumn(
                            label = stringResource(R.string.unit_min).uppercase(),
                            value = customMinutes.toString(),
                            maxDigits = 3,
                            imeAction = ImeAction.Next,
                            onValueChange = { input ->
                                val m = input.filter(Char::isDigit).toIntOrNull() ?: 0
                                onCustomChange(m.coerceIn(0, 999), customSeconds)
                            },
                            decrementText = "-1M",
                            incrementText = "+1M",
                            onDecrement = { adjustCustom(-1, 0) },
                            onIncrement = { adjustCustom(1, 0) },
                            modifier = Modifier.weight(1f)
                        )

                        CustomTimeColumn(
                            label = stringResource(R.string.unit_sec).uppercase(),
                            value = customSeconds.toString(),
                            maxDigits = 2,
                            imeAction = ImeAction.Done,
                            onValueChange = { input ->
                                val s = input.filter(Char::isDigit).toIntOrNull() ?: 0
                                onCustomChange(customMinutes, s.coerceIn(0, 59))
                            },
                            decrementText = "-15S",
                            incrementText = "+15S",
                            onDecrement = { adjustCustom(0, -15) },
                            onIncrement = { adjustCustom(0, 15) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = stringResource(R.string.duration_label).uppercase(),
                        style = typography.labelSmall,
                        fontSize = 10.sp,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "30S" to (0 to 30),
                            "45S" to (0 to 45),
                            "2M" to (2 to 0),
                            "15M" to (15 to 0),
                            "30M" to (30 to 0)
                        ).forEach { (lbl, pair) ->
                            val isSelected = customMinutes == pair.first && customSeconds == pair.second
                            TerminalBadge(
                                text = lbl,
                                isActive = isSelected,
                                modifier = Modifier.clickable { onCustomChange(pair.first, pair.second) }
                            )
                        }
                    }
                }
            }

            // Dynamic explanation
            Text(
                text = explanationText,
                style = typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 4.dp)
            )
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
    decrementText: String,
    incrementText: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = WattimTheme.colors
    val typography = WattimTheme.typography
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var textInput by remember(value) { mutableStateOf(value) }

    Column(modifier = modifier) {
        Text(
            text = label,
            fontFamily = typography.bodyMedium.fontFamily,
            fontSize = 10.sp,
            color = colors.textSecondary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.border, RoundedCornerShape(4.dp))
                .background(colors.surface, RoundedCornerShape(4.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            if (textInput.isEmpty()) {
                Text(
                    text = "0",
                    fontFamily = typography.bodyMedium.fontFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textSecondary.copy(alpha = 0.4f)
                )
            }
            BasicTextField(
                value = textInput,
                onValueChange = { input ->
                    val filtered = input.filter(Char::isDigit).take(maxDigits)
                    textInput = filtered
                    onValueChange(filtered)
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
                    imeAction = imeAction
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    },
                    onNext = {
                        focusManager.moveFocus(FocusDirection.Next)
                    }
                ),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .onKeyEvent {
                        if (it.key == Key.Enter) {
                            focusManager.clearFocus()
                            keyboardController?.hide()
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
