package com.example.domain.usecase

import com.example.data.remote.EndpointProbeResult
import com.example.data.remote.PingTester
import com.example.domain.model.EndpointCatalog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EndpointScannerUseCaseTest {

    private lateinit var fakePingTester: FakePingTester
    private lateinit var useCase: EndpointScannerUseCase

    @Before
    fun setUp() {
        fakePingTester = FakePingTester()
        useCase = EndpointScannerUseCase(fakePingTester)
    }

    @Test
    fun testScanCountryEndpoints_defaultAll_scansAllPreconfiguredEndpoints() = runBlocking {
        val results = useCase.scanCountryEndpoints("ALL")

        val expectedCount = EndpointCatalog.preconfiguredEndpoints.size
        assertEquals(expectedCount, results.size)
        assertEquals(expectedCount, fakePingTester.probedEndpoints.size)

        // All preconfigured endpoints were probed
        val expectedEndpoints = EndpointCatalog.preconfiguredEndpoints.map { it.fullEndpoint }.toSet()
        assertEquals(expectedEndpoints, fakePingTester.probedEndpoints.toSet())
    }

    @Test
    fun testScanCountryEndpoints_specificCountry_filtersEndpointsByCountry() = runBlocking {
        val countryCode = "DE"
        val results = useCase.scanCountryEndpoints(countryCode)

        val expectedDeEndpoints = EndpointCatalog.preconfiguredEndpoints.filter { it.countryCode == countryCode }
        assertTrue(results.isNotEmpty())
        assertEquals(expectedDeEndpoints.size, results.size)
        assertTrue(results.all { it.countryCode == countryCode })

        val probedSet = fakePingTester.probedEndpoints.toSet()
        assertEquals(expectedDeEndpoints.map { it.fullEndpoint }.toSet(), probedSet)
    }

    @Test
    fun testScanCountryEndpoints_unknownCountry_returnsEmptyList() = runBlocking {
        val results = useCase.scanCountryEndpoints("NON_EXISTENT_COUNTRY")

        assertTrue(results.isEmpty())
        assertTrue(fakePingTester.probedEndpoints.isEmpty())
    }

    @Test
    fun testDiscoverNewEndpoints_generatesCandidatesAndFiltersByCountry() = runBlocking {
        val count = 10
        val countryCode = "NL"
        val results = useCase.discoverNewEndpoints(count = count, countryCode = countryCode)

        assertEquals(count, results.size)
        assertEquals(count, fakePingTester.probedEndpoints.size)
        assertTrue(results.all { it.countryCode == countryCode })
    }

    @Test
    fun testDiscoverNewEndpoints_defaultAllCountry() = runBlocking {
        val count = 8
        val results = useCase.discoverNewEndpoints(count = count, countryCode = "ALL")

        assertEquals(count, results.size)
        assertEquals(count, fakePingTester.probedEndpoints.size)
        assertTrue(results.all { it.countryCode == "ALL" })
    }

    @Test
    fun testScanCountryEndpoints_sortingOrder_aliveFirstThenLowestPing() = runBlocking {
        val countryCode = "DE"
        val deEndpoints = EndpointCatalog.preconfiguredEndpoints.filter { it.countryCode == countryCode }
        assertTrue("Test requires at least 3 DE endpoints", deEndpoints.size >= 3)

        val ep1 = deEndpoints[0].fullEndpoint
        val ep2 = deEndpoints[1].fullEndpoint
        val ep3 = deEndpoints[2].fullEndpoint

        fakePingTester.reachabilityMap[ep1] = true
        fakePingTester.latencyMap[ep1] = 120L

        fakePingTester.reachabilityMap[ep2] = true
        fakePingTester.latencyMap[ep2] = 25L

        fakePingTester.reachabilityMap[ep3] = false
        fakePingTester.latencyMap[ep3] = null

        val results = useCase.scanCountryEndpoints(countryCode)

        val res1 = results.first { it.fullEndpoint == ep1 }
        val res2 = results.first { it.fullEndpoint == ep2 }
        val res3 = results.first { it.fullEndpoint == ep3 }

        assertTrue(res1.isAlive)
        assertEquals(120L, res1.lastPingMs)

        assertTrue(res2.isAlive)
        assertEquals(25L, res2.lastPingMs)

        assertFalse(res3.isAlive)

        // Verification of sorting order:
        // ep2 (25ms, alive) -> ep1 (120ms, alive) -> ep3 (unreachable)
        val indexOfEp1 = results.indexOfFirst { it.fullEndpoint == ep1 }
        val indexOfEp2 = results.indexOfFirst { it.fullEndpoint == ep2 }
        val indexOfEp3 = results.indexOfFirst { it.fullEndpoint == ep3 }

        assertTrue(indexOfEp2 < indexOfEp1)
        assertTrue(indexOfEp1 < indexOfEp3)
    }

    private class FakePingTester : PingTester() {
        val probedEndpoints = mutableListOf<String>()
        val latencyMap = mutableMapOf<String, Long?>()
        val reachabilityMap = mutableMapOf<String, Boolean>()

        override suspend fun testEndpoint(
            endpoint: String,
            peerPublicKey: String,
            clientPrivateKey: String?,
            h1: Long,
            s1: Int
        ): EndpointProbeResult {
            probedEndpoints.add(endpoint)
            val isReachable = reachabilityMap[endpoint] ?: true
            val latency = latencyMap[endpoint] ?: 50L
            return EndpointProbeResult(
                endpoint = endpoint,
                isReachable = isReachable,
                latencyMs = if (isReachable) latency else null
            )
        }
    }
}
