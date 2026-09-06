package io.ronesec.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ronesec.android.ui.components.TerminalButton
import io.ronesec.android.ui.components.TerminalInputField
import io.ronesec.android.ui.theme.LocalTerminalAccent
import io.ronesec.android.ui.theme.TerminalBackground
import io.ronesec.android.ui.theme.TerminalBorder
import io.ronesec.android.ui.theme.TerminalFontFamily
import io.ronesec.android.ui.theme.TerminalSurface
import io.ronesec.android.ui.theme.TerminalTextPrimary
import io.ronesec.android.ui.theme.TerminalTextSecondary
import io.ronesec.android.ui.viewmodel.InstalledAppInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAppDialog(
    installedApps: List<InstalledAppInfo>,
    alreadyProtectedPackages: Set<String>,
    onSelectApp: (InstalledAppInfo) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val accent = LocalTerminalAccent.current

    val filtered = remember(searchQuery, installedApps, alreadyProtectedPackages) {
        installedApps.filter { app ->
            !alreadyProtectedPackages.contains(app.packageName) &&
                    (app.label.contains(searchQuery, ignoreCase = true) ||
                            app.packageName.contains(searchQuery, ignoreCase = true))
        }
    }

    BasicAlertDialog(
        onDismissRequest = onDismiss
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(4.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, TerminalBorder),
            color = TerminalSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "SELECT APP TO PROTECT",
                    fontFamily = TerminalFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    letterSpacing = 0.15.sp,
                    color = accent
                )

                Spacer(modifier = Modifier.height(12.dp))

                TerminalInputField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = "SEARCH APPS",
                    maxLength = 40,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    items(filtered, key = { it.packageName }) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectApp(app) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.label,
                                    fontFamily = TerminalFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = TerminalTextPrimary
                                )
                                Text(
                                    text = app.packageName,
                                    fontFamily = TerminalFontFamily,
                                    fontSize = 11.sp,
                                    color = TerminalTextSecondary
                                )
                            }

                            Text(
                                text = "[ ADD ]",
                                fontFamily = TerminalFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = accent
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                TerminalButton(
                    text = "CANCEL",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
