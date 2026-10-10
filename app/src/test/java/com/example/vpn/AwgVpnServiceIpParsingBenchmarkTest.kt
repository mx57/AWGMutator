package com.example.vpn

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress
import kotlin.system.measureNanoTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AwgVpnServiceIpParsingBenchmarkTest {

    @Test
    fun benchmarkIpAddressParsing() {
        val ipv4Addresses = listOf("10.2.0.2", "192.168.1.1", "172.16.0.1", "1.1.1.1", "8.8.8.8")
        val ipv6Addresses = listOf("fd00:1:1::2", "2606:4700:110:893c::1", "::1", "fe80::1", "2001:db8::1")
        val hostnames = listOf("example.com", "invalid.domain.local", "dns.google", "nonexistent.test.org")

        // Warmup
        for (i in 0 until 100) {
            for (ip in ipv4Addresses + ipv6Addresses) {
                AwgVpnService.parseNumericAddressOrNull(ip)
            }
            for (host in hostnames) {
                AwgVpnService.parseNumericAddressOrNull(host)
            }
        }

        val iterations = 1000
        val totalNs = measureNanoTime {
            for (i in 0 until iterations) {
                for (ip in ipv4Addresses) {
                    val res = AwgVpnService.parseNumericAddressOrNull(ip)
                    assertNotNull(res)
                }
                for (ip in ipv6Addresses) {
                    val res = AwgVpnService.parseNumericAddressOrNull(ip)
                    assertNotNull(res)
                }
                for (host in hostnames) {
                    val res = AwgVpnService.parseNumericAddressOrNull(host)
                    assertNull(res)
                }
            }
        }

        val totalOps = iterations * (ipv4Addresses.size + ipv6Addresses.size + hostnames.size)
        val avgUs = (totalNs.toDouble() / totalOps) / 1_000.0
        println("Benchmark AwgVpnService.parseNumericAddressOrNull over $totalOps ops: average $avgUs us per IP parse operation")
    }

    @Test
    fun testParseNumericAddressOrNull_correctlyParsesValidLiterals() {
        val v4Parsed = AwgVpnService.parseNumericAddressOrNull("10.2.0.2")
        assertNotNull(v4Parsed)
        assertTrue(v4Parsed!!.address.contentEquals(InetAddress.getByName("10.2.0.2").address))

        val v6Parsed = AwgVpnService.parseNumericAddressOrNull("fd00:1:1::2")
        assertNotNull(v6Parsed)
        assertTrue(v6Parsed!!.address.contentEquals(InetAddress.getByName("fd00:1:1::2").address))
    }

    @Test
    fun testParseNumericAddressOrNull_rejectsHostnamesAndInvalidStrings() {
        assertNull(AwgVpnService.parseNumericAddressOrNull("example.com"))
        assertNull(AwgVpnService.parseNumericAddressOrNull("invalid..ip"))
        assertNull(AwgVpnService.parseNumericAddressOrNull("256.256.256.256"))
        assertNull(AwgVpnService.parseNumericAddressOrNull(""))
        assertNull(AwgVpnService.parseNumericAddressOrNull("   "))
    }
}
