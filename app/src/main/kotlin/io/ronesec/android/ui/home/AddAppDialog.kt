package io.ronesec.android.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.ronesec.android.R
import io.ronesec.android.platform.system.PackageAppEntry
import io.ronesec.android.ui.designsystem.TerminalButton
import io.ronesec.android.ui.designsystem.TerminalButtonVariant
import io.ronesec.android.ui.designsystem.TerminalInputField
import io.ronesec.android.ui.designsystem.WattimTheme

@Composable
fun AddAppDialog(
    state: AddAppDialogState,
    onQueryChange: (String) -> Unit,
    onSelectApp: (PackageAppEntry) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!state.isOpen) return

    val colors = WattimTheme.colors
    val dimensions = WattimTheme.dimensions
    val typography = WattimTheme.typography
    val shape = RoundedCornerShape(4.dp)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.80f)
                .border(1.dp, colors.border, shape)
                .background(colors.surface, shape)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Dialog Title
                Text(
                    text = stringResource(R.string.add_app_dialog_title).uppercase(),
                    fontFamily = typography.bodyMedium.fontFamily,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    fontSize = 14.sp,
                    letterSpacing = 0.15.sp,
                    color = colors.accent
                )

                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))

                // Live search input
                TerminalInputField(
                    value = state.searchQuery,
                    onValueChange = onQueryChange,
                    placeholder = stringResource(R.string.search_apps_placeholder),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))

                // Content Area: Loading / Error / Empty / Results
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        state.isLoading -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(dimensions.space8)
                            ) {
                                CircularProgressIndicator(color = colors.accent)
                                Text(
                                    text = stringResource(R.string.catalog_loading),
                                    style = typography.bodyMedium,
                                    color = colors.textSecondary
                                )
                            }
                        }

                        state.errorMessage != null -> {
                            Text(
                                text = state.errorMessage,
                                style = typography.bodyMedium,
                                color = colors.error
                            )
                        }

                        state.allCandidates.isEmpty() -> {
                            Text(
                                text = stringResource(R.string.catalog_empty),
                                style = typography.bodyMedium,
                                color = colors.textSecondary
                            )
                        }

                        state.filteredApps.isEmpty() -> {
                            Text(
                                text = stringResource(R.string.catalog_no_results),
                                style = typography.bodyMedium,
                                color = colors.textSecondary
                            )
                        }

                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(
                                    items = state.filteredApps,
                                    key = { it.packageName }
                                ) { entry ->
                                    AppCandidateRow(
                                        entry = entry,
                                        enabled = !state.isAdding,
                                        onClick = { onSelectApp(entry) }
                                    )
                                }
                            }
                        }
                    }
                }

                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))

                TerminalButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = onDismiss,
                    variant = TerminalButtonVariant.SECONDARY,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun AppCandidateRow(
    entry: PackageAppEntry,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = WattimTheme.colors
    val dimensions = WattimTheme.dimensions
    val typography = WattimTheme.typography

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = dimensions.minTouchTarget)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.label,
                fontFamily = typography.bodyMedium.fontFamily,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                fontSize = 14.sp,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = entry.packageName,
                fontFamily = typography.bodyMedium.fontFamily,
                fontSize = 11.sp,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = "+ ${stringResource(R.string.action_add_app).uppercase()}",
            fontFamily = typography.bodyMedium.fontFamily,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            fontSize = 12.sp,
            color = colors.accent
        )
    }
}
