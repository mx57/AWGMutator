package com.example.presentation.configs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
                        it.endpoint.contains(uiState.searchQuery, ignoreCase = true)
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
                    modifier = Modifier.padding(bottom = 8.dp).testTag("import_fab")
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = "Import Config")
                }
                FloatingActionButton(
                    onClick = { viewModel.showAddDialog(true) },
                    containerColor = CyberCyan,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("add_custom_fab")
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
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Configurations",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${configs.size} Profiles stored in local database",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = { viewModel.showWarpDialog(true) },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberPurple),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("generate_warp_header_button")
                ) {
                    if (uiState.isGenerating) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New WARP", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            // Search Bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search profiles by name or IP...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CyberCyan) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("config_search_bar"),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (filteredConfigs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No configurations found",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap 'New WARP' or + to add an AmneziaWG profile",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredConfigs, key = { it.id }) { config ->
                        ConfigItemCard(
                            config = config,
                            onDelete = { viewModel.deleteConfig(config.id) },
                            onDuplicate = { viewModel.duplicateConfig(config) },
                            onShowQr = { viewModel.showQrDialog(config) },
                            onExport = { viewModel.showExportDialog(config) },
                            onShare = { viewModel.shareConfigFile(context, config) }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }

    // Dialogs
    if (uiState.showWarpDialog) {
        AdvancedWarpDialog(
            onDismiss = { viewModel.showWarpDialog(false) },
            onGenerate = { name, license, dns, endpoint, antiDpi ->
                viewModel.generateAdvancedWarp(name, license, dns, endpoint, antiDpi)
            }
        )
    }

    if (uiState.showAddDialog) {
        AddAwgConfigDialog(
            onDismiss = { viewModel.showAddDialog(false) },
            onCreate = { name, endpoint, peerKey, preset, jc, jmin, jmax, s1, s2, s3, s4, h1, h2, h3, h4, mtu ->
                viewModel.createCustomAwg(name, endpoint, peerKey, preset, jc, jmin, jmax, s1, s2, s3, s4, h1, h2, h3, h4, mtu)
            }
        )
    }

    if (uiState.showImportDialog) {
        ImportConfigDialog(
            onDismiss = { viewModel.showImportDialog(false) },
            onImport = { text, name -> viewModel.importConfig(text, name) }
        )
    }

    uiState.activeQrConfig?.let { config ->
        QrCodeViewerDialog(
            config = config,
            onDismiss = { viewModel.showQrDialog(null) }
        )
    }

    uiState.activeExportConfig?.let { config ->
        ExportConfigDialog(
            config = config,
            onDismiss = { viewModel.showExportDialog(null) }
        )
    }
}

@Composable
fun ConfigItemCard(
    config: AwgConfig,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onShowQr: () -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("config_item_${config.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
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
                        color = if (config.isWarp) CyberPurple.copy(alpha = 0.2f) else CyberCyan.copy(alpha = 0.2f)
                    ) {
                        Icon(
                            imageVector = if (config.isWarp) Icons.Default.Security else Icons.Default.Tune,
                            contentDescription = null,
                            tint = if (config.isWarp) CyberPurple else CyberCyan,
                            modifier = Modifier.padding(6.dp).size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = config.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${config.endpoint} • DNS ${config.dns}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row {
                    IconButton(onClick = onDuplicate) {
                        Icon(Icons.Default.Difference, contentDescription = "Duplicate", tint = CyberCyan, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onShowQr) {
                        Icon(Icons.Default.QrCode, contentDescription = "QR Code", tint = CyberCyan, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onShare) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = DangerRed, modifier = Modifier.size(20.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Parameter Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ParamChip("Jc: ${config.jc}")
                ParamChip("Jmin/max: ${config.jmin}-${config.jmax}")
                ParamChip("S1..S4: ${config.s1},${config.s2},${config.s3},${config.s4}")
                ParamChip("MTU: ${config.mtu}")
            }
        }
    }
}

@Composable
fun ParamChip(label: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun AdvancedWarpDialog(
    onDismiss: () -> Unit,
    onGenerate: (name: String, license: String?, dns: String, endpoint: String, antiDpi: Boolean) -> Unit
) {
    var name by remember { mutableStateOf("Cloudflare WARP Ultra") }
    var licenseKey by remember { mutableStateOf("") }
    var dnsChoice by remember { mutableStateOf("1.1.1.1, 1.0.0.1") }
    var endpointChoice by remember { mutableStateOf("162.159.193.1:2408") }
    var injectAntiDpi by remember { mutableStateOf(true) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Generate Cloudflare WARP",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = CyberPurple
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Profile Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = licenseKey,
                    onValueChange = { licenseKey = it },
                    label = { Text("WARP+ License Key (Optional)") },
                    placeholder = { Text("e.g. 26-char key for unlimited quota") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = endpointChoice,
                    onValueChange = { endpointChoice = it },
                    label = { Text("WARP Endpoint (IP:Port)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = dnsChoice,
                    onValueChange = { dnsChoice = it },
                    label = { Text("DNS (1.1.1.1, 1.1.1.2 malware block...)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Anti-DPI Obfuscation injection toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Inject AmneziaWG Anti-DPI Headers", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        Text("Adds Jc junk packets and S1-S4 randomized headers to bypass ISP blocking", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = injectAntiDpi,
                        onCheckedChange = { injectAntiDpi = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = CyberCyan, checkedTrackColor = CyberCyan.copy(alpha = 0.5f))
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onGenerate(name, licenseKey.ifBlank { null }, dnsChoice, endpointChoice, injectAntiDpi)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberPurple)
                    ) {
                        Text("Generate WARP", color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    }
}

@Composable
fun AddAwgConfigDialog(
    onDismiss: () -> Unit,
    onCreate: (
        name: String,
        endpoint: String,
        peerKey: String,
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
        mtu: Int
    ) -> Unit
) {
    var name by remember { mutableStateOf("AmneziaWG Server") }
    var endpoint by remember { mutableStateOf("185.195.23.4:51820") }
    var peerKey by remember { mutableStateOf("") }
    var selectedPreset by remember { mutableStateOf<ObfuscationPreset?>(ObfuscationPreset.BALANCED) }

    var jc by remember { mutableIntStateOf(4) }
    var jmin by remember { mutableIntStateOf(64) }
    var jmax by remember { mutableIntStateOf(512) }
    var s1 by remember { mutableIntStateOf(15) }
    var s2 by remember { mutableIntStateOf(30) }
    var s3 by remember { mutableIntStateOf(10) }
    var s4 by remember { mutableIntStateOf(8) }
    var h1 by remember { mutableLongStateOf(1234567890L) }
    var h2 by remember { mutableLongStateOf(2345678901L) }
    var h3 by remember { mutableLongStateOf(3456789012L) }
    var h4 by remember { mutableLongStateOf(4123456789L) }
    var mtu by remember { mutableIntStateOf(1360) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "New AmneziaWG Config",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = CyberCyan
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Profile Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = endpoint,
                    onValueChange = { endpoint = it },
                    label = { Text("Endpoint (IP:Port)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = peerKey,
                    onValueChange = { peerKey = it },
                    label = { Text("Peer PublicKey (Auto-generated if empty)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))
                Text("Obfuscation Headers (AmneziaWG 2.0)", style = MaterialTheme.typography.titleSmall, color = CyberPurple)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = jc.toString(),
                        onValueChange = { jc = it.toIntOrNull() ?: jc },
                        label = { Text("Jc (0-10)") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = mtu.toString(),
                        onValueChange = { mtu = it.toIntOrNull() ?: mtu },
                        label = { Text("MTU") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = jmin.toString(),
                        onValueChange = { jmin = it.toIntOrNull() ?: jmin },
                        label = { Text("Jmin") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = jmax.toString(),
                        onValueChange = { jmax = it.toIntOrNull() ?: jmax },
                        label = { Text("Jmax") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = s1.toString(),
                        onValueChange = { s1 = it.toIntOrNull() ?: s1 },
                        label = { Text("S1") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = s2.toString(),
                        onValueChange = { s2 = it.toIntOrNull() ?: s2 },
                        label = { Text("S2") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = s3.toString(),
                        onValueChange = { s3 = it.toIntOrNull() ?: s3 },
                        label = { Text("S3") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = s4.toString(),
                        onValueChange = { s4 = it.toIntOrNull() ?: s4 },
                        label = { Text("S4") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onCreate(name, endpoint, peerKey, selectedPreset, jc, jmin, jmax, s1, s2, s3, s4, h1, h2, h3, h4, mtu)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
                    ) {
                        Text("Save Profile", color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    }
}

@Composable
fun ImportConfigDialog(
    onDismiss: () -> Unit,
    onImport: (text: String, name: String) -> Unit
) {
    var configText by remember { mutableStateOf("") }
    var configName by remember { mutableStateOf("Imported Profile") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import .conf File") },
        text = {
            Column {
                OutlinedTextField(
                    value = configName,
                    onValueChange = { configName = it },
                    label = { Text("Configuration Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = configText,
                    onValueChange = { configText = it },
                    label = { Text("Paste .conf content ([Interface], [Peer]...)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    maxLines = 15
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onImport(configText, configName) },
                enabled = configText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
            ) {
                Text("Import", color = MaterialTheme.colorScheme.onPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun QrCodeViewerDialog(
    config: AwgConfig,
    onDismiss: () -> Unit
) {
    val qrBitmap = remember(config) {
        QrCodeGenerator.generateQrBitmap(config.toConfString(), 400)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = config.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(16.dp))

                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "QR Code",
                    modifier = Modifier.size(240.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Scan with AmneziaVPN or WireGuard client",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)) {
                    Text("Close", color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

@Composable
fun ExportConfigDialog(
    config: AwgConfig,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val confString = remember(config) { config.toConfString() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(config.name) },
        text = {
            Column {
                Text("Raw WireGuard / AmneziaWG Configuration:", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    Text(
                        text = confString,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                        modifier = Modifier
                            .padding(10.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("AWG Config", confString))
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Copy to Clipboard", color = MaterialTheme.colorScheme.onPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}
