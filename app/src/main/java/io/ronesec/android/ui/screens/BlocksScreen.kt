package io.ronesec.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ronesec.android.domain.model.BlockSchedule
import io.ronesec.android.domain.model.BlockSession
import io.ronesec.android.domain.model.TargetApp
import io.ronesec.android.ui.components.TerminalBadge
import io.ronesec.android.ui.components.TerminalButton
import io.ronesec.android.ui.components.TerminalCard
import io.ronesec.android.ui.theme.LocalTerminalAccent
import io.ronesec.android.ui.theme.TerminalBackground
import io.ronesec.android.ui.theme.TerminalFontFamily
import io.ronesec.android.ui.theme.TerminalTextPrimary
import io.ronesec.android.ui.theme.TerminalTextSecondary
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.util.Locale

@Composable
fun BlocksScreen(
    activeSessions: List<BlockSession>,
    schedules: List<BlockSchedule>,
    targets: List<TargetApp>,
    onStartHardBlock: (String, Int, Set<String>) -> Unit,
    onStopHardBlock: (Long) -> Unit,
    onSaveSchedule: (BlockSchedule) -> Unit,
    onDeleteSchedule: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalTerminalAccent.current
    val activeSession = activeSessions.firstOrNull { it.active && it.endTime.isAfter(Instant.now()) }

    var remainingSeconds by remember(activeSession) {
        val rem = if (activeSession != null) Duration.between(Instant.now(), activeSession.endTime).seconds else 0L
        mutableLongStateOf(rem.coerceAtLeast(0L))
    }

    LaunchedEffect(activeSession) {
        while (activeSession != null && remainingSeconds > 0) {
            delay(1000L)
            remainingSeconds = Duration.between(Instant.now(), activeSession.endTime).seconds.coerceAtLeast(0L)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalBackground)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "HARD BLOCK",
            fontFamily = TerminalFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            letterSpacing = 0.15.sp,
            color = accent
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (activeSession != null && remainingSeconds > 0) {
            // Active session card
            TerminalCard(modifier = Modifier.fillMaxWidth()) {
                val hours = remainingSeconds / 3600
                val mins = (remainingSeconds % 3600) / 60
                val secs = remainingSeconds % 60
                val timeStr = String.format(Locale.US, "%02d:%02d:%02d", hours, mins, secs)

                Text(
                    text = "ACTIVE FOCUS SESSION",
                    fontFamily = TerminalFontFamily,
                    fontSize = 12.sp,
                    letterSpacing = 0.1.sp,
                    color = accent
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = timeStr,
                    fontFamily = TerminalFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp,
                    color = TerminalTextPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Blocking ${activeSession.packages.size} apps",
                    fontFamily = TerminalFontFamily,
                    fontSize = 12.sp,
                    color = TerminalTextSecondary
                )

                Spacer(modifier = Modifier.height(12.dp))

                TerminalButton(
                    text = "STOP SESSION",
                    onClick = { onStopHardBlock(activeSession.id) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            // Quick launcher
            TerminalCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "START FOCUS SESSION",
                    fontFamily = TerminalFontFamily,
                    fontSize = 12.sp,
                    color = TerminalTextSecondary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                val allTargetPackages = targets.map { it.packageName }.toSet()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val durations = listOf(
                        "15m" to 15,
                        "30m" to 30,
                        "1h" to 60,
                        "2h" to 120
                    )
                    durations.forEach { (lbl, mins) ->
                        TerminalButton(
                            text = lbl,
                            onClick = {
                                onStartHardBlock("FOCUS SESSION", mins, allTargetPackages)
                            },
                            isPrimary = (mins == 30),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Scheduled Blocks
        Text(
            text = "SCHEDULED BLOCKS",
            fontFamily = TerminalFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            letterSpacing = 0.15.sp,
            color = accent
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (schedules.isEmpty()) {
            TerminalCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "NO SCHEDULED BLOCKS",
                    fontFamily = TerminalFontFamily,
                    fontSize = 13.sp,
                    color = TerminalTextSecondary
                )
            }
        } else {
            schedules.forEach { schedule ->
                TerminalCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = schedule.name.uppercase(),
                                fontFamily = TerminalFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = TerminalTextPrimary
                            )
                            Text(
                                text = "${schedule.days.joinToString(", ") { it.name.take(3) }} · ${schedule.start} → ${schedule.end}",
                                fontFamily = TerminalFontFamily,
                                fontSize = 12.sp,
                                color = TerminalTextSecondary
                            )
                        }

                        TerminalBadge(
                            text = if (schedule.enabled) "ACTIVE" else "OFF",
                            isActive = schedule.enabled,
                            modifier = Modifier.clickable {
                                onSaveSchedule(schedule.copy(enabled = !schedule.enabled))
                            }
                        )
                    }
                }
            }
        }
    }
}
