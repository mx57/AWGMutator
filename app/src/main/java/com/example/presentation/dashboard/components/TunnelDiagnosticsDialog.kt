package com.example.presentation.dashboard.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.model.DiagnosticActionType
import com.example.domain.model.DiagnosticStatus
import com.example.domain.model.DiagnosticStep
import com.example.domain.model.TunnelDiagnosticReport
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberPurple
import com.example.ui.theme.DangerRed
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.WarningAmber

@Composable
fun TunnelDiagnosticsDialog(
    report: TunnelDiagnosticReport,
    onDismiss: () -> Unit,
    onRunDiagnostics: () -> Unit,
    onApplyAction: (DiagnosticActionType, String?) -> Unit
) {
    Dialog(
        onDismissRequest = { if (!report.isRunning) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .testTag("tunnel_diagnostics_dialog"),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.5.dp, if (report.isHealthy) NeonGreen else CyberCyan),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = CyberCyan.copy(alpha = 0.15f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Troubleshoot,
                                contentDescription = null,
                                tint = CyberCyan,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Диагностика туннеля & ТСПУ",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Глубокий анализ причин сбоя соединения",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        enabled = !report.isRunning
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Overall Verdict Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            report.isHealthy -> NeonGreen.copy(alpha = 0.12f)
                            report.steps.any { it.status == DiagnosticStatus.ERROR } -> DangerRed.copy(alpha = 0.12f)
                            report.isRunning -> CyberCyan.copy(alpha = 0.12f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                    border = BorderStroke(
                        1.dp,
                        when {
                            report.isHealthy -> NeonGreen
                            report.steps.any { it.status == DiagnosticStatus.ERROR } -> DangerRed
                            report.isRunning -> CyberCyan
                            else -> MaterialTheme.colorScheme.outline
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (report.isRunning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = CyberCyan
                                )
                            } else {
                                Icon(
                                    imageVector = if (report.isHealthy) Icons.Default.CheckCircle else if (report.steps.any { it.status == DiagnosticStatus.ERROR }) Icons.Default.Error else Icons.Default.Info,
                                    contentDescription = null,
                                    tint = if (report.isHealthy) NeonGreen else if (report.steps.any { it.status == DiagnosticStatus.ERROR }) DangerRed else WarningAmber,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (report.isRunning) "Выполняется диагностика..." else "Заключение анализатора",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = if (report.isHealthy) NeonGreen else if (report.steps.any { it.status == DiagnosticStatus.ERROR }) DangerRed else CyberCyan
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = report.overallVerdict,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Steps list
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(report.steps) { step ->
                        DiagnosticStepCard(
                            step = step,
                            onApplyAction = { action, payload -> onApplyAction(action, payload) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onRunDiagnostics,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("rerun_diagnostics_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black),
                        enabled = !report.isRunning
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Перезапустить тест", fontWeight = FontWeight.Bold)
                    }

                    if (report.bestEndpoint != null) {
                        Button(
                            onClick = { onApplyAction(DiagnosticActionType.APPLY_ENDPOINT, report.bestEndpoint) },
                            modifier = Modifier
                                .weight(1.2f)
                                .testTag("apply_best_endpoint_button"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black),
                            enabled = !report.isRunning
                        ) {
                            Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Применить ${report.bestEndpoint}", fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticStepCard(
    step: DiagnosticStep,
    onApplyAction: (DiagnosticActionType, String?) -> Unit
) {
    var expandedDetails by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        border = BorderStroke(
            1.dp,
            when (step.status) {
                DiagnosticStatus.SUCCESS -> NeonGreen.copy(alpha = 0.5f)
                DiagnosticStatus.WARNING -> WarningAmber.copy(alpha = 0.5f)
                DiagnosticStatus.ERROR -> DangerRed.copy(alpha = 0.7f)
                DiagnosticStatus.RUNNING -> CyberCyan.copy(alpha = 0.7f)
                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            }
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    when (step.status) {
                        DiagnosticStatus.RUNNING -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = CyberCyan
                            )
                        }
                        DiagnosticStatus.SUCCESS -> {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(18.dp))
                        }
                        DiagnosticStatus.WARNING -> {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(18.dp))
                        }
                        DiagnosticStatus.ERROR -> {
                            Icon(Icons.Default.Cancel, contentDescription = null, tint = DangerRed, modifier = Modifier.size(18.dp))
                        }
                        DiagnosticStatus.IDLE -> {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.outline)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = step.title,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = step.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (step.latencyMs != null) {
                    Text(
                        text = "${step.latencyMs} ms",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        ),
                        color = CyberCyan
                    )
                }
            }

            if (!step.resultText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = step.resultText,
                        style = MaterialTheme.typography.bodySmall,
                        color = when (step.status) {
                            DiagnosticStatus.SUCCESS -> NeonGreen
                            DiagnosticStatus.ERROR -> DangerRed
                            DiagnosticStatus.WARNING -> WarningAmber
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            if (!step.details.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .clickable { expandedDetails = !expandedDetails }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (expandedDetails) "Скрыть детальные результаты узлов" else "Показать детальные результаты узлов",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = CyberCyan
                    )
                    Icon(
                        imageVector = if (expandedDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }

                AnimatedVisibility(visible = expandedDetails) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Black.copy(alpha = 0.3f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        Text(
                            text = step.details,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }

            // Recommended Action Button
            if (step.recommendedAction != DiagnosticActionType.NONE && !step.actionLabel.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { onApplyAction(step.recommendedAction, step.actionPayload) },
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (step.status == DiagnosticStatus.ERROR) DangerRed else CyberPurple,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(step.actionLabel, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}
