package com.example.presentation.logs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.BottleneckSeverity
import com.example.domain.model.ConnectionBottleneck
import com.example.domain.model.DiagnosticActionType
import com.example.domain.model.HandshakeStageStatus
import com.example.domain.model.LogDiagnosticReport
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberPurple
import com.example.ui.theme.DangerRed
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.WarningAmber

@Composable
fun DiagnosticConsoleView(
    report: LogDiagnosticReport,
    onApplyAction: (DiagnosticActionType, String?) -> Unit,
    onOpenPasteDialog: () -> Unit,
    onClearCustomReport: () -> Unit,
    isCustomReport: Boolean = false,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("diagnostic_console_view"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Custom report banner if reviewing pasted log
        if (isCustomReport) {
            item {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = CyberPurple.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, CyberPurple),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ContentPaste, contentDescription = null, tint = CyberPurple)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Анализ вставленного внешнего лога",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = CyberPurple
                            )
                        }
                        IconButton(onClick = onClearCustomReport) {
                            Icon(Icons.Default.Close, contentDescription = "Clear Custom", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // 1. Diagnostic Summary Card
        item {
            DiagnosticHeroCard(report = report)
        }

        // 2. Handshake Lifecycle Stages
        item {
            HandshakeStagesCard(stages = report.stages)
        }

        // 3. Bottlenecks Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Troubleshoot,
                        contentDescription = null,
                        tint = if (report.bottlenecks.isNotEmpty()) DangerRed else NeonGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Выявленные сбои и узкие места (${report.bottlenecks.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = CyberCyan.copy(alpha = 0.15f),
                    modifier = Modifier.clickable { onOpenPasteDialog() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(14.dp), tint = CyberCyan)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Вставить лог", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = CyberCyan)
                    }
                }
            }
        }

        // 4. Bottleneck Items
        if (report.bottlenecks.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = NeonGreen.copy(alpha = 0.08f)),
                    border = BorderStroke(1.dp, NeonGreen.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Критических блокировок не обнаружено",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = NeonGreen
                            )
                            Text(
                                text = "Рукопожатие и трафик проходят без зарегистрированных аномалий.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else {
            items(report.bottlenecks) { bottleneck ->
                BottleneckCard(
                    bottleneck = bottleneck,
                    onApplyAction = onApplyAction
                )
            }
        }

        // 5. Clean Anycast Endpoints Matrix
        item {
            CleanEndpointsMatrixCard(
                currentEndpoint = report.targetEndpoint,
                recommendedEndpoints = report.recommendedEndpoints,
                onSelectEndpoint = { ep ->
                    onApplyAction(DiagnosticActionType.APPLY_ENDPOINT, ep)
                }
            )
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DiagnosticHeroCard(
    report: LogDiagnosticReport,
    modifier: Modifier = Modifier
) {
    val borderColor = when {
        report.isInternetFunctional -> NeonGreen
        report.isHandshakeSucceeded -> WarningAmber
        report.bottlenecks.any { it.severity == BottleneckSeverity.CRITICAL } -> DangerRed
        else -> CyberCyan
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        ),
        border = BorderStroke(1.5.dp, borderColor.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Диагностический статус",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = report.activeConfigName ?: "Текущий профиль",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Health Score Gauge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = borderColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, borderColor)
                ) {
                    Text(
                        text = "Здоровье: ${report.overallHealthScore}%",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = borderColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Traffic & Endpoint Metrics Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricPill(
                    label = "Эндпоинт",
                    value = report.targetEndpoint ?: "N/A",
                    color = CyberCyan,
                    modifier = Modifier.weight(1.2f)
                )
                MetricPill(
                    label = "Tx Отправлено",
                    value = "${report.txBytes} B",
                    color = if (report.txBytes > 0) CyberPurple else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(0.9f)
                )
                MetricPill(
                    label = "Rx Принято",
                    value = "${report.rxBytes} B",
                    color = if (report.rxBytes > 0) NeonGreen else DangerRed,
                    modifier = Modifier.weight(0.9f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Verdict text
            Text(
                text = report.summaryVerdict,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = if (report.bottlenecks.isNotEmpty()) MaterialTheme.colorScheme.onSurface else NeonGreen
            )
        }
    }
}

@Composable
private fun MetricPill(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                ),
                color = color,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun HandshakeStagesCard(
    stages: List<HandshakeStageStatus>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Этапы установления соединения (Handshake Pipeline)",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(10.dp))

            stages.forEachIndexed { index, stage ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val statusColor = when {
                        stage.isSuccess -> NeonGreen
                        stage.isCurrentOrFailed -> DangerRed
                        else -> MaterialTheme.colorScheme.outline
                    }

                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(statusColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (stage.isSuccess) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(12.dp))
                        } else if (stage.isCurrentOrFailed) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = DangerRed, modifier = Modifier.size(12.dp))
                        } else {
                            Box(modifier = Modifier.size(6.dp).background(statusColor, CircleShape))
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stage.stageName,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = if (stage.isCurrentOrFailed) DangerRed else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stage.details,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (index < stages.size - 1) {
                    Divider(
                        modifier = Modifier.padding(start = 28.dp, top = 2.dp, bottom = 2.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}

@Composable
private fun BottleneckCard(
    bottleneck: ConnectionBottleneck,
    onApplyAction: (DiagnosticActionType, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    val (badgeColor, badgeText) = when (bottleneck.severity) {
        BottleneckSeverity.CRITICAL -> Pair(DangerRed, "КРИТИЧЕСКИЙ СБОЙ")
        BottleneckSeverity.WARNING -> Pair(WarningAmber, "ПРЕДУПРЕЖДЕНИЕ")
        BottleneckSeverity.OPTIMIZATION -> Pair(CyberPurple, "ОПТИМИЗАЦИЯ")
        BottleneckSeverity.INFO -> Pair(CyberCyan, "ИНФО")
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .testTag("bottleneck_card_${bottleneck.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
        ),
        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp
                        ),
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = bottleneck.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = bottleneck.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!bottleneck.detectedInLog.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "🔎 В логе: ${bottleneck.detectedInLog}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        ),
                        color = CyberCyan,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        text = "Техническая причина:",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = bottleneck.technicalDetails,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Решение:",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = NeonGreen
                    )
                    Text(
                        text = bottleneck.recommendedFix,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (bottleneck.actionType != DiagnosticActionType.NONE) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { onApplyAction(bottleneck.actionType, bottleneck.actionPayload) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (bottleneck.severity == BottleneckSeverity.CRITICAL) DangerRed else CyberCyan,
                        contentColor = if (bottleneck.severity == BottleneckSeverity.CRITICAL) Color.White else Color.Black
                    )
                ) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Устранить: ${bottleneck.recommendedFix.take(38)}...",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun CleanEndpointsMatrixCard(
    currentEndpoint: String?,
    recommendedEndpoints: List<String>,
    onSelectEndpoint: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Чистые Anycast-узлы без блокировок",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = NeonGreen.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "TSPU Bypass",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                        color = NeonGreen,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Нажмите на узел для мгновенной ротации в активном профиле:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                recommendedEndpoints.forEach { ep ->
                    val isCurrent = ep.equals(currentEndpoint, ignoreCase = true)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isCurrent) CyberCyan.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, if (isCurrent) CyberCyan else MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectEndpoint(ep) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isCurrent) Icons.Default.CheckCircle else Icons.Default.Dns,
                                    contentDescription = null,
                                    tint = if (isCurrent) CyberCyan else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = ep,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Text(
                                text = if (isCurrent) "Активен" else "Применить",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (isCurrent) CyberCyan else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PasteLogDialog(
    onDismiss: () -> Unit,
    onAnalyze: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Вставить лог для анализа", fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    text = "Вставьте строки журнала WireGuard / AmneziaWG для выявления причины блокировки рукопожатия:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    placeholder = { Text("Вставьте лог сюда (с [TUN_WARN], Tx=..., Rx=..., и т.д.)...") },
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onAnalyze(text) },
                enabled = text.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black)
            ) {
                Text("Анализировать", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
