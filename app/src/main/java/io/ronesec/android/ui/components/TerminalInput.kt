package io.ronesec.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ronesec.android.ui.theme.LocalTerminalAccent
import io.ronesec.android.ui.theme.TerminalBorder
import io.ronesec.android.ui.theme.TerminalFontFamily
import io.ronesec.android.ui.theme.TerminalSurface
import io.ronesec.android.ui.theme.TerminalTextPrimary
import io.ronesec.android.ui.theme.TerminalTextSecondary

@Composable
fun TerminalInputField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    maxLength: Int = 80,
    maxLines: Int = 3
) {
    val accent = LocalTerminalAccent.current

    Column(modifier = modifier) {
        if (label != null) {
            Text(
                text = label.uppercase(),
                fontFamily = TerminalFontFamily,
                fontSize = 12.sp,
                color = TerminalTextSecondary,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, TerminalBorder, RoundedCornerShape(4.dp))
                .background(TerminalSurface, RoundedCornerShape(4.dp))
                .padding(12.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = { input ->
                    if (input.length <= maxLength) {
                        onValueChange(input)
                    }
                },
                textStyle = TextStyle(
                    fontFamily = TerminalFontFamily,
                    fontSize = 15.sp,
                    color = TerminalTextPrimary
                ),
                cursorBrush = SolidColor(accent),
                maxLines = maxLines,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Text(
            text = "${value.length} / $maxLength",
            fontFamily = TerminalFontFamily,
            fontSize = 11.sp,
            color = TerminalTextSecondary,
            modifier = Modifier
                .align(Alignment.End)
                .padding(top = 4.dp)
        )
    }
}
