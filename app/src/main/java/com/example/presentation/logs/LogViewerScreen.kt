package com.example.presentation.logs

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
    val liveReport by viewModel.liveDiagnosticReport.collectAsState()

    val activeReport = uiState.customLogAnalysisReport ?: liveReport

    val listState = rememberLazyListState()

    LaunchedEffect(filteredLogs.size, uiState.isAutoScrollEnabled) {
        if (uiState.isAutoScrollEnabled && filteredLogs.isNotEmpty() && uiState.activeTab == 1) {
            listState.animateScrollToItem(filteredLogs.size - 1)
        }
    }

    LaunchedEffect(uiState.userFeedbackMessage) {
        uiState.userFeedbackMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearFeedbackMessage()
        }
    }

    if (uiState.isPastedLogDialogOpen) {
        PasteLogDialog(
            onDismiss = { viewModel.setPastedLogDialogVisible(false) },
            onAnalyze = { text -> viewModel.analyzeCustomLogText(text) }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Консоль диагностики и логов",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        val statusText = when (vpnStatus.state) {
                            VpnState.CONNECTED -> "Подключено: ${vpnStatus.endpoint ?: "Protected"}"
                            VpnState.CONNECTING -> "Установление рукопожатия..."
                            else -> "Туннель отключен"
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
                    if (uiState.activeTab == 0) {
                        IconButton(
                            onClick = { viewModel.setPastedLogDialogVisible(true) },
                            modifier = Modifier.testTag("btn_paste_log")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = "Paste External Log",
                                tint = CyberCyan
                            )
                        }
                    }

                    if (uiState.activeTab == 1) {
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
                    }

                    IconButton(
                        onClick = {
                            val allText = if (uiState.activeTab == 0) {
                                buildString {
                                    appendLine("=== AWG MUTATOR / WIREGUARD DIAGNOSTIC CONSOLE ===")
                                    appendLine("Verdict: ${activeReport.summaryVerdict}")
                                    appendLine("Health: ${activeReport.overallHealthScore}%")
                                    appendLine("Endpoint: ${activeReport.targetEndpoint}")
                                    appendLine("Tx: ${activeReport.txBytes} B, Rx: ${activeReport.rxBytes} B")
                                    appendLine("Bottlenecks (${activeReport.bottlenecks.size}):")
                                    activeReport.bottlenecks.forEach { b ->
                                        appendLine("- [${b.severity}] ${b.title}: ${b.technicalDetails}")
                                        appendLine("  Fix: ${b.recommendedFix}")
                                    }
                                }
                            } else {
                                filteredLogs.joinToString("\n") { it.formattedDisplay }
                            }
                            clipboardManager.setText(AnnotatedString(allText))
                            Toast.makeText(context, "Отчет скопирован в буфер", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("btn_copy_logs")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Report"
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
            // Tab Switcher between Diagnostic Console and Raw Event Stream
            TabRow(
                selectedTabIndex = uiState.activeTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = CyberCyan,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[uiState.activeTab]),
                        color = CyberCyan
                    )
                }
            ) {
                Tab(
                    selected = uiState.activeTab == 0,
                    onClick = { viewModel.setActiveTab(0) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Troubleshoot, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Диагностика сбоев", fontWeight = FontWeight.Bold)
                        }
                    }
                )

                Tab(
                    selected = uiState.activeTab == 1,
                    onClick = { viewModel.setActiveTab(1) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Журнал логов (${stats.consolidatedCount})", fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (uiState.activeTab == 0) {
                // Tab 0: Diagnostic Console View
                DiagnosticConsoleView(
                    report = activeReport,
                    onApplyAction = { action, payload ->
                        viewModel.applyAction(action, payload)
                    },
                    onOpenPasteDialog = { viewModel.setPastedLogDialogVisible(true) },
                    onClearCustomReport = { viewModel.clearCustomLogReport() },
                    isCustomReport = uiState.customLogAnalysisReport != null
                )
            } else {
                // Tab 1: Raw / Filtered Log Stream
                LogStatsSummaryCard(stats = stats)

                Spacer(modifier = Modifier.height(8.dp))

                // Search input field
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_log_search"),
                    placeholder = { Text("Поиск по логам, IP или тегу...") },
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

                // Severity Filter Chips Row
                LogFilterChipsRow(
                    selectedSeverity = uiState.selectedSeverity,
                    onSelectSeverity = { viewModel.setSeverityFilter(it) }
                )

                Spacer(modifier = Modifier.height(8.dp))

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
                                text = if (uiState.searchQuery.isNotBlank()) "Нет записей по запросу" else "Логи еще не записаны",
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
                                    Toast.makeText(context, "Строка скопирована", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }
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
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatBadge(label = "Всего событий", count = stats.consolidatedCount, color = MaterialTheme.colorScheme.onSurface)
            StatBadge(label = "Ошибки", count = stats.errorCount, color = DangerRed)
            StatBadge(label = "Варнинги", count = stats.warningCount, color = WarningAmber)
            StatBadge(label = "Anti-DPI", count = stats.antiDpiCount, color = CyberCyan)
        }
    }
}

@Composable
private fun StatBadge(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$count",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace
            ),
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

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
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = selectedSeverity == null,
            onClick = { onSelectSeverity(null) },
            label = { Text("Все") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = CyberCyan.copy(alpha = 0.2f),
                selectedLabelColor = CyberCyan
            )
        )

        FilterChip(
            selected = selectedSeverity == LogSeverity.ERROR,
            onClick = { onSelectSeverity(LogSeverity.ERROR) },
            label = { Text("Ошибки") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = DangerRed.copy(alpha = 0.2f),
                selectedLabelColor = DangerRed
            )
        )

        FilterChip(
            selected = selectedSeverity == LogSeverity.WARN,
            onClick = { onSelectSeverity(LogSeverity.WARN) },
            label = { Text("Варнинги") },
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
            selected = selectedSeverity == LogSeverity.STATS,
            onClick = { onSelectSeverity(LogSeverity.STATS) },
            label = { Text("Трафик") },
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
    val (severityColor, severityBg) = when (item.severity) {
        LogSeverity.ERROR -> Pair(DangerRed, DangerRed.copy(alpha = 0.12f))
        LogSeverity.WARN -> Pair(WarningAmber, WarningAmber.copy(alpha = 0.12f))
        LogSeverity.ANTI_DPI -> Pair(CyberPurple, CyberPurple.copy(alpha = 0.12f))
        LogSeverity.STATS -> Pair(NeonGreen, NeonGreen.copy(alpha = 0.08f))
        LogSeverity.SUCCESS -> Pair(NeonGreen, NeonGreen.copy(alpha = 0.15f))
        LogSeverity.ROUTING -> Pair(CyberCyan, CyberCyan.copy(alpha = 0.10f))
        LogSeverity.DNS -> Pair(CyberCyan, CyberCyan.copy(alpha = 0.10f))
        LogSeverity.MASQUE -> Pair(CyberPurple, CyberPurple.copy(alpha = 0.10f))
        LogSeverity.INFO -> Pair(CyberCyan, CyberCyan.copy(alpha = 0.08f))
        LogSeverity.DEBUG -> Pair(MaterialTheme.colorScheme.onSurfaceVariant, Color.Transparent)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { onToggleExpand() }
            .testTag("log_item_${item.id}"),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        border = BorderStroke(1.dp, severityColor.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = severityBg,
                        border = BorderStroke(1.dp, severityColor.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = item.tag,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            ),
                            color = severityColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (item.count > 1) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = CircleShape,
                            color = CyberCyan.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, CyberCyan)
                        ) {
                            Text(
                                text = "×${item.count}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 9.sp
                                ),
                                color = CyberCyan,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                Text(
                    text = item.timestamp,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = item.message,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (isExpanded) Int.MAX_VALUE else 3
            )

            if (isExpanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onCopyItem,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Скопировать строку", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
