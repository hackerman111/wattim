package io.ronesec.android.ui.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TerminalInputField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    isError: Boolean = false,
    errorMessage: String? = null,
    maxLength: Int? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 3,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    val colors = WattimTheme.colors
    val dimensions = WattimTheme.dimensions
    val typography = WattimTheme.typography

    val borderColor = when {
        isError -> colors.error
        !enabled -> colors.border.copy(alpha = 0.50f)
        else -> colors.border
    }

    val textColor = if (enabled) colors.textPrimary else colors.textPrimary.copy(alpha = 0.40f)
    val shape = RoundedCornerShape(4.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isError && errorMessage != null) {
                    Modifier.semantics { error(errorMessage) }
                } else Modifier
            )
    ) {
        if (label != null) {
            Text(
                text = label.uppercase(),
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontSize = 12.sp,
                color = colors.textSecondary,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(BorderStroke(1.dp, borderColor), shape)
                .background(colors.surface, shape)
                .padding(12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = value,
                onValueChange = { newValue ->
                    if (maxLength == null || newValue.length <= maxLength) {
                        onValueChange(newValue)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontSize = 15.sp,
                    color = textColor
                ),
                cursorBrush = SolidColor(colors.accent),
                singleLine = singleLine,
                maxLines = maxLines,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                interactionSource = interactionSource,
                decorationBox = { innerTextField ->
                    if (value.isEmpty() && placeholder != null) {
                        Text(
                            text = placeholder,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 15.sp,
                            color = colors.textSecondary
                        )
                    }
                    innerTextField()
                }
            )
        }

        if (errorMessage != null || maxLength != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (isError && errorMessage != null) {
                    Text(
                        text = errorMessage,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = colors.error,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                } else {
                    Box(modifier = Modifier.weight(1f, fill = false))
                }

                if (maxLength != null) {
                    Text(
                        text = "${value.length} / $maxLength",
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = if (isError) colors.error else colors.textSecondary
                    )
                }
            }
        }
    }
}
