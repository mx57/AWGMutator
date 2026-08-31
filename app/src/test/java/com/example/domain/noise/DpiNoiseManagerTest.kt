package com.example.domain.noise

import com.example.domain.model.AwgConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DpiNoiseManagerTest {

    private lateinit var noiseManager: DpiNoiseManager

    @Before
    fun setUp() {
        noiseManager = DpiNoiseManager()
    }

    @Test
    fun `calculateDynamicWindowSize coerces lower bound when baseMtu is small or zero`() {
        // baseMtu * baseMultiplier + jitter when baseMtu is 0 or negative will be <= 0 + jitter <= 512
        // which must be clamped to 32768
        val resultZero = noiseManager.calculateDynamicWindowSize(baseMtu = 0)
        assertEquals(32768, resultZero)

        val resultSmall = noiseManager.calculateDynamicWindowSize(baseMtu = 100)
        assertEquals(32768, resultSmall)

        val resultNegative = noiseManager.calculateDynamicWindowSize(baseMtu = -500)
        assertEquals(32768, resultNegative)
    }

    @Test
    fun `calculateDynamicWindowSize coerces upper bound when baseMtu is very large`() {
        // baseMtu * 32..64 + jitter with baseMtu = 10000 -> >= 320000 - 512 = 319488
        // which must be clamped to 262144
        val resultLarge = noiseManager.calculateDynamicWindowSize(baseMtu = 10000)
        assertEquals(262144, resultLarge)

        val resultVeryLarge = noiseManager.calculateDynamicWindowSize(baseMtu = 50000)
        assertEquals(262144, resultVeryLarge)
    }

    @Test
    fun `calculateDynamicWindowSize with standard baseMtu produces values strictly within bounds`() {
        // Test standard MTUs across 100 iterations
        val standardMtus = listOf(1280, 1360, 1420, 1500)
        for (mtu in standardMtus) {
            repeat(100) {
                val windowSize = noiseManager.calculateDynamicWindowSize(baseMtu = mtu)
                assertTrue(
                    "Calculated window size $windowSize for MTU $mtu should be >= 32768 and <= 262144",
                    windowSize in 32768..262144
                )
            }
        }
    }

    @Test
    fun `generateHandshakeNoisePadding returns byte array within expected bounds`() {
        repeat(50) {
            val padding = noiseManager.generateHandshakeNoisePadding(minBytes = 32, maxBytes = 256)
            assertTrue("Padding size should be in 32..256 range", padding.size in 32..256)
        }

        // Test equal min and max
        val exactPadding = noiseManager.generateHandshakeNoisePadding(minBytes = 64, maxBytes = 64)
        assertEquals(64, exactPadding.size)

        // Test minBytes > maxBytes fallback
        val fallbackPadding = noiseManager.generateHandshakeNoisePadding(minBytes = 100, maxBytes = 50)
        assertEquals(100, fallbackPadding.size)
    }

    @Test
    fun `applyRuntimeNoiseModulation modulates jmin jmax s1 s2 within valid bounds`() {
        val baseConfig = AwgConfig(
            name = "TestConfig",
            privateKey = "dGVzdF9wcml2YXRlX2tleV9mb3JfdW5pdF90ZXN0czA=",
            mtu = 1360,
            jmin = 40,
            jmax = 100,
            s1 = 20,
            s2 = 30
        )

        repeat(50) {
            val modulated = noiseManager.applyRuntimeNoiseModulation(baseConfig)

            assertTrue("jmin should be within 16..512", modulated.jmin in 16..512)
            assertTrue("jmax should be >= jmin + 32 and <= 1024", modulated.jmax >= modulated.jmin + 32 && modulated.jmax <= 1024)
            assertTrue("s1 should be within 4..64", modulated.s1 in 4..64)
            assertTrue("s2 should be within 8..64", modulated.s2 in 8..64)
        }
    }

    @Test
    fun `injectNoiseIntoConfig applies distinct noise profile settings`() {
        val baseConfig = AwgConfig(
            name = "TestConfig",
            privateKey = "dGVzdF9wcml2YXRlX2tleV9mb3JfdW5pdF90ZXN0czA="
        )

        for (profile in NoiseProfile.values()) {
            val injected = noiseManager.injectNoiseIntoConfig(baseConfig, profile)

            assertTrue("jc should be positive", injected.jc > 0)
            assertTrue("jmin should be positive", injected.jmin > 0)
            assertTrue("jmax should be > jmin", injected.jmax > injected.jmin)
            assertTrue("s1 should be positive", injected.s1 > 0)
            assertTrue("s2 should be positive", injected.s2 > 0)
            assertTrue("h1 should be positive", injected.h1 > 0L)
            assertTrue("h2 should be positive", injected.h2 > 0L)
            assertTrue("h3 should be positive", injected.h3 > 0L)
            assertTrue("h4 should be positive", injected.h4 > 0L)
            assertEquals(1360, injected.mtu)
        }
    }
}
