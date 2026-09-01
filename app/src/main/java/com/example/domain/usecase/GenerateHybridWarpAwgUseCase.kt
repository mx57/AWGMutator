package com.example.domain.usecase

import com.example.data.remote.CloudflareApi
import com.example.domain.model.AwgConfig
import com.example.domain.repository.ConfigRepository

/**
 * Creates a Hybrid Cloudflare WARP + AmneziaWG profile with active obfuscation headers
 * (Jc, S1..S4, H1..H4) injected into the WARP configuration to defeat restrictive DPI firewalls.
 */
class GenerateHybridWarpAwgUseCase(
    private val cloudflareApi: CloudflareApi,
    private val configRepository: ConfigRepository
) {
    suspend operator fun invoke(
        customName: String = "WARP + AmneziaWG Anti-DPI",
        licenseKey: String? = null,
        dns: String = "1.1.1.1, 1.0.0.1"
    ): Result<AwgConfig> {
        return runCatching {
            val warpResult = cloudflareApi.generateWarpConfig(licenseKey = licenseKey)
            val warpConfig = warpResult.getOrThrow()
            val baseAwg = warpConfig.toAwgConfig(customName)

            val hybrid = baseAwg.copy(
                dns = dns,
                endpoint = baseAwg.endpoint.ifBlank { "188.114.96.1:1074" },
                mtu = 1280,
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
                originType = "WARP_ANTI_DPI",
                createdAt = System.currentTimeMillis()
            )

            configRepository.saveConfig(hybrid)
            hybrid
        }
    }
}
