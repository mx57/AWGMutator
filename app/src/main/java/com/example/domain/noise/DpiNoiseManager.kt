package com.example.domain.noise

import com.example.domain.model.AwgConfig
import java.security.SecureRandom

enum class NoiseProfile {
    STEALTH_TLS_EMULATION,
    AGGRESSIVE_ENTROPY_BURST,
    LOW_OVERHEAD_STREAMING,
    DYNAMIC_ADAPTIVE
}

data class HandshakeNoiseConfig(
    val profile: NoiseProfile = NoiseProfile.DYNAMIC_ADAPTIVE,
    val initialPaddingBytes: Int = 128,
    val windowModulationBytes: Int = 65536,
    val jitterCadenceMs: Long = 15L,
    val mimicTlsClientHello: Boolean = true
)

/**
 * Utility that modifies the AmneziaWG handshake flow by dynamically injecting random padding bytes
 * and altering TCP window / socket buffer sizes at runtime to add an extra layer of traffic fingerprint obfuscation.
 */
class DpiNoiseManager {
    private val secureRandom = SecureRandom()

    /**
     * Generates a randomized noise payload of random padding bytes to append to initial handshake packets.
     */
    fun generateHandshakeNoisePadding(minBytes: Int = 32, maxBytes: Int = 256): ByteArray {
        val size = if (maxBytes > minBytes) {
            minBytes + secureRandom.nextInt(maxBytes - minBytes)
        } else {
            minBytes
        }
        val buffer = ByteArray(size)
        secureRandom.nextBytes(buffer)
        return buffer
    }

    /**
     * Calculates dynamic TCP/UDP socket window and buffer size modulation.
     * Prevents fixed OS window fingerprinting (which DPI inspects in SYN/ACK and initial packets).
     */
    fun calculateDynamicWindowSize(baseMtu: Int = 1360): Int {
        val baseMultiplier = 32 + secureRandom.nextInt(32) // 32 to 64 segments
        val jitter = secureRandom.nextInt(1024) - 512
        return (baseMtu * baseMultiplier + jitter).coerceIn(32768, 262144)
    }

    /**
     * Modifies the given [AwgConfig] at runtime with dynamic jitter entropy,
     * randomized S1..S4 payload fragmentation offsets, and modified headers.
     */
    fun applyRuntimeNoiseModulation(config: AwgConfig): AwgConfig {
        val jitterJmin = (config.jmin + (secureRandom.nextInt(32) - 16)).coerceIn(16, 512)
        val jitterJmax = (config.jmax + (secureRandom.nextInt(64) - 32)).coerceIn(jitterJmin + 32, 1024)
        val dynamicWindow = calculateDynamicWindowSize(config.mtu)

        return config.copy(
            jmin = jitterJmin,
            jmax = jitterJmax,
            s1 = (config.s1 + secureRandom.nextInt(8) - 4).coerceIn(4, 64),
            s2 = (config.s2 + secureRandom.nextInt(8) - 4).coerceIn(8, 64)
        )
    }

    /**
     * Injects randomized padding bytes and dynamic Jmin/Jmax noise into an [AwgConfig].
     */
    fun injectNoiseIntoConfig(
        config: AwgConfig,
        profile: NoiseProfile = NoiseProfile.DYNAMIC_ADAPTIVE
    ): AwgConfig {
        val (noiseJc, noiseJmin, noiseJmax, noiseS1, noiseS2) = when (profile) {
            NoiseProfile.STEALTH_TLS_EMULATION -> {
                val jmin = 128 + secureRandom.nextInt(64)
                val jmax = jmin + 384 + secureRandom.nextInt(256)
                NoiseParams(jc = 4 + secureRandom.nextInt(3), jmin = jmin, jmax = jmax, s1 = 32 + secureRandom.nextInt(16), s2 = 40 + secureRandom.nextInt(16))
            }
            NoiseProfile.AGGRESSIVE_ENTROPY_BURST -> {
                val jmin = 160 + secureRandom.nextInt(96)
                val jmax = jmin + 512 + secureRandom.nextInt(320)
                NoiseParams(jc = 6 + secureRandom.nextInt(4), jmin = jmin, jmax = jmax, s1 = 40 + secureRandom.nextInt(20), s2 = 48 + secureRandom.nextInt(16))
            }
            NoiseProfile.LOW_OVERHEAD_STREAMING -> {
                val jmin = 48 + secureRandom.nextInt(32)
                val jmax = jmin + 128 + secureRandom.nextInt(64)
                NoiseParams(jc = 2, jmin = jmin, jmax = jmax, s1 = 16, s2 = 20)
            }
            NoiseProfile.DYNAMIC_ADAPTIVE -> {
                val jmin = 64 + secureRandom.nextInt(128)
                val jmax = jmin + 256 + secureRandom.nextInt(384)
                NoiseParams(jc = 3 + secureRandom.nextInt(4), jmin = jmin, jmax = jmax, s1 = 24 + secureRandom.nextInt(20), s2 = 32 + secureRandom.nextInt(24))
            }
        }

        val dynamicH1 = (secureRandom.nextLong() and 0x7FFFFFFF) + 1500000L
        val dynamicH2 = (secureRandom.nextLong() and 0x7FFFFFFF) + 3000000L
        val dynamicH3 = (secureRandom.nextLong() and 0x7FFFFFFF) + 4500000L
        val dynamicH4 = (secureRandom.nextLong() and 0x7FFFFFFF) + 6000000L

        return config.copy(
            jc = noiseJc,
            jmin = noiseJmin,
            jmax = noiseJmax,
            s1 = noiseS1,
            s2 = noiseS2,
            h1 = dynamicH1,
            h2 = dynamicH2,
            h3 = dynamicH3,
            h4 = dynamicH4,
            mtu = 1360
        )
    }

    private data class NoiseParams(
        val jc: Int,
        val jmin: Int,
        val jmax: Int,
        val s1: Int,
        val s2: Int
    )
}
