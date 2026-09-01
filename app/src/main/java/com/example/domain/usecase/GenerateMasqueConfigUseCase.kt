package com.example.domain.usecase

import com.example.data.remote.CloudflareApi
import com.example.domain.model.MasqueConfig
import com.example.domain.repository.ConfigRepository

/**
 * Generates and stores a Cloudflare WARP MASQUE node configuration (as in LxBox / sing-box),
 * using HTTP/3 Connect-IP over QUIC and clean Anycast edge servers.
 */
class GenerateMasqueConfigUseCase(
    private val cloudflareApi: CloudflareApi,
    private val configRepository: ConfigRepository
) {
    suspend operator fun invoke(
        customName: String = "Cloudflare WARP MASQUE",
        licenseKey: String? = null,
        sniOverride: String? = null,
        serverIp: String = "188.114.97.1",
        serverPort: Int = 443
    ): Result<MasqueConfig> {
        return runCatching {
            val warpResult = cloudflareApi.generateWarpConfig(licenseKey = licenseKey)
            val warpConfig = warpResult.getOrThrow()

            val masqueConfig = MasqueConfig(
                name = customName,
                server = "engage.cloudflareclient.com",
                serverPort = serverPort,
                serverIp = serverIp,
                sni = sniOverride?.ifBlank { "engage.cloudflareclient.com" } ?: "engage.cloudflareclient.com",
                accountId = warpConfig.accountId,
                accessToken = warpConfig.accessToken,
                v4Address = warpConfig.v4Address,
                v6Address = warpConfig.v6Address,
                privateKey = warpConfig.privateKey,
                publicKey = warpConfig.publicKey,
                reserved = warpConfig.reserved
            )

            // Also save the AWG representation in the repository so user can connect locally
            val awgConfig = warpConfig.toAwgConfig(customName)
            configRepository.saveConfig(
                awgConfig.copy(
                    originType = "MASQUE",
                    endpoint = "$serverIp:854",
                    sni = masqueConfig.sni
                )
            )

            masqueConfig
        }
    }
}
