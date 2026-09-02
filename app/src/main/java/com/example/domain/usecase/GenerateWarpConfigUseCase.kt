package com.example.domain.usecase

import com.example.data.remote.CloudflareApi
import com.example.domain.model.AwgConfig
import com.example.domain.repository.ConfigRepository

/**
 * Automates registration with Cloudflare WARP API, enables the tunnel, applies optional WARP+ keys,
 * custom DNS presets, and stores the resulting config.
 */
class GenerateWarpConfigUseCase(
    private val cloudflareApi: CloudflareApi,
    private val configRepository: ConfigRepository
) {
    /**
     * Executes the WARP configuration generation workflow.
     * Invoked via Kotlin operator syntax `generateWarpUseCase(...)`.
     */
    @Suppress("unused")
    suspend operator fun invoke(
        customName: String = "Cloudflare WARP Auto",
        licenseKey: String? = null,
        dns: String = "1.1.1.1, 1.0.0.1",
        endpoint: String? = null,
        mtu: Int = 1280
    ): Result<AwgConfig> {
        return runCatching {
            val warpResult = cloudflareApi.generateWarpConfig(licenseKey = licenseKey)
            val warpConfig = warpResult.getOrThrow()
            var awgConfig = warpConfig.toAwgConfig(customName)

            val safeEndpoint = if (!endpoint.isNullOrBlank()) {
                endpoint
            } else if (awgConfig.endpoint.contains("188.114.") || awgConfig.endpoint.contains("162.159.192.") || awgConfig.endpoint.contains("162.159.193.")) {
                "162.159.130.1:1074"
            } else {
                awgConfig.endpoint.ifBlank { "162.159.130.1:1074" }
            }

            val warpH1 = AwgConfig.calculateWarpH1(awgConfig.reserved)
            awgConfig = awgConfig.copy(
                dns = dns,
                mtu = mtu,
                endpoint = safeEndpoint,
                jc = 4,
                jmin = 40,
                jmax = 70,
                s1 = 0,
                s2 = 0,
                s3 = 0,
                s4 = 0,
                h1 = warpH1,
                h2 = 2L,
                h3 = 3L,
                h4 = 4L,
                allowedIps = "0.0.0.0/0, ::/0",
                originType = "WARP",
                createdAt = System.currentTimeMillis()
            )

            configRepository.saveConfig(awgConfig)
            awgConfig
        }
    }
}
