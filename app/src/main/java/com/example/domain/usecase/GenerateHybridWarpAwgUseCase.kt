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

            val jmin = 64 + random.nextInt(128)
            val jmax = jmin + random.nextInt(256)
            val jc = 3 + random.nextInt(4)

            val s1 = 10 + random.nextInt(20)
            val s2 = 15 + random.nextInt(20)
            val s3 = 10 + random.nextInt(15)
            val s4 = 5 + random.nextInt(10)

            val h1 = (random.nextLong() and 0x7FFFFFFF) + 1100000L
            val h2 = (random.nextLong() and 0x7FFFFFFF) + 2200000L
            val h3 = (random.nextLong() and 0x7FFFFFFF) + 3300000L
            val h4 = (random.nextLong() and 0x7FFFFFFF) + 4400000L

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
                h4 = h4
            )

            configRepository.saveConfig(hybrid)
            hybrid
        }
    }
}
