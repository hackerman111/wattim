package io.ronesec.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ronesec.android.ui.i18n.LocalAppStrings
import io.ronesec.android.ui.theme.LocalAppPalette
import io.ronesec.android.ui.theme.TerminalFontFamily

@Composable
fun TimeInputSection(
    title: String,
    hour: Int,
    minute: Int,
    onTimeChange: (h: Int, m: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val accent = palette.accent
    val strings = LocalAppStrings.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val formattedTime = String.format(java.util.Locale.US, "%02d:%02d", hour, minute)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.surfaceElevated, RoundedCornerShape(6.dp))
            .border(1.dp, palette.border, RoundedCornerShape(6.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = palette.textSecondary
            )
            Text(
                text = formattedTime,
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = accent
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Hours input
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = strings.hoursLabel,
                    fontFamily = TerminalFontFamily,
                    fontSize = 10.sp,
                    color = palette.textSecondary,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, palette.border, RoundedCornerShape(4.dp))
                        .background(palette.surface, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    BasicTextField(
                        value = String.format(java.util.Locale.US, "%02d", hour),
                        onValueChange = { input ->
                            val parsed = input.filter { it.isDigit() }.take(2).toIntOrNull()
                            if (parsed != null) {
                                onTimeChange(parsed.coerceIn(0, 23), minute)
                            }
                        },
                        textStyle = TextStyle(
                            fontFamily = TerminalFontFamily,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary,
                            textAlign = TextAlign.Center
                        ),
                        cursorBrush = SolidColor(accent),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Next) }
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Text(
                text = ":",
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = accent,
                modifier = Modifier.padding(top = 12.dp)
            )

            // Minutes input
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = strings.minutesLabel,
                    fontFamily = TerminalFontFamily,
                    fontSize = 10.sp,
                    color = palette.textSecondary,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, palette.border, RoundedCornerShape(4.dp))
                        .background(palette.surface, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    BasicTextField(
                        value = String.format(java.util.Locale.US, "%02d", minute),
                        onValueChange = { input ->
                            val parsed = input.filter { it.isDigit() }.take(2).toIntOrNull()
                            if (parsed != null) {
                                onTimeChange(hour, parsed.coerceIn(0, 59))
                            }
                        },
                        textStyle = TextStyle(
                            fontFamily = TerminalFontFamily,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary,
                            textAlign = TextAlign.Center
                        ),
                        cursorBrush = SolidColor(accent),
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
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        val hUnit = strings.hoursLabel.take(1).lowercase()
        val mUnit = strings.minutesUnit.take(1)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.weight(2f),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                TerminalButton(
                    text = "-1$hUnit",
                    onClick = {
                        focusManager.clearFocus()
                        val newH = (hour - 1).mod(24)
                        onTimeChange(newH, minute)
                    },
                    modifier = Modifier.weight(1f)
                )
                TerminalButton(
                    text = "+1$hUnit",
                    onClick = {
                        focusManager.clearFocus()
                        val newH = (hour + 1).mod(24)
                        onTimeChange(newH, minute)
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.weight(4f),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                TerminalButton(
                    text = "-5$mUnit",
                    onClick = {
                        focusManager.clearFocus()
                        val totalMins = hour * 60 + minute - 5
                        val normalized = totalMins.mod(1440)
                        onTimeChange(normalized / 60, normalized % 60)
                    },
                    modifier = Modifier.weight(1f)
                )
                TerminalButton(
                    text = "-1$mUnit",
                    onClick = {
                        focusManager.clearFocus()
                        val totalMins = hour * 60 + minute - 1
                        val normalized = totalMins.mod(1440)
                        onTimeChange(normalized / 60, normalized % 60)
                    },
                    modifier = Modifier.weight(1f)
                )
                TerminalButton(
                    text = "+1$mUnit",
                    onClick = {
                        focusManager.clearFocus()
                        val totalMins = hour * 60 + minute + 1
                        val normalized = totalMins.mod(1440)
                        onTimeChange(normalized / 60, normalized % 60)
                    },
                    modifier = Modifier.weight(1f)
                )
                TerminalButton(
                    text = "+5$mUnit",
                    onClick = {
                        focusManager.clearFocus()
                        val totalMins = hour * 60 + minute + 5
                        val normalized = totalMins.mod(1440)
                        onTimeChange(normalized / 60, normalized % 60)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
