package io.ronesec.android.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ronesec.android.R

@Composable
fun TimeInputSection(
    hours: Int,
    minutes: Int,
    onTimeChange: (newHours: Int, newMinutes: Int) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    title: String? = label,
    enabled: Boolean = true
) {
    val colors = WattimTheme.colors
    val typography = WattimTheme.typography
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val headerTitle = title ?: label
    val formattedTime = String.format(java.util.Locale.US, "%02d:%02d", hours, minutes)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceElevated, RoundedCornerShape(6.dp))
            .border(1.dp, colors.border, RoundedCornerShape(6.dp))
            .padding(10.dp)
    ) {
        if (headerTitle != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = headerTitle.uppercase(),
                    style = typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = colors.textSecondary
                )
                Text(
                    text = formattedTime,
                    style = typography.labelSmall.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = colors.accent
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Hours input
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.unit_hours),
                    style = typography.labelSmall.copy(fontSize = 10.sp),
                    color = colors.textSecondary,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 40.dp)
                        .border(1.dp, colors.border, RoundedCornerShape(4.dp))
                        .background(colors.surface, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .semantics { contentDescription = "Hours: %02d".format(hours) },
                    contentAlignment = Alignment.Center
                ) {
                    BasicTextField(
                        value = String.format(java.util.Locale.US, "%02d", hours),
                        onValueChange = { input ->
                            input.filter(Char::isDigit).take(2).toIntOrNull()?.let { parsed ->
                                onTimeChange(parsed.coerceIn(0, 23), minutes)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = enabled,
                        textStyle = typography.bodyMedium.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (enabled) colors.textPrimary else colors.textPrimary.copy(alpha = 0.4f),
                            textAlign = TextAlign.Center
                        ),
                        cursorBrush = SolidColor(colors.accent),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Next) }
                        ),
                        singleLine = true
                    )
                }
            }

            Text(
                text = ":",
                style = typography.displayLarge.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = colors.accent,
                modifier = Modifier.padding(top = 12.dp)
            )

            // Minutes input
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.unit_min),
                    style = typography.labelSmall.copy(fontSize = 10.sp),
                    color = colors.textSecondary,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 40.dp)
                        .border(1.dp, colors.border, RoundedCornerShape(4.dp))
                        .background(colors.surface, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .semantics { contentDescription = "Minutes: %02d".format(minutes) },
                    contentAlignment = Alignment.Center
                ) {
                    BasicTextField(
                        value = String.format(java.util.Locale.US, "%02d", minutes),
                        onValueChange = { input ->
                            input.filter(Char::isDigit).take(2).toIntOrNull()?.let { parsed ->
                                onTimeChange(hours, parsed.coerceIn(0, 59))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = enabled,
                        textStyle = typography.bodyMedium.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (enabled) colors.textPrimary else colors.textPrimary.copy(alpha = 0.4f),
                            textAlign = TextAlign.Center
                        ),
                        cursorBrush = SolidColor(colors.accent),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            }
                        ),
                        singleLine = true
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        fun adjustTime(deltaMinutes: Int) {
            if (!enabled) return
            val currentTotal = hours * 60 + minutes
            val newTotal = (currentTotal + deltaMinutes).mod(24 * 60)
            onTimeChange(newTotal / 60, newTotal % 60)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.weight(2f),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TimeStepButton("-1H", enabled, Modifier.weight(1f)) { adjustTime(-60) }
                TimeStepButton("+1H", enabled, Modifier.weight(1f)) { adjustTime(60) }
            }

            Row(
                modifier = Modifier.weight(4f),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TimeStepButton("-5M", enabled, Modifier.weight(1f)) { adjustTime(-5) }
                TimeStepButton("-1M", enabled, Modifier.weight(1f)) { adjustTime(-1) }
                TimeStepButton("+1M", enabled, Modifier.weight(1f)) { adjustTime(1) }
                TimeStepButton("+5M", enabled, Modifier.weight(1f)) { adjustTime(5) }
            }
        }
    }
}

@Composable
private fun TimeStepButton(
    text: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = WattimTheme.colors
    val typography = WattimTheme.typography

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .border(1.dp, colors.border, RoundedCornerShape(4.dp))
            .background(colors.surface, RoundedCornerShape(4.dp))
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 4.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = typography.button.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            ),
            color = if (enabled) colors.textPrimary else colors.textPrimary.copy(alpha = 0.40f)
        )
    }
}
