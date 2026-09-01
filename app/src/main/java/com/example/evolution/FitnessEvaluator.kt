package com.example.evolution

import com.example.data.remote.PingTester
import com.example.domain.model.AwgConfig
import com.example.domain.model.BlockedService
import com.example.domain.model.BlockedServicesCatalog
import com.example.domain.model.FitnessResult
import com.example.domain.model.Genome
import com.example.domain.model.ServiceProbeResult
import kotlinx.coroutines.delay

/**
 * Advanced Multi-Stage Evaluator that executes:
 * 1. True WireGuard / AmneziaWG UDP Handshake reachability to candidate endpoint.
 * 2. Real-time active probes against targeted blocked services (YouTube, Instagram, Telegram, Twitch, X/Twitter, Discord, etc.).
 * 3. Anti-DPI Obfuscation & Packet entropy scoring (Jc, S1..S4, H1..H4, MTU, SNI).
 * 4. Censored platform bypass rewards & penalties.
 */
class FitnessEvaluator(
    private val pingTester: PingTester
) {
    suspend fun evaluate(
        genome: Genome,
        baseConfig: AwgConfig,
        targetUrls: List<String> = BlockedServicesCatalog.allServices.map { it.testUrl },
        targetServices: List<BlockedService> = BlockedServicesCatalog.allServices
    ): FitnessResult {
        // Yield to let UI update progress bar smoothly
        delay(25)

        val targetEndpoint = (if (!genome.endpoint.isNullOrBlank()) genome.endpoint else baseConfig.endpoint).ifBlank { "188.114.96.1:1074" }
        val peerPubKey = baseConfig.peerPublicKey.ifBlank { com.example.util.WireGuardProbe.DEFAULT_CLOUDFLARE_WARP_PUBKEY }
        val clientPrivKey = baseConfig.privateKey.ifBlank { null }

        // STAGE 1: Real UDP handshake test to endpoint using genome's obfuscation parameters
        val endpointProbe = pingTester.testEndpoint(
            endpoint = targetEndpoint,
            peerPublicKey = peerPubKey,
            clientPrivateKey = clientPrivKey,
            h1 = genome.h1,
            s1 = genome.s1
        )

        if (!endpointProbe.isReachable || endpointProbe.latencyMs == null || endpointProbe.latencyMs <= 0) {
            val failedServiceResults = targetServices.map {
                ServiceProbeResult(
                    service = it,
                    isAccessible = false,
                    latencyMs = null,
                    error = "UDP Туннель недоступен"
                )
            }
            return FitnessResult(
                genomeId = genome.id,
                avgPingMs = 9999L,
                minPingMs = 9999L,
                maxPingMs = 9999L,
                successRate = 0.0,
                fitnessScore = 0.0,
                testedUrls = listOf(targetEndpoint),
                errorMessage = endpointProbe.error ?: "UDP Handshake dropped / TSPU Filtered",
                serviceResults = failedServiceResults,
                unblockedServicesCount = 0,
                totalServicesCount = targetServices.size
            )
        }

        val udpLatency = endpointProbe.latencyMs

        // STAGE 2: Real Blocked & Censored Platforms Probe Matrix
        val effectiveServices = if (targetServices.isNotEmpty()) {
            targetServices
        } else {
            BlockedServicesCatalog.allServices.filter { targetUrls.contains(it.testUrl) }
                .ifEmpty { BlockedServicesCatalog.allServices }
        }

        val serviceResults = pingTester.probeBlockedServices(effectiveServices)
        val accessibleServices = serviceResults.filter { it.isAccessible }
        val unblockedCount = accessibleServices.size
        val totalCount = serviceResults.size
        val bypassSuccessRate = if (totalCount > 0) unblockedCount.toDouble() / totalCount.toDouble() else 1.0

        val serviceLatencies = accessibleServices.mapNotNull { it.latencyMs }
        val avgServiceLatency = if (serviceLatencies.isNotEmpty()) {
            serviceLatencies.average().toLong().coerceAtLeast(1L)
        } else {
            udpLatency
        }

        // Combined average latency (weighted between endpoint UDP handshake and service responses)
        val combinedAvgLatency = ((udpLatency * 0.4) + (avgServiceLatency * 0.6)).toLong().coerceAtLeast(1L)

        // STAGE 3: Anti-DPI Obfuscation & Packet Entropy Score (0.8 to 2.5)
        val antiDpiScore = computeAntiDpiScore(genome)

        // STAGE 4: Censored Service Bypass Multiplier
        val bypassMultiplier = calculateBypassMultiplier(unblockedCount, totalCount, combinedAvgLatency)

        // Base network performance score
        val baseSpeedScore = (1000.0 / (combinedAvgLatency + 1.0)) * 2.0
        val combinedFitness = baseSpeedScore * antiDpiScore * bypassMultiplier

        return FitnessResult(
            genomeId = genome.id,
            avgPingMs = combinedAvgLatency,
            minPingMs = minOf(udpLatency, avgServiceLatency),
            maxPingMs = maxOf(udpLatency, avgServiceLatency),
            successRate = bypassSuccessRate,
            fitnessScore = if (combinedFitness.isNaN() || combinedFitness <= 0.0) 0.0 else combinedFitness,
            testedUrls = listOf(targetEndpoint) + effectiveServices.map { it.name },
            errorMessage = if (unblockedCount == 0 && totalCount > 0) "Заблокированы все целевые сервисы" else null,
            serviceResults = serviceResults,
            unblockedServicesCount = unblockedCount,
            totalServicesCount = totalCount
        )
    }

    /**
     * Rewards profiles that successfully bypass Russian TSPU blocks across YouTube, Instagram, Telegram, etc.
     */
    private fun calculateBypassMultiplier(unblocked: Int, total: Int, avgLatency: Long): Double {
        if (total == 0) return 1.0
        val ratio = unblocked.toDouble() / total.toDouble()
        return when {
            ratio >= 1.0 && avgLatency < 90 -> 2.50 // 100% bypass with lightning speed
            ratio >= 1.0 -> 2.00                    // 100% bypass
            ratio >= 0.75 -> 1.50                   // 75%+ bypass
            ratio >= 0.50 -> 1.10                   // 50%+ bypass
            ratio > 0.0 -> 0.70                     // Minor bypass
            else -> 0.25                            // Complete DPI lock / 0 services passed
        }
    }

    /**
     * Scores how resilient this genome's obfuscation parameters are against Deep Packet Inspection (DPI).
     */
    fun computeAntiDpiScore(genome: Genome): Double {
        var score = 1.0

        // 1. Junk packet coverage (Jc)
        if (genome.jc in 3..8) score += 0.40
        else if (genome.jc in 1..2 || genome.jc in 9..10) score += 0.20

        // 2. Junk size variation range (Jmax - Jmin)
        val range = genome.jmax - genome.jmin
        if (range in 128..600) score += 0.35
        else if (range > 64) score += 0.20

        // 3. Handshake prefix padding entropy (S1..S4)
        if (genome.s1 in 16..64 && genome.s2 in 16..64) score += 0.35
        if (genome.s3 in 8..40 && genome.s4 in 6..24) score += 0.20

        // 4. Header distinction (H1 != H2 != H3 != H4)
        val uniqueHeaders = setOf(genome.h1, genome.h2, genome.h3, genome.h4).size
        if (uniqueHeaders == 4 && genome.h1 > 100000L) score += 0.35

        // 5. Safe MTU to avoid packet fragmentation signatures
        if (genome.mtu in 1280..1380) score += 0.20

        // 6. Russian SNI Mimicry bonus
        if (!genome.sni.isNullOrBlank()) score += 0.15

        return score
    }
}

