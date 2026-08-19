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
 * [GeneticDiagnostics] for automated stagnation detection, hypermutation, and island model migration.
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
        baseConfig: AwgConfig,
        populationSize: Int = 12,
        maxGenerations: Int = 15,
        targetUrls: List<String> = pingTester.defaultTargets
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
        var mutationStrategy = MutationStrategy(mutationRate = 0.20, magnitude = 0.25)

        val seedGenome = Genome(
            jc = baseConfig.jc,
            jmin = baseConfig.jmin,
            jmax = baseConfig.jmax,
            s1 = baseConfig.s1,
            s2 = baseConfig.s2,
            s3 = baseConfig.s3,
            s4 = baseConfig.s4,
            h1 = baseConfig.h1,
            h2 = baseConfig.h2,
            h3 = baseConfig.h3,
            h4 = baseConfig.h4,
            mtu = baseConfig.mtu
        ).validated()

        var population = populationManager.createInitialPopulation(seedGenome)
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
            logs = listOf("Initializing Genetic Evolution with ${population.size} candidate genomes..."),
            generationHistory = emptyList()
        )

        try {
            for (generation in 1..effectiveGens) {
                if (isCancelled) break

                val evaluatedPopulation = mutableListOf<Genome>()

                for ((idx, candidateGenome) in population.withIndex()) {
                    if (isCancelled) break

                    val candidateIndex = idx + 1
                    val logEntry = "Gen $generation | Candidate #$candidateIndex (Jc=${candidateGenome.jc}, S1=${candidateGenome.s1}, MTU=${candidateGenome.mtu})..."
                    logs.add(0, logEntry)

                    // Emit StateFlow progress for candidate start
                    _progress.value = _progress.value.copy(
                        phase = EvolutionPhase.EVALUATING,
                        currentGeneration = generation,
                        currentGenomeIndex = candidateIndex,
                        logs = logs.take(60)
                    )

                    // Evaluate fitness
                    val rawFitnessResult = evaluator.evaluate(candidateGenome, baseConfig, targetUrls)

                    // State machine validation
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

                    // Persist generation step to database
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
                    evaluatedPopulation.add(seedGenome.copy(fitness = 10.0, generation = generation))
                }

                // Sort by fitness descending
                val sorted = evaluatedPopulation.sortedByDescending { it.fitness }
                val currentGenBest = sorted.first()

                if (overallBest == null || currentGenBest.fitness > overallBest.fitness) {
                    overallBest = currentGenBest
                }

                history.add(Pair(generation, overallBest.fitness))

                // Genetic Diagnostics Observation: Check for Stagnation / Island Migration
                val islandImmigrants = diagnostics.evaluateGeneration(
                    generation = generation,
                    currentBestFitness = currentGenBest.fitness,
                    populationSize = effectivePopSize,
                    seedGenome = seedGenome
                )

                val diagnosticNote = when {
                    islandImmigrants != null -> {
                        logs.add(0, "⚠️ Stagnation detected across 3 gens! Triggering Island Migration (${islandImmigrants.size} immigrants) & Hypermutation (rate: ${diagnostics.currentMutationRate})")
                        "Island Migration (${islandImmigrants.size} immigrants) + Hypermutation Active"
                    }
                    diagnostics.isHypermutationActive -> {
                        "Hypermutation Active (Rate: ${(diagnostics.currentMutationRate * 100).toInt()}%)"
                    }
                    else -> null
                }

                // Update mutation strategy if hypermutation is active
                mutationStrategy = MutationStrategy(
                    mutationRate = diagnostics.currentMutationRate,
                    magnitude = diagnostics.currentMagnitude
                )

                val genSummary = "✓ Gen $generation Complete | Top: ${"%.2f".format(currentGenBest.fitness)} | Latency: ${currentGenBest.avgPingMs}ms | Anti-DPI Jc: ${currentGenBest.jc}"
                logs.add(0, genSummary)

                // Emit StateFlow progress for generation completion
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

                // Transition phase: Breeding next generation
                _progress.value = _progress.value.copy(phase = EvolutionPhase.BREEDING)

                val nextGen = mutableListOf<Genome>()

                // 1. Elitism: Retain top 2 genomes unaltered
                nextGen.add(sorted[0].copy(generation = generation + 1).validated())
                if (sorted.size > 1) {
                    nextGen.add(sorted[1].copy(generation = generation + 1).validated())
                }

                // 2. Island model integration if immigrants exist
                if (islandImmigrants != null) {
                    for (immigrant in islandImmigrants) {
                        if (nextGen.size < effectivePopSize) {
                            nextGen.add(immigrant)
                        }
                    }
                }

                // 3. Selection, Crossover & Mutation for remaining slots
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
                logs = (listOf("Genetic Evolution complete. Best Anti-DPI profile converged.") + logs).take(60)
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
