package io.ronesec.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.FocusDirection
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
import io.ronesec.android.domain.model.TargetApp
import io.ronesec.android.domain.util.TimeFormatUtils
import io.ronesec.android.overlay.InterventionOverlayContent
import io.ronesec.android.ui.components.TerminalBadge
import io.ronesec.android.ui.components.TerminalButton
import io.ronesec.android.ui.components.TerminalCard
import io.ronesec.android.ui.components.TerminalInputField
import io.ronesec.android.ui.theme.LocalAppPalette
import io.ronesec.android.ui.theme.TerminalFontFamily
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetSettingsScreen(
    target: TargetApp,
    onSave: (TargetApp) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
    onStartHardBlock: ((String, Int, Set<String>) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val accent = palette.accent
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var enabled by remember { mutableStateOf(target.enabled) }
    var phrase by remember { mutableStateOf(target.intervention.phrase) }
    var durationSeconds by remember { mutableFloatStateOf(target.intervention.durationMs / 1000f) }
    var durationInput by remember {
        mutableStateOf((target.intervention.durationMs / 1000L).coerceIn(1L, 120L).toString())
    }
    var selectedAnimation by remember { mutableStateOf(target.intervention.animation) }
    var remindAgainMs by remember { mutableStateOf(target.intervention.reinterventionMs) }
    val standardReinterventionPresets = listOf(60_000L, 180_000L, 300_000L, 600_000L)
    var isCustomReintervention by remember {
        mutableStateOf(remindAgainMs != null && remindAgainMs !in standardReinterventionPresets)
    }

    val initialCustomMs = target.intervention.reinterventionMs ?: 120_000L
    var customMinutesInput by remember {
        mutableStateOf((initialCustomMs / 60_000L).toString())
    }
    var customSecondsInput by remember {
        mutableStateOf(((initialCustomMs % 60_000L) / 1000L).toString())
    }

    var quickReturnGraceSec by remember { mutableLongStateOf(target.intervention.quickReturnGraceMs / 1000L) }

    var showPreview by remember { mutableStateOf(false) }

    if (showPreview) {
        BasicAlertDialog(onDismissRequest = { showPreview = false }) {
            Box(modifier = Modifier.fillMaxSize()) {
                InterventionOverlayContent(
                    targetAppName = target.displayName,
                    config = target.intervention.copy(
                        phrase = phrase,
                        durationMs = (durationSeconds * 1000).toLong(),
                        animation = selectedAnimation
                    ),
                    savedTimeText = "Предпросмотр: вы сберегли 2 дня жизни",
                    onClose = { showPreview = false },
                    onContinue = { showPreview = false }
                )
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Back header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onBack() }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "← ${target.displayName.uppercase()}",
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                letterSpacing = 0.15.sp,
                color = accent
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Status Card
        TerminalCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "СТАТУС ЗАЩИТЫ",
                        fontFamily = TerminalFontFamily,
                        fontSize = 11.sp,
                        color = palette.textSecondary
                    )
                    Text(
                        text = if (enabled) "АКТИВЕН" else "ОТКЛЮЧЕН",
                        fontFamily = TerminalFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (enabled) accent else palette.textSecondary
                    )
                }

                TerminalBadge(
                    text = if (enabled) "ВКЛ" else "ВЫКЛ",
                    isActive = enabled,
                    modifier = Modifier.clickable { enabled = !enabled }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Phrase Editor
        TerminalCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "ФРАЗА ОСОЗНАННОСТИ",
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                color = palette.textSecondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            TerminalInputField(
                value = phrase,
                onValueChange = { phrase = it },
                maxLength = 80,
                maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                })
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Animation Choice & Duration Card
        TerminalCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "ТИП АНИМАЦИИ",
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
                        text = anim.displayName,
                        onClick = { selectedAnimation = anim },
                        isPrimary = isSelected,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "ДЛИТЕЛЬНОСТЬ ПАУЗЫ (ВДОХ И ВЫДОХ)",
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
                // Text input for exact seconds
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
                            durationInput = filtered
                            val s = filtered.toFloatOrNull() ?: 8f
                            durationSeconds = s.coerceIn(1f, 120f)
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
                                    durationInput = durationSeconds.toInt().toString()
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
                                        durationInput = durationSeconds.toInt().toString()
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
                    text = "СЕК",
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
                listOf(-5f to "-5с", -1f to "-1с", 1f to "+1с", 5f to "+5с").forEach { (delta, label) ->
                    TerminalButton(
                        text = label,
                        onClick = {
                            focusManager.clearFocus()
                            val cur = durationInput.filter { it.isDigit() }.toFloatOrNull() ?: 8f
                            val next = (cur + delta).coerceIn(1f, 120f)
                            durationInput = next.toInt().toString()
                            durationSeconds = next
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
                        text = "${sec}с",
                        isActive = isSelected,
                        modifier = Modifier.clickable {
                            durationInput = sec.toString()
                            durationSeconds = sec.toFloat()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            TerminalButton(
                text = "ПРЕДПРОСМОТР АНИМАЦИИ",
                onClick = { showPreview = true },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Remind Again (Re-intervention) Card
        TerminalCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "ПОВТОРНЫЙ ПЕРЕХВАТ",
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                color = palette.textSecondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val intervals = listOf(
                    "ВЫКЛ" to null,
                    "1м" to 60_000L,
                    "3м" to 180_000L,
                    "5м" to 300_000L,
                    "10м" to 600_000L
                )
                intervals.forEach { (lbl, ms) ->
                    val isSelected = !isCustomReintervention && remindAgainMs == ms
                    TerminalBadge(
                        text = lbl,
                        isActive = isSelected,
                        modifier = Modifier.clickable {
                            isCustomReintervention = false
                            remindAgainMs = ms
                        }
                    )
                }

                TerminalBadge(
                    text = "СВОЁ",
                    isActive = isCustomReintervention,
                    modifier = Modifier.clickable {
                        isCustomReintervention = true
                        val total = TimeFormatUtils.parseDuration(customMinutesInput, customSecondsInput, minMs = 5_000L)
                        remindAgainMs = total
                    }
                )
            }

            if (isCustomReintervention) {
                Spacer(modifier = Modifier.height(12.dp))

                val curMins = customMinutesInput.filter { it.isDigit() }.toLongOrNull() ?: 0L
                val curSecs = customSecondsInput.filter { it.isDigit() }.toLongOrNull() ?: 0L
                val currentEffectiveMs = curMins * 60_000L + curSecs * 1000L
                val displayTime = TimeFormatUtils.formatDurationRu(currentEffectiveMs)

                fun applyAdjustment(deltaMs: Long) {
                    focusManager.clearFocus()
                    val (newM, newS) = TimeFormatUtils.calculateAdjustedTime(
                        customMinutesInput,
                        customSecondsInput,
                        deltaMs
                    )
                    customMinutesInput = newM
                    customSecondsInput = newS
                    remindAgainMs = TimeFormatUtils.parseDuration(newM, newS)
                }

                fun applyPreset(presetMs: Long) {
                    focusManager.clearFocus()
                    val newM = (presetMs / 60_000L).toString()
                    val newS = ((presetMs % 60_000L) / 1000L).toString()
                    customMinutesInput = newM
                    customSecondsInput = newS
                    remindAgainMs = presetMs
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.surfaceElevated, RoundedCornerShape(6.dp))
                        .border(1.dp, palette.border, RoundedCornerShape(6.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "СВОЙ ИНТЕРВАЛ",
                            fontFamily = TerminalFontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textSecondary
                        )

                        Text(
                            text = displayTime,
                            fontFamily = TerminalFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = accent
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Minutes column
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "МИНУТЫ",
                                fontFamily = TerminalFontFamily,
                                fontSize = 10.sp,
                                color = palette.textSecondary,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, palette.border, RoundedCornerShape(4.dp))
                                    .background(palette.surface, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                if (customMinutesInput.isEmpty()) {
                                    Text(
                                        text = "0",
                                        fontFamily = TerminalFontFamily,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.textSecondary.copy(alpha = 0.4f)
                                    )
                                }
                                BasicTextField(
                                    value = customMinutesInput,
                                    onValueChange = { input ->
                                        val filtered = input.filter { it.isDigit() }.take(3)
                                        customMinutesInput = filtered
                                        val total = TimeFormatUtils.parseDuration(filtered, customSecondsInput)
                                        remindAgainMs = total
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
                                        imeAction = ImeAction.Next
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onNext = {
                                            focusManager.moveFocus(FocusDirection.Next)
                                        },
                                        onDone = {
                                            if (customMinutesInput.isBlank()) {
                                                customMinutesInput = "0"
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
                                                focusManager.moveFocus(FocusDirection.Next)
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
                                    text = "-1м",
                                    onClick = { applyAdjustment(-60_000L) },
                                    modifier = Modifier.weight(1f)
                                )
                                TerminalButton(
                                    text = "+1м",
                                    onClick = { applyAdjustment(60_000L) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // Seconds column
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "СЕКУНДЫ",
                                fontFamily = TerminalFontFamily,
                                fontSize = 10.sp,
                                color = palette.textSecondary,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, palette.border, RoundedCornerShape(4.dp))
                                    .background(palette.surface, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                if (customSecondsInput.isEmpty()) {
                                    Text(
                                        text = "0",
                                        fontFamily = TerminalFontFamily,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.textSecondary.copy(alpha = 0.4f)
                                    )
                                }
                                BasicTextField(
                                    value = customSecondsInput,
                                    onValueChange = { input ->
                                        val filtered = input.filter { it.isDigit() }.take(2)
                                        customSecondsInput = filtered
                                        val total = TimeFormatUtils.parseDuration(customMinutesInput, filtered)
                                        remindAgainMs = total
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
                                            if (customSecondsInput.isBlank()) {
                                                customSecondsInput = "0"
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
                                                if (customSecondsInput.isBlank()) {
                                                    customSecondsInput = "0"
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

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                TerminalButton(
                                    text = "-15с",
                                    onClick = { applyAdjustment(-15_000L) },
                                    modifier = Modifier.weight(1f)
                                )
                                TerminalButton(
                                    text = "+15с",
                                    onClick = { applyAdjustment(15_000L) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "БЫСТРЫЙ ВЫБОР",
                        fontFamily = TerminalFontFamily,
                        fontSize = 10.sp,
                        color = palette.textSecondary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "30с" to 30_000L,
                            "45с" to 45_000L,
                            "2м" to 120_000L,
                            "15м" to 900_000L,
                            "30м" to 1800_000L
                        ).forEach { (lbl, ms) ->
                            TerminalBadge(
                                text = lbl,
                                isActive = currentEffectiveMs == ms,
                                modifier = Modifier.clickable { applyPreset(ms) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val descText = if (remindAgainMs == null && !isCustomReintervention) {
                "Повторный вопрос во время непрерывной работы приложения отключен."
            } else {
                val effectiveMs = if (isCustomReintervention) {
                    val m = customMinutesInput.filter { it.isDigit() }.toLongOrNull() ?: 0L
                    val s = customSecondsInput.filter { it.isDigit() }.toLongOrNull() ?: 0L
                    m * 60_000L + s * 1000L
                } else {
                    remindAgainMs ?: 0L
                }
                val timeStr = TimeFormatUtils.formatDurationRu(effectiveMs)
                "Через $timeStr непрерывного использования появится экран с вопросом «Хотите продолжить?»."
            }

            Text(
                text = descText,
                fontFamily = TerminalFontFamily,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = palette.textSecondary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quick Return Grace Card
        TerminalCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "БЫСТРЫЙ ВОЗВРАТ (БЕЗ ПАУЗЫ)",
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                color = palette.textSecondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val graces = listOf(
                    0L to "0с",
                    15L to "15с",
                    30L to "30с",
                    60L to "1м",
                    120L to "2м",
                    300L to "5м"
                )
                graces.forEach { (sec, lbl) ->
                    val isSelected = quickReturnGraceSec == sec
                    TerminalBadge(
                        text = lbl,
                        isActive = isSelected,
                        modifier = Modifier.clickable { quickReturnGraceSec = sec }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (quickReturnGraceSec == 0L)
                    "0с — новая анимация будет показываться сразу при каждом выходе и повторном входе."
                else
                    "Если вернуться в приложение в течение $quickReturnGraceSec сек, пауза показываться не будет.",
                fontFamily = TerminalFontFamily,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = palette.textSecondary
            )
        }

        if (onStartHardBlock != null) {
            Spacer(modifier = Modifier.height(16.dp))

            TerminalCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "БЫСТРАЯ БЛОКИРОВКА ПРИЛОЖЕНИЯ",
                    fontFamily = TerminalFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = palette.textSecondary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Text(
                    text = "Заблокировать «${target.displayName}» прямо сейчас на выбранное время:",
                    fontFamily = TerminalFontFamily,
                    fontSize = 11.sp,
                    color = palette.textSecondary,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val durations = listOf(
                        "15м" to 15,
                        "30м" to 30,
                        "1ч" to 60,
                        "2ч" to 120
                    )
                    durations.forEach { (lbl, mins) ->
                        TerminalButton(
                            text = lbl,
                            onClick = {
                                onStartHardBlock("Фокус: ${target.displayName}", mins, setOf(target.packageName))
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Save Button
        TerminalButton(
            text = "СОХРАНИТЬ ИЗМЕНЕНИЯ",
            onClick = {
                focusManager.clearFocus()
                keyboardController?.hide()
                val finalReinterventionMs = if (!isCustomReintervention) {
                    remindAgainMs
                } else {
                    TimeFormatUtils.parseDuration(customMinutesInput, customSecondsInput, minMs = 5_000L)
                }
                val parsedDuration = durationInput.filter { it.isDigit() }.toLongOrNull()
                    ?: (durationSeconds.toLong().coerceAtLeast(1L))
                val finalDurationMs = parsedDuration.coerceIn(1L, 120L) * 1000L
                val updated = target.copy(
                    enabled = enabled,
                    intervention = target.intervention.copy(
                        phrase = phrase.trim().ifEmpty { "Сделайте глубокий вдох" },
                        durationMs = finalDurationMs,
                        animation = selectedAnimation,
                        reinterventionMs = finalReinterventionMs,
                        quickReturnGraceMs = quickReturnGraceSec * 1000L
                    )
                )
                onSave(updated)
                onBack()
            },
            isPrimary = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Delete Button
        TerminalButton(
            text = "УДАЛИТЬ ИЗ ЗАЩИТЫ",
            onClick = {
                onDelete(target.packageName)
                onBack()
            },
            isPrimary = false,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
