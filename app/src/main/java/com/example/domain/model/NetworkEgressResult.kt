package com.example.domain.model

/**
 * Detailed verification of outbound internet connectivity and egress routing through the active config.
 */
data class NetworkEgressResult(
    val isFunctional: Boolean = false,
    val publicIp: String? = null,
    val countryCode: String? = null,
    val cityOrIsp: String? = null,
    val warpStatus: String? = null,
    val isWarpActive: Boolean = false,
    val dnsReachable: Boolean = false,
    val latencyMs: Long? = null,
    val testedAt: Long = System.currentTimeMillis(),
    val errorMessage: String? = null
)
