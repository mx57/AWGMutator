package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigParserTest {

    @Test
    fun testSanitizeConfigName_nullOrBlank_returnsFallback() {
        assertEquals("Imported AWG", ConfigParser.sanitizeConfigName(null))
        assertEquals("Imported AWG", ConfigParser.sanitizeConfigName(""))
        assertEquals("Imported AWG", ConfigParser.sanitizeConfigName("   "))
        assertEquals("Custom Fallback", ConfigParser.sanitizeConfigName(null, "Custom Fallback"))
        assertEquals("Custom Fallback", ConfigParser.sanitizeConfigName("  ", "Custom Fallback"))
    }

    @Test
    fun testSanitizeConfigName_controlCharactersAndNewlines_stripped() {
        val input = "My\nConfig\r\n\tName\u0000With\u0007ControlChars"
        val sanitized = ConfigParser.sanitizeConfigName(input)
        assertEquals("MyConfigNameWithControlChars", sanitized)
    }

    @Test
    fun testSanitizeConfigName_excessiveLength_truncatedTo100Chars() {
        val longName = "A".repeat(150)
        val sanitized = ConfigParser.sanitizeConfigName(longName)
        assertEquals(100, sanitized.length)
        assertEquals("A".repeat(100), sanitized)
    }

    @Test
    fun testSanitizeConfigName_validName_preserved() {
        val validName = "Cloudflare WARP - Primary"
        assertEquals(validName, ConfigParser.sanitizeConfigName(validName))
    }

    @Test
    fun testParse_minimalStandardWireGuardConfig_successWithDefaults() {
        val rawConfig = """
            [Interface]
            PrivateKey = dGhlIHF1aWNrIGJyb3duIGZveCBqdW1wcyBvdmVy
            Address = 10.0.0.2/32

            [Peer]
            PublicKey = bGF6eSBkb2cgdGhhdCB3YXMgdmVyeSBjb29sIQ==
            Endpoint = 192.168.1.1:51820
        """.trimIndent()

        val result = ConfigParser.parse(rawConfig, "Test Standard WG")
        assertTrue(result.isSuccess)

        val config = result.getOrThrow()
        assertEquals("Test Standard WG", config.name)
        assertEquals("dGhlIHF1aWNrIGJyb3duIGZveCBqdW1wcyBvdmVy", config.privateKey)
        assertEquals("10.0.0.2/32", config.address)
        assertEquals("bGF6eSBkb2cgdGhhdCB3YXMgdmVyeSBjb29sIQ==", config.peerPublicKey)
        assertEquals("192.168.1.1:51820", config.endpoint)
        assertEquals("1.1.1.1, 8.8.8.8, 1.0.0.1", config.dns)
        assertEquals(1280, config.mtu)
        assertEquals("0.0.0.0/0, ::/0", config.allowedIps)
        assertEquals(25, config.persistentKeepalive)
        assertFalse(config.isWarp)
    }

    @Test
    fun testParse_addressFormatting_addsMasksWhenMissing() {
        val rawConfig = """
            [Interface]
            PrivateKey = dGhlIHF1aWNrIGJyb3duIGZveCBqdW1wcyBvdmVy
            Address = 10.0.0.2, fd00::2

            [Peer]
            PublicKey = bGF6eSBkb2cgdGhhdCB3YXMgdmVyeSBjb29sIQ==
            Endpoint = 10.0.0.1:51820
        """.trimIndent()

        val config = ConfigParser.parse(rawConfig).getOrThrow()
        assertEquals("10.0.0.2/32, fd00::2/128", config.address)
    }

    @Test
    fun testParse_amneziaObfuscationParameters_parsedCorrectly() {
        val rawConfig = """
            [Interface]
            PrivateKey = dGhlIHF1aWNrIGJyb3duIGZveCBqdW1wcyBvdmVy
            Address = 10.0.0.2/32
            Jc = 3
            Jmin = 50
            Jmax = 100
            S1 = 15
            S2 = 20
            S3 = 5
            S4 = 10
            H1 = 123456
            H2 = 654321
            H3 = 111111
            H4 = 222222
            SNI = example.com
            I1 = custom_i1

            [Peer]
            PublicKey = bGF6eSBkb2cgdGhhdCB3YXMgdmVyeSBjb29sIQ==
            Endpoint = 1.2.3.4:51820
            PresharedKey = c29tZSBwcmVzaGFyZWQga2V5IGhlcmU=
            Reserved = 10,20,30
        """.trimIndent()

        val config = ConfigParser.parse(rawConfig).getOrThrow()
        assertEquals(3, config.jc)
        assertEquals(50, config.jmin)
        assertEquals(100, config.jmax)
        assertEquals(15, config.s1)
        assertEquals(20, config.s2)
        assertEquals(5, config.s3)
        assertEquals(10, config.s4)
        assertEquals(123456L, config.h1)
        assertEquals(654321L, config.h2)
        assertEquals(111111L, config.h3)
        assertEquals(222222L, config.h4)
        assertEquals("example.com", config.sni)
        assertEquals("custom_i1", config.i1)
        assertEquals("c29tZSBwcmVzaGFyZWQga2V5IGhlcmU=", config.presharedKey)
        assertEquals("10,20,30", config.reserved)
    }

    @Test
    fun testParse_hexValueObfuscationParameters_parsedCorrectly() {
        val rawConfig = """
            [Interface]
            PrivateKey = dGhlIHF1aWNrIGJyb3duIGZveCBqdW1wcyBvdmVy
            Address = 10.0.0.2/32
            Jc = 0x05
            Jmin = 0x28
            Jmax = 0x46
            H1 = 0x1A2B3C4D
            H2 = 0x00000002

            [Peer]
            PublicKey = bGF6eSBkb2cgdGhhdCB3YXMgdmVyeSBjb29sIQ==
            Endpoint = 1.2.3.4:51820
        """.trimIndent()

        val config = ConfigParser.parse(rawConfig).getOrThrow()
        assertEquals(5, config.jc)
        assertEquals(40, config.jmin)
        assertEquals(70, config.jmax)
        assertEquals(0x1A2B3C4DL, config.h1)
        assertEquals(2L, config.h2)
    }

    @Test
    fun testParse_extractS1S2FromHexPayloadI1I2_whenS1S2AreZero() {
        val rawConfig = """
            [Interface]
            PrivateKey = dGhlIHF1aWNrIGJyb3duIGZveCBqdW1wcyBvdmVy
            Address = 10.0.0.2/32
            I1 = <b 0x01020304050607080910>
            I2 = 0xA1B2C3D4E5F6

            [Peer]
            PublicKey = bGF6eSBkb2cgdGhhdCB3YXMgdmVyeSBjb29sIQ==
            Endpoint = 1.2.3.4:51820
        """.trimIndent()

        val config = ConfigParser.parse(rawConfig).getOrThrow()
        assertEquals(10, config.s1) // 10 bytes from 20 hex digits
        assertEquals(6, config.s2)  // 6 bytes from 12 hex digits
    }

    @Test
    fun testParse_jminJmaxBoundsAdjustment_whenJcGreaterThanZero() {
        // Case 1: Jmin and Jmax are 0 -> default to 40 and 70
        val rawConfig1 = """
            [Interface]
            PrivateKey = dGhlIHF1aWNrIGJyb3duIGZveCBqdW1wcyBvdmVy
            Address = 10.0.0.2/32
            Jc = 4

            [Peer]
            PublicKey = bGF6eSBkb2cgdGhhdCB3YXMgdmVyeSBjb29sIQ==
            Endpoint = 1.2.3.4:51820
        """.trimIndent()

        val config1 = ConfigParser.parse(rawConfig1).getOrThrow()
        assertEquals(40, config1.jmin)
        assertEquals(70, config1.jmax)

        // Case 2: Jmin > Jmax -> swapped
        val rawConfig2 = """
            [Interface]
            PrivateKey = dGhlIHF1aWNrIGJyb3duIGZveCBqdW1wcyBvdmVy
            Address = 10.0.0.2/32
            Jc = 4
            Jmin = 80
            Jmax = 30

            [Peer]
            PublicKey = bGF6eSBkb2cgdGhhdCB3YXMgdmVyeSBjb29sIQ==
            Endpoint = 1.2.3.4:51820
        """.trimIndent()

        val config2 = ConfigParser.parse(rawConfig2).getOrThrow()
        assertEquals(30, config2.jmin)
        assertEquals(80, config2.jmax)
    }

    @Test
    fun testParse_cloudflareWarpDetection_viaPublicKey() {
        val warpPeerKey = "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo="
        val rawConfig = """
            [Interface]
            PrivateKey = dGhlIHF1aWNrIGJyb3duIGZveCBqdW1wcyBvdmVy
            Address = 172.16.0.2/32
            DNS = 0.0.0.0
            Reserved = 100,200,300

            [Peer]
            PublicKey = $warpPeerKey
            Endpoint = 162.159.193.1:2408
        """.trimIndent()

        val config = ConfigParser.parse(rawConfig, "Imported").getOrThrow()
        assertTrue(config.isWarp)
        assertEquals("1.1.1.1, 1.0.0.1, 8.8.8.8", config.dns)
    }

    @Test
    fun testParse_cloudflareWarpDetection_viaEndpointDomain() {
        val rawConfig = """
            [Interface]
            PrivateKey = dGhlIHF1aWNrIGJyb3duIGZveCBqdW1wcyBvdmVy
            Address = 172.16.0.2/32

            [Peer]
            PublicKey = bGF6eSBkb2cgdGhhdCB3YXMgdmVyeSBjb29sIQ==
            Endpoint = engage.cloudflareclient.com:2408
        """.trimIndent()

        val config = ConfigParser.parse(rawConfig).getOrThrow()
        assertTrue(config.isWarp)
    }

    @Test
    fun testParse_cloudflareWarpDetection_viaDefaultName() {
        val rawConfig = """
            [Interface]
            PrivateKey = dGhlIHF1aWNrIGJyb3duIGZveCBqdW1wcyBvdmVy
            Address = 172.16.0.2/32

            [Peer]
            PublicKey = bGF6eSBkb2cgdGhhdCB3YXMgdmVyeSBjb29sIQ==
            Endpoint = 1.1.1.1:2408
        """.trimIndent()

        val config = ConfigParser.parse(rawConfig, "My WARP Config").getOrThrow()
        assertTrue(config.isWarp)
    }

    @Test
    fun testParse_endpointPortSanitization_defaultsTo51820ForStandardAnd854ForWarp() {
        // Standard WG without port -> 51820
        val stdConfig = """
            [Interface]
            PrivateKey = dGhlIHF1aWNrIGJyb3duIGZveCBqdW1wcyBvdmVy
            Address = 10.0.0.2/32

            [Peer]
            PublicKey = bGF6eSBkb2cgdGhhdCB3YXMgdmVyeSBjb29sIQ==
            Endpoint = 1.2.3.4
        """.trimIndent()

        val parsedStd = ConfigParser.parse(stdConfig).getOrThrow()
        assertEquals("1.2.3.4:51820", parsedStd.endpoint)

        // WARP config without port -> 854
        val warpConfig = """
            [Interface]
            PrivateKey = dGhlIHF1aWNrIGJyb3duIGZveCBqdW1wcyBvdmVy
            Address = 172.16.0.2/32

            [Peer]
            PublicKey = bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=
            Endpoint = 162.159.193.1
        """.trimIndent()

        val parsedWarp = ConfigParser.parse(warpConfig).getOrThrow()
        assertEquals("162.159.193.1:854", parsedWarp.endpoint)
    }

    @Test
    fun testParse_commentsAndAliasesAndCaseInsensitivity() {
        val rawConfig = """
            # Global comment line
            [interface] # Section comment
            private_key = my_private_key # Inline comment
            addresses = 10.0.0.2/32
            client_id = 1,2,3

            [PEER]
            public_key = my_public_key
            allowed_ips = 0.0.0.0/0
            endpoint = 5.6.7.8:51820
            persistent_keepalive = 30
        """.trimIndent()

        val config = ConfigParser.parse(rawConfig).getOrThrow()
        assertEquals("my_private_key", config.privateKey)
        assertEquals("10.0.0.2/32", config.address)
        assertEquals("my_public_key", config.peerPublicKey)
        assertEquals("0.0.0.0/0", config.allowedIps)
        assertEquals("5.6.7.8:51820", config.endpoint)
        assertEquals(30, config.persistentKeepalive)
        assertEquals("1,2,3", config.reserved)
    }

    @Test
    fun testParse_missingRequiredFields_returnsFailure() {
        // Missing PrivateKey
        val noPrivateKey = """
            [Interface]
            Address = 10.0.0.2/32

            [Peer]
            PublicKey = bGF6eSBkb2cgdGhhdCB3YXMgdmVyeSBjb29sIQ==
            Endpoint = 1.2.3.4:51820
        """.trimIndent()
        val res1 = ConfigParser.parse(noPrivateKey)
        assertTrue(res1.isFailure)
        assertTrue(res1.exceptionOrNull() is IllegalArgumentException)
        assertEquals("Missing PrivateKey in [Interface]", res1.exceptionOrNull()?.message)

        // Missing PublicKey
        val noPublicKey = """
            [Interface]
            PrivateKey = dGhlIHF1aWNrIGJyb3duIGZveCBqdW1wcyBvdmVy
            Address = 10.0.0.2/32

            [Peer]
            Endpoint = 1.2.3.4:51820
        """.trimIndent()
        val res2 = ConfigParser.parse(noPublicKey)
        assertTrue(res2.isFailure)
        assertTrue(res2.exceptionOrNull() is IllegalArgumentException)
        assertEquals("Missing PublicKey in [Peer]", res2.exceptionOrNull()?.message)

        // Missing Endpoint
        val noEndpoint = """
            [Interface]
            PrivateKey = dGhlIHF1aWNrIGJyb3duIGZveCBqdW1wcyBvdmVy
            Address = 10.0.0.2/32

            [Peer]
            PublicKey = bGF6eSBkb2cgdGhhdCB3YXMgdmVyeSBjb29sIQ==
        """.trimIndent()
        val res3 = ConfigParser.parse(noEndpoint)
        assertTrue(res3.isFailure)
        assertTrue(res3.exceptionOrNull() is IllegalArgumentException)
        assertEquals("Missing Endpoint in [Peer]", res3.exceptionOrNull()?.message)
    }

    @Test
    fun testParse_emptyOrMalformedInput_returnsFailure() {
        val emptyResult = ConfigParser.parse("")
        assertTrue(emptyResult.isFailure)

        val junkResult = ConfigParser.parse("This is not a valid wireguard config file at all!")
        assertTrue(junkResult.isFailure)
    }
}
