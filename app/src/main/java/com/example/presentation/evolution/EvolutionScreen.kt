package com.example.presentation.evolution

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
        Text(
            text = "🧬 Genetic Evolution",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = CyberCyan
        )
        Text(
            text = "Optimizes AWG obfuscation headers & packet padding by live latency",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Base Configuration Picker
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
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
                Column(modifier = Modifier.weight(1f)) {
                    Text("Base Template", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = baseConfig?.name ?: "No Config Selected",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }

                Box {
                    Button(
                        onClick = { baseMenuExpanded = true },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Select", color = MaterialTheme.colorScheme.onPrimary)
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

        // Target Suite Selector
        var targetMenuExpanded by remember { mutableStateOf(false) }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
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
                Column(modifier = Modifier.weight(1f)) {
                    Text("Optimization Target Suite", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = screenState.targetProfile.label,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = CyberCyan
                    )
                }

                Box {
                    OutlinedButton(
                        onClick = { targetMenuExpanded = true },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, CyberCyan)
                    ) {
                        Text("Change", color = CyberCyan)
                    }

                    DropdownMenu(
                        expanded = targetMenuExpanded,
                        onDismissRequest = { targetMenuExpanded = false }
                    ) {
                        EvolutionTargetProfile.values().forEach { profile ->
                            DropdownMenuItem(
                                text = { Text(profile.label) },
                                onClick = {
                                    viewModel.selectTargetProfile(profile)
                                    targetMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Controls (Start / Stop)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!progress.isRunning) {
                Button(
                    onClick = { viewModel.startEvolution() },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("start_evolution_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberPurple)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Start Evolution", fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = { viewModel.stopEvolution() },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("stop_evolution_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Stop Evolution", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (progress.isRunning) {
            Spacer(modifier = Modifier.height(14.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Generation ${progress.currentGeneration}/${progress.maxGenerations} • Candidate #${progress.currentGenomeIndex}/${progress.populationSize}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = CyberCyan
                        )
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = CyberCyan, strokeWidth = 2.dp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    val progressRatio = if (progress.maxGenerations > 0) {
                        (progress.currentGeneration - 1 + (progress.currentGenomeIndex.toFloat() / progress.populationSize.toFloat())) / progress.maxGenerations.toFloat()
                    } else 0f
                    LinearProgressIndicator(
                        progress = { progressRatio.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = CyberCyan,
                        trackColor = MaterialTheme.colorScheme.surface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Realtime Latency & Reachability Canvas
        RealtimeLatencyCanvas(
            progress = progress,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Live Fitness Graph
        Text(
            text = "Fitness Convergence Curve",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))

        FitnessChart(
            history = progress.generationHistory,
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Best Evolved Genome Card
        progress.bestGenome?.let { best ->
            BestGenomeCard(
                genome = best,
                onApply = { viewModel.applyEvolvedConfig(best) },
                onSave = { viewModel.saveEvolvedConfig(best) }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Live Mutation Logs
        Text(
            text = "Live Mutation & Evaluation Logs",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                reverseLayout = false
            ) {
                items(progress.logs) { log ->
                    Text(
                        text = log,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        ),
                        color = if (log.contains("Complete") || log.contains("Best")) NeonGreen else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun FitnessChart(
    history: List<Pair<Int, Double>>,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        if (history.size < 2) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Awaiting generation data points...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                val maxFitness = (history.maxOfOrNull { it.second } ?: 1.0).coerceAtLeast(1.0)
                val minFitness = (history.minOfOrNull { it.second } ?: 0.0)
                val range = (maxFitness - minFitness).coerceAtLeast(0.1)

                val points = history.mapIndexed { idx, pair ->
                    val x = (idx.toFloat() / (history.size - 1).toFloat()) * size.width
                    val y = size.height - (((pair.second - minFitness) / range) * size.height).toFloat()
                    Offset(x, y.coerceIn(0f, size.height))
                }

                val path = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    for (i in 1 until points.size) {
                        lineTo(points[i].x, points[i].y)
                    }
                }

                drawPath(
                    path = path,
                    color = CyberCyan,
                    style = Stroke(width = 3.dp.toPx())
                )

                // Draw dots
                for (pt in points) {
                    drawCircle(color = CyberPurple, radius = 4.dp.toPx(), center = pt)
                    drawCircle(color = Color.White, radius = 2.dp.toPx(), center = pt)
                }
            }
        }
    }
}

@Composable
fun BestGenomeCard(
    genome: Genome,
    onApply: () -> Unit,
    onSave: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("best_genome_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, NeonGreen)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = NeonGreen)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Best Evolved Genome",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = NeonGreen
                    )
                }
                Text(
                    text = "Fitness: ${"%.2f".format(genome.fitness)}",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                    color = NeonGreen
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Ping: ${genome.avgPingMs} ms • Jc: ${genome.jc} • Jmin/max: ${genome.jmin}/${genome.jmax} • MTU: ${genome.mtu}",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            )
            Text(
                text = "Prefixes: S1=${genome.s1}, S2=${genome.s2}, S3=${genome.s3}, S4=${genome.s4}",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            )
            Text(
                text = "Headers: H1=${genome.h1}, H2=${genome.h2}, H3=${genome.h3}, H4=${genome.h4}",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onApply,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Apply to VPN", color = Color.Black, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onSave,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, CyberCyan)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, tint = CyberCyan)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save Config", color = CyberCyan)
                }
            }
        }
    }
}
