package com.example.domain.model

/**
 * Result of latency and reliability ping evaluation for a given configuration.
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
    val errorMessage: String? = null
)
