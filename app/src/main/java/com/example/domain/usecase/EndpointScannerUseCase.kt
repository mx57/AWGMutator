package com.example.domain.usecase

import com.example.data.remote.PingTester
import com.example.domain.model.EndpointCatalog
import com.example.domain.model.EndpointItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * UseCase to discover, scan, and benchmark working WireGuard / AmneziaWG endpoints
 * by country and search for previously unknown IP/Port candidates.
 */
class EndpointScannerUseCase(
    private val pingTester: PingTester
) {
    /**
     * Scans preconfigured endpoints for a given country code ("ALL", "DE", "NL", "FI", "SE", etc.)
     */
    suspend fun scanCountryEndpoints(countryCode: String = "ALL"): List<EndpointItem> = withContext(Dispatchers.IO) {
        val candidates = if (countryCode == "ALL") {
            EndpointCatalog.preconfiguredEndpoints
        } else {
            EndpointCatalog.preconfiguredEndpoints.filter { it.countryCode == countryCode }
        }

        coroutineScope {
            candidates.map { item ->
                async {
                    val probe = pingTester.testEndpoint(item.fullEndpoint)
                    item.copy(
                        lastPingMs = probe.latencyMs,
                        isAlive = probe.isReachable
                    )
                }
            }.awaitAll()
        }.sortedWith(compareBy({ !it.isAlive }, { it.lastPingMs ?: 9999L }))
    }

    /**
     * Actively explores and discovers newly generated, unexplored IP/Port combinations in WARP subnets.
     */
    suspend fun discoverNewEndpoints(
        count: Int = 18,
        countryCode: String = "ALL"
    ): List<EndpointItem> = withContext(Dispatchers.IO) {
        val candidates = EndpointCatalog.generateCandidateEndpoints(count = count, countryCode = countryCode)

        coroutineScope {
            candidates.map { item ->
                async {
                    val probe = pingTester.testEndpoint(item.fullEndpoint)
                    item.copy(
                        lastPingMs = probe.latencyMs,
                        isAlive = probe.isReachable
                    )
                }
            }.awaitAll()
        }.sortedWith(compareBy({ !it.isAlive }, { it.lastPingMs ?: 9999L }))
    }
}
