package com.example.evolution

import com.example.data.remote.EndpointProbeResult
import com.example.data.remote.PingTester
import com.example.domain.model.AwgConfig
import com.example.domain.model.BlockedService
import com.example.domain.model.BlockedServicesCatalog
import com.example.domain.model.Genome
import com.example.domain.model.ServiceProbeResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FitnessEvaluatorTest {

    private class FakePingTester : PingTester() {
        var testEndpointResult: EndpointProbeResult = EndpointProbeResult(
            endpoint = "188.114.96.1:1074",
            isReachable = true,
            latencyMs = 50L,
            error = null
        )

        var probeBlockedServicesResult: List<ServiceProbeResult> = emptyList()

        var lastEndpointTested: String? = null
        var lastPeerPublicKey: String? = null
        var lastClientPrivateKey: String? = null
        var lastH1: Long? = null
        var lastS1: Int? = null
        var lastServicesProbed: List<BlockedService>? = null

        override suspend fun testEndpoint(
            endpoint: String,
            peerPublicKey: String,
            clientPrivateKey: String?,
            h1: Long,
            s1: Int
        ): EndpointProbeResult {
            lastEndpointTested = endpoint
            lastPeerPublicKey = peerPublicKey
            lastClientPrivateKey = clientPrivateKey
            lastH1 = h1
            lastS1 = s1
            return testEndpointResult
        }

        override suspend fun probeBlockedServices(
            services: List<BlockedService>
        ): List<ServiceProbeResult> {
            lastServicesProbed = services
            return if (probeBlockedServicesResult.isNotEmpty()) {
                probeBlockedServicesResult
            } else {
                services.map {
                    ServiceProbeResult(
                        service = it,
                        isAccessible = true,
                        latencyMs = 60L
                    )
                }
            }
        }
    }

    @Test
    fun testEvaluate_whenEndpointUnreachable_returnsZeroFitnessAndFailedResult() = runTest {
        val fakePingTester = FakePingTester().apply {
            testEndpointResult = EndpointProbeResult(
                endpoint = "188.114.96.1:1074",
                isReachable = false,
                latencyMs = null,
                error = "UDP Timeout"
            )
        }

        val evaluator = FitnessEvaluator(fakePingTester)
        val genome = Genome(id = "g1")
        val baseConfig = AwgConfig(name = "BaseConfig", privateKey = "privKey==")

        val result = evaluator.evaluate(genome, baseConfig)

        assertEquals("g1", result.genomeId)
        assertEquals(9999L, result.avgPingMs)
        assertEquals(9999L, result.minPingMs)
        assertEquals(9999L, result.maxPingMs)
        assertEquals(0.0, result.successRate, 0.001)
        assertEquals(0.0, result.fitnessScore, 0.001)
        assertEquals("UDP Timeout", result.errorMessage)
        assertEquals(0, result.unblockedServicesCount)
        assertEquals(BlockedServicesCatalog.allServices.size, result.totalServicesCount)
        assertTrue(result.serviceResults.all { !it.isAccessible && it.error == "UDP Туннель недоступен" })
    }

    @Test
    fun testEvaluate_whenAllServicesAccessible_calculatesWeightedLatencyAndHighFitness() = runTest {
        val fakePingTester = FakePingTester().apply {
            testEndpointResult = EndpointProbeResult(
                endpoint = "1.2.3.4:1074",
                isReachable = true,
                latencyMs = 50L
            )
        }

        val targetServices = BlockedServicesCatalog.allServices.take(2)
        fakePingTester.probeBlockedServicesResult = targetServices.map {
            ServiceProbeResult(service = it, isAccessible = true, latencyMs = 100L)
        }

        val evaluator = FitnessEvaluator(fakePingTester)
        val genome = Genome(id = "g2")
        val baseConfig = AwgConfig(name = "BaseConfig", privateKey = "privKey==", endpoint = "1.2.3.4:1074")

        val result = evaluator.evaluate(genome, baseConfig, targetServices = targetServices)

        assertEquals("g2", result.genomeId)
        // combinedAvgLatency = (50 * 0.4) + (100 * 0.6) = 20 + 60 = 80
        assertEquals(80L, result.avgPingMs)
        assertEquals(50L, result.minPingMs)
        assertEquals(100L, result.maxPingMs)
        assertEquals(1.0, result.successRate, 0.001)
        assertTrue(result.fitnessScore > 0.0)
        assertNull(result.errorMessage)
        assertEquals(2, result.unblockedServicesCount)
        assertEquals(2, result.totalServicesCount)
    }

    @Test
    fun testEvaluate_whenNoServicesAccessible_returnsLowFitnessAndErrorMessage() = runTest {
        val fakePingTester = FakePingTester().apply {
            testEndpointResult = EndpointProbeResult(
                endpoint = "1.2.3.4:1074",
                isReachable = true,
                latencyMs = 50L
            )
        }

        val targetServices = BlockedServicesCatalog.allServices.take(2)
        fakePingTester.probeBlockedServicesResult = targetServices.map {
            ServiceProbeResult(service = it, isAccessible = false, latencyMs = null, error = "DPI Blocked")
        }

        val evaluator = FitnessEvaluator(fakePingTester)
        val genome = Genome(id = "g3")
        val baseConfig = AwgConfig(name = "BaseConfig", privateKey = "privKey==", endpoint = "1.2.3.4:1074")

        val result = evaluator.evaluate(genome, baseConfig, targetServices = targetServices)

        assertEquals("g3", result.genomeId)
        assertEquals(50L, result.avgPingMs)
        assertEquals(0.0, result.successRate, 0.001)
        assertEquals("Заблокированы все целевые сервисы", result.errorMessage)
        assertEquals(0, result.unblockedServicesCount)
        assertEquals(2, result.totalServicesCount)
    }

    @Test
    fun testEvaluate_whenPartialServicesAccessible_calculatesCorrectBypassRateAndFitness() = runTest {
        val fakePingTester = FakePingTester().apply {
            testEndpointResult = EndpointProbeResult(
                endpoint = "1.2.3.4:1074",
                isReachable = true,
                latencyMs = 40L
            )
        }

        val targetServices = BlockedServicesCatalog.allServices.take(4)
        fakePingTester.probeBlockedServicesResult = listOf(
            ServiceProbeResult(service = targetServices[0], isAccessible = true, latencyMs = 80L),
            ServiceProbeResult(service = targetServices[1], isAccessible = true, latencyMs = 120L),
            ServiceProbeResult(service = targetServices[2], isAccessible = false, latencyMs = null),
            ServiceProbeResult(service = targetServices[3], isAccessible = false, latencyMs = null)
        )

        val evaluator = FitnessEvaluator(fakePingTester)
        val genome = Genome(id = "g4")
        val baseConfig = AwgConfig(name = "BaseConfig", privateKey = "privKey==", endpoint = "1.2.3.4:1074")

        val result = evaluator.evaluate(genome, baseConfig, targetServices = targetServices)

        assertEquals(0.5, result.successRate, 0.001)
        assertEquals(2, result.unblockedServicesCount)
        assertEquals(4, result.totalServicesCount)
        assertNull(result.errorMessage)
        // avgServiceLatency = (80 + 120) / 2 = 100
        // combinedAvgLatency = (40 * 0.4) + (100 * 0.6) = 16 + 60 = 76
        assertEquals(76L, result.avgPingMs)
        assertEquals(40L, result.minPingMs)
        assertEquals(100L, result.maxPingMs)
    }

    @Test
    fun testEvaluate_usesGenomeEndpointAndBaseConfigKeys() = runTest {
        val fakePingTester = FakePingTester().apply {
            testEndpointResult = EndpointProbeResult(
                endpoint = "custom.endpoint:51820",
                isReachable = true,
                latencyMs = 30L
            )
        }

        val evaluator = FitnessEvaluator(fakePingTester)
        val genome = Genome(
            id = "g5",
            endpoint = "custom.endpoint:51820",
            h1 = 987654321L,
            s1 = 15
        )
        val baseConfig = AwgConfig(
            name = "BaseConfig",
            endpoint = "fallback.endpoint:1074",
            peerPublicKey = "customPubKey==",
            privateKey = "customPrivKey=="
        )

        evaluator.evaluate(genome, baseConfig)

        assertEquals("custom.endpoint:51820", fakePingTester.lastEndpointTested)
        assertEquals("customPubKey==", fakePingTester.lastPeerPublicKey)
        assertEquals("customPrivKey==", fakePingTester.lastClientPrivateKey)
        assertEquals(987654321L, fakePingTester.lastH1)
        assertEquals(15, fakePingTester.lastS1)
    }

    @Test
    fun testEvaluate_emptyTargetServices_filtersCatalogByTargetUrls() = runTest {
        val fakePingTester = FakePingTester().apply {
            testEndpointResult = EndpointProbeResult(
                endpoint = "188.114.96.1:1074",
                isReachable = true,
                latencyMs = 30L
            )
        }

        val evaluator = FitnessEvaluator(fakePingTester)
        val genome = Genome(id = "g6")
        val baseConfig = AwgConfig(name = "BaseConfig", privateKey = "privKey==", endpoint = "188.114.96.1:1074")
        val youtube = BlockedServicesCatalog.allServices.first { it.id == "youtube" }

        evaluator.evaluate(genome, baseConfig, targetUrls = listOf(youtube.testUrl), targetServices = emptyList())

        assertNotNull(fakePingTester.lastServicesProbed)
        assertEquals(1, fakePingTester.lastServicesProbed?.size)
        assertEquals("YouTube", fakePingTester.lastServicesProbed?.first()?.name)
    }

    @Test
    fun testComputeAntiDpiScore_optimalGenome_returnsMaxScore() {
        val evaluator = FitnessEvaluator(PingTester())
        val optimalGenome = Genome(
            jc = 5,              // in 3..8 -> +0.40
            jmin = 50,
            jmax = 300,          // range 250 in 128..600 -> +0.35
            s1 = 20,             // in 16..64
            s2 = 20,             // in 16..64 -> +0.35
            s3 = 10,             // in 8..40
            s4 = 10,             // in 6..24 -> +0.20
            h1 = 100001L,        // unique & > 100000 -> +0.35
            h2 = 200002L,
            h3 = 300003L,
            h4 = 400004L,
            mtu = 1360,          // in 1280..1380 -> +0.20
            sni = "rutube.ru"     // non-blank -> +0.15
        )

        val score = evaluator.computeAntiDpiScore(optimalGenome)
        // 1.0 + 0.40 (jc) + 0.35 (range) + 0.35 (s1 & s2) + 0.20 (s3 & s4) + 0.35 (unique headers & h1 > 100000) + 0.20 (mtu) + 0.15 (sni) = 3.00
        assertEquals(3.00, score, 0.001)
    }

    @Test
    fun testComputeAntiDpiScore_minimalGenome_returnsBaseScore() {
        val evaluator = FitnessEvaluator(PingTester())
        val minimalGenome = Genome(
            jc = 0,              // no bonus
            jmin = 40,
            jmax = 40,           // range 0
            s1 = 0,
            s2 = 0,
            s3 = 0,
            s4 = 0,
            h1 = 1L,             // <= 100000
            h2 = 2L,
            h3 = 3L,
            h4 = 4L,
            mtu = 1420,          // not in 1280..1380
            sni = null           // blank
        )

        val score = evaluator.computeAntiDpiScore(minimalGenome)
        assertEquals(1.0, score, 0.001)
    }
}
