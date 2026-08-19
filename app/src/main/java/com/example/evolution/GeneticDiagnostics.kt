package com.example.evolution

import com.example.domain.model.Genome
import java.util.Random

/**
 * Diagnostic event emitted when population fitness stagnation or evolutionary leaps occur.
 */
sealed class DiagnosticEvent {
    data class StagnationDetected(val consecutiveGenerations: Int, val bestFitness: Double) : DiagnosticEvent()
    data class HypermutationTriggered(val boostedMutationRate: Double, val magnitude: Double) : DiagnosticEvent()
    data class IslandMigrationTriggered(val immigrantCount: Int, val islandGenomes: List<Genome>) : DiagnosticEvent()
    data class FitnessImprovement(val generation: Int, val newBestFitness: Double, val previousBest: Double) : DiagnosticEvent()
}

/**
 * Observer that monitors evolutionary population health, detects fitness stagnation,
 * and dynamically triggers hypermutation bursts or 'island model' immigrant introductions
 * when no fitness improvement is observed for 3 consecutive generations.
 */
class GeneticDiagnostics(
    private val stagnationThreshold: Int = 3,
    private val standardMutationRate: Double = 0.20,
    private val hypermutationRate: Double = 0.55,
    private val standardMagnitude: Double = 0.25,
    private val hyperMagnitude: Double = 0.60
) {
    private var lastBestFitness: Double = 0.0
    private var stagnantGenerationCount: Int = 0
    private val random = Random()

    var currentMutationRate: Double = standardMutationRate
        private set

    var currentMagnitude: Double = standardMagnitude
        private set

    var isHypermutationActive: Boolean = false
        private set

    private val _diagnosticEvents = mutableListOf<DiagnosticEvent>()
    val diagnosticEvents: List<DiagnosticEvent> get() = _diagnosticEvents.toList()

    fun reset() {
        lastBestFitness = 0.0
        stagnantGenerationCount = 0
        currentMutationRate = standardMutationRate
        currentMagnitude = standardMagnitude
        isHypermutationActive = false
        _diagnosticEvents.clear()
    }

    /**
     * Inspects the current generation's best fitness score and returns adaptive strategies
     * (e.g. immigrant genomes for island migration, or updated mutation parameters).
     */
    fun evaluateGeneration(
        generation: Int,
        currentBestFitness: Double,
        populationSize: Int,
        seedGenome: Genome
    ): List<Genome>? {
        if (currentBestFitness > lastBestFitness + 0.001) {
            // Fitness improved!
            if (lastBestFitness > 0.0) {
                _diagnosticEvents.add(
                    DiagnosticEvent.FitnessImprovement(
                        generation = generation,
                        newBestFitness = currentBestFitness,
                        previousBest = lastBestFitness
                    )
                )
            }
            lastBestFitness = currentBestFitness
            stagnantGenerationCount = 0

            // Reset hypermutation back to baseline
            currentMutationRate = standardMutationRate
            currentMagnitude = standardMagnitude
            isHypermutationActive = false
            return null
        }

        // Stagnation detected
        stagnantGenerationCount++
        _diagnosticEvents.add(
            DiagnosticEvent.StagnationDetected(
                consecutiveGenerations = stagnantGenerationCount,
                bestFitness = currentBestFitness
            )
        )

        if (stagnantGenerationCount >= stagnationThreshold) {
            // Trigger Hypermutation
            isHypermutationActive = true
            currentMutationRate = hypermutationRate
            currentMagnitude = hyperMagnitude
            _diagnosticEvents.add(
                DiagnosticEvent.HypermutationTriggered(
                    boostedMutationRate = currentMutationRate,
                    magnitude = currentMagnitude
                )
            )

            // Trigger Island Model Migration: Create a set of diverse foreign immigrant genomes
            val immigrantCount = (populationSize * 0.35).toInt().coerceAtLeast(2)
            val immigrantGenomes = generateIslandImmigrants(immigrantCount, generation, seedGenome)

            _diagnosticEvents.add(
                DiagnosticEvent.IslandMigrationTriggered(
                    immigrantCount = immigrantCount,
                    islandGenomes = immigrantGenomes
                )
            )

            // Reset counter so we give the island immigrants a chance to integrate
            stagnantGenerationCount = 0
            return immigrantGenomes
        }

        return null
    }

    private fun generateIslandImmigrants(count: Int, generation: Int, seed: Genome): List<Genome> {
        val immigrants = mutableListOf<Genome>()
        for (i in 1..count) {
            val jmin = 32 + random.nextInt(180)
            val jmax = jmin + 128 + random.nextInt(512)
            val immigrant = Genome(
                jc = 1 + random.nextInt(9),
                jmin = jmin,
                jmax = jmax,
                s1 = 12 + random.nextInt(48),
                s2 = 16 + random.nextInt(48),
                s3 = 10 + random.nextInt(40),
                s4 = 6 + random.nextInt(24),
                h1 = (random.nextLong() and 0x7FFFFFFF) + 2000000L,
                h2 = (random.nextLong() and 0x7FFFFFFF) + 4000000L,
                h3 = (random.nextLong() and 0x7FFFFFFF) + 6000000L,
                h4 = (random.nextLong() and 0x7FFFFFFF) + 8000000L,
                mtu = 1280 + random.nextInt(120),
                generation = generation
            ).validated()
            immigrants.add(immigrant)
        }
        return immigrants
    }
}
