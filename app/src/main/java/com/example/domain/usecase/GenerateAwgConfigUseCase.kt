package com.example.domain.usecase

import com.example.domain.model.AwgConfig
import com.example.domain.repository.ConfigRepository
import com.example.util.WireGuardKeyGen
import java.security.SecureRandom
import java.util.UUID

enum class ObfuscationPreset {
    VERIFIED_AWG_RUSSIAN_BYPASS,
    BALANCED,
    EXTREME_ANTI_DPI,
    DYNAMIC_JITTER_ENTROPY,
    RANDOM_PAYLOAD_FRAGMENTATION,
    LIGHT_SPEED,
    GAMING_LOW_LATENCY
}

/**
 * Generates and validates an AmneziaWG configuration with randomized or custom obfuscation headers,
 * advanced junk packet patterns, I1 handshake noise, Russian whitelist SNI, and fine-grained subnets.
 */
class GenerateAwgConfigUseCase(
    private val configRepository: ConfigRepository
) {
    private val random = SecureRandom()

    suspend operator fun invoke(
        name: String = "AmneziaWG Custom",
        endpoint: String = "188.114.97.1:1074",
        peerPublicKey: String = "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=",
        presharedKey: String? = null,
        address: String = "172.16.0.2/32",
        dns: String = "1.1.1.1, 8.8.8.8, 1.0.0.1",
        mtu: Int = 1280,
        preset: ObfuscationPreset? = ObfuscationPreset.VERIFIED_AWG_RUSSIAN_BYPASS,
        customJc: Int? = null,
        customJmin: Int? = null,
        customJmax: Int? = null,
        customS1: Int? = null,
        customS2: Int? = null,
        customS3: Int? = null,
        customS4: Int? = null,
        customH1: Long? = null,
        customH2: Long? = null,
        customH3: Long? = null,
        customH4: Long? = null,
        customI1: String? = null,
        customSni: String? = null,
        isWarp: Boolean = false,
        reserved: String? = null
    ): Result<AwgConfig> {
        return runCatching {
            val keyPair = WireGuardKeyGen.generateKeyPair()
            val effectivePeerKey = peerPublicKey.ifBlank { "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=" }

            val presetParams = resolvePresetParams(preset)
            val obfuscation = computeEffectiveObfuscation(
                presetParams = presetParams,
                customJc = customJc,
                customJmin = customJmin,
                customJmax = customJmax,
                customS1 = customS1,
                customS2 = customS2,
                customS3 = customS3,
                customS4 = customS4,
                customH1 = customH1,
                customH2 = customH2,
                customH3 = customH3,
                customH4 = customH4
            )

            val i1 = generateI1Noise(preset, customI1)

            val isCloudflareWarpPeer = isWarp
            val effectiveH1 = if (isCloudflareWarpPeer && customH1 == null) 1L else obfuscation.h1
            val effectiveH2 = if (isCloudflareWarpPeer && customH2 == null) 2L else obfuscation.h2
            val effectiveH3 = if (isCloudflareWarpPeer && customH3 == null) 3L else obfuscation.h3
            val effectiveH4 = if (isCloudflareWarpPeer && customH4 == null) 4L else obfuscation.h4
            val effectiveJc = if (isCloudflareWarpPeer && customJc == null) 0 else obfuscation.jc.coerceIn(0, 10)

            val config = AwgConfig(
                id = UUID.randomUUID().toString(),
                name = name,
                privateKey = keyPair.privateKey,
                address = address,
                dns = dns,
                mtu = mtu.coerceIn(1280, 1420),
                jc = effectiveJc,
                jmin = if (isCloudflareWarpPeer && customJmin == null) 0 else obfuscation.jmin.coerceIn(0, 1024),
                jmax = if (isCloudflareWarpPeer && customJmax == null) 0 else obfuscation.jmax.coerceIn(obfuscation.jmin.coerceIn(0, 1024), 1024),
                s1 = if (isCloudflareWarpPeer && customS1 == null) 0 else obfuscation.s1.coerceIn(0, 64),
                s2 = if (isCloudflareWarpPeer && customS2 == null) 0 else obfuscation.s2.coerceIn(0, 64),
                s3 = if (isCloudflareWarpPeer && customS3 == null) 0 else obfuscation.s3.coerceIn(0, 64),
                s4 = if (isCloudflareWarpPeer && customS4 == null) 0 else obfuscation.s4.coerceIn(0, 32),
                h1 = effectiveH1,
                h2 = effectiveH2,
                h3 = effectiveH3,
                h4 = effectiveH4,
                i1 = if (isCloudflareWarpPeer && customI1 == null) null else i1,
                sni = customSni,
                peerPublicKey = effectivePeerKey,
                presharedKey = presharedKey,
                endpoint = endpoint,
                persistentKeepalive = 25,
                isWarp = isCloudflareWarpPeer,
                reserved = reserved,
                originType = "MANUAL",
                createdAt = System.currentTimeMillis()
            )

            configRepository.saveConfig(config)
            config
        }
    }

    private fun resolvePresetParams(preset: ObfuscationPreset?): ObfuscationParams {
        return when (preset) {
            ObfuscationPreset.VERIFIED_AWG_RUSSIAN_BYPASS -> {
                ObfuscationParams(40, 70, 4, 0, 0, 0, 0, 1L, 2L, 3L, 4L)
            }
            ObfuscationPreset.EXTREME_ANTI_DPI -> {
                val jmin = 120 + random.nextInt(60)
                val jmax = jmin + 400 + random.nextInt(300)
                ObfuscationParams(
                    jmin, jmax, 5 + random.nextInt(4),
                    28 + random.nextInt(12), 36 + random.nextInt(16),
                    24 + random.nextInt(10), 16 + random.nextInt(8),
                    123456L, 234567L, 345678L, 456789L
                )
            }
            ObfuscationPreset.DYNAMIC_JITTER_ENTROPY -> {
                val jmin = 48 + random.nextInt(120)
                val jmax = jmin + 256 + random.nextInt(450)
                ObfuscationParams(
                    jmin, jmax, 4 + random.nextInt(3),
                    20 + random.nextInt(15), 28 + random.nextInt(18),
                    18 + random.nextInt(12), 12 + random.nextInt(8),
                    112233L, 223344L, 334455L, 445566L
                )
            }
            ObfuscationPreset.RANDOM_PAYLOAD_FRAGMENTATION -> {
                val jmin = 80 + random.nextInt(80)
                val jmax = jmin + 320 + random.nextInt(280)
                ObfuscationParams(
                    jmin, jmax, 3 + random.nextInt(4),
                    32 + random.nextInt(24), 44 + random.nextInt(20),
                    28 + random.nextInt(14), 18 + random.nextInt(10),
                    1001L, 2002L, 3003L, 4004L
                )
            }
            ObfuscationPreset.LIGHT_SPEED -> {
                ObfuscationParams(64, 192, 2, 8, 12, 8, 4, 1L, 2L, 3L, 4L)
            }
            ObfuscationPreset.GAMING_LOW_LATENCY -> {
                ObfuscationParams(40, 120, 1, 4, 8, 4, 2, 1L, 2L, 3L, 4L)
            }
            else -> {
                val jmin = 40 + random.nextInt(60)
                val jmax = jmin + 60 + random.nextInt(100)
                ObfuscationParams(jmin, jmax, 4, 0, 0, 0, 0, 1L, 2L, 3L, 4L)
            }
        }
    }

    private fun computeEffectiveObfuscation(
        presetParams: ObfuscationParams,
        customJc: Int?,
        customJmin: Int?,
        customJmax: Int?,
        customS1: Int?,
        customS2: Int?,
        customS3: Int?,
        customS4: Int?,
        customH1: Long?,
        customH2: Long?,
        customH3: Long?,
        customH4: Long?
    ): ObfuscationParams {
        val jmin = (customJmin ?: presetParams.jmin).coerceIn(40, 60)
        val jmax = (customJmax ?: presetParams.jmax).coerceIn(jmin, 1400)
        val jc = (customJc ?: presetParams.jc).coerceIn(1, 128)

        val s1 = (customS1 ?: presetParams.s1).coerceIn(0, 50)
        val s2 = (customS2 ?: presetParams.s2).coerceIn(0, 50)
        val s3 = (customS3 ?: presetParams.s3).coerceIn(0, 50)
        val s4 = (customS4 ?: presetParams.s4).coerceIn(0, 50)

        val h1 = customH1 ?: presetParams.h1
        val h2 = customH2 ?: presetParams.h2
        val h3 = customH3 ?: presetParams.h3
        val h4 = customH4 ?: presetParams.h4

        return ObfuscationParams(jmin, jmax, jc, s1, s2, s3, s4, h1, h2, h3, h4)
    }

    private fun generateI1Noise(preset: ObfuscationPreset?, customI1: String?): String? {
        return customI1 ?: if (preset == ObfuscationPreset.VERIFIED_AWG_RUSSIAN_BYPASS || preset == ObfuscationPreset.EXTREME_ANTI_DPI) {
            val noise = ByteArray(48).apply { random.nextBytes(this) }
            "<b 0x${noise.joinToString("") { "%02x".format(it) }}>"
        } else null
    }

    private data class ObfuscationParams(
        val jmin: Int,
        val jmax: Int,
        val jc: Int,
        val s1: Int,
        val s2: Int,
        val s3: Int,
        val s4: Int,
        val h1: Long,
        val h2: Long,
        val h3: Long,
        val h4: Long
    )
}
