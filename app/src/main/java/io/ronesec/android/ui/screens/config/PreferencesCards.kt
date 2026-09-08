package io.ronesec.android.ui.screens.config

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ronesec.android.ui.components.TerminalBadge
import io.ronesec.android.ui.components.TerminalButton
import io.ronesec.android.ui.components.TerminalCard
import io.ronesec.android.ui.i18n.AppLanguage
import io.ronesec.android.ui.i18n.LocalAppStrings
import io.ronesec.android.ui.theme.AppTheme
import io.ronesec.android.ui.theme.LocalAppPalette
import io.ronesec.android.ui.theme.TerminalFontFamily

@Composable
fun LanguageSelectionCard(
    currentLanguage: AppLanguage,
    onSelectLanguage: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val strings = LocalAppStrings.current

    TerminalCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = strings.languageSection,
            fontFamily = TerminalFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            letterSpacing = 0.1.sp,
            color = palette.textSecondary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppLanguage.entries.forEach { lang ->
                val isSelected = lang == currentLanguage
                TerminalButton(
                    text = when (lang) {
                        AppLanguage.SYSTEM -> "AUTO"
                        AppLanguage.EN -> "ENGLISH"
                        AppLanguage.RU -> "РУССКИЙ"
                    },
                    onClick = { onSelectLanguage(lang) },
                    isPrimary = isSelected,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun ThemeSelectionCard(
    currentTheme: AppTheme,
    onSelectTheme: (AppTheme) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val accent = palette.accent
    val strings = LocalAppStrings.current

    TerminalCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = strings.themeSection,
            fontFamily = TerminalFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            letterSpacing = 0.1.sp,
            color = palette.textSecondary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppTheme.entries.forEach { theme ->
                val isSelected = theme == currentTheme
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSelected) palette.surfaceElevated else palette.surface)
                        .border(
                            width = 1.dp,
                            color = if (isSelected) accent else palette.border,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .clickable { onSelectTheme(theme) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(theme.palette.background, CircleShape)
                                .border(1.dp, palette.border, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(theme.palette.accent, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = theme.displayName,
                            fontFamily = TerminalFontFamily,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp,
                            color = if (isSelected) accent else palette.textPrimary
                        )
                    }

                    if (isSelected) {
                        TerminalBadge(text = strings.activeBadge, isActive = true)
                    }
                }
            }
        }
    }
}

@Composable
fun SessionDurationCard(
    sessionMinutes: Int,
    onSelectSessionMinutes: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val strings = LocalAppStrings.current

    TerminalCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = strings.sessionDurationSection,
            fontFamily = TerminalFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            letterSpacing = 0.1.sp,
            color = palette.textSecondary,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Text(
            text = strings.sessionDurationDesc,
            fontFamily = TerminalFontFamily,
            fontSize = 11.sp,
            color = palette.textSecondary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(5, 7, 10, 15).forEach { mins ->
                val isSelected = mins == sessionMinutes
                TerminalButton(
                    text = "$mins ${strings.minutesUnit}",
                    onClick = { onSelectSessionMinutes(mins) },
                    isPrimary = isSelected,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun StatsOnOverlayCard(
    showSavedTimeStats: Boolean,
    onToggleShowSavedTimeStats: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val strings = LocalAppStrings.current

    TerminalCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = strings.statsOnOverlaySection,
                    fontFamily = TerminalFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    letterSpacing = 0.1.sp,
                    color = palette.textSecondary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = strings.statsOnOverlayDesc,
                    fontFamily = TerminalFontFamily,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = palette.textSecondary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TerminalBadge(
                    text = strings.onLabel,
                    isActive = showSavedTimeStats,
                    modifier = Modifier.clickable { onToggleShowSavedTimeStats(true) }
                )
                TerminalBadge(
                    text = strings.offLabel,
                    isActive = !showSavedTimeStats,
                    modifier = Modifier.clickable { onToggleShowSavedTimeStats(false) }
                )
            }
        }
    }
}
