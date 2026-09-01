package com.example.presentation.logs

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.domain.model.LogSeverity
import com.example.domain.model.TunnelLogItem
import com.example.domain.model.VpnState
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberPurple
import com.example.ui.theme.DangerRed
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.WarningAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(
    viewModel: LogViewerViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val vpnStatus by viewModel.vpnStatus.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val filteredLogs by viewModel.filteredLogs.collectAsState()
    val stats by viewModel.statistics.collectAsState()

    val listState = rememberLazyListState()

    LaunchedEffect(filteredLogs.size, uiState.isAutoScrollEnabled) {
        if (uiState.isAutoScrollEnabled && filteredLogs.isNotEmpty()) {
            listState.animateScrollToItem(filteredLogs.size - 1)
        }
    }

    LaunchedEffect(uiState.userFeedbackMessage) {
        uiState.userFeedbackMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearFeedbackMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Tunnel Diagnostics & Logs",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        val statusText = when (vpnStatus.state) {
                            VpnState.CONNECTED -> "Active Tunnel • Egress: ${vpnStatus.egressIp ?: "Protected"}"
                            VpnState.CONNECTING -> "Handshake / Connecting..."
                            else -> "Tunnel Inactive"
                        }
                        val statusColor = when (vpnStatus.state) {
                            VpnState.CONNECTED -> NeonGreen
                            VpnState.CONNECTING -> WarningAmber
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = statusColor
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.toggleAutoScroll() },
                        modifier = Modifier.testTag("btn_toggle_autoscroll")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = "Toggle Auto Scroll",
                            tint = if (uiState.isAutoScrollEnabled) CyberCyan else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = {
                            val allText = filteredLogs.joinToString("\n") { it.formattedDisplay }
                            clipboardManager.setText(AnnotatedString(allText))
                            Toast.makeText(context, "Copied ${filteredLogs.size} logs", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("btn_copy_logs")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Filtered Logs"
                        )
                    }

                    IconButton(
                        onClick = { viewModel.shareLogs(context) },
                        modifier = Modifier.testTag("btn_share_logs")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share Full Report"
                        )
                    }

                    IconButton(
                        onClick = { viewModel.clearLogs() },
                        modifier = Modifier.testTag("btn_clear_logs")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear Logs",
                            tint = DangerRed
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp)
        ) {
            // Stats summary card
            LogStatsSummaryCard(stats = stats)

            Spacer(modifier = Modifier.height(10.dp))

            // Search input field
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_log_search"),
                placeholder = { Text("Filter logs by keyword, IP, or tag...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear Search")
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyberCyan,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Severity & Category Filter Chips Row
            LogFilterChipsRow(
                selectedSeverity = uiState.selectedSeverity,
                onSelectSeverity = { viewModel.setSeverityFilter(it) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Log entries LazyColumn with color-coded severity & merged repeats
            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (uiState.searchQuery.isNotBlank()) "No logs match query" else "No logs recorded yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("list_tunnel_logs"),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(
                        items = filteredLogs,
                        key = { it.id }
                    ) { item ->
                        val isExpanded = uiState.expandedLogId == item.id
                        LogItemCard(
                            item = item,
                            isExpanded = isExpanded,
                            onToggleExpand = { viewModel.toggleExpandLog(item.id) },
                            onCopyItem = {
                                clipboardManager.setText(AnnotatedString(item.formattedDisplay))
                                Toast.makeText(context, "Log line copied", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
private fun LogStatsSummaryCard(
    stats: LogStatistics,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Consolidated Logs",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${stats.consolidatedCount} entries (${stats.totalRawCount} raw events)",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (stats.errorCount > 0) {
                    BadgeCounter(count = stats.errorCount, label = "Errors", color = DangerRed)
                }
                if (stats.warningCount > 0) {
                    BadgeCounter(count = stats.warningCount, label = "Warn", color = WarningAmber)
                }
                if (stats.antiDpiCount > 0) {
                    BadgeCounter(count = stats.antiDpiCount, label = "Anti-DPI", color = CyberPurple)
                }
            }
        }
    }
}

@Composable
private fun BadgeCounter(count: Int, label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(color, CircleShape)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "$count $label",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                color = color
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogFilterChipsRow(
    selectedSeverity: LogSeverity?,
    onSelectSeverity: (LogSeverity?) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FilterChip(
            selected = selectedSeverity == null,
            onClick = { onSelectSeverity(null) },
            label = { Text("All") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = CyberCyan.copy(alpha = 0.2f),
                selectedLabelColor = CyberCyan
            )
        )
        FilterChip(
            selected = selectedSeverity == LogSeverity.ERROR,
            onClick = { onSelectSeverity(LogSeverity.ERROR) },
            label = { Text("Errors") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = DangerRed.copy(alpha = 0.2f),
                selectedLabelColor = DangerRed
            )
        )
        FilterChip(
            selected = selectedSeverity == LogSeverity.WARN,
            onClick = { onSelectSeverity(LogSeverity.WARN) },
            label = { Text("Warnings") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = WarningAmber.copy(alpha = 0.2f),
                selectedLabelColor = WarningAmber
            )
        )
        FilterChip(
            selected = selectedSeverity == LogSeverity.ANTI_DPI,
            onClick = { onSelectSeverity(LogSeverity.ANTI_DPI) },
            label = { Text("Anti-DPI") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = CyberPurple.copy(alpha = 0.2f),
                selectedLabelColor = CyberPurple
            )
        )
        FilterChip(
            selected = selectedSeverity == LogSeverity.MASQUE,
            onClick = { onSelectSeverity(LogSeverity.MASQUE) },
            label = { Text("MASQUE") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = Color(0xFFFFB300).copy(alpha = 0.2f),
                selectedLabelColor = Color(0xFFFFB300)
            )
        )
        FilterChip(
            selected = selectedSeverity == LogSeverity.DNS,
            onClick = { onSelectSeverity(LogSeverity.DNS) },
            label = { Text("DNS / DoH") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = CyberCyan.copy(alpha = 0.2f),
                selectedLabelColor = CyberCyan
            )
        )
        FilterChip(
            selected = selectedSeverity == LogSeverity.ROUTING,
            onClick = { onSelectSeverity(LogSeverity.ROUTING) },
            label = { Text("Routing") }
        )
        FilterChip(
            selected = selectedSeverity == LogSeverity.SUCCESS,
            onClick = { onSelectSeverity(LogSeverity.SUCCESS) },
            label = { Text("Success") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = NeonGreen.copy(alpha = 0.2f),
                selectedLabelColor = NeonGreen
            )
        )
    }
}

@Composable
private fun LogItemCard(
    item: TunnelLogItem,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onCopyItem: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (badgeBg, badgeFg, borderCol) = when (item.severity) {
        LogSeverity.ERROR -> Triple(DangerRed.copy(alpha = 0.15f), DangerRed, DangerRed.copy(alpha = 0.4f))
        LogSeverity.WARN -> Triple(WarningAmber.copy(alpha = 0.15f), WarningAmber, WarningAmber.copy(alpha = 0.4f))
        LogSeverity.SUCCESS -> Triple(NeonGreen.copy(alpha = 0.15f), NeonGreen, NeonGreen.copy(alpha = 0.4f))
        LogSeverity.ANTI_DPI -> Triple(CyberPurple.copy(alpha = 0.15f), CyberPurple, CyberPurple.copy(alpha = 0.4f))
        LogSeverity.MASQUE -> Triple(Color(0xFFFFB300).copy(alpha = 0.15f), Color(0xFFFFB300), Color(0xFFFFB300).copy(alpha = 0.4f))
        LogSeverity.DNS -> Triple(CyberCyan.copy(alpha = 0.15f), CyberCyan, CyberCyan.copy(alpha = 0.4f))
        LogSeverity.ROUTING -> Triple(Color(0xFF388E3C).copy(alpha = 0.15f), Color(0xFF81C784), Color(0xFF388E3C).copy(alpha = 0.3f))
        LogSeverity.STATS -> Triple(Color(0xFF455A64).copy(alpha = 0.15f), Color(0xFF90A4AE), Color(0xFF455A64).copy(alpha = 0.3f))
        else -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.outlineVariant)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { onToggleExpand() }
            .testTag("log_item_${item.id}"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, borderCol)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Severity / Tag Chip
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = badgeBg,
                        border = BorderStroke(1.dp, borderCol)
                    ) {
                        Text(
                            text = item.tag,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = badgeFg,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Timestamp
                    Text(
                        text = item.lastTimestamp,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }

                // Repeat Badge if merged
                if (item.count > 1) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CyberCyan.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, CyberCyan)
                    ) {
                        Text(
                            text = "×${item.count}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = CyberCyan,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Main Message
            Text(
                text = item.message,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            // Expanded details & quick copy
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    if (item.count > 1) {
                        Text(
                            text = "First seen: ${item.firstTimestamp} • Repeated ${item.count} times (last: ${item.lastTimestamp})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { onCopyItem() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy Line",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Copy Log",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
