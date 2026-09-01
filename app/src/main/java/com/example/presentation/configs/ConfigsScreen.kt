package com.example.presentation.configs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.domain.model.AwgConfig
import com.example.domain.model.DnsCatalog
import com.example.domain.model.EndpointCatalog
import com.example.domain.model.EndpointItem
import com.example.domain.model.SniCatalog
import com.example.domain.usecase.ObfuscationPreset
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberPurple
import com.example.ui.theme.DangerRed
import com.example.ui.theme.NeonGreen
import com.example.util.QrCodeGenerator

@Composable
fun ConfigsScreen(
    snackbarHostState: SnackbarHostState,
    viewModel: ConfigsViewModel = viewModel()
) {
    val context = LocalContext.current
    val configs by viewModel.configs.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val filteredConfigs = remember(configs, uiState.searchQuery) {
        if (uiState.searchQuery.isBlank()) {
            configs
        } else {
            configs.filter {
                it.name.contains(uiState.searchQuery, ignoreCase = true) ||
                        it.endpoint.contains(uiState.searchQuery, ignoreCase = true) ||
                        it.originType.contains(uiState.searchQuery, ignoreCase = true)
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                FloatingActionButton(
                    onClick = { viewModel.showImportDialog(true) },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = CyberCyan,
                    modifier = Modifier.padding(bottom = 8.dp).size(48.dp).testTag("import_fab")
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = "Import Config", modifier = Modifier.size(20.dp))
                }
                FloatingActionButton(
                    onClick = { viewModel.showAddDialog(true) },
                    containerColor = CyberCyan,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(52.dp).testTag("add_custom_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add AWG Config")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Configurations (${configs.size})",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = if (uiState.isRootAvailable) "⚡ Root Engine Ready" else "🔒 Standard VpnService",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (uiState.isRootAvailable) NeonGreen else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = { viewModel.showWarpDialog(true) },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberPurple),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(34.dp).testTag("generate_warp_header_button")
                    ) {
                        if (uiState.isGenerating && uiState.showWarpDialog) {
                            CircularProgressIndicator(modifier = Modifier.size(12.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ WARP", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    Button(
                        onClick = { viewModel.showMasqueDialog(true) },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(34.dp).testTag("generate_masque_header_button")
                    ) {
                        if (uiState.isGenerating && uiState.showMasqueDialog) {
                            CircularProgressIndicator(modifier = Modifier.size(12.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ MASQUE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }

            // Quick Tools Bar: Endpoints, DNS, SNI
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = false,
                    onClick = { viewModel.showScannerDialog(true) },
                    label = { Text("🌐 Endpoints & Ping", style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = { Icon(Icons.Default.Explore, contentDescription = null, modifier = Modifier.size(13.dp), tint = CyberCyan) },
                    colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                )

                FilterChip(
                    selected = false,
                    onClick = { viewModel.showDnsSelectionDialog(true) },
                    label = { Text("🛡️ DNS (${uiState.selectedDnsList.size})", style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = { Icon(Icons.Default.Dns, contentDescription = null, modifier = Modifier.size(13.dp), tint = NeonGreen) },
                    colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                )

                FilterChip(
                    selected = false,
                    onClick = { viewModel.showSniSelectionDialog(true) },
                    label = { Text("🎭 SNI (${uiState.selectedSniDomain})", style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = { Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(13.dp), tint = CyberPurple) },
                    colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                )
            }

            // Search Bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Filter by name, IP or type (Gen, WARP, Manual)...", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(16.dp)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("config_search_bar"),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (filteredConfigs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "No profiles found",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Import a .conf or generate a new profile to begin",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredConfigs, key = { it.id }) { config ->
                        CompactConfigCard(
                            config = config,
                            isTesting = uiState.testingConfigId == config.id,
                            isRootAvailable = uiState.isRootAvailable,
                            onTest = { viewModel.testConfigEndpoint(config) },
                            onApplyRoot = { viewModel.applyRootTunnel(config) },
                            onExportMagisk = { viewModel.exportMagiskModule(context, config) },
                            onDelete = { viewModel.deleteConfig(config.id) },
                            onDuplicate = { viewModel.duplicateConfig(config) },
                            onShare = { viewModel.shareConfigFile(context, config) },
                            onShowQr = { viewModel.showQrDialog(config) },
                            onShowExport = { viewModel.showExportDialog(config) },
                            onFixEndpoint = { viewModel.fixConfigEndpoint(config) }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(72.dp))
                    }
                }
            }
        }
    }

    // Dialogs
    if (uiState.showAddDialog) {
        AddAwgConfigDialog(
            defaultDns = DnsCatalog.formatMultiple(uiState.selectedDnsList),
            defaultSni = uiState.selectedSniDomain,
            onDismiss = { viewModel.showAddDialog(false) },
            onCreate = { name, ep, peer, dns, preset, jc, jmin, jmax, s1, s2, s3, s4, h1, h2, h3, h4, i1, sni, mtu ->
                viewModel.createCustomAwg(name, ep, peer, dns, preset, jc, jmin, jmax, s1, s2, s3, s4, h1, h2, h3, h4, i1, sni, mtu)
            }
        )
    }

    if (uiState.showWarpDialog) {
        GenerateWarpDialog(
            isGenerating = uiState.isGenerating,
            defaultDns = DnsCatalog.formatMultiple(uiState.selectedDnsList),
            onDismiss = { viewModel.showWarpDialog(false) },
            onGenerate = { name, license, dns, ep, antiDpi ->
                viewModel.generateAdvancedWarp(name, license, dns, ep, antiDpi)
            }
        )
    }

    if (uiState.showImportDialog) {
        ImportConfigDialog(
            onDismiss = { viewModel.showImportDialog(false) },
            onImport = { raw, name -> viewModel.importConfig(raw, name) }
        )
    }

    if (uiState.showScannerDialog) {
        EndpointScannerDialog(
            endpoints = uiState.discoveredEndpoints,
            selectedCountry = uiState.selectedCountry,
            isScanning = uiState.isScanningEndpoints,
            configs = configs,
            onSelectCountry = { viewModel.selectCountry(it) },
            onRefresh = { viewModel.scanCountryEndpoints() },
            onDiscoverNew = { viewModel.discoverNewUnknownEndpoints() },
            onApplyToConfig = { config, ep -> viewModel.applyEndpointToConfig(config, ep) },
            onDismiss = { viewModel.showScannerDialog(false) }
        )
    }

    if (uiState.showDnsSelectionDialog) {
        DnsSelectionDialog(
            selectedIds = uiState.selectedDnsList,
            onToggle = { viewModel.toggleDnsSelection(it) },
            onDismiss = { viewModel.showDnsSelectionDialog(false) }
        )
    }

    if (uiState.showSniSelectionDialog) {
        SniSelectionDialog(
            selectedSni = uiState.selectedSniDomain,
            onSelect = { viewModel.selectSni(it) },
            onDismiss = { viewModel.showSniSelectionDialog(false) }
        )
    }

    uiState.activeQrConfig?.let { config ->
        QrCodeDialog(config = config, onDismiss = { viewModel.showQrDialog(null) })
    }

    uiState.activeExportConfig?.let { config ->
        ExportConfigDialog(config = config, onDismiss = { viewModel.showExportDialog(null) })
    }

    if (uiState.showMasqueDialog) {
        GenerateMasqueDialog(
            isGenerating = uiState.isGenerating,
            defaultSni = uiState.selectedSniDomain,
            onDismiss = { viewModel.showMasqueDialog(false) },
            onGenerate = { name, license, sni, serverIp, serverPort ->
                viewModel.generateMasqueProfile(name, license, sni, serverIp, serverPort)
            }
        )
    }

    uiState.activeMasqueJson?.let { jsonText ->
        MasqueJsonViewerDialog(
            jsonText = jsonText,
            onDismiss = { viewModel.dismissMasqueJsonDialog() }
        )
    }
}

@Composable
fun CompactConfigCard(
    config: AwgConfig,
    isTesting: Boolean,
    isRootAvailable: Boolean,
    onTest: () -> Unit,
    onApplyRoot: () -> Unit,
    onExportMagisk: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onShare: () -> Unit,
    onShowQr: () -> Unit,
    onShowExport: () -> Unit,
    onFixEndpoint: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("config_card_${config.id}"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Lineage Badge & Timestamp Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OriginBadge(config.originType, config.evolutionGeneration)
                    Text(
                        text = "📅 ${config.formattedDateTime}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                if (config.lastPingMs != null) {
                    Surface(
                        color = NeonGreen.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "${config.lastPingMs} ms",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = NeonGreen,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Profile Title and Endpoint
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = config.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = config.endpoint,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    color = if (config.endpoint.startsWith("162.159.192") || config.endpoint.startsWith("162.159.193")) DangerRed else CyberCyan
                )
            }

            if (config.endpoint.startsWith("162.159.192") || config.endpoint.startsWith("162.159.193") || config.dns.contains("111.88")) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(DangerRed.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "⚠️ Заблокирован провайдером",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
                        color = DangerRed
                    )
                    TextButton(
                        onClick = onFixEndpoint,
                        modifier = Modifier.height(26.dp)
                    ) {
                        Text("⚡ Заменить на чистый (188.114.97.1)", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = CyberCyan)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Compact Genes & Obfuscation parameters
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ParamBadge(label = "Jc", value = "${config.jc}")
                ParamBadge(label = "Jit", value = "${config.jmin}-${config.jmax}")
                ParamBadge(label = "S1", value = "${config.s1}")
                ParamBadge(label = "H1", value = "${config.h1}")
                ParamBadge(label = "MTU", value = "${config.mtu}")
                if (!config.sni.isNullOrBlank()) {
                    ParamBadge(label = "SNI", value = config.sni)
                }
                if (!config.i1.isNullOrBlank()) {
                    ParamBadge(label = "I1", value = "Noise")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Compact Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(
                        onClick = onTest,
                        enabled = !isTesting,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(30.dp).testTag("test_endpoint_button_${config.id}")
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(12.dp), color = CyberCyan)
                        } else {
                            Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(13.dp), tint = CyberCyan)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Ping", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    if (isRootAvailable) {
                        Button(
                            onClick = onApplyRoot,
                            colors = ButtonDefaults.buttonColors(containerColor = CyberPurple.copy(alpha = 0.8f)),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(30.dp).testTag("root_tunnel_button_${config.id}")
                        ) {
                            Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Root Apply", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    IconButton(
                        onClick = onExportMagisk,
                        modifier = Modifier.size(30.dp).testTag("magisk_export_${config.id}")
                    ) {
                        Icon(Icons.Default.Extension, contentDescription = "Magisk Module", tint = NeonGreen, modifier = Modifier.size(16.dp))
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(onClick = onShowQr, modifier = Modifier.size(28.dp).testTag("qr_button_${config.id}")) {
                        Icon(Icons.Default.QrCode, contentDescription = "QR Code", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onShowExport, modifier = Modifier.size(28.dp).testTag("export_button_${config.id}")) {
                        Icon(Icons.Default.Difference, contentDescription = "View Conf", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onShare, modifier = Modifier.size(28.dp).testTag("share_button_${config.id}")) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDuplicate, modifier = Modifier.size(28.dp).testTag("duplicate_button_${config.id}")) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp).testTag("delete_button_${config.id}")) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = DangerRed, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun OriginBadge(originType: String, generation: Int?) {
    val (label, bg, fg) = when (originType) {
        "EVOLUTION" -> Triple(if (generation != null) "🧬 Gen #$generation" else "🧬 Evolved", CyberCyan.copy(alpha = 0.2f), CyberCyan)
        "WARP" -> Triple("🌐 WARP", CyberPurple.copy(alpha = 0.2f), CyberPurple)
        "HYBRID" -> Triple("⚡ Hybrid", NeonGreen.copy(alpha = 0.2f), NeonGreen)
        "IMPORTED" -> Triple("📥 Imported", Color(0xFFFFA000).copy(alpha = 0.2f), Color(0xFFFFA000))
        else -> Triple("✍️ Manual", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Surface(
        color = bg,
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = fg,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
        )
    }
}

@Composable
fun ParamBadge(label: String, value: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Row(modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)) {
            Text(
                text = "$label:",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun DnsSelectionDialog(
    selectedIds: List<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().height(520.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp).fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Censorship-Resistant DNS",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "${selectedIds.size} servers selected for generation & evolution",
                            style = MaterialTheme.typography.bodySmall,
                            color = NeonGreen
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(DnsCatalog.servers, key = { it.id }) { server ->
                        val isSelected = selectedIds.contains(server.id)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(server.id) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) CyberCyan.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, if (isSelected) CyberCyan else MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { onToggle(server.id) },
                                    colors = CheckboxDefaults.colors(checkedColor = CyberCyan)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = server.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = server.country,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = server.formatted,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        color = CyberCyan
                                    )
                                    Text(
                                        text = server.description,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
                ) {
                    Text("Apply Selection (${selectedIds.size})")
                }
            }
        }
    }
}

@Composable
fun SniSelectionDialog(
    selectedSni: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().height(480.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp).fillMaxSize()) {
                Text(
                    text = "SNI Spoofing Whitelist",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Disguises WireGuard/AWG handshakes under allowed Russian domains",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(SniCatalog.russianWhitelistDomains, key = { it.id }) { item ->
                        val isSelected = selectedSni == item.domain
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(item.domain) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) CyberPurple.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, if (isSelected) CyberPurple else MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { onSelect(item.domain) },
                                    colors = RadioButtonDefaults.colors(selectedColor = CyberPurple)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = item.domain,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                    )
                                    Text(
                                        text = "${item.serviceName} (${item.category})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberPurple)
                ) {
                    Text("Done")
                }
            }
        }
    }
}

@Composable
fun EndpointScannerDialog(
    endpoints: List<EndpointItem>,
    selectedCountry: String,
    isScanning: Boolean,
    configs: List<AwgConfig>,
    onSelectCountry: (String) -> Unit,
    onRefresh: () -> Unit,
    onDiscoverNew: () -> Unit,
    onApplyToConfig: (AwgConfig, String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTargetConfig by remember { mutableStateOf(configs.firstOrNull()) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().height(620.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Endpoint Discovery & Ping",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Find unblocked high-speed WireGuard / WARP endpoints",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    EndpointCatalog.countries.forEach { (code, label) ->
                        FilterChip(
                            selected = selectedCountry == code,
                            onClick = { onSelectCountry(code) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyberCyan,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onRefresh,
                        enabled = !isScanning,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = CyberCyan)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = CyberCyan)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Scan Known", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    Button(
                        onClick = onDiscoverNew,
                        enabled = !isScanning,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberPurple)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Discover New 🔍", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(endpoints, key = { it.fullEndpoint }) { item ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, if (item.isAlive) NeonGreen.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = item.fullEndpoint,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                                            color = CyberCyan
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = CyberPurple.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = item.countryCode,
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = CyberPurple,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "${item.countryName} (${item.ispName})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (item.lastPingMs != null) {
                                        Surface(
                                            color = if (item.isAlive) NeonGreen.copy(alpha = 0.2f) else DangerRed.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "${item.lastPingMs} ms",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = if (item.isAlive) NeonGreen else DangerRed,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    if (configs.isNotEmpty()) {
                                        IconButton(
                                            onClick = {
                                                selectedTargetConfig?.let { cfg ->
                                                    onApplyToConfig(cfg, item.fullEndpoint)
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Default.NetworkCheck, contentDescription = "Apply", tint = CyberCyan)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text("Close", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
fun AddAwgConfigDialog(
    defaultDns: String,
    defaultSni: String,
    onDismiss: () -> Unit,
    onCreate: (
        name: String,
        endpoint: String,
        peerKey: String,
        dns: String,
        preset: ObfuscationPreset?,
        jc: Int,
        jmin: Int,
        jmax: Int,
        s1: Int,
        s2: Int,
        s3: Int,
        s4: Int,
        h1: Long,
        h2: Long,
        h3: Long,
        h4: Long,
        i1: String?,
        sni: String?,
        mtu: Int
    ) -> Unit
) {
    var name by remember { mutableStateOf("AmneziaWG Russian Bypass") }
    var endpoint by remember { mutableStateOf("188.114.97.1:854") }
    var peerKey by remember { mutableStateOf("bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=") }
    var dns by remember { mutableStateOf(defaultDns.ifBlank { "1.1.1.1, 8.8.8.8, 1.0.0.1" }) }
    var selectedPreset by remember { mutableStateOf(ObfuscationPreset.VERIFIED_AWG_RUSSIAN_BYPASS) }

    var jc by remember { mutableIntStateOf(4) }
    var jmin by remember { mutableIntStateOf(40) }
    var jmax by remember { mutableIntStateOf(70) }
    var s1 by remember { mutableIntStateOf(0) }
    var s2 by remember { mutableIntStateOf(0) }
    var s3 by remember { mutableIntStateOf(0) }
    var s4 by remember { mutableIntStateOf(0) }
    var h1 by remember { mutableLongStateOf(1L) }
    var h2 by remember { mutableLongStateOf(2L) }
    var h3 by remember { mutableLongStateOf(3L) }
    var h4 by remember { mutableLongStateOf(4L) }
    var i1 by remember { mutableStateOf("<b 0x2ae1f9c4708a38d94b0c791350a4d9ef4a87e20b3361849a0e671239c0f45532>") }
    var sni by remember { mutableStateOf(defaultSni) }
    var mtu by remember { mutableIntStateOf(1280) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().height(600.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Generate AmneziaWG Config",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Obfuscated WireGuard 2.0 / 3.0 with Russian Whitelist SNI",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Profile Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = endpoint,
                    onValueChange = { endpoint = it },
                    label = { Text("Server Endpoint (IP:Port)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = dns,
                    onValueChange = { dns = it },
                    label = { Text("DNS Resolvers") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = sni,
                    onValueChange = { sni = it },
                    label = { Text("SNI Spoofing Domain (e.g. vk.com)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text("Obfuscation Preset", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ObfuscationPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = selectedPreset == preset,
                            onClick = {
                                selectedPreset = preset
                                when (preset) {
                                    ObfuscationPreset.VERIFIED_AWG_RUSSIAN_BYPASS -> {
                                        jc = 4; jmin = 40; jmax = 70; s1 = 0; s2 = 0; s3 = 0; s4 = 0
                                        h1 = 1L; h2 = 2L; h3 = 3L; h4 = 4L; mtu = 1280
                                    }
                                    ObfuscationPreset.EXTREME_ANTI_DPI -> {
                                        jc = 6; jmin = 120; jmax = 520; s1 = 32; s2 = 40; s3 = 24; s4 = 16
                                        h1 = 123456L; h2 = 234567L; h3 = 345678L; h4 = 456789L; mtu = 1360
                                    }
                                    ObfuscationPreset.BALANCED -> {
                                        jc = 3; jmin = 64; jmax = 256; s1 = 16; s2 = 24; s3 = 12; s4 = 8
                                        h1 = 1L; h2 = 2L; h3 = 3L; h4 = 4L; mtu = 1360
                                    }
                                    else -> {}
                                }
                            },
                            label = { Text(preset.name.replace("_", " "), style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("Obfuscation Parameters (AmneziaWG 2.0)", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = jc.toString(),
                        onValueChange = { jc = it.toIntOrNull() ?: 0 },
                        label = { Text("Jc") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = jmin.toString(),
                        onValueChange = { jmin = it.toIntOrNull() ?: 0 },
                        label = { Text("Jmin") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = jmax.toString(),
                        onValueChange = { jmax = it.toIntOrNull() ?: 0 },
                        label = { Text("Jmax") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = s1.toString(),
                        onValueChange = { s1 = it.toIntOrNull() ?: 0 },
                        label = { Text("S1") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = s2.toString(),
                        onValueChange = { s2 = it.toIntOrNull() ?: 0 },
                        label = { Text("S2") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = s3.toString(),
                        onValueChange = { s3 = it.toIntOrNull() ?: 0 },
                        label = { Text("S3") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = s4.toString(),
                        onValueChange = { s4 = it.toIntOrNull() ?: 0 },
                        label = { Text("S4") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = h1.toString(),
                        onValueChange = { h1 = it.toLongOrNull() ?: 1L },
                        label = { Text("H1") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = h2.toString(),
                        onValueChange = { h2 = it.toLongOrNull() ?: 2L },
                        label = { Text("H2") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = h3.toString(),
                        onValueChange = { h3 = it.toLongOrNull() ?: 3L },
                        label = { Text("H3") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = h4.toString(),
                        onValueChange = { h4 = it.toLongOrNull() ?: 4L },
                        label = { Text("H4") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onCreate(name, endpoint, peerKey, dns, selectedPreset, jc, jmin, jmax, s1, s2, s3, s4, h1, h2, h3, h4, i1, sni, mtu)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
                    ) {
                        Text("Create Config")
                    }
                }
            }
        }
    }
}

@Composable
fun GenerateWarpDialog(
    isGenerating: Boolean,
    defaultDns: String,
    onDismiss: () -> Unit,
    onGenerate: (name: String, license: String?, dns: String, endpoint: String, antiDpi: Boolean) -> Unit
) {
    var name by remember { mutableStateOf("WARP Russian Bypass Profile") }
    var license by remember { mutableStateOf("") }
    var dns by remember { mutableStateOf(defaultDns.ifBlank { "1.1.1.1, 8.8.8.8, 1.0.0.1" }) }
    var endpoint by remember { mutableStateOf("188.114.97.1:854") }
    var injectAntiDpi by remember { mutableStateOf(true) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Generate Cloudflare WARP / AWG",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Auto-probes 14+ mirrors and registers unblocked bypass port 1074",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Profile Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = endpoint,
                    onValueChange = { endpoint = it },
                    label = { Text("Bypass Endpoint (IP:Port)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = dns,
                    onValueChange = { dns = it },
                    label = { Text("DNS Resolvers") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = license,
                    onValueChange = { license = it },
                    label = { Text("WARP+ License Key (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Inject Anti-DPI Obfuscation", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        Text("Adds Jc=4, Jitter, and Russian SNI", style = MaterialTheme.typography.labelSmall, color = CyberCyan)
                    }
                    Switch(
                        checked = injectAntiDpi,
                        onCheckedChange = { injectAntiDpi = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = CyberCyan)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onGenerate(name, license.ifBlank { null }, dns, endpoint, injectAntiDpi) },
                        enabled = !isGenerating,
                        colors = ButtonDefaults.buttonColors(containerColor = CyberPurple)
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text("Generate Profile")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ImportConfigDialog(
    onDismiss: () -> Unit,
    onImport: (raw: String, name: String) -> Unit
) {
    var name by remember { mutableStateOf("Imported AWG") }
    var rawText by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Import WireGuard / AWG .conf",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Profile Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = rawText,
                    onValueChange = { rawText = it },
                    label = { Text("Paste .conf File Content") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    maxLines = 10
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onImport(rawText, name) },
                        enabled = rawText.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
                    ) {
                        Text("Import")
                    }
                }
            }
        }
    }
}

@Composable
fun QrCodeDialog(config: AwgConfig, onDismiss: () -> Unit) {
    val bitmap = remember(config) {
        QrCodeGenerator.generateQrBitmap(config.toConfString(), 512)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "${config.name} (QR Code)") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Config QR Code",
                    modifier = Modifier.size(240.dp)
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun ExportConfigDialog(config: AwgConfig, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val confText = remember(config) { config.toConfString() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "${config.name}.conf") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(260.dp)
                ) {
                    Text(
                        text = confText,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.padding(10.dp).verticalScroll(rememberScrollState())
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("AWG Config", confText))
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Copy .conf")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun GenerateMasqueDialog(
    isGenerating: Boolean,
    defaultSni: String,
    onDismiss: () -> Unit,
    onGenerate: (name: String, license: String?, sni: String?, serverIp: String, serverPort: Int) -> Unit
) {
    var name by remember { mutableStateOf("WARP MASQUE (HTTP/3)") }
    var licenseKey by remember { mutableStateOf("") }
    var sni by remember { mutableStateOf(defaultSni.ifBlank { "vk.com" }) }
    var serverIp by remember { mutableStateOf("188.114.97.1") }
    var serverPort by remember { mutableIntStateOf(443) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Generate MASQUE (HTTP/3)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = "Generates a Cloudflare MASQUE (RFC 9298 Connect-IP) configuration with HTTP/3 encapsulation and TLS SNI spoofing modeled after LxBox for optimal Russian ISP/TSPU bypass.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Profile Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = licenseKey,
                    onValueChange = { licenseKey = it },
                    label = { Text("WARP+ License Key (Optional)") },
                    placeholder = { Text("Leave empty for free WARP") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = sni,
                    onValueChange = { sni = it },
                    label = { Text("TLS SNI Domain (Bypass / Spoof)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Quick SNI suggestions
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("vk.com", "yandex.ru", "engage.cloudflareclient.com", "cloudflare.com").forEach { domain ->
                        FilterChip(
                            selected = sni == domain,
                            onClick = { sni = domain },
                            label = { Text(domain, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyberCyan.copy(alpha = 0.2f),
                                selectedLabelColor = CyberCyan
                            )
                        )
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = serverIp,
                        onValueChange = { serverIp = it },
                        label = { Text("Server IP") },
                        modifier = Modifier.weight(2f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = serverPort.toString(),
                        onValueChange = { serverPort = it.toIntOrNull() ?: 443 },
                        label = { Text("Port") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onGenerate(
                                name,
                                licenseKey.ifBlank { null },
                                sni.ifBlank { null },
                                serverIp,
                                serverPort
                            )
                        },
                        enabled = !isGenerating,
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text("Generate MASQUE")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MasqueJsonViewerDialog(
    jsonText: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Terminal, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sing-box MASQUE Configuration")
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Ready to import into sing-box, Clash.Meta, or Nekobox with native MASQUE (HTTP/3 Connect-IP) support:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(280.dp)
                ) {
                    Text(
                        text = jsonText,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                        modifier = Modifier.padding(10.dp).verticalScroll(rememberScrollState())
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Sing-box MASQUE Config", jsonText))
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Copy Sing-box JSON")
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = {
                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(android.content.Intent.EXTRA_SUBJECT, "masque-singbox.json")
                            putExtra(android.content.Intent.EXTRA_TEXT, jsonText)
                        }
                        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share MASQUE Config"))
                    }
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share")
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}
