package io.ronesec.android.ui.intervention

import android.animation.ValueAnimator
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ronesec.android.R
import io.ronesec.android.ui.designsystem.BreathingCanvas
import io.ronesec.android.ui.designsystem.TerminalButton
import io.ronesec.android.ui.designsystem.TerminalButtonVariant
import io.ronesec.android.ui.designsystem.WattimColors
import io.ronesec.android.ui.designsystem.WattimTheme
import io.ronesec.domain.breathing.BreathingProgress
import io.ronesec.domain.policy.EffectiveInterventionConfig
import io.ronesec.domain.protection.AttentionCheckUi
import kotlin.math.max

@Composable
fun AttentionCheckContent(
    config: EffectiveInterventionConfig,
    pausedProgress: BreathingProgress,
    attentionCheck: AttentionCheckUi,
    onSubmit: (String) -> Unit,
    onExit: () -> Unit,
    onEmergency: () -> Unit,
    nowElapsedMs: Long,
    modifier: Modifier = Modifier
) {
    val colors = WattimTheme.colors
    val typography = WattimTheme.typography

    val remainingMs = max(0L, attentionCheck.deadlineElapsedMs - nowElapsedMs)
    val remainingFraction = if (attentionCheck.timeoutMs > 0L) {
        (remainingMs.toFloat() / attentionCheck.timeoutMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    var input by remember(attentionCheck.code) { mutableStateOf("") }
    LaunchedEffect(attentionCheck.hasError) {
        if (attentionCheck.hasError) {
            input = ""
        }
    }

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
            keyboardController?.show()
        } catch (_: Exception) {}
    }

    val targetTitle = config.displayName.ifBlank { config.packageName }.uppercase()
    val formattedCode = attentionCheck.code.toCharArray().joinToString(" ")

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // Paused breathing animation background
        BreathingCanvas(
            style = config.animation,
            progress = pausedProgress.p.toFloat(),
            elapsedMs = pausedProgress.elapsedMs,
            reducedMotion = !ValueAnimator.areAnimatorsEnabled(),
            modifier = Modifier.fillMaxSize()
        )

        // Semi-transparent scrim to preserve legibility and contrast
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(WattimColors.EmergencyScrim)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .displayCutoutPadding()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text(
                    text = targetTitle,
                    style = typography.titleLarge,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.attention_check_prompt_header),
                    style = typography.titleMedium,
                    color = colors.accent,
                    textAlign = TextAlign.Center
                )
            }

            // Center card
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .background(colors.surface, RoundedCornerShape(8.dp))
                    .border(1.dp, if (attentionCheck.hasError) colors.error else colors.border, RoundedCornerShape(8.dp))
                    .padding(20.dp)
            ) {
                Text(
                    text = stringResource(R.string.attention_check_instruction),
                    style = typography.bodyMedium,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center
                )

                // Code Display
                Text(
                    text = formattedCode,
                    style = typography.displayLarge,
                    color = colors.accent,
                    textAlign = TextAlign.Center,
                    letterSpacing = 4.sp
                )

                // Linear Timer Progress Bar
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .background(colors.surfaceElevated, RoundedCornerShape(3.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(remainingFraction)
                                .fillMaxHeight()
                                .background(
                                    if (remainingMs < 2000L || attentionCheck.hasError) colors.error else colors.accent,
                                    RoundedCornerShape(3.dp)
                                )
                        )
                    }
                    Text(
                        text = stringResource(R.string.attention_check_time_remaining, remainingMs / 1000f),
                        style = typography.labelSmall,
                        color = if (remainingMs < 2000L) colors.error else colors.textSecondary,
                        modifier = Modifier.align(Alignment.End)
                    )
                }

                // Digit Input
                val expectedLength = attentionCheck.code.length
                val instructionText = stringResource(R.string.attention_check_instruction)
                BasicTextField(
                    value = input,
                    onValueChange = { newVal ->
                        val digits = newVal.filter { it in '0'..'9' }.take(expectedLength)
                        input = digits
                        if (digits.length == expectedLength) {
                            onSubmit(digits)
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    textStyle = typography.titleMedium.copy(color = Color.Transparent),
                    cursorBrush = SolidColor(Color.Transparent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .focusRequester(focusRequester)
                        .semantics { contentDescription = instructionText },
                    decorationBox = { innerTextField ->
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            innerTextField()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                repeat(expectedLength) { index ->
                                    val char = if (index < input.length) input[index].toString() else "_"
                                    Text(
                                        text = char,
                                        style = typography.titleLarge,
                                        color = if (attentionCheck.hasError) colors.error else colors.accent
                                    )
                                }
                            }
                        }
                    }
                )

                if (attentionCheck.hasError) {
                    Text(
                        text = stringResource(R.string.attention_check_error),
                        style = typography.bodyMedium,
                        color = colors.error,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Bottom Actions: Exit and Emergency
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 400.dp)
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TerminalButton(
                    text = stringResource(R.string.action_exit),
                    onClick = onExit,
                    modifier = Modifier.weight(1f),
                    variant = TerminalButtonVariant.SECONDARY
                )

                TerminalButton(
                    text = stringResource(R.string.action_emergency),
                    onClick = onEmergency,
                    modifier = Modifier.weight(1f),
                    variant = TerminalButtonVariant.SECONDARY
                )
            }
        }
    }
}
