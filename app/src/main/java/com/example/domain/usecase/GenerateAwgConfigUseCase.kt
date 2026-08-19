package com.example.domain.usecase

import com.example.domain.model.AwgConfig
import com.example.domain.repository.ConfigRepository
import com.example.util.WireGuardKeyGen
import java.util.Random
import java.util.UUID

enum class ObfuscationPreset {
    BALANCED,
    EXTREME_ANTI_DPI,
    DYNAMIC_JITTER_ENTROPY,
    RANDOM_PAYLOAD_FRAGMENTATION,
    LIGHT_SPEED,
    GAMING_LOW_LATENCY
}

/**
 * Generates and validates an AmneziaWG configuration with randomized or custom obfuscation headers,
 * advanced junk packet patterns (frequently varying Jmin/Jmax spreads), and random payload
 * fragmentation settings tailored to bypass Deep Packet Inspection (DPI) signatures.
 */
class GenerateAwgConfigUseCase(
    private val configRepository: ConfigRepository
) {
    private val random = Random()

    suspend operator fun invoke(
        name: String = "AmneziaWG Custom",
        endpoint: String = "192.168.1.1:51820",
        peerPublicKey: String = "",
        presharedKey: String? = null,
        address: String = "10.0.0.2/32",
        dns: String = "1.1.1.1, 8.8.8.8",
        mtu: Int = 1360,
        preset: ObfuscationPreset? = null,
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
        customH4: Long? = null
    ): Result<AwgConfig> {
        return runCatching {
            val keyPair = WireGuardKeyGen.generateKeyPair()
            val effectivePeerKey = if (peerPublicKey.isNotBlank()) peerPublicKey else WireGuardKeyGen.generateKeyPair().publicKey

            val (pjmin, pjmax, pjc, ps1, ps2, ps3, ps4) = when (preset) {
                ObfuscationPreset.EXTREME_ANTI_DPI -> {
                    // Maximum entropy and obfuscation depth for TSPU / RKN / GFW
                    val jmin = 120 + random.nextInt(60)
                    val jmax = jmin + 400 + random.nextInt(300)
                    Tuple7(jmin, jmax, 5 + random.nextInt(4), 28 + random.nextInt(12), 36 + random.nextInt(16), 24 + random.nextInt(10), 16 + random.nextInt(8))
                }
                ObfuscationPreset.DYNAMIC_JITTER_ENTROPY -> {
                    // Frequently shifted Jmin/Jmax spread to defeat statistical packet-size learning
                    val jmin = 48 + random.nextInt(120)
                    val jmax = jmin + 256 + random.nextInt(450)
                    Tuple7(jmin, jmax, 4 + random.nextInt(3), 20 + random.nextInt(15), 28 + random.nextInt(18), 18 + random.nextInt(12), 12 + random.nextInt(8))
                }
                ObfuscationPreset.RANDOM_PAYLOAD_FRAGMENTATION -> {
                    // Emulates TLS/QUIC packet size distributions
                    val jmin = 80 + random.nextInt(80)
                    val jmax = jmin + 320 + random.nextInt(280)
                    Tuple7(jmin, jmax, 3 + random.nextInt(4), 32 + random.nextInt(24), 44 + random.nextInt(20), 28 + random.nextInt(14), 18 + random.nextInt(10))
                }
                ObfuscationPreset.LIGHT_SPEED -> {
                    Tuple7(64, 192, 2, 8, 12, 8, 4)
                }
                ObfuscationPreset.GAMING_LOW_LATENCY -> {
                    Tuple7(40, 120, 1, 4, 8, 4, 2)
                }
                else -> {
                    val jmin = 64 + random.nextInt(128)
                    val jmax = jmin + 180 + random.nextInt(300)
                    Tuple7(
                        jmin,
                        jmax,
                        2 + random.nextInt(5),
                        14 + random.nextInt(26),
                        18 + random.nextInt(30),
                        12 + random.nextInt(20),
                        6 + random.nextInt(14)
                    )
                }
            }

            val jmin = customJmin ?: pjmin
            val jmax = customJmax ?: pjmax.coerceAtLeast(jmin + 32)
            val jc = customJc ?: pjc

            val s1 = customS1 ?: ps1
            val s2 = customS2 ?: ps2
            val s3 = customS3 ?: ps3
            val s4 = customS4 ?: ps4

            // Ensure H1..H4 are strictly distinct high-entropy magic numbers
            val h1 = customH1 ?: generateDistinctHeader(emptySet())
            val h2 = customH2 ?: generateDistinctHeader(setOf(h1))
            val h3 = customH3 ?: generateDistinctHeader(setOf(h1, h2))
            val h4 = customH4 ?: generateDistinctHeader(setOf(h1, h2, h3))

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
                peerPublicKey = effectivePeerKey,
                presharedKey = presharedKey,
                allowedIps = "0.0.0.0/0, ::/0",
                endpoint = endpoint,
                persistentKeepalive = 25
            )

            configRepository.saveConfig(config)
            config
        }
    }

    private fun generateDistinctHeader(existing: Set<Long>): Long {
        var h: Long
        do {
            h = (random.nextLong() and 0x7FFFFFFF) + 1000000L
        } while (existing.contains(h))
        return h
    }

    private data class Tuple7(
        val a: Int,
        val b: Int,
        val c: Int,
        val d: Int,
        val e: Int,
        val f: Int,
        val g: Int
    )
}
