package com.example.domain.model

/**
 * Result of latency, reliability ping evaluation, and blocked services reachability.
 */
data class FitnessResult(
    val genomeId: String,
    val avgPingMs: Long,
    val minPingMs: Long,
    val maxPingMs: Long,
    val successRate: Double,
    val fitnessScore: Double,
    val testedUrls: List<String> = emptyList(),
    val isReachable: Boolean = successRate > 0.0,
    val errorMessage: String? = null,
    val serviceResults: List<ServiceProbeResult> = emptyList(),
    val unblockedServicesCount: Int = 0,
    val totalServicesCount: Int = 0
)
