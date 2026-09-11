package io.ronesec.android.ui.intervention

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.ronesec.android.ui.designsystem.WattimTheme

@Composable
fun DigitCodeInput(value: String, length: Int, onValueChange: (String) -> Unit, label: String) {
    val colors = WattimTheme.colors
    Column {
        Text(label, style = WattimTheme.typography.bodyMedium, color = colors.textSecondary)
        BasicTextField(
            value = value,
            onValueChange = { input -> onValueChange(input.filter { it in '0'..'9' }.take(length)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            textStyle = WattimTheme.typography.titleMedium.copy(color = Color.Transparent),
            cursorBrush = SolidColor(Color.Transparent),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = label },
            decorationBox = { innerTextField ->
                Box {
                    innerTextField()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        repeat(length) { index ->
                            Text(
                                if (index < value.length) "●" else "_",
                                color = colors.accent,
                                style = WattimTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        )
    }
}
