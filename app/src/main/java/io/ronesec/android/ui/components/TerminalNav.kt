package io.ronesec.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ronesec.android.ui.theme.LocalAppPalette
import io.ronesec.android.ui.theme.TerminalFontFamily

enum class NavDestination(val label: String) {
    APPS("ФОКУС"),
    BLOCK("БЛОК"),
    STATS("СТАТИСТИКА"),
    CONFIG("НАСТРОЙКИ")
}

@Composable
fun TerminalBottomNav(
    currentDestination: NavDestination,
    onNavigate: (NavDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val accent = palette.accent

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.background)
            .border(width = 1.dp, color = palette.border)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavDestination.entries.forEach { destination ->
            val isSelected = destination == currentDestination

            Box(
                modifier = Modifier
                    .clickable { onNavigate(destination) }
                    .background(
                        if (isSelected) palette.surface else Color.Transparent,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .then(
                        if (isSelected) Modifier.border(1.dp, accent, RoundedCornerShape(4.dp))
                        else Modifier
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = destination.label,
                    fontFamily = TerminalFontFamily,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 12.sp,
                    letterSpacing = 0.1.sp,
                    color = if (isSelected) accent else palette.textSecondary
                )
            }
        }
    }
}
