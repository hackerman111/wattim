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
import io.ronesec.android.domain.model.AnimationType
import io.ronesec.android.ui.components.TerminalBadge
import io.ronesec.android.ui.components.TerminalButton
import io.ronesec.android.ui.components.TerminalCard
import io.ronesec.android.ui.i18n.LocalAppStrings
import io.ronesec.android.ui.theme.LocalAppPalette
import io.ronesec.android.ui.theme.TerminalFontFamily

@Composable
fun TargetAnimationAndDurationCard(
    selectedAnimation: AnimationType,
    onAnimationSelected: (AnimationType) -> Unit,
    durationSeconds: Float,
    durationInput: String,
    onDurationInputChange: (String, Float) -> Unit,
    onPreviewClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val strings = LocalAppStrings.current
    val accent = palette.accent
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    TerminalCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = strings.targetAnimationType,
            fontFamily = TerminalFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            color = palette.textSecondary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                AnimationType.FILL,
                AnimationType.PULSE,
                AnimationType.CIRCLE
            ).forEach { anim ->
                val isSelected = anim == selectedAnimation
                TerminalButton(
                    text = strings.animationName(anim),
                    onClick = { onAnimationSelected(anim) },
                    isPrimary = isSelected,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = strings.targetPauseDuration,
            fontFamily = TerminalFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            color = palette.textSecondary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, palette.border, RoundedCornerShape(4.dp))
                    .background(palette.surface, RoundedCornerShape(4.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                if (durationInput.isEmpty()) {
                    Text(
                        text = "8",
                        fontFamily = TerminalFontFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.textSecondary.copy(alpha = 0.4f)
                    )
                }
                BasicTextField(
                    value = durationInput,
                    onValueChange = { input ->
                        val filtered = input.filter { it.isDigit() }.take(3)
                        val s = filtered.toFloatOrNull() ?: 8f
                        val clamped = s.coerceIn(1f, 120f)
                        onDurationInputChange(filtered, clamped)
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
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (durationInput.isBlank()) {
                                onDurationInputChange(durationSeconds.toInt().toString(), durationSeconds)
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
                                    onDurationInputChange(durationSeconds.toInt().toString(), durationSeconds)
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
                text = if (strings.secondsUnitShort == "с") "СЕК" else "SEC",
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = accent
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Steppers row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                -5f to "-5${strings.secondsUnitShort}",
                -1f to "-1${strings.secondsUnitShort}",
                1f to "+1${strings.secondsUnitShort}",
                5f to "+5${strings.secondsUnitShort}"
            ).forEach { (delta, label) ->
                TerminalButton(
                    text = label,
                    onClick = {
                        focusManager.clearFocus()
                        val cur = durationInput.filter { it.isDigit() }.toFloatOrNull() ?: 8f
                        val next = (cur + delta).coerceIn(1f, 120f)
                        onDurationInputChange(next.toInt().toString(), next)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Quick presets
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(3, 5, 8, 10, 15, 20, 30).forEach { sec ->
                val isSelected = durationSeconds.toInt() == sec
                TerminalBadge(
                    text = "$sec${strings.secondsUnitShort}",
                    isActive = isSelected,
                    modifier = Modifier.clickable {
                        onDurationInputChange(sec.toString(), sec.toFloat())
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        TerminalButton(
            text = strings.targetPreviewAnimation,
            onClick = onPreviewClick,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
