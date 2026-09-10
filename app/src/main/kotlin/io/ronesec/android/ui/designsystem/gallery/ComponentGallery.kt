package io.ronesec.android.ui.designsystem.gallery

import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.ronesec.android.ui.designsystem.BreathingCanvas
import io.ronesec.android.ui.designsystem.TerminalBadge
import io.ronesec.android.ui.designsystem.TerminalBottomNav
import io.ronesec.android.ui.designsystem.TerminalButton
import io.ronesec.android.ui.designsystem.TerminalButtonVariant
import io.ronesec.android.ui.designsystem.TerminalCard
import io.ronesec.android.ui.designsystem.TerminalInputField
import io.ronesec.android.ui.designsystem.TerminalTab
import io.ronesec.android.ui.designsystem.ThemeId
import io.ronesec.android.ui.designsystem.TimeInputSection
import io.ronesec.android.ui.designsystem.WattimTheme
import io.ronesec.domain.model.AnimationMode

@Composable
internal fun ComponentGallery(
    selectedTheme: ThemeId = ThemeId.NORD,
    modifier: Modifier = Modifier
) {
    WattimTheme(themeId = selectedTheme) {
        val colors = WattimTheme.colors
        val typography = WattimTheme.typography
        val dimensions = WattimTheme.dimensions

        var textInput by remember { mutableStateOf("Custom phrase text") }
        var currentTab by remember { mutableStateOf(TerminalTab.APPS) }
        var badgeActive by remember { mutableStateOf(true) }
        var hours by remember { mutableStateOf(9) }
        var minutes by remember { mutableStateOf(30) }

        Column(
            modifier = modifier
                .fillMaxSize()
                .background(colors.background)
                .verticalScroll(rememberScrollState())
                .padding(dimensions.space16),
            verticalArrangement = Arrangement.spacedBy(dimensions.space16)
        ) {
            Text(
                text = "WATTIM COMPONENT GALLERY - ${selectedTheme.displayName.uppercase()}",
                style = typography.titleLarge,
                color = colors.accent
            )

            // Typography Showcase
            TerminalCard {
                Column(verticalArrangement = Arrangement.spacedBy(dimensions.space8)) {
                    Text(text = "DISPLAY LARGE (32SP BOLD)", style = typography.displayLarge, color = colors.textPrimary)
                    Text(text = "TITLE LARGE (20SP BOLD)", style = typography.titleLarge, color = colors.textPrimary)
                    Text(text = "TITLE MEDIUM (14SP SEMIBOLD)", style = typography.titleMedium, color = colors.textPrimary)
                    Text(text = "BODY LARGE (16SP REGULAR)", style = typography.bodyLarge, color = colors.textPrimary)
                    Text(text = "BODY MEDIUM (14SP REGULAR)", style = typography.bodyMedium, color = colors.textSecondary)
                    Text(text = "LABEL SMALL (12SP REGULAR)", style = typography.labelSmall, color = colors.textSecondary)
                    Text(text = "PHRASE SCALE (22SP REGULAR)", style = typography.phrase, color = colors.accent)
                }
            }

            // Cards Showcase
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(dimensions.space8)) {
                TerminalCard(modifier = Modifier.weight(1f)) {
                    Text("Normal Card", style = typography.bodyMedium, color = colors.textPrimary)
                }
                TerminalCard(isError = true, modifier = Modifier.weight(1f)) {
                    Text("Error Card", style = typography.bodyMedium, color = colors.error)
                }
            }

            // Buttons Showcase
            Column(verticalArrangement = Arrangement.spacedBy(dimensions.space8)) {
                Text(text = "BUTTONS", style = typography.labelSmall, color = colors.textSecondary)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(dimensions.space8)) {
                    TerminalButton(text = "Primary", onClick = {}, variant = TerminalButtonVariant.PRIMARY, modifier = Modifier.weight(1f))
                    TerminalButton(text = "Secondary", onClick = {}, variant = TerminalButtonVariant.SECONDARY, modifier = Modifier.weight(1f))
                    TerminalButton(text = "Danger", onClick = {}, variant = TerminalButtonVariant.DANGER, modifier = Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(dimensions.space8)) {
                    TerminalButton(text = "Disabled", onClick = {}, enabled = false, modifier = Modifier.weight(1f))
                    TerminalButton(text = "Loading", onClick = {}, isLoading = true, modifier = Modifier.weight(1f))
                }
            }

            // Badges Showcase
            Column(verticalArrangement = Arrangement.spacedBy(dimensions.space8)) {
                Text(text = "BADGES", style = typography.labelSmall, color = colors.textSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(dimensions.space8)) {
                    TerminalBadge(text = if (badgeActive) "ACTIVE" else "OFF", isActive = badgeActive, onClick = { badgeActive = !badgeActive })
                    TerminalBadge(text = "ON", isActive = true)
                    TerminalBadge(text = "OFF", isActive = false)
                }
            }

            // Input Fields Showcase
            Column(verticalArrangement = Arrangement.spacedBy(dimensions.space8)) {
                Text(text = "INPUT FIELDS", style = typography.labelSmall, color = colors.textSecondary)
                TerminalInputField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    label = "CUSTOM PHRASE",
                    maxLength = 80
                )
                TerminalInputField(
                    value = "Invalid input",
                    onValueChange = {},
                    label = "ERROR STATE",
                    isError = true,
                    errorMessage = "Duration must be between 1 and 120 seconds"
                )
            }

            // Time Input Section
            Column(verticalArrangement = Arrangement.spacedBy(dimensions.space8)) {
                Text(text = "TIME INPUT SECTION", style = typography.labelSmall, color = colors.textSecondary)
                TimeInputSection(
                    hours = hours,
                    minutes = minutes,
                    onTimeChange = { h, m -> hours = h; minutes = m },
                    label = "SCHEDULE TIME"
                )
            }

            // Navigation Bar
            Column(verticalArrangement = Arrangement.spacedBy(dimensions.space8)) {
                Text(text = "BOTTOM NAVIGATION", style = typography.labelSmall, color = colors.textSecondary)
                TerminalBottomNav(
                    selectedTab = currentTab,
                    onTabSelected = { currentTab = it }
                )
            }

            // Animation Modes Showcase
            Column(verticalArrangement = Arrangement.spacedBy(dimensions.space8)) {
                Text(text = "BREATHING ANIMATIONS (FILL, PULSE, CIRCLE)", style = typography.labelSmall, color = colors.textSecondary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(dimensions.space8)
                ) {
                    Box(modifier = Modifier.weight(1f).height(120.dp).background(colors.surface)) {
                        BreathingCanvas(style = AnimationMode.FILL, progress = 0.7f, elapsedMs = 3000L)
                    }
                    Box(modifier = Modifier.weight(1f).height(120.dp).background(colors.surface)) {
                        BreathingCanvas(style = AnimationMode.PULSE, progress = 0.7f, elapsedMs = 3000L)
                    }
                    Box(modifier = Modifier.weight(1f).height(120.dp).background(colors.surface)) {
                        BreathingCanvas(style = AnimationMode.CIRCLE, progress = 0.7f, elapsedMs = 3000L)
                    }
                }
            }

            Spacer(modifier = Modifier.height(dimensions.space24))
        }
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 1000)
@Composable
private fun ComponentGalleryCyberPreview() {
    ComponentGallery(selectedTheme = ThemeId.CYBER_TERMINAL)
}

@Preview(showBackground = true, widthDp = 380, heightDp = 1000)
@Composable
private fun ComponentGalleryNordPreview() {
    ComponentGallery(selectedTheme = ThemeId.NORD)
}

@Preview(showBackground = true, widthDp = 380, heightDp = 1000)
@Composable
private fun ComponentGalleryCatppuccinPreview() {
    ComponentGallery(selectedTheme = ThemeId.CATPPUCCIN)
}

@Preview(showBackground = true, widthDp = 380, heightDp = 1000)
@Composable
private fun ComponentGalleryDraculaPreview() {
    ComponentGallery(selectedTheme = ThemeId.DRACULA)
}

@Preview(showBackground = true, widthDp = 380, heightDp = 1000)
@Composable
private fun ComponentGalleryGruvboxPreview() {
    ComponentGallery(selectedTheme = ThemeId.GRUVBOX)
}

@Preview(showBackground = true, widthDp = 380, heightDp = 1000)
@Composable
private fun ComponentGalleryTokyoNightPreview() {
    ComponentGallery(selectedTheme = ThemeId.TOKYO_NIGHT)
}
