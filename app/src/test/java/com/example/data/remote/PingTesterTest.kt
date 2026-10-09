package com.example.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureNanoTime

class PingTesterTest {

    private val pingTester = PingTester()

    @Test
    fun testIsValidHost_validIPv4() {
        assertTrue(pingTester.isValidHost("1.1.1.1"))
        assertTrue(pingTester.isValidHost("8.8.8.8"))
        assertTrue(pingTester.isValidHost("127.0.0.1"))
        assertTrue(pingTester.isValidHost("255.255.255.255"))
        assertTrue(pingTester.isValidHost("0.0.0.0"))
    }

    @Test
    fun testIsValidHost_validIPv6() {
        assertTrue(pingTester.isValidHost("::1"))
        assertTrue(pingTester.isValidHost("::"))
        assertTrue(pingTester.isValidHost("2001:db8::1"))
        assertTrue(pingTester.isValidHost("2606:4700:d0::a29f:c001"))
        assertTrue(pingTester.isValidHost("fe80::1"))
        assertTrue(pingTester.isValidHost("::ffff:192.0.2.1"))
    }

    @Test
    fun testIsValidHost_validHostnames() {
        assertTrue(pingTester.isValidHost("google.com"))
        assertTrue(pingTester.isValidHost("sub.domain-name.co.uk"))
        assertTrue(pingTester.isValidHost("localhost"))
        assertTrue(pingTester.isValidHost("host-1"))
    }

    @Test
    fun testIsValidHost_rejectsCommandInjectionAndInvalidHosts() {
        assertFalse("Command injection with semicolon should be rejected", pingTester.isValidHost("1.1.1.1; id"))
        assertFalse("Command injection with pipe should be rejected", pingTester.isValidHost("1.1.1.1|whoami"))
        assertFalse("Command injection with ampersand should be rejected", pingTester.isValidHost("1.1.1.1&id"))
        assertFalse("Command injection with newline should be rejected", pingTester.isValidHost("1.1.1.1\ncat /etc/passwd"))
        assertFalse("Null byte injection should be rejected", pingTester.isValidHost("1.1.1.1\u0000"))
        assertFalse("Subshell execution should be rejected", pingTester.isValidHost("1.1.1.1$(id)"))
        assertFalse("Command substitution should be rejected", pingTester.isValidHost("1.1.1.1`id`"))
        assertFalse("Leading whitespace should be rejected", pingTester.isValidHost(" -c 1"))
        assertFalse("Option flag starting with hyphen should be rejected", pingTester.isValidHost("-c"))
        assertFalse("Path starting with slash should be rejected", pingTester.isValidHost("/bin/sh"))
        assertFalse("IPv4 with leading zero octet should be rejected", pingTester.isValidHost("01.1.1.1"))
        assertFalse("IPv4 out-of-range octet should be rejected", pingTester.isValidHost("256.1.1.1"))
        assertFalse("Non-ASCII Cyrillic hostname should be rejected", pingTester.isValidHost("а.com"))
        assertFalse("Single colon should be rejected", pingTester.isValidHost(":"))
        assertFalse("Leading colon without double colon should be rejected", pingTester.isValidHost(":1"))
        assertFalse("Trailing colon without double colon should be rejected", pingTester.isValidHost("1:"))
        assertFalse("Hostname starting with hyphen should be rejected", pingTester.isValidHost("-host.com"))
        assertFalse("Hostname label ending with hyphen should be rejected", pingTester.isValidHost("host-.com"))
        assertFalse("Empty label in hostname should be rejected", pingTester.isValidHost("host..com"))
    }

    private val samplePingOutput1 = """
        PING 1.1.1.1 (1.1.1.1) 56(84) bytes of data.
        64 bytes from 1.1.1.1: icmp_seq=1 ttl=57 time=24.5 ms

        --- 1.1.1.1 ping statistics ---
        1 packets transmitted, 1 received, 0% packet loss, time 0ms
        rtt min/avg/max/mdev = 24.512/24.512/24.512/0.000 ms
    """.trimIndent()

    private val samplePingOutput2 = """
        PING 8.8.8.8 (8.8.8.8) 56(84) bytes of data.
        64 bytes from 8.8.8.8: icmp_seq=1 ttl=117

        --- 8.8.8.8 ping statistics ---
        1 packets transmitted, 1 received, 0% packet loss, time 0ms
        rtt min/avg/max/mdev = 15.100/18.354/22.100/2.500 ms
    """.trimIndent()

    @Test
    fun testPingParsingRegex() {
        // Test format 1: time=XX.X ms
        val timeMatch1 = Regex("time=([0-9.]+)\\s*ms").find(samplePingOutput1)
        val ms1 = timeMatch1?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.toLong()
        assertEquals(24L, ms1)

        // Test format 2: rtt min/avg/max/mdev = min/avg/max/mdev
        val timeMatch2 = Regex("time=([0-9.]+)\\s*ms").find(samplePingOutput2)
        assertEquals(null, timeMatch2)

        val rttMatch2 = Regex("rtt min/avg/max/mdev = [0-9.]+/([0-9.]+)/").find(samplePingOutput2)
        val ms2 = rttMatch2?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.toLong()
        assertEquals(18L, ms2)
    }

    @Test
    fun testRegexPerformanceBenchmark() {
        val iterations = 100_000

        // Warm up
        for (i in 0..1000) {
            Regex("time=([0-9.]+)\\s*ms").find(samplePingOutput1)
        }

        // Measure unoptimized (instantiating Regex inside loop)
        val unoptimizedTime = measureNanoTime {
            for (i in 0 until iterations) {
                val timeMatch = Regex("time=([0-9.]+)\\s*ms").find(samplePingOutput1)
                if (timeMatch != null) {
                    timeMatch.groupValues[1].toDoubleOrNull()
                } else {
                    val rttMatch = Regex("rtt min/avg/max/mdev = [0-9.]+/([0-9.]+)/").find(samplePingOutput1)
                    rttMatch?.groupValues?.getOrNull(1)?.toDoubleOrNull()
                }
            }
        }

        // Precompiled regexes
        val timeRegex = Regex("time=([0-9.]+)\\s*ms")
        val rttRegex = Regex("rtt min/avg/max/mdev = [0-9.]+/([0-9.]+)/")

        // Warm up
        for (i in 0..1000) {
            timeRegex.find(samplePingOutput1)
        }

        // Measure optimized (using precompiled Regex)
        val optimizedTime = measureNanoTime {
            for (i in 0 until iterations) {
                val timeMatch = timeRegex.find(samplePingOutput1)
                if (timeMatch != null) {
                    timeMatch.groupValues[1].toDoubleOrNull()
                } else {
                    val rttMatch = rttRegex.find(samplePingOutput1)
                    rttMatch?.groupValues?.getOrNull(1)?.toDoubleOrNull()
                }
            }
        }

        println("Unoptimized time for $iterations iterations: ${unoptimizedTime / 1_000_000.0} ms")
        println("Optimized time for $iterations iterations: ${optimizedTime / 1_000_000.0} ms")
        println("Speedup: ${unoptimizedTime.toDouble() / optimizedTime.toDouble()}x")
    }
}
