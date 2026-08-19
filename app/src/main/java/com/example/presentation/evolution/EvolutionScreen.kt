package com.example.presentation.evolution

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import com.example.domain.model.EvolutionSettings
import com.example.domain.model.Genome
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
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "🧬 Genetic Evolution",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = CyberCyan
                )
                Text(
                    text = "Optimizes AWG obfuscation, SNI, and DNS by live latency",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedButton(
                onClick = { viewModel.toggleSettingDialog(true) },
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, CyberCyan)
            ) {
                Icon(Icons.Default.Tune, contentDescription = "Gene Settings", tint = CyberCyan, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Genes", style = MaterialTheme.typography.labelSmall, color = CyberCyan)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Target Configuration Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Base Seed Configuration",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = CyberPurple
                )
                Spacer(modifier = Modifier.height(6.dp))

                Box {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp)),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        onClick = { baseMenuExpanded = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = baseConfig?.name ?: "No configs found (Create one in Configs tab)",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = "Change ▾",
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
                                text = { Text(cfg.name) },
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

        Spacer(modifier = Modifier.height(12.dp))

        // Realtime Latency Canvas
        RealtimeLatencyCanvas(
            progress = progress
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
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
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("start_stop_evolution_button")
            ) {
                if (progress.isRunning) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Stop Evolution")
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Start Evolution")
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Progress Section
        if (progress.isRunning || progress.currentGeneration > 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Generation ${progress.currentGeneration} / ${progress.maxGenerations}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = NeonGreen
                        )
                        Text(
                            text = "Phase: ${progress.phase}",
                            style = MaterialTheme.typography.bodySmall,
                            color = CyberCyan
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (progress.currentGeneration.toFloat() / progress.maxGenerations.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = NeonGreen,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Best Fitness", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text("${"%.2f".format(progress.bestFitness)}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = CyberCyan)
                        }
                        Column {
                            Text("Min Latency", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text("${progress.bestPingMs} ms", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = NeonGreen)
                        }
                        Column {
                            Text("Population", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text("${progress.currentGenomeIndex}/${progress.populationSize}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Best Evolved Genome Card
        progress.bestGenome?.let { best ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, NeonGreen)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "👑 Champion Genome (Gen ${best.generation})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = NeonGreen
                        )
                        Text(
                            text = "${best.avgPingMs} ms",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = NeonGreen
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Jc=${best.jc} | Jmin=${best.jmin} Jmax=${best.jmax} | S1=${best.s1} S2=${best.s2} S3=${best.s3} S4=${best.s4}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "H1=${best.h1} | H2=${best.h2} | H3=${best.h3} | H4=${best.h4} | MTU=${best.mtu}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = CyberCyan
                    )
                    if (!best.sni.isNullOrBlank()) {
                        Text(
                            text = "SNI: ${best.sni}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = CyberPurple
                        )
                    }
                    if (!best.dns.isNullOrBlank()) {
                        Text(
                            text = "DNS: ${best.dns}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = NeonGreen
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.applyEvolvedConfig(best) },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Apply & Connect", color = MaterialTheme.colorScheme.onPrimary)
                        }

                        Button(
                            onClick = { viewModel.saveEvolvedConfig(best) },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save to List", color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Logs Console
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Telemetry & Mutation Logs",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = CyberCyan
                )
                Spacer(modifier = Modifier.height(6.dp))

                Box(modifier = Modifier.height(160.dp)) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(progress.logs) { log ->
                            Text(
                                text = log,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
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
