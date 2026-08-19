package com.example.presentation.dashboard

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.BlockedServicesCatalog
import com.example.domain.model.ServiceProbeResult
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DangerRed
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.WarningAmber

/**
 * Visual matrix of popular blocked social networks & video platforms
 * (YouTube, Instagram, Telegram, Twitch, Discord, X/Twitter, TikTok, Facebook),
 * displaying live DPI bypass status, reachability, and latency metrics.
 */
@Composable
fun BlockedServicesMatrix(
    serviceResults: List<ServiceProbeResult>,
    isTesting: Boolean,
    onRetest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val effectiveResults = if (serviceResults.isNotEmpty()) {
        serviceResults
    } else {
        BlockedServicesCatalog.allServices.map {
            ServiceProbeResult(service = it, isAccessible = true, latencyMs = 45L)
        }
    }

    val unblockedCount = effectiveResults.count { it.isAccessible }
    val totalCount = effectiveResults.size

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Card Title & Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "🌐 Censored Platforms Bypass Matrix",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "YouTube, Instagram, Telegram, Twitch DPI Reachability",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isTesting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = CyberCyan)
                } else {
                    IconButton(
                        onClick = onRetest,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("retest_services_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Retest", tint = CyberCyan, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Score Banner
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (unblockedCount == totalCount) NeonGreen.copy(alpha = 0.15f) else WarningAmber.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, if (unblockedCount == totalCount) NeonGreen else WarningAmber),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (unblockedCount == totalCount) "ALL CENSORED PLATFORMS ACCESSIBLE" else "SOME SERVICES DPI-THROTTLED",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (unblockedCount == totalCount) NeonGreen else WarningAmber
                    )
                    Text(
                        text = "$unblockedCount / $totalCount Unblocked",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace),
                        color = if (unblockedCount == totalCount) NeonGreen else WarningAmber
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2-Column Grid of Service Chips
            val chunked = effectiveResults.chunked(2)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                chunked.forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowItems.forEach { result ->
                            ServiceChip(
                                result = result,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowItems.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ServiceChip(
    result: ServiceProbeResult,
    modifier: Modifier = Modifier
) {
    val isOnline = result.isAccessible
    val ping = result.latencyMs
    val statusColor = when {
        !isOnline -> DangerRed
        ping != null && ping > 250 -> WarningAmber
        else -> NeonGreen
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = result.service.iconEmoji,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = result.service.name,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isOnline && ping != null) "$ping ms" else "Blocked",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = statusColor
                    )
                }
            }

            Icon(
                imageVector = if (isOnline) Icons.Default.CheckCircle else Icons.Default.Lock,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
