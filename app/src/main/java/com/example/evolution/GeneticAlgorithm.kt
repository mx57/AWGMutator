package com.example.evolution

import com.example.data.local.entity.EvolutionLogEntity
import com.example.data.remote.PingTester
import com.example.domain.model.AwgConfig
import com.example.domain.model.Genome
import com.example.domain.repository.EvolutionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class EvolutionPhase {
    IDLE,
    INITIALIZING,
    EVALUATING,
    GENERATION_COMPLETE,
    BREEDING,
    COMPLETED,
    CANCELLED
}

data class EvolutionProgress(
    val isRunning: Boolean = false,
    val phase: EvolutionPhase = EvolutionPhase.IDLE,
    val currentGeneration: Int = 0,
    val maxGenerations: Int = 15,
    val currentGenomeIndex: Int = 0,
    val populationSize: Int = 12,
    val bestFitness: Double = 0.0,
    val bestPingMs: Long = 0L,
    val bestGenome: Genome? = null,
    val currentPopulation: List<Genome> = emptyList(),
    val logs: List<String> = emptyList(),
    val generationHistory: List<Pair<Int, Double>> = emptyList(),
    val latestLatencyMs: Long = 0L,
    val latestSuccessRate: Double = 1.0,
    val recentProbes: List<Pair<Long, Double>> = emptyList(),
    val isHypermutation: Boolean = false,
    val diagnosticNote: String? = null
)

/**
 * Robust Genetic Algorithm engine managing the evolutionary state machine with
 * multi-config seed pooling, stagnation detection, hypermutation, and island model migration.
 */
class GeneticAlgorithm(
    private val pingTester: PingTester,
    private val evolutionRepository: EvolutionRepository
) {
    private val _progress = MutableStateFlow(EvolutionProgress())
    val progress: StateFlow<EvolutionProgress> = _progress.asStateFlow()

    @Volatile
    private var isCancelled = false
    private val evaluator = FitnessEvaluator(pingTester)
    private val diagnostics = GeneticDiagnostics(stagnationThreshold = 3)

    fun stop() {
        isCancelled = true
        _progress.value = _progress.value.copy(
            isRunning = false,
            phase = EvolutionPhase.CANCELLED
        )
    }

    suspend fun runEvolution(
        seedConfigs: List<AwgConfig>,
        populationSize: Int = 12,
        maxGenerations: Int = 15,
        targetUrls: List<String> = pingTester.defaultTargets,
        settings: com.example.domain.model.EvolutionSettings = com.example.domain.model.EvolutionSettings()
    ): Genome? {
        isCancelled = false
        diagnostics.reset()
        val sessionId = UUID.randomUUID().toString()
        val effectivePopSize = populationSize.coerceIn(4, 32)
        val effectiveGens = maxGenerations.coerceIn(2, 50)

        val populationManager = Population(
            size = effectivePopSize,
            tournamentSize = 3.coerceAtMost(effectivePopSize - 1),
            eliteCount = 2
        )
        val crossoverStrategy = CrossoverStrategy(crossoverRate = 0.75)
        var mutationStrategy = MutationStrategy(
            mutationRate = settings.mutationRate.toDouble(),
            magnitude = 0.25,
            settings = settings
        )

        // Convert all provided configs to seed genomes
        val seedGenomes = seedConfigs.map { cfg ->
            Genome(
                jc = cfg.jc,
                jmin = cfg.jmin,
                jmax = cfg.jmax,
                s1 = cfg.s1,
                s2 = cfg.s2,
                s3 = cfg.s3,
                s4 = cfg.s4,
                h1 = cfg.h1,
                h2 = cfg.h2,
                h3 = cfg.h3,
                h4 = cfg.h4,
                i1 = cfg.i1,
                sni = cfg.sni,
                endpoint = cfg.endpoint,
                mtu = cfg.mtu
            ).validated()
        }.ifEmpty {
            listOf(Genome().validated())
        }

        val primarySeed = seedGenomes.first()
        val baseConfig = seedConfigs.firstOrNull() ?: AwgConfig(
            name = "Base Seed",
            privateKey = "a".repeat(43) + "="
        )

        // Create population from the diverse multi-config seed pool
        var population = mutableListOf<Genome>()
        for (i in 0 until effectivePopSize) {
            val seed = seedGenomes[i % seedGenomes.size]
            val mutatedSeed = if (i < seedGenomes.size) seed else mutationStrategy.mutate(seed)
            population.add(mutatedSeed)
        }

        var overallBest: Genome? = null
        val history = mutableListOf<Pair<Int, Double>>()
        val logs = mutableListOf<String>()
        val probesHistory = mutableListOf<Pair<Long, Double>>()

        _progress.value = EvolutionProgress(
            isRunning = true,
            phase = EvolutionPhase.INITIALIZING,
            currentGeneration = 1,
            maxGenerations = effectiveGens,
            currentGenomeIndex = 0,
            populationSize = effectivePopSize,
            logs = listOf("Seeded ${seedGenomes.size} profile(s) into genetic pool of ${population.size} candidate genomes..."),
            generationHistory = emptyList()
        )

        try {
            for (generation in 1..effectiveGens) {
                if (isCancelled) break

                val evaluatedPopulation = mutableListOf<Genome>()

                for ((idx, candidateGenome) in population.withIndex()) {
                    if (isCancelled) break

                    val candidateIndex = idx + 1
                    val logEntry = "Gen $generation | Specimen #$candidateIndex (Jc=${candidateGenome.jc}, S1=${candidateGenome.s1}, MTU=${candidateGenome.mtu})..."
                    logs.add(0, logEntry)

                    _progress.value = _progress.value.copy(
                        phase = EvolutionPhase.EVALUATING,
                        currentGeneration = generation,
                        currentGenomeIndex = candidateIndex,
                        logs = logs.take(60)
                    )

                    // Evaluate fitness
                    val rawFitnessResult = evaluator.evaluate(candidateGenome, baseConfig, targetUrls)

                    val validatedFitness = sanitizeFitness(rawFitnessResult.fitnessScore)
                    val validatedPing = if (rawFitnessResult.avgPingMs in 1..10000L) rawFitnessResult.avgPingMs else 50L
                    val validatedSuccess = rawFitnessResult.successRate.coerceIn(0.0, 1.0)

                    probesHistory.add(Pair(validatedPing, validatedSuccess))

                    val evaluatedGenome = candidateGenome.copy(
                        fitness = validatedFitness,
                        avgPingMs = validatedPing,
                        successRate = validatedSuccess,
                        generation = generation
                    ).validated()

                    evaluatedPopulation.add(evaluatedGenome)

                    _progress.value = _progress.value.copy(
                        latestLatencyMs = validatedPing,
                        latestSuccessRate = validatedSuccess,
                        recentProbes = probesHistory.takeLast(24)
                    )

                    evolutionRepository.recordLog(
                        EvolutionLogEntity(
                            sessionId = sessionId,
                            generation = generation,
                            genomeIndex = candidateIndex,
                            avgPingMs = validatedPing,
                            successRate = validatedSuccess,
                            fitness = validatedFitness,
                            jc = evaluatedGenome.jc,
                            s1 = evaluatedGenome.s1,
                            s2 = evaluatedGenome.s2,
                            s3 = evaluatedGenome.s3,
                            s4 = evaluatedGenome.s4,
                            h1 = evaluatedGenome.h1,
                            h2 = evaluatedGenome.h2,
                            h3 = evaluatedGenome.h3,
                            h4 = evaluatedGenome.h4
                        )
                    )
                }

                if (isCancelled) break

                if (evaluatedPopulation.isEmpty()) {
                    evaluatedPopulation.add(primarySeed.copy(fitness = 10.0, generation = generation))
                }

                val sorted = evaluatedPopulation.sortedByDescending { it.fitness }
                val currentGenBest = sorted.first()

                if (overallBest == null || currentGenBest.fitness > overallBest.fitness) {
                    overallBest = currentGenBest
                }

                history.add(Pair(generation, overallBest.fitness))

                val islandImmigrants = diagnostics.evaluateGeneration(
                    generation = generation,
                    currentBestFitness = currentGenBest.fitness,
                    populationSize = effectivePopSize,
                    seedGenome = primarySeed
                )

                val diagnosticNote = when {
                    islandImmigrants != null -> {
                        logs.add(0, "⚠️ Stagnation detected! Triggering Island Migration (${islandImmigrants.size} immigrants) & Hypermutation")
                        "Island Migration (${islandImmigrants.size} immigrants) + Hypermutation"
                    }
                    diagnostics.isHypermutationActive -> {
                        "Hypermutation Active (Rate: ${(diagnostics.currentMutationRate * 100).toInt()}%)"
                    }
                    else -> null
                }

                mutationStrategy = MutationStrategy(
                    mutationRate = diagnostics.currentMutationRate,
                    magnitude = diagnostics.currentMagnitude
                )

                val genSummary = "✓ Gen $generation Complete | Top Fitness: ${"%.2f".format(currentGenBest.fitness)} | Latency: ${currentGenBest.avgPingMs}ms | Jc: ${currentGenBest.jc}"
                logs.add(0, genSummary)

                _progress.value = _progress.value.copy(
                    phase = EvolutionPhase.GENERATION_COMPLETE,
                    currentGeneration = generation,
                    currentGenomeIndex = effectivePopSize,
                    bestFitness = overallBest.fitness,
                    bestPingMs = overallBest.avgPingMs,
                    bestGenome = overallBest,
                    currentPopulation = sorted,
                    logs = logs.take(60),
                    generationHistory = history.toList(),
                    isHypermutation = diagnostics.isHypermutationActive,
                    diagnosticNote = diagnosticNote
                )

                if (generation == effectiveGens) break

                _progress.value = _progress.value.copy(phase = EvolutionPhase.BREEDING)

                val nextGen = mutableListOf<Genome>()

                // 1. Elitism: Top 2 preserved
                nextGen.add(sorted[0].copy(generation = generation + 1).validated())
                if (sorted.size > 1) {
                    nextGen.add(sorted[1].copy(generation = generation + 1).validated())
                }

                // 2. Island model integration
                if (islandImmigrants != null) {
                    for (immigrant in islandImmigrants) {
                        if (nextGen.size < effectivePopSize) {
                            nextGen.add(immigrant)
                        }
                    }
                }

                // 3. Selection, Crossover & Mutation
                while (nextGen.size < effectivePopSize) {
                    val parentA = populationManager.selectParent(sorted)
                    val parentB = populationManager.selectParent(sorted)

                    val (childA, childB) = crossoverStrategy.crossover(parentA, parentB, generation + 1)
                    nextGen.add(mutationStrategy.mutate(childA))
                    if (nextGen.size < effectivePopSize) {
                        nextGen.add(mutationStrategy.mutate(childB))
                    }
                }

                population = nextGen
            }
        } catch (e: Exception) {
            if (e !is CancellationException) {
                logs.add(0, "Evolution Engine notice: ${e.localizedMessage}")
            }
        } finally {
            _progress.value = _progress.value.copy(
                isRunning = false,
                phase = if (isCancelled) EvolutionPhase.CANCELLED else EvolutionPhase.COMPLETED,
                logs = (listOf("🧬 Evolution run finalized. Superior Anti-DPI parameters selected.") + logs).take(60)
            )
        }

        return overallBest
    }

    private fun sanitizeFitness(score: Double): Double {
        return if (score.isNaN() || score.isInfinite() || score <= 0.0) {
            1.0
        } else {
            score
        }
    }
}
