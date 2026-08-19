package com.example.presentation.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.AppConnectionStatus
import com.example.domain.model.AppTrafficStat
import com.example.domain.model.VpnState
import com.example.domain.model.VpnStatus
import com.example.vpn.AppTrafficTracker
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DangerRed
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.WarningAmber

/**
 * Interactive card displaying per-application traffic telemetry passing strictly through the active VPN tunnel.
 * If the tunnel is not connected or no data passes through it, no apps are shown.
 * Features a toggle to completely turn traffic monitoring on/off.
 */
@Composable
fun AppTrafficDashboardCard(
    vpnStatus: VpnStatus,
    appStats: List<AppTrafficStat>,
    isMonitoringEnabled: Boolean,
    onToggleMonitoring: (Boolean) -> Unit,
    onOpenSplitTunnelSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    val isVpnConnected = vpnStatus.state == VpnState.CONNECTED
    val displayApps = if (isExpanded) appStats.take(20) else appStats.take(5)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Icon, Title, Switch & Expand/Collapse
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isMonitoringEnabled && isVpnConnected) CyberCyan.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                    ) {
                        Icon(
                            imageVector = Icons.Default.DataUsage,
                            contentDescription = null,
                            tint = if (isMonitoringEnabled && isVpnConnected) CyberCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(6.dp).size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "📊 Трафик приложений в туннеле",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (!isMonitoringEnabled) {
                                "Мониторинг выключен"
                            } else if (!isVpnConnected) {
                                "VPN отключен • Ожидание подключения"
                            } else if (appStats.isEmpty()) {
                                "Трафик через туннель: 0 KB"
                            } else {
                                "${appStats.size} прилож. активны через туннель"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = isMonitoringEnabled,
                        onCheckedChange = onToggleMonitoring,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = CyberCyan,
                            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier.testTag("toggle_traffic_monitoring_switch")
                    )

                    if (isMonitoringEnabled && appStats.isNotEmpty()) {
                        IconButton(
                            onClick = { isExpanded = !isExpanded },
                            modifier = Modifier.size(32.dp).testTag("toggle_app_traffic_expand")
                        ) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = "Развернуть",
                                tint = CyberCyan
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (!isMonitoringEnabled) {
                // Card when monitoring is disabled
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Мониторинг трафика выключен для экономии заряда батареи.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { onToggleMonitoring(true) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
                        ) {
                            Text("Включить", color = Color.Black, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            } else {
                // Check usage stats permission
                val context = LocalContext.current
                var hasPermission by remember { mutableStateOf(AppTrafficTracker.hasUsageStatsPermission(context)) }

                androidx.compose.runtime.LaunchedEffect(Unit) {
                    hasPermission = AppTrafficTracker.hasUsageStatsPermission(context)
                }

                if (!hasPermission) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = WarningAmber.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Доступ к статистике использования",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = WarningAmber
                                )
                                Text(
                                    text = "Предоставьте доступ для точного подсчета трафика приложений через туннель.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    AppTrafficTracker.openUsageAccessSettings(context)
                                    hasPermission = AppTrafficTracker.hasUsageStatsPermission(context)
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = WarningAmber)
                            ) {
                                Text("Разрешить", color = Color.Black, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }

                if (!isVpnConnected) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "VPN-туннель не активен. Статистика приложений отображается только при активном соединении и наличии трафика через туннель.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else if (appStats.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Через VPN-туннель пока не проходил трафик приложений (> 0 KB). Приложения появятся здесь автоматически при использовании сети.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        displayApps.forEach { stat ->
                            AppTrafficItemRow(stat = stat)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onOpenSplitTunnelSettings,
                modifier = Modifier.fillMaxWidth().testTag("manage_split_tunnel_btn"),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.6f))
            ) {
                Icon(Icons.Default.Tune, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Настроить раздельное туннелирование",
                    color = CyberCyan,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        }
    }
}

@Composable
fun AppTrafficItemRow(
    stat: AppTrafficStat,
    modifier: Modifier = Modifier
) {
    val isRouted = stat.connectionStatus == AppConnectionStatus.ROUTED_VIA_VPN
    val statusColor = if (isRouted) NeonGreen else WarningAmber

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (isRouted) NeonGreen.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // App Info (Initial Badge + Name + Status)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(CyberCyan.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stat.appName.take(1).uppercase(),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                        color = CyberCyan
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column {
                    Text(
                        text = stat.appName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = statusColor.copy(alpha = 0.15f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = statusColor,
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "В туннеле VPN",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = statusColor
                                )
                            }
                        }
                    }
                }
            }

            // Live Transfer Metrics
            Column(horizontalAlignment = Alignment.End) {
                // Live Speeds
                if (stat.rxSpeedBytesPerSec > 0 || stat.txSpeedBytesPerSec > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(11.dp))
                        Text(
                            text = "${formatSpeed(stat.rxSpeedBytesPerSec)}/с",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ),
                            color = NeonGreen
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(11.dp))
                        Text(
                            text = "${formatSpeed(stat.txSpeedBytesPerSec)}/с",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ),
                            color = CyberCyan
                        )
                    }
                }

                // Total Session Volume
                Text(
                    text = "Всего: ${formatTrafficBytes(stat.totalBytes)}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatSpeed(bytesPerSec: Long): String {
    if (bytesPerSec < 1024) return "$bytesPerSec Б"
    val kb = bytesPerSec / 1024.0
    if (kb < 1024) return "%.1f КБ".format(kb)
    val mb = kb / 1024.0
    return "%.1f МБ".format(mb)
}

private fun formatTrafficBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes Б"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f КБ".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f МБ".format(mb)
    val gb = mb / 1024.0
    return "%.2f ГБ".format(gb)
}
