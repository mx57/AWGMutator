package com.example.presentation.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.App
import com.example.domain.model.VpnState
import com.example.domain.model.VpnStatus
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberPurple
import com.example.ui.theme.DangerRed
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.WarningAmber
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Diagnostic Card verifying VPN Service Lifecycle, Routing Table Modifications,
 * and live packet translation logs (TCP/UDP/DNS/ICMP flow).
 */
@Composable
fun PacketDiagnosticsCard(
    vpnStatus: VpnStatus,
    tunnelLogs: List<String>,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var isCopied by remember { mutableStateOf(false) }

    val isConnected = vpnStatus.state == VpnState.CONNECTED

    LaunchedEffect(tunnelLogs.size) {
        if (tunnelLogs.isNotEmpty()) {
            listState.animateScrollToItem(tunnelLogs.size - 1)
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            DiagnosticsHeader(
                isConnected = isConnected,
                isExpanded = isExpanded,
                onToggleExpanded = { isExpanded = !isExpanded }
            )

            Spacer(modifier = Modifier.height(10.dp))

            RoutingStatusSection(isConnected = isConnected)

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    LiveLogsHeader(
                        logCount = tunnelLogs.size,
                        isCopied = isCopied,
                        onCopyLogs = {
                            val fullLog = App.instance.tunnelManager.getFormattedFullLogText()
                            clipboardManager.setText(AnnotatedString(fullLog))
                            isCopied = true
                            scope.launch {
                                delay(2500)
                                isCopied = false
                            }
                        },
                        onClearLogs = onClearLogs
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    LiveLogsViewer(
                        tunnelLogs = tunnelLogs,
                        listState = listState
                    )
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsHeader(
    isConnected: Boolean,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = CyberPurple.copy(alpha = 0.15f)
            ) {
                Icon(
                    imageVector = Icons.Default.AltRoute,
                    contentDescription = null,
                    tint = CyberPurple,
                    modifier = Modifier.padding(6.dp).size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "🛠️ Tunnel Routing & Diagnostic Logs",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isConnected) "Active Routing Modified • 0.0.0.0/0 -> TUN" else "TUN Interface Standby",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isConnected) NeonGreen else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        IconButton(
            onClick = onToggleExpanded,
            modifier = Modifier.size(32.dp).testTag("toggle_packet_diagnostics")
        ) {
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = "Expand Diagnostics",
                tint = CyberPurple
            )
        }
    }
}

@Composable
private fun RoutingStatusSection(
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        RoutingStatusBadge(
            title = "IPv4 Route",
            value = if (isConnected) "0.0.0.0/0 (TUN)" else "Default ISP",
            isSuccess = isConnected,
            modifier = Modifier.weight(1f)
        )
        RoutingStatusBadge(
            title = "Socket Protect",
            value = if (isConnected) "Bypass OK" else "Idle",
            isSuccess = isConnected,
            modifier = Modifier.weight(1f)
        )
        RoutingStatusBadge(
            title = "DNS Server",
            value = if (isConnected) "1.1.1.1 (Secure)" else "ISP DNS",
            isSuccess = isConnected,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun LiveLogsHeader(
    logCount: Int,
    isCopied: Boolean,
    onCopyLogs: () -> Unit,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Terminal, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Live Engine Logs ($logCount)",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FilledTonalButton(
                onClick = onCopyLogs,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (isCopied) NeonGreen.copy(alpha = 0.2f) else CyberCyan.copy(alpha = 0.15f),
                    contentColor = if (isCopied) NeonGreen else CyberCyan
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .height(30.dp)
                    .testTag("copy_tunnel_logs_btn")
            ) {
                Icon(
                    imageVector = if (isCopied) Icons.Default.CheckCircle else Icons.Default.ContentCopy,
                    contentDescription = "Копировать",
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isCopied) "Скопировано!" else "Копировать",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp)
                )
            }

            IconButton(
                onClick = onClearLogs,
                modifier = Modifier.size(30.dp).testTag("clear_tunnel_logs_btn")
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Clear Logs",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun LiveLogsViewer(
    tunnelLogs: List<String>,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF0F141C),
        border = BorderStroke(1.dp, Color(0xFF232D3F)),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp, max = 280.dp)
    ) {
        if (tunnelLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No events logged yet. Connect tunnel to view detailed system logs and handshake progress.",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                    color = Color.Gray
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                items(tunnelLogs) { logLine ->
                    LogLine(logLine = logLine)
                }
            }
        }
    }
}

@Composable
private fun LogLine(
    logLine: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = logLine,
        style = MaterialTheme.typography.labelSmall.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            lineHeight = 14.sp
        ),
        color = getLogColor(logLine),
        modifier = modifier.padding(vertical = 1.dp)
    )
}

private fun getLogColor(logLine: String): Color = when {
    logLine.contains("[TUN_ERROR]") || logLine.contains("[VPN_ERROR]") || logLine.contains("[PACKET_TCP_ERR]") -> DangerRed
    logLine.contains("[TUN_WARN]") || logLine.contains("[PACKET_DNS]") -> WarningAmber
    logLine.contains("[TUN_LIFECYCLE]") || logLine.contains("[WG_STATE]") || logLine.contains("[DEVICE_INFO]") -> CyberPurple
    logLine.contains("[TUN_CONF]") -> CyberCyan
    logLine.contains("[TUN_TRAFFIC]") || logLine.contains("[VPN_VERIFY]") -> NeonGreen
    else -> Color(0xFFD1D5DB)
}

@Composable
private fun RoutingStatusBadge(
    title: String,
    value: String,
    isSuccess: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (isSuccess) NeonGreen.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                ),
                color = if (isSuccess) NeonGreen else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
