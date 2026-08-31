package com.example

import com.example.util.ConfigParser
import com.example.util.WireGuardKeyGen
import org.amnezia.awg.config.BadConfigException
import org.amnezia.awg.config.Config
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class AmneziaWgConfigTest {

    @Test
    fun testParseAmneziaWgConfigWithObfuscationParameters() {
        val keyPair1 = WireGuardKeyGen.generateKeyPair()
        val keyPair2 = WireGuardKeyGen.generateKeyPair()

        val sampleAwgConf = """
            [Interface]
            PrivateKey = ${keyPair1.privateKey}
            Address = 10.0.0.2/32, fd00::2/128
            DNS = 1.1.1.1, 8.8.8.8
            MTU = 1360
            Jc = 4
            Jmin = 40
            Jmax = 70
            S1 = 15
            S2 = 25
            H1 = 0x12345678
            H2 = 0x87654321
            H3 = 100003
            H4 = 100004
            I1 = custom_init_junk
            SNI = google.com
            Reserved = 1,2,3

            [Peer]
            PublicKey = ${keyPair2.publicKey}
            AllowedIPs = 0.0.0.0/0, ::/0
            Endpoint = 194.87.12.34:51820
            PersistentKeepalive = 25
        """.trimIndent()

        val parseResult = ConfigParser.parse(sampleAwgConf, "Test WgTunnel AWG")
        assertTrue(parseResult.isSuccess)

        val config = parseResult.getOrThrow()
        assertEquals(4, config.jc)
        assertEquals(40, config.jmin)
        assertEquals(70, config.jmax)
        assertEquals(15, config.s1)
        assertEquals(25, config.s2)
        assertEquals(0x12345678L, config.h1)
        assertEquals(0x87654321L, config.h2)
        assertEquals(100003L, config.h3)
        assertEquals(100004L, config.h4)
        assertEquals("custom_init_junk", config.i1)
        assertEquals("google.com", config.sni)
        assertEquals("1,2,3", config.reserved)

        // Convert back to conf string
        val exportedConf = config.toConfString()

        assertTrue(exportedConf.contains("Jc = 4"))
        assertTrue(exportedConf.contains("Jmin = 40"))
        assertTrue(exportedConf.contains("Jmax = 70"))
        assertTrue(exportedConf.contains("S1 = 15"))
        assertTrue(exportedConf.contains("S2 = 25"))
        assertTrue(exportedConf.contains("H1 = 305419896"))
        assertTrue(exportedConf.contains("I1 = custom_init_junk"))
        assertTrue(exportedConf.contains("Reserved = 1, 2, 3") || exportedConf.contains("Reserved = 1,2,3"))

        val extendedKeys = setOf("reserved", "sni", "i1", "i2", "i3", "i4", "s3", "s4")
        val nativeConf = exportedConf.lines().filterNot { line ->
            val key = line.substringBefore("=").trim().lowercase()
            key in extendedKeys
        }.joinToString("\n")

        // Verify native AmneziaWG Config parser accepts this configuration!
        val inputStream = ByteArrayInputStream(nativeConf.toByteArray(Charsets.UTF_8))
        val awgNativeConfig = Config.parse(inputStream)
        assertNotNull(awgNativeConfig)
        assertEquals(4, awgNativeConfig.`interface`.junkPacketCount.orElse(0))
        assertEquals(40, awgNativeConfig.`interface`.junkPacketMinSize.orElse(0))
        assertEquals(70, awgNativeConfig.`interface`.junkPacketMaxSize.orElse(0))
        assertEquals(15, awgNativeConfig.`interface`.initPacketJunkSize.orElse(0))
        assertEquals(25, awgNativeConfig.`interface`.responsePacketJunkSize.orElse(0))
    }

    @Test
    fun testParseStandardWireGuardConfig() {
        val keyPair1 = WireGuardKeyGen.generateKeyPair()
        val keyPair2 = WireGuardKeyGen.generateKeyPair()

        val sampleWgConf = """
            [Interface]
            PrivateKey = ${keyPair1.privateKey}
            Address = 10.2.0.2/32
            DNS = 1.1.1.1

            [Peer]
            PublicKey = ${keyPair2.publicKey}
            AllowedIPs = 0.0.0.0/0
            Endpoint = 1.1.1.1:51820
        """.trimIndent()

        val parseResult = ConfigParser.parse(sampleWgConf, "Standard WireGuard")
        assertTrue(parseResult.isSuccess)

        val config = parseResult.getOrThrow()
        assertEquals(0, config.jc)

        val exportedConf = config.toConfString()
        val nativeConf = exportedConf.lines().filterNot { line ->
            val trimmed = line.trim().lowercase()
            trimmed.startsWith("reserved =") ||
            trimmed.startsWith("sni =") ||
            trimmed.startsWith("i1 =") ||
            trimmed.startsWith("i2 =") ||
            trimmed.startsWith("i3 =") ||
            trimmed.startsWith("i4 =")
        }.joinToString("\n")

        val inputStream = ByteArrayInputStream(nativeConf.toByteArray(Charsets.UTF_8))
        val awgNativeConfig = Config.parse(inputStream)
        assertNotNull(awgNativeConfig)
    }

    @Test
    fun testExtractS1FromI1HexPayload() {
        val keyPair1 = WireGuardKeyGen.generateKeyPair()
        val keyPair2 = WireGuardKeyGen.generateKeyPair()

        val sampleAwg3Conf = """
            [Interface]
            PrivateKey = ${keyPair1.privateKey}
            Address = 172.16.0.2/32
            DNS = 1.1.1.1, 8.8.8.8
            S1 = 0
            S2 = 0
            I1 = <b 0xce000000010897a297ecc34cd6dd0000>

            [Peer]
            PublicKey = ${keyPair2.publicKey}
            AllowedIPs = 0.0.0.0/0
            Endpoint = 194.87.12.34:51820
        """.trimIndent()

        val parseResult = ConfigParser.parse(sampleAwg3Conf, "AWG 3.0 Test")
        assertTrue(parseResult.isSuccess)

        val config = parseResult.getOrThrow()
        assertEquals(16, config.s1)

        val exportedConf = config.toConfString()
        assertTrue(exportedConf.contains("S1 = 16"))

        val nativeConf = exportedConf.lines().filterNot { line ->
            val trimmed = line.trim().lowercase()
            trimmed.startsWith("reserved =") ||
            trimmed.startsWith("sni =") ||
            trimmed.startsWith("i1 =") ||
            trimmed.startsWith("i2 =") ||
            trimmed.startsWith("i3 =") ||
            trimmed.startsWith("i4 =")
        }.joinToString("\n")

        val inputStream = ByteArrayInputStream(nativeConf.toByteArray(Charsets.UTF_8))
        val awgNativeConfig = Config.parse(inputStream)
        assertNotNull(awgNativeConfig)
        assertEquals(16, awgNativeConfig.`interface`.initPacketJunkSize.orElse(0))
    }

    @Test
    fun testJminJmaxBoundEnforcement() {
        val keyPair1 = WireGuardKeyGen.generateKeyPair()
        val keyPair2 = WireGuardKeyGen.generateKeyPair()

        val sampleAwgConf = """
            [Interface]
            PrivateKey = ${keyPair1.privateKey}
            Address = 10.0.0.2/32
            Jc = 5
            Jmin = 100
            Jmax = 50

            [Peer]
            PublicKey = ${keyPair2.publicKey}
            AllowedIPs = 0.0.0.0/0
            Endpoint = 1.2.3.4:51820
        """.trimIndent()

        val parseResult = ConfigParser.parse(sampleAwgConf, "Jmin/Jmax test")
        assertTrue(parseResult.isSuccess)

        val config = parseResult.getOrThrow()
        assertTrue(config.jmin <= config.jmax)
        assertEquals(50, config.jmin)
        assertEquals(100, config.jmax)
    }

    @Test
    fun testSanitizeConfigNameInjectedNewlinesAndControlCharacters() {
        val maliciousInput = "Test\r\n[Injected Log Header]\nName\u0000Special\t"
        val sanitized = ConfigParser.sanitizeConfigName(maliciousInput)
        assertEquals("Test[Injected Log Header]NameSpecial", sanitized)

        val nullInputSanitized = ConfigParser.sanitizeConfigName(null, "Fallback Tunnel")
        assertEquals("Fallback Tunnel", nullInputSanitized)

        val longInput = "A".repeat(150)
        val truncated = ConfigParser.sanitizeConfigName(longInput)
        assertEquals(100, truncated.length)
        assertEquals("A".repeat(100), truncated)
    }
}
