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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ronesec.android.domain.model.AnimationType
import io.ronesec.android.domain.model.TargetApp
import io.ronesec.android.overlay.InterventionOverlayContent
import io.ronesec.android.ui.components.TerminalBadge
import io.ronesec.android.ui.components.TerminalButton
import io.ronesec.android.ui.components.TerminalCard
import io.ronesec.android.ui.components.TerminalInputField
import io.ronesec.android.ui.theme.LocalTerminalAccent
import io.ronesec.android.ui.theme.TerminalBackground
import io.ronesec.android.ui.theme.TerminalError
import io.ronesec.android.ui.theme.TerminalFontFamily
import io.ronesec.android.ui.theme.TerminalTextPrimary
import io.ronesec.android.ui.theme.TerminalTextSecondary
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetSettingsScreen(
    target: TargetApp,
    onSave: (TargetApp) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalTerminalAccent.current

    var enabled by remember { mutableStateOf(target.enabled) }
    var phrase by remember { mutableStateOf(target.intervention.phrase) }
    var durationSeconds by remember { mutableFloatStateOf(target.intervention.durationMs / 1000f) }
    var remindAgainMs by remember { mutableStateOf(target.intervention.reinterventionMs) }
    var quickReturnGraceSec by remember { mutableLongStateOf(target.intervention.quickReturnGraceMs / 1000L) }

    var showPreview by remember { mutableStateOf(false) }

    if (showPreview) {
        BasicAlertDialog(onDismissRequest = { showPreview = false }) {
            Box(modifier = Modifier.fillMaxSize()) {
                InterventionOverlayContent(
                    targetAppName = target.displayName,
                    config = target.intervention.copy(
                        phrase = phrase,
                        durationMs = (durationSeconds * 1000).toLong()
                    ),
                    onClose = { showPreview = false },
                    onContinue = { showPreview = false }
                )
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalBackground)
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
                text = "< ${target.displayName.uppercase()}",
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                letterSpacing = 0.15.sp,
                color = accent
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Status Card
        TerminalCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "STATUS",
                        fontFamily = TerminalFontFamily,
                        fontSize = 11.sp,
                        color = TerminalTextSecondary
                    )
                    Text(
                        text = if (enabled) "ACTIVE" else "DISABLED",
                        fontFamily = TerminalFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (enabled) accent else TerminalTextSecondary
                    )
                }

                TerminalBadge(
                    text = if (enabled) "●" else "○",
                    isActive = enabled,
                    modifier = Modifier.clickable { enabled = !enabled }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Phrase Editor
        TerminalCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "PHRASE",
                fontFamily = TerminalFontFamily,
                fontSize = 11.sp,
                color = TerminalTextSecondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            TerminalInputField(
                value = phrase,
                onValueChange = { phrase = it },
                maxLength = 80,
                maxLines = 3
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Animation & Duration Card
        TerminalCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "ANIMATION",
                fontFamily = TerminalFontFamily,
                fontSize = 11.sp,
                color = TerminalTextSecondary
            )

            Text(
                text = "> FILL",
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = accent,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "DURATION",
                fontFamily = TerminalFontFamily,
                fontSize = 11.sp,
                color = TerminalTextSecondary
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = String.format(Locale.US, "%04.1f SEC", durationSeconds),
                    fontFamily = TerminalFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = TerminalTextPrimary
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TerminalButton(
                        text = "-2s",
                        onClick = { durationSeconds = (durationSeconds - 2f).coerceAtLeast(4f) }
                    )
                    TerminalButton(
                        text = "+2s",
                        onClick = { durationSeconds = (durationSeconds + 2f).coerceAtMost(20f) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            TerminalButton(
                text = "PREVIEW ANIMATION",
                onClick = { showPreview = true },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Remind Again (Re-intervention) Card
        TerminalCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "REMIND AGAIN (RE-INTERVENTION)",
                fontFamily = TerminalFontFamily,
                fontSize = 11.sp,
                color = TerminalTextSecondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val intervals = listOf(
                    "OFF" to null,
                    "1m" to 60_000L,
                    "3m" to 180_000L,
                    "5m" to 300_000L,
                    "10m" to 600_000L
                )
                intervals.forEach { (lbl, ms) ->
                    val isSelected = remindAgainMs == ms
                    TerminalBadge(
                        text = lbl,
                        isActive = isSelected,
                        modifier = Modifier.clickable { remindAgainMs = ms }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quick Return Grace Card
        TerminalCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "QUICK RETURN GRACE",
                fontFamily = TerminalFontFamily,
                fontSize = 11.sp,
                color = TerminalTextSecondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val graces = listOf(30L, 60L, 120L, 300L)
                graces.forEach { sec ->
                    val isSelected = quickReturnGraceSec == sec
                    TerminalBadge(
                        text = "${sec}s",
                        isActive = isSelected,
                        modifier = Modifier.clickable { quickReturnGraceSec = sec }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Save Button
        TerminalButton(
            text = "SAVE CHANGES",
            onClick = {
                val updated = target.copy(
                    enabled = enabled,
                    intervention = target.intervention.copy(
                        phrase = phrase.trim().ifEmpty { "Сделайте глубокий вдох" },
                        durationMs = (durationSeconds * 1000).toLong(),
                        reinterventionMs = remindAgainMs,
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
            text = "REMOVE FROM PROTECTION",
            onClick = {
                onDelete(target.packageName)
                onBack()
            },
            isPrimary = false,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
