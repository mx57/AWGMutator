package com.example.domain.usecase

import com.example.data.remote.CloudflareApi
import com.example.domain.model.AwgConfig
import com.example.domain.repository.ConfigRepository
import java.util.Random

/**
 * Creates a Hybrid Cloudflare WARP + AmneziaWG profile with active obfuscation headers
 * (Jc, S1..S4, H1..H4) injected into the WARP configuration to defeat restrictive DPI firewalls.
 */
class GenerateHybridWarpAwgUseCase(
    private val cloudflareApi: CloudflareApi,
    private val configRepository: ConfigRepository
) {
    private val random = Random()

    suspend operator fun invoke(
        customName: String = "WARP + AmneziaWG Anti-DPI",
        licenseKey: String? = null,
        dns: String = "1.1.1.1, 1.0.0.1"
    ): Result<AwgConfig> {
        return runCatching {
            val warpResult = cloudflareApi.generateWarpConfig(licenseKey = licenseKey)
            val warpConfig = warpResult.getOrThrow()
            val baseAwg = warpConfig.toAwgConfig(customName)

            val jmin = 40 + random.nextInt(20) // [40, 60]
            val jmax = jmin + random.nextInt(80) // [40, 140]
            val jc = 1 + random.nextInt(8) // [1, 8]

            val s1 = 5 + random.nextInt(25) // [5, 30]
            val s2 = 5 + random.nextInt(25) // [5, 30]
            val s3 = 0 + random.nextInt(15)
            val s4 = 0 + random.nextInt(10)

            val h1 = 1L + random.nextInt(100)
            val h2 = 1L + random.nextInt(100)
            val h3 = 1L + random.nextInt(100)
            val h4 = 1L + random.nextInt(100)

            val hybrid = baseAwg.copy(
                dns = dns,
                jc = jc,
                jmin = jmin,
                jmax = jmax,
                s1 = s1,
                s2 = s2,
                s3 = s3,
                s4 = s4,
                h1 = h1,
                h2 = h2,
                h3 = h3,
                h4 = h4,
                originType = "HYBRID",
                createdAt = System.currentTimeMillis()
            )

            configRepository.saveConfig(hybrid)
            hybrid
        }
    }
}
