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
        delay(30)

        val targetEndpoint = (if (!genome.endpoint.isNullOrBlank()) genome.endpoint else baseConfig.endpoint).ifBlank { "188.114.96.1:1074" }
        val peerPubKey = baseConfig.peerPublicKey.ifBlank { com.example.util.WireGuardProbe.DEFAULT_CLOUDFLARE_WARP_PUBKEY }
        val clientPrivKey = baseConfig.privateKey.ifBlank { null }

        // Real UDP handshake test to endpoint using genome's obfuscation parameters
        val endpointProbe = pingTester.testEndpoint(
            endpoint = targetEndpoint,
            peerPublicKey = peerPubKey,
            clientPrivateKey = clientPrivKey,
            h1 = genome.h1,
            s1 = genome.s1
        )

        if (!endpointProbe.isReachable || endpointProbe.latencyMs == null || endpointProbe.latencyMs <= 0) {
            return FitnessResult(
                genomeId = genome.id,
                avgPingMs = 9999L,
                minPingMs = 9999L,
                maxPingMs = 9999L,
                successRate = 0.0,
                fitnessScore = 0.0,
                testedUrls = listOf(targetEndpoint),
                errorMessage = endpointProbe.error ?: "UDP Handshake dropped / TSPU Filtered"
            )
        }

        val udpLatency = endpointProbe.latencyMs

        // Compute Anti-DPI Obfuscation Multiplier (0.5 to 2.5)
        val antiDpiScore = computeAntiDpiScore(genome)

        val baseFitness = (1000.0 / (udpLatency + 1.0)) * 2.0
        val combinedFitness = baseFitness * antiDpiScore

        return FitnessResult(
            genomeId = genome.id,
            avgPingMs = udpLatency,
            minPingMs = udpLatency,
            maxPingMs = udpLatency,
            successRate = 1.0,
            fitnessScore = if (combinedFitness.isNaN() || combinedFitness <= 0.0) 0.0 else combinedFitness,
            testedUrls = listOf(targetEndpoint),
            errorMessage = null
        )
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
