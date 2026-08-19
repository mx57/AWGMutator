package com.example.presentation.dashboard

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.domain.model.VpnState
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberPurple
import com.example.ui.theme.DangerRed
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.WarningAmber

@Composable
fun DashboardScreen(
    onNavigateToEvolution: () -> Unit,
    onNavigateToConfigs: () -> Unit,
    onNavigateToAntiDpi: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    snackbarHostState: SnackbarHostState,
    viewModel: DashboardViewModel = viewModel()
) {
    val context = LocalContext.current
    val vpnStatus by viewModel.vpnStatus.collectAsStateWithLifecycle()
    val configs by viewModel.configs.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val appStats by viewModel.appStats.collectAsStateWithLifecycle()
    val tunnelLogs by viewModel.tunnelLogs.collectAsStateWithLifecycle()

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.startVpnDirectly()
        } else {
            viewModel.onVpnPermissionDenied()
        }
    }

    LaunchedEffect(uiState.vpnPrepareIntent) {
        uiState.vpnPrepareIntent?.let { intent ->
            vpnPermissionLauncher.launch(intent)
            viewModel.clearVpnPrepareIntent()
        }
    }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val activeConfig = uiState.selectedConfig ?: configs.firstOrNull()
    var configMenuExpanded by remember { mutableStateOf(false) }

    val isConnected = vpnStatus.state == VpnState.CONNECTED
    val isConnecting = vpnStatus.state == VpnState.CONNECTING

    val pulseTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isConnecting || isConnected) 1.06f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val buttonGlowColor by animateColorAsState(
        targetValue = when (vpnStatus.state) {
            VpnState.CONNECTED -> NeonGreen
            VpnState.CONNECTING -> WarningAmber
            VpnState.ERROR -> DangerRed
            else -> CyberCyan
        },
        label = "buttonColor"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Header & Live Badge
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "🧬 AWGMutator",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    ),
                    color = CyberCyan
                )
                Text(
                    text = "Genetic Obfuscation & WARP Engine",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = when (vpnStatus.state) {
                    VpnState.CONNECTED -> NeonGreen.copy(alpha = 0.15f)
                    VpnState.CONNECTING -> WarningAmber.copy(alpha = 0.15f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                border = BorderStroke(
                    1.dp,
                    when (vpnStatus.state) {
                        VpnState.CONNECTED -> NeonGreen
                        VpnState.CONNECTING -> WarningAmber
                        else -> MaterialTheme.colorScheme.outline
                    }
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(buttonGlowColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = vpnStatus.state.name,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = buttonGlowColor
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Main VPN Button
        Box(
            modifier = Modifier
                .size(190.dp)
                .scale(pulseScale),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(185.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                buttonGlowColor.copy(alpha = 0.35f),
                                buttonGlowColor.copy(alpha = 0.05f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Surface(
                modifier = Modifier
                    .size(145.dp)
                    .clip(CircleShape)
                    .clickable { viewModel.toggleVpn(context) }
                    .testTag("vpn_toggle_button"),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(3.dp, buttonGlowColor),
                shadowElevation = 12.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(34.dp),
                            color = WarningAmber,
                            strokeWidth = 3.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "VPN Toggle",
                            tint = buttonGlowColor,
                            modifier = Modifier.size(50.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isConnected) "DISCONNECT" else "CONNECT",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        ),
                        color = buttonGlowColor
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Active Profile Selector
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable { configMenuExpanded = true }
                .testTag("config_selector_card"),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (activeConfig?.isWarp == true) CyberPurple.copy(alpha = 0.2f) else CyberCyan.copy(alpha = 0.2f)
                    ) {
                        Icon(
                            imageVector = if (activeConfig?.isWarp == true) Icons.Default.Security else Icons.Default.Tune,
                            contentDescription = null,
                            tint = if (activeConfig?.isWarp == true) CyberPurple else CyberCyan,
                            modifier = Modifier.padding(6.dp).size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = activeConfig?.name ?: "No Config Selected",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (activeConfig != null) "${activeConfig.endpoint} • MTU ${activeConfig.mtu}" else "Tap to choose or generate",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                DropdownMenu(
                    expanded = configMenuExpanded,
                    onDismissRequest = { configMenuExpanded = false }
                ) {
                    if (configs.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No configs saved yet") },
                            onClick = { configMenuExpanded = false }
                        )
                    } else {
                        configs.forEach { cfg ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(cfg.name, fontWeight = FontWeight.Medium)
                                        Text(cfg.endpoint, style = MaterialTheme.typography.bodySmall)
                                    }
                                },
                                onClick = {
                                    viewModel.selectConfig(cfg)
                                    configMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Live Telemetry Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Latency & Speed Probe Card
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Speed, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Latency", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(
                            onClick = { viewModel.runSpeedPingCheck() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            if (uiState.isTestingSpeed) {
                                CircularProgressIndicator(modifier = Modifier.size(12.dp), color = CyberCyan, strokeWidth = 1.5.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Test Ping", tint = CyberCyan, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    val pingVal = uiState.measuredPingMs ?: vpnStatus.currentPingMs
                    Text(
                        text = if (pingVal != null) "$pingVal ms" else "—",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                        color = CyberCyan
                    )
                }
            }

            // Obfuscation / Traffic Card
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = CyberPurple, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("DPI Shield", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (activeConfig != null && activeConfig.jc > 0) "Active (Jc=${activeConfig.jc})" else "Standard",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                        color = if (activeConfig != null && activeConfig.jc > 0) NeonGreen else CyberPurple,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(12.dp))
                        Text(
                            text = formatBytes(vpnStatus.rxBytes),
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(12.dp))
                        Text(
                            text = formatBytes(vpnStatus.txBytes),
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Internet Exit & Egress Verification Card
        NetworkEgressCard(
            vpnStatus = vpnStatus,
            egressResult = uiState.egressResult,
            isVerifying = uiState.isVerifyingEgress,
            onVerifyEgress = { viewModel.verifyNetworkEgress() }
        )

        Spacer(modifier = Modifier.height(14.dp))

        // App Traffic Telemetry & Connection Status Card
        AppTrafficDashboardCard(
            appStats = appStats,
            onOpenSplitTunnelSettings = onNavigateToSettings
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Tunnel Routing & Packet Flow Diagnostics Card
        PacketDiagnosticsCard(
            vpnStatus = vpnStatus,
            tunnelLogs = tunnelLogs,
            onClearLogs = { viewModel.clearTunnelLogs() }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Blocked Services Bypass Matrix
        BlockedServicesMatrix(
            serviceResults = uiState.serviceResults,
            isTesting = uiState.isTestingServices,
            onRetest = { viewModel.checkBlockedServices() }
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Quick Generation Hub
        Text(
            text = "Instant 1-Tap Provisioning",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.generateQuickWarp() },
                modifier = Modifier
                    .weight(1f)
                    .testTag("quick_warp_button"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CyberPurple),
                enabled = !uiState.isGeneratingWarp
            ) {
                if (uiState.isGeneratingWarp) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White)
                } else {
                    Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Auto WARP", style = MaterialTheme.typography.labelMedium)
                }
            }

            Button(
                onClick = { viewModel.generateHybridAntiDpi() },
                modifier = Modifier
                    .weight(1.2f)
                    .testTag("quick_hybrid_button"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                enabled = !uiState.isGeneratingWarp
            ) {
                Icon(imageVector = Icons.Default.Shield, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("WARP + Anti-DPI", color = Color.Black, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onNavigateToAntiDpi,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("quick_antidpi_button"),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, CyberPurple)
        ) {
            Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = CyberPurple, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Open Anti-DPI Vulnerability Inspector", color = CyberPurple, style = MaterialTheme.typography.labelMedium)
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onNavigateToEvolution,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("quick_evolution_button"),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, CyberCyan)
        ) {
            Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Launch Genetic Algorithm Evolution Engine", color = CyberCyan, style = MaterialTheme.typography.labelMedium)
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    val gb = mb / 1024.0
    return "%.2f GB".format(gb)
}
