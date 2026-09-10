package io.ronesec.android.ui.config

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ronesec.android.R
import io.ronesec.android.ui.designsystem.TerminalBadge
import io.ronesec.android.ui.designsystem.TerminalCard
import io.ronesec.android.ui.designsystem.ThemeId
import io.ronesec.android.ui.designsystem.ThemeRegistry
import io.ronesec.android.ui.designsystem.WattimColors
import io.ronesec.android.ui.designsystem.WattimTheme

/**
 * F73 Theme selector: 6 rows with background+accent swatches, name, selected frame and Active badge.
 */
@Composable
fun ThemeSelectorCard(
    selectedTheme: ThemeId,
    onSelectTheme: (ThemeId) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = WattimTheme.colors
    val typography = WattimTheme.typography

    TerminalCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.config_theme_title),
            fontFamily = typography.bodyMedium.fontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            letterSpacing = 0.1.sp,
            color = colors.textSecondary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeId.entries.forEach { themeId ->
                val themeColors = ThemeRegistry.getColors(themeId)
                val isSelected = themeId == selectedTheme

                ThemeRow(
                    themeId = themeId,
                    themeColors = themeColors,
                    isSelected = isSelected,
                    onClick = { onSelectTheme(themeId) }
                )
            }
        }
    }
}

@Composable
private fun ThemeRow(
    themeId: ThemeId,
    themeColors: WattimColors,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentThemeColors = WattimTheme.colors
    val typography = WattimTheme.typography

    val borderColor = if (isSelected) currentThemeColors.accent else currentThemeColors.border
    val shape = RoundedCornerShape(4.dp)
    val bgColor = if (isSelected) currentThemeColors.surfaceElevated else currentThemeColors.surface

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .background(bgColor, shape)
            .border(BorderStroke(1.dp, borderColor), shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Swatches: background circle and accent circle
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(themeColors.background, CircleShape)
                    .border(1.dp, currentThemeColors.border, CircleShape)
            )
            Spacer(modifier = Modifier.padding(start = 6.dp))
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(themeColors.accent, CircleShape)
            )
            Spacer(modifier = Modifier.padding(start = 12.dp))

            Text(
                text = themeId.displayName.uppercase(),
                fontFamily = typography.bodyMedium.fontFamily,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 13.sp,
                color = if (isSelected) currentThemeColors.accent else currentThemeColors.textPrimary
            )
        }

        if (isSelected) {
            TerminalBadge(
                text = stringResource(R.string.status_active),
                isActive = true
            )
        }
    }
}
