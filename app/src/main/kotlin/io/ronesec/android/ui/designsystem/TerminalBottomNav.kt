package io.ronesec.android.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class TerminalTab(val defaultTitle: String) {
    APPS("APPS"),
    CODES("CODES"),
    BLOCK("BLOCK"),
    STATS("STATS"),
    CONFIG("CONFIG")
}

@Composable
fun TerminalBottomNav(
    selectedTab: TerminalTab,
    onTabSelected: (TerminalTab) -> Unit,
    modifier: Modifier = Modifier,
    tabLabels: Map<TerminalTab, String> = emptyMap()
) {
    val colors = WattimTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup()
            .background(colors.background)
            .border(width = 1.dp, color = colors.border)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (tab in TerminalTab.entries) {
            val isSelected = tab == selectedTab
            val label = tabLabels[tab] ?: tab.defaultTitle

            Box(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .selectable(
                        selected = isSelected,
                        role = Role.Tab,
                        onClick = { onTabSelected(tab) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            if (isSelected) colors.surface else Color.Transparent,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .then(
                            if (isSelected) Modifier.border(1.dp, colors.accent, RoundedCornerShape(4.dp))
                            else Modifier
                        )
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Medium,
                        fontSize = 12.sp,
                        letterSpacing = 0.1.sp,
                        color = if (isSelected) colors.accent else colors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
