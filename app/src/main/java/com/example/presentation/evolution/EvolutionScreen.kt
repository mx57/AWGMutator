package com.example.presentation.evolution

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.domain.model.BlockedServicesCatalog
import com.example.domain.model.EvolutionSettings
import com.example.domain.model.Genome
import com.example.domain.model.ServiceProbeResult
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberPurple
import com.example.ui.theme.DangerRed
import com.example.ui.theme.NeonGreen

@Composable
fun EvolutionScreen(
    snackbarHostState: SnackbarHostState,
    viewModel: EvolutionViewModel = viewModel()
) {
    val progress by viewModel.evolutionProgress.collectAsStateWithLifecycle()
    val configs by viewModel.configs.collectAsStateWithLifecycle()
    val screenState by viewModel.screenState.collectAsStateWithLifecycle()

    LaunchedEffect(screenState.userMessage) {
        screenState.userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    var baseMenuExpanded by remember { mutableStateOf(false) }
    val baseConfig = screenState.selectedBaseConfig ?: configs.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        // Compact Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "🧬 Genetic Evolution",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = CyberCyan
                )
                Text(
                    text = "Multi-config seeding • Latency fitness optimizer",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedButton(
                onClick = { viewModel.toggleSettingDialog(true) },
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, CyberCyan),
                modifier = Modifier.height(34.dp)
            ) {
                Icon(Icons.Default.Tune, contentDescription = "Genes", tint = CyberCyan, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Gene Toggles", style = MaterialTheme.typography.labelSmall, color = CyberCyan)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Multi-Config Seed Pool & Selection Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Seed Pool Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Seed Pool (${configs.size} Profiles Available)",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = NeonGreen
                        )
                        Text(
                            text = if (screenState.useAllConfigsAsSeeds) "Using ALL saved configs as evolutionary seed population" else "Using single selected profile as seed",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = screenState.useAllConfigsAsSeeds,
                        onCheckedChange = { viewModel.setUseAllConfigsAsSeeds(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = NeonGreen)
                    )
                }

                if (!screenState.useAllConfigsAsSeeds) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp)),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            onClick = { baseMenuExpanded = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = baseConfig?.name ?: "No configs found (Create one in Configs tab)",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = "Choose ▾",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CyberCyan
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = baseMenuExpanded,
                            onDismissRequest = { baseMenuExpanded = false }
                        ) {
                            configs.forEach { cfg ->
                                DropdownMenuItem(
                                    text = { Text(cfg.name, fontSize = 13.sp) },
                                    onClick = {
                                        viewModel.selectBaseConfig(cfg)
                                        baseMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Realtime Latency Canvas
        RealtimeLatencyCanvas(
            progress = progress
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Start / Stop Controls
        Button(
            onClick = {
                if (progress.isRunning) {
                    viewModel.stopEvolution()
                } else {
                    viewModel.startEvolution()
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (progress.isRunning) DangerRed else CyberCyan
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .testTag("start_stop_evolution_button")
        ) {
            if (progress.isRunning) {
                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Stop Evolution Engine")
            } else {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Start Evolution (${if (screenState.useAllConfigsAsSeeds) "All ${configs.size} Seeds" else baseConfig?.name ?: "Default"})")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Progress Card
        if (progress.isRunning || progress.currentGeneration > 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Generation ${progress.currentGeneration} / ${progress.maxGenerations}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp),
                            color = NeonGreen
                        )
                        Text(
                            text = "Phase: ${progress.phase}",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyberCyan
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (progress.currentGeneration.toFloat() / progress.maxGenerations.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = NeonGreen,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Best Fitness", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text("${"%.2f".format(progress.bestFitness)}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp), color = CyberCyan)
                        }
                        Column {
                            Text("Min Latency", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text("${progress.bestPingMs} ms", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp), color = NeonGreen)
                        }
                        Column {
                            Text("Specimens", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text("${progress.currentGenomeIndex}/${progress.populationSize}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp), color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Real-Time Censored Services Bypass Test Results
        if (progress.isRunning || progress.latestServiceResults.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, if (progress.bestUnblockedCount == progress.totalTargetServicesCount && progress.bestUnblockedCount > 0) NeonGreen.copy(alpha = 0.8f) else CyberCyan.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🛡️ Live Censored Bypass Probe",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (progress.bestUnblockedCount > 0) NeonGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.errorContainer,
                            border = BorderStroke(1.dp, if (progress.bestUnblockedCount > 0) NeonGreen.copy(alpha = 0.5f) else MaterialTheme.colorScheme.error)
                        ) {
                            Text(
                                text = "${progress.bestUnblockedCount}/${progress.totalTargetServicesCount} Unblocked",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (progress.bestUnblockedCount > 0) NeonGreen else MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val servicesToDisplay = if (progress.latestServiceResults.isNotEmpty()) {
                        progress.latestServiceResults
                    } else {
                        BlockedServicesCatalog.allServices.map {
                            ServiceProbeResult(service = it, isAccessible = false, latencyMs = null)
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        servicesToDisplay.chunked(2).forEach { rowServices ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                rowServices.forEach { probe ->
                                    Surface(
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (probe.isAccessible) NeonGreen.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        border = BorderStroke(0.5.dp, if (probe.isAccessible) NeonGreen.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                Text(probe.service.iconEmoji, fontSize = 12.sp)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = probe.service.name,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1
                                                )
                                            }
                                            if (probe.isAccessible) {
                                                Text(
                                                    text = "${probe.latencyMs ?: 0}ms",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                                                    color = NeonGreen
                                                )
                                            } else {
                                                Text(
                                                    text = "Blocked",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                                )
                                            }
                                        }
                                    }
                                }
                                if (rowServices.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }

        // Best Evolved Genome Card
        progress.bestGenome?.let { best ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, NeonGreen)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "👑 Champion Genome (Gen ${best.generation})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp),
                            color = NeonGreen
                        )
                        Text(
                            text = "${best.avgPingMs} ms",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = NeonGreen
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Jc=${best.jc} | Jmin=${best.jmin} Jmax=${best.jmax} | S1=${best.s1} S2=${best.s2} S3=${best.s3} S4=${best.s4}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "H1=${best.h1} | H2=${best.h2} | H3=${best.h3} | H4=${best.h4} | MTU=${best.mtu}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                        color = CyberCyan
                    )
                    if (!best.sni.isNullOrBlank()) {
                        Text(
                            text = "SNI: ${best.sni}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                            color = CyberPurple
                        )
                    }
                    if (!best.dns.isNullOrBlank()) {
                        Text(
                            text = "DNS: ${best.dns}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                            color = NeonGreen
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.applyEvolvedConfig(best) },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1f).height(34.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Apply & Connect", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall)
                        }

                        Button(
                            onClick = { viewModel.saveEvolvedConfig(best) },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1f).height(34.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save to List", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Logs Console
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = "Telemetry & Mutation Logs",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = CyberCyan
                )
                Spacer(modifier = Modifier.height(4.dp))

                Box(modifier = Modifier.height(140.dp)) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(progress.logs) { log ->
                            Text(
                                text = log,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(60.dp))
    }

    // Evolution Settings Dialog
    if (screenState.showSettingsDialog) {
        EvolutionSettingsDialog(
            settings = screenState.evolutionSettings,
            onUpdate = { transform -> viewModel.updateEvolutionSettings(transform) },
            onDismiss = { viewModel.toggleSettingDialog(false) }
        )
    }
}

@Composable
fun EvolutionSettingsDialog(
    settings: EvolutionSettings,
    onUpdate: ((EvolutionSettings) -> EvolutionSettings) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().height(580.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Evolution Parameters & Toggles",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Enable or disable individual genes participating in mutations",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                GeneToggleRow("Mutate Junk Packet Count (Jc)", settings.mutateJc) {
                    onUpdate { s -> s.copy(mutateJc = it) }
                }
                GeneToggleRow("Mutate Junk Packet Spread (Jmin / Jmax)", settings.mutateJminJmax) {
                    onUpdate { s -> s.copy(mutateJminJmax = it) }
                }
                GeneToggleRow("Mutate Split Packets S1 / S2", settings.mutateS1S2) {
                    onUpdate { s -> s.copy(mutateS1S2 = it) }
                }
                GeneToggleRow("Mutate Split Packets S3 / S4", settings.mutateS3S4) {
                    onUpdate { s -> s.copy(mutateS3S4 = it) }
                }
                GeneToggleRow("Mutate Magic Headers (H1..H4)", settings.mutateHeadersH1H4) {
                    onUpdate { s -> s.copy(mutateHeadersH1H4 = it) }
                }
                GeneToggleRow("Mutate Payload Noise (I1 Init)", settings.mutatePayloadNoiseI1) {
                    onUpdate { s -> s.copy(mutatePayloadNoiseI1 = it) }
                }
                GeneToggleRow("Mutate Russian Whitelist SNI", settings.mutateSni) {
                    onUpdate { s -> s.copy(mutateSni = it) }
                }
                GeneToggleRow("Mutate DNS Resolvers", settings.mutateDns) {
                    onUpdate { s -> s.copy(mutateDns = it) }
                }
                GeneToggleRow("Mutate Endpoints (IP:Port)", settings.mutateEndpoints) {
                    onUpdate { s -> s.copy(mutateEndpoints = it) }
                }
                GeneToggleRow("Mutate MTU Range (1280-1420)", settings.mutateMtu) {
                    onUpdate { s -> s.copy(mutateMtu = it) }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
                ) {
                    Text("Save & Close")
                }
            }
        }
    }
}

@Composable
fun GeneToggleRow(label: String, isChecked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = CyberCyan)
        )
    }
}
