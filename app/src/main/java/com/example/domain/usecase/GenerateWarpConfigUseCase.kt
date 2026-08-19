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
        mtu: Int = 1360
    ): Result<AwgConfig> {
        return runCatching {
            val warpResult = cloudflareApi.generateWarpConfig(licenseKey = licenseKey)
            val warpConfig = warpResult.getOrThrow()
            var awgConfig = warpConfig.toAwgConfig(customName)

            if (!endpoint.isNullOrBlank()) {
                awgConfig = awgConfig.copy(endpoint = endpoint)
            }
            awgConfig = awgConfig.copy(dns = dns, mtu = mtu)

            configRepository.saveConfig(awgConfig)
            awgConfig
        }
    }
}
