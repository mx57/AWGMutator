package com.example.presentation.evolution

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.evolution.EvolutionProgress
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DangerRed
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.WarningAmber

/**
 * High-performance realtime canvas visualizing peer probe latency distribution,
 * dynamic success rate gauges, and candidate probe timelines during the genetic evolution phase.
 * Optimized with cached Path reuse to prevent ashmem native memory thrashing on Android Q+.
 */
@Composable
fun RealtimeLatencyCanvas(
    progress: EvolutionProgress,
    modifier: Modifier = Modifier
) {
    val probes: List<Pair<Long, Double>> = progress.recentProbes
    val latestPing: Long = progress.latestLatencyMs
    val successRate: Double = progress.latestSuccessRate
    val isHyper: Boolean = progress.isHypermutation

    val pulse = rememberInfiniteTransition(label = "canvasPulse")
    val glowAlpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    // Cached paths to avoid native Skia/ashmem allocations on every animated frame
    val trendPath = remember { Path() }
    val gradientAreaPath = remember { Path() }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(
            1.2.dp,
            if (isHyper) WarningAmber.copy(alpha = glowAlpha) else CyberCyan.copy(alpha = 0.6f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header with Diagnostic Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (progress.isRunning) NeonGreen else MaterialTheme.colorScheme.outline)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Realtime Latency & Reachability",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (isHyper) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = WarningAmber.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, WarningAmber)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "HYPERMUTATION / ISLAND MIGRATION",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Black),
                                color = WarningAmber
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Metrics Summary Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Latency Metric
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Speed, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text("Current Probe", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = if (latestPing > 0) "$latestPing ms" else "—",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                                color = CyberCyan
                            )
                        }
                    }
                }

                // Reachability Metric
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (successRate >= 0.8) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (successRate >= 0.8) NeonGreen else WarningAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text("Success Rate", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "${(successRate * 100).toInt()}%",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                                color = if (successRate >= 0.8) NeonGreen else WarningAmber
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main Custom Canvas Visualizer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Canvas(modifier = Modifier.matchParentSize().padding(8.dp)) {
                    val width = size.width
                    val height = size.height

                    // 1. Draw Grid lines
                    val lineCount = 4
                    for (i in 1..lineCount) {
                        val y = (height / (lineCount + 1)) * i
                        drawLine(
                            color = Color.Gray.copy(alpha = 0.15f),
                            start = Offset(0f, y),
                            end = Offset(width, y),
                            strokeWidth = 1f
                        )
                    }

                    if (probes.isEmpty()) {
                        drawCircle(
                            color = CyberCyan.copy(alpha = 0.3f),
                            radius = 4.dp.toPx(),
                            center = Offset(width / 2, height / 2)
                        )
                        return@Canvas
                    }

                    // 2. Draw Probe Histogram / Distribution Bars
                    val stepX = width / probes.size.coerceAtLeast(1)
                    val maxLatency = probes.maxOfOrNull { it.first }?.coerceAtLeast(150L)?.toFloat() ?: 150f

                    trendPath.reset()
                    gradientAreaPath.reset()

                    for (index in probes.indices) {
                        val pair = probes[index]
                        val ping: Long = pair.first
                        val x = index * stepX + (stepX / 2)
                        val normalizedY = (ping.toFloat() / maxLatency).coerceIn(0.05f, 0.95f)
                        val y = height - (normalizedY * height)

                        val barColor = when {
                            ping < 50L -> NeonGreen
                            ping < 100L -> CyberCyan
                            ping < 180L -> WarningAmber
                            else -> DangerRed
                        }

                        // Bar background
                        drawRoundRect(
                            color = barColor.copy(alpha = 0.35f),
                            topLeft = Offset(x - (stepX * 0.3f), y),
                            size = Size(stepX * 0.6f, height - y),
                            cornerRadius = CornerRadius(2.dp.toPx())
                        )

                        // Peak dot
                        drawCircle(
                            color = barColor,
                            radius = 3.dp.toPx(),
                            center = Offset(x, y)
                        )

                        if (index == 0) {
                            trendPath.moveTo(x, y)
                            gradientAreaPath.moveTo(x, height)
                            gradientAreaPath.lineTo(x, y)
                        } else {
                            trendPath.lineTo(x, y)
                            gradientAreaPath.lineTo(x, y)
                        }

                        if (index == probes.size - 1) {
                            gradientAreaPath.lineTo(x, height)
                            gradientAreaPath.close()
                        }
                    }

                    // Trend Line
                    drawPath(
                        path = trendPath,
                        color = CyberCyan,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Gradient under trend
                    drawPath(
                        path = gradientAreaPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(CyberCyan.copy(alpha = 0.25f), Color.Transparent)
                        )
                    )
                }
            }

            if (progress.diagnosticNote != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "🧬 ${progress.diagnosticNote}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                    color = WarningAmber
                )
            }
        }
    }
}
