package com.example.evolution

import com.example.data.remote.PingTester
import com.example.domain.model.AwgConfig
import com.example.domain.model.BlockedServicesCatalog
import com.example.domain.model.FitnessResult
import com.example.domain.model.Genome
import kotlinx.coroutines.delay

/**
 * Advanced Evaluator that computes a combined Anti-DPI resistance score + network throughput fitness,
 * prioritizing configurations that maintain high success rates and low latency for major censored services
 * (YouTube, Instagram, Telegram, Twitch, X/Twitter, Discord).
 */
class FitnessEvaluator(
    private val pingTester: PingTester
) {
    suspend fun evaluate(
        genome: Genome,
        baseConfig: AwgConfig,
        targetUrls: List<String> = BlockedServicesCatalog.allServices.map { it.testUrl }
    ): FitnessResult {
        // Yield to let UI update progress bar smoothly
        delay(50)

        val pingResult = pingTester.evaluateTargets(
            genomeId = genome.id,
            targets = targetUrls,
            attemptsPerTarget = 1
        )

        // Compute Anti-DPI Obfuscation Multiplier (0.5 to 2.5)
        val antiDpiScore = computeAntiDpiScore(genome)

        // Target-specific censored service priority multiplier
        val censoredServiceBonus = calculateCensoredServiceBonus(pingResult)

        val combinedFitness = pingResult.fitnessScore * antiDpiScore * censoredServiceBonus

        return pingResult.copy(fitnessScore = combinedFitness)
    }

    /**
     * Prioritizes high success rates specifically for blocked social media and streaming targets.
     */
    private fun calculateCensoredServiceBonus(result: FitnessResult): Double {
        val success = result.successRate
        val ping = result.avgPingMs

        // Exponential reward for >85% success on censored targets with ping under 120ms
        return when {
            success >= 0.95 && ping < 80 -> 1.50
            success >= 0.85 && ping < 120 -> 1.30
            success >= 0.70 -> 1.10
            else -> 0.85 // Penalize profiles blocked on social media
        }
    }

    /**
     * Scores how resilient this genome's obfuscation parameters are against Deep Packet Inspection (DPI).
     */
    fun computeAntiDpiScore(genome: Genome): Double {
        var score = 1.0

        // 1. Junk packet coverage (Jc)
        if (genome.jc in 2..7) score += 0.35
        else if (genome.jc > 7) score += 0.20

        // 2. Junk size variation range (Jmax - Jmin)
        val range = genome.jmax - genome.jmin
        if (range in 128..512) score += 0.35
        else if (range > 64) score += 0.20

        // 3. Handshake prefix padding entropy (S1..S4)
        if (genome.s1 in 12..48 && genome.s2 in 16..64) score += 0.30
        if (genome.s3 in 10..40 && genome.s4 in 6..24) score += 0.20

        // 4. Header distinction (H1 != H2 != H3 != H4)
        val uniqueHeaders = setOf(genome.h1, genome.h2, genome.h3, genome.h4).size
        if (uniqueHeaders == 4 && genome.h1 > 100000L) score += 0.35

        // 5. Safe MTU to avoid packet fragmentation signatures
        if (genome.mtu in 1280..1380) score += 0.15

        return score
    }
}
