package com.example.presentation.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.App
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberPurple
import com.example.ui.theme.NeonGreen
import com.example.vpn.SplitTunnelMode
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    snackbarHostState: SnackbarHostState,
    viewModel: SettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val appStats by viewModel.appStats.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (uiState.installedApps.isEmpty()) {
            viewModel.loadInstalledApps()
        }
    }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val trafficMap = remember(appStats) {
        appStats.associateBy { it.packageName }
    }

    val filteredApps = remember(uiState.installedApps, uiState.searchQuery) {
        if (uiState.searchQuery.isBlank()) {
            uiState.installedApps
        } else {
            uiState.installedApps.filter {
                it.appName.contains(uiState.searchQuery, ignoreCase = true) ||
                        it.packageName.contains(uiState.searchQuery, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            text = "Settings & Routing",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Split tunneling & network probe destinations",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Root Tunneling Settings Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.DeveloperMode, contentDescription = null, tint = CyberCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Root Tunnel Mode",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = if (uiState.isRootDeviceAvailable) "SU Access Available" else "No Root / SU Access detected",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (uiState.isRootDeviceAvailable) NeonGreen else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Switch(
                        checked = uiState.isRootModeEnabled,
                        onCheckedChange = { viewModel.setRootModeEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CyberCyan,
                            checkedTrackColor = CyberPurple
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Split Tunneling Mode Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.AltRoute, contentDescription = null, tint = CyberCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Split Tunneling Mode",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                SplitOptionRow(
                    title = "Route All Apps (Full Tunnel)",
                    selected = uiState.splitMode == SplitTunnelMode.ALL_THROUGH_VPN,
                    onClick = { viewModel.setSplitMode(SplitTunnelMode.ALL_THROUGH_VPN) }
                )

                SplitOptionRow(
                    title = "Only Selected Apps through VPN (Whitelist)",
                    selected = uiState.splitMode == SplitTunnelMode.ONLY_SELECTED_THROUGH_VPN,
                    onClick = { viewModel.setSplitMode(SplitTunnelMode.ONLY_SELECTED_THROUGH_VPN) }
                )

                SplitOptionRow(
                    title = "All Except Selected Apps (Bypass VPN)",
                    selected = uiState.splitMode == SplitTunnelMode.ALL_EXCEPT_SELECTED,
                    onClick = { viewModel.setSplitMode(SplitTunnelMode.ALL_EXCEPT_SELECTED) }
                )
            }
        }

        if (uiState.splitMode != SplitTunnelMode.ALL_THROUGH_VPN) {
            Spacer(modifier = Modifier.height(12.dp))

            // App Filter & Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("Search installed applications...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("app_search_field"),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = { viewModel.loadInstalledApps() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh Apps")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (uiState.isLoadingApps) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = CyberCyan)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        val isChecked = uiState.selectedPackages.contains(app.packageName)
                        val stat = trafficMap[app.packageName]
                        val isRouted = when (uiState.splitMode) {
                            SplitTunnelMode.ALL_THROUGH_VPN -> true
                            SplitTunnelMode.ONLY_SELECTED_THROUGH_VPN -> isChecked
                            SplitTunnelMode.ALL_EXCEPT_SELECTED -> !isChecked
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isRouted) CyberCyan.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = app.appName,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (isRouted) NeonGreen.copy(alpha = 0.15f) else CyberPurple.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = if (isRouted) "Config Tunnel" else "Direct ISP",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = if (isRouted) NeonGreen else CyberPurple,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = app.packageName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (stat != null && stat.totalBytes > 0) {
                                            Text(
                                                text = "• ${formatSettingsBytes(stat.totalBytes)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = NeonGreen
                                            )
                                        }
                                    }
                                }
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { viewModel.toggleApp(app.packageName) },
                                    colors = CheckboxDefaults.colors(checkedColor = CyberCyan)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.height(14.dp))
            // About & Protocol Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = CyberPurple)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AmneziaWG 2.0 Obfuscation Specs",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• S1–S4: Random dynamic prefixes for handshake packets\n" +
                                "• Jc, Jmin, Jmax: Junk packet padding to defeat Deep Packet Inspection (DPI)\n" +
                                "• H1–H4: Dynamic headers replacing standard WireGuard protocol signatures\n" +
                                "• Cloudflare WARP: X25519 automated client provisioning with reserved byte routing",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Diagnostic Logs & Device Details Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                val scope = androidx.compose.runtime.rememberCoroutineScope()

                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.BugReport, contentDescription = null, tint = CyberCyan)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Отладка и логирование туннеля",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Полный лог событий нативного движка WireGuard, handshake-пакетов, ошибок подключения и информации об устройстве.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            val fullLog = com.example.App.instance.tunnelManager.getFormattedFullLogText()
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(fullLog))
                            scope.launch {
                                snackbarHostState.showSnackbar("📋 Полный диагностический лог скопирован в буфер обмена!")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Скопировать полный отчет с логами", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun SplitOptionRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = CyberCyan)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) CyberCyan else MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatSettingsBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    val gb = mb / 1024.0
    return "%.2f GB".format(gb)
}
