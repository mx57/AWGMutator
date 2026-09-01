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

            if (!endpoint.isNullOrBlank()) {
                awgConfig = awgConfig.copy(endpoint = endpoint)
            }
            awgConfig = awgConfig.copy(
                dns = dns,
                mtu = mtu,
                jc = 0,
                jmin = 0,
                jmax = 0,
                s1 = 0,
                s2 = 0,
                s3 = 0,
                s4 = 0,
                h1 = 1L,
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
