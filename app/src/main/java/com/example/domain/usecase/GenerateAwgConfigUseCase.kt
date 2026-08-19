package com.example.domain.usecase

import com.example.domain.model.AwgConfig
import com.example.domain.repository.ConfigRepository
import com.example.util.WireGuardKeyGen
import java.util.Random
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
    private val random = Random()

    suspend operator fun invoke(
        name: String = "AmneziaWG Custom",
        endpoint: String = "162.159.192.13:1074",
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
            val effectivePeerKey = if (peerPublicKey.isNotBlank()) peerPublicKey else "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo="

            val (pjmin, pjmax, pjc, ps1, ps2, ps3, ps4, ph1, ph2, ph3, ph4) = when (preset) {
                ObfuscationPreset.VERIFIED_AWG_RUSSIAN_BYPASS -> {
                    Tuple11(40, 70, 4, 0, 0, 0, 0, 1L, 2L, 3L, 4L)
                }
                ObfuscationPreset.EXTREME_ANTI_DPI -> {
                    val jmin = 120 + random.nextInt(60)
                    val jmax = jmin + 400 + random.nextInt(300)
                    Tuple11(jmin, jmax, 5 + random.nextInt(4), 28 + random.nextInt(12), 36 + random.nextInt(16), 24 + random.nextInt(10), 16 + random.nextInt(8), 123456L, 234567L, 345678L, 456789L)
                }
                ObfuscationPreset.DYNAMIC_JITTER_ENTROPY -> {
                    val jmin = 48 + random.nextInt(120)
                    val jmax = jmin + 256 + random.nextInt(450)
                    Tuple11(jmin, jmax, 4 + random.nextInt(3), 20 + random.nextInt(15), 28 + random.nextInt(18), 18 + random.nextInt(12), 12 + random.nextInt(8), 112233L, 223344L, 334455L, 445566L)
                }
                ObfuscationPreset.RANDOM_PAYLOAD_FRAGMENTATION -> {
                    val jmin = 80 + random.nextInt(80)
                    val jmax = jmin + 320 + random.nextInt(280)
                    Tuple11(jmin, jmax, 3 + random.nextInt(4), 32 + random.nextInt(24), 44 + random.nextInt(20), 28 + random.nextInt(14), 18 + random.nextInt(10), 1001L, 2002L, 3003L, 4004L)
                }
                ObfuscationPreset.LIGHT_SPEED -> {
                    Tuple11(64, 192, 2, 8, 12, 8, 4, 1L, 2L, 3L, 4L)
                }
                ObfuscationPreset.GAMING_LOW_LATENCY -> {
                    Tuple11(40, 120, 1, 4, 8, 4, 2, 1L, 2L, 3L, 4L)
                }
                else -> {
                    val jmin = 40 + random.nextInt(60)
                    val jmax = jmin + 60 + random.nextInt(100)
                    Tuple11(jmin, jmax, 4, 0, 0, 0, 0, 1L, 2L, 3L, 4L)
                }
            }

            val jmin = (customJmin ?: pjmin).coerceIn(40, 60)
            val jmax = (customJmax ?: pjmax).coerceIn(jmin, 1400)
            val jc = (customJc ?: pjc).coerceIn(1, 128)

            val s1 = (customS1 ?: ps1).coerceIn(0, 50)
            val s2 = (customS2 ?: ps2).coerceIn(0, 50)
            val s3 = (customS3 ?: ps3).coerceIn(0, 50)
            val s4 = (customS4 ?: ps4).coerceIn(0, 50)

            val h1 = customH1 ?: ph1
            val h2 = customH2 ?: ph2
            val h3 = customH3 ?: ph3
            val h4 = customH4 ?: ph4

            val i1 = customI1 ?: if (preset == ObfuscationPreset.VERIFIED_AWG_RUSSIAN_BYPASS || preset == ObfuscationPreset.EXTREME_ANTI_DPI) {
                val noise = ByteArray(48).apply { random.nextBytes(this) }
                "<b 0x${noise.joinToString("") { "%02x".format(it) }}>"
            } else null

            val config = AwgConfig(
                id = UUID.randomUUID().toString(),
                name = name,
                privateKey = keyPair.privateKey,
                address = address,
                dns = dns,
                mtu = mtu.coerceIn(1280, 1420),
                jc = jc.coerceIn(0, 10),
                jmin = jmin.coerceIn(0, 1024),
                jmax = jmax.coerceIn(jmin, 1024),
                s1 = s1.coerceIn(0, 64),
                s2 = s2.coerceIn(0, 64),
                s3 = s3.coerceIn(0, 64),
                s4 = s4.coerceIn(0, 32),
                h1 = h1,
                h2 = h2,
                h3 = h3,
                h4 = h4,
                i1 = i1,
                sni = customSni,
                peerPublicKey = effectivePeerKey,
                presharedKey = presharedKey,
                endpoint = endpoint,
                persistentKeepalive = 25,
                isWarp = isWarp,
                reserved = reserved,
                originType = "MANUAL",
                createdAt = System.currentTimeMillis()
            )

            configRepository.saveConfig(config)
            config
        }
    }

    private data class Tuple11(
        val a: Int, val b: Int, val c: Int, val d: Int, val e: Int, val f: Int, val g: Int,
        val h: Long, val i: Long, val j: Long, val k: Long
    )
}
