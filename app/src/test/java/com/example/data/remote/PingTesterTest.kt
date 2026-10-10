package com.example.data.remote

import com.example.domain.model.BlockedService
import com.example.domain.model.DnsServer
import com.example.domain.model.ServiceCategory
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
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
    fun testIsValidHost() {
        val pingTester = PingTester()

        // Valid hosts
        assertTrue(pingTester.isValidHost("1.1.1.1"))
        assertTrue(pingTester.isValidHost("192.168.1.100"))
        assertTrue(pingTester.isValidHost("2606:4700:4700::1111"))
        assertTrue(pingTester.isValidHost("::1"))
        assertTrue(pingTester.isValidHost("example.com"))
        assertTrue(pingTester.isValidHost("sub.domain.co.uk"))

        // Invalid hosts
        assertFalse(pingTester.isValidHost(""))
        assertFalse(pingTester.isValidHost("   "))
        assertFalse(pingTester.isValidHost("-invalid.com"))
        assertFalse(pingTester.isValidHost("invalid..com"))
        assertFalse(pingTester.isValidHost("label_with_underscore.com"))
        assertFalse(pingTester.isValidHost(":::1"))
        assertFalse(pingTester.isValidHost("2001:db8::8a2e::7334"))
        assertFalse(pingTester.isValidHost("a".repeat(254)))
    }

    @Test
    fun testParseEndpointHostAndPort() {
        val pingTester = PingTester()

        // Empty or blank
        assertEquals(Pair("", 854), pingTester.parseEndpointHostAndPort(""))
        assertEquals(Pair("", 854), pingTester.parseEndpointHostAndPort("   "))

        // IPv4 with and without port
        assertEquals(Pair("1.1.1.1", 2408), pingTester.parseEndpointHostAndPort("1.1.1.1:2408"))
        assertEquals(Pair("1.1.1.1", 854), pingTester.parseEndpointHostAndPort("1.1.1.1"))
        assertEquals(Pair("1.1.1.1", 854), pingTester.parseEndpointHostAndPort("1.1.1.1:not_a_number"))

        // IPv6 with brackets
        assertEquals(Pair("2606:4700::1", 2408), pingTester.parseEndpointHostAndPort("[2606:4700::1]:2408"))
        assertEquals(Pair("2606:4700::1", 854), pingTester.parseEndpointHostAndPort("[2606:4700::1]"))

        // IPv6 without brackets
        assertEquals(Pair("2606:4700::1", 854), pingTester.parseEndpointHostAndPort("2606:4700::1"))
    }

    @Test
    fun testTestEndpoint_withInvalidHost_returnsError() = runBlocking {
        val pingTester = PingTester()

        val result1 = pingTester.testEndpoint("")
        assertFalse(result1.isReachable)
        assertNull(result1.latencyMs)
        assertEquals("Некорректный хост эндпоинта", result1.error)

        val result2 = pingTester.testEndpoint("-invalid-host:1234")
        assertFalse(result2.isReachable)
        assertNull(result2.latencyMs)
        assertEquals("Некорректный хост эндпоинта", result2.error)
    }

    @Test
    fun testTestEndpoint_withUnreachableHost_returnsFailure() = runBlocking {
        val pingTester = PingTester()

        // Test with non-routable IPv4 address
        val result = pingTester.testEndpoint("10.255.255.1:1234")
        assertFalse(result.isReachable)
        assertNull(result.latencyMs)
        assertNotNull(result.error)
    }

    @Test
    fun testEvaluateTargets_whenEmptyTargets_returnsZeroFitnessAndFallbackMetrics() = runBlocking {
        val pingTester = PingTester()

        val result = pingTester.evaluateTargets("genome-1", targets = emptyList())

        assertEquals("genome-1", result.genomeId)
        assertEquals(9999L, result.avgPingMs)
        assertEquals(9999L, result.minPingMs)
        assertEquals(9999L, result.maxPingMs)
        assertEquals(0.0, result.successRate, 0.0001)
        assertEquals(0.0, result.fitnessScore, 0.0001)
        assertNotNull(result.errorMessage)
    }

    @Test
    fun testProbeService_whenHttpClientThrowsException_fallsBackAndCatchesError() = runBlocking {
        val failingClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { throw IOException("Connection refused by test mock") })
            .build()

        val pingTester = PingTester(failingClient)

        val mockService = BlockedService(
            id = "test_service",
            name = "Test Service",
            iconEmoji = "🧪",
            testUrl = "https://10.255.255.1:9999/",
            fallbackHost = "10.255.255.1",
            fallbackPort = 9999,
            category = ServiceCategory.SOCIAL_NETWORK
        )

        val result = pingTester.probeService(mockService)

        assertFalse(result.isAccessible)
        assertNull(result.latencyMs)
        assertFalse(result.isDpiThrottled)
        assertEquals("Блокировка ТСПУ / DPI Filtered", result.error)
    }

    @Test
    fun testEvaluateBlockedServices_whenAllFail_returnsDpiBlockedResults() = runBlocking {
        val failingClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { throw IOException("DPI drop simulated") })
            .build()

        val pingTester = PingTester(failingClient)

        val mockServices = listOf(
            BlockedService(
                id = "s1",
                name = "Service 1",
                iconEmoji = "⚡",
                testUrl = "https://10.255.255.1:9999/",
                fallbackHost = "10.255.255.1",
                fallbackPort = 9999,
                category = ServiceCategory.VIDEO_STREAMING
            )
        )

        val results = pingTester.evaluateBlockedServices(mockServices)

        assertEquals(1, results.size)
        assertFalse(results[0].isAccessible)
        assertNull(results[0].latencyMs)
        assertTrue(results[0].isDpiThrottled)
        assertEquals("DPI Blocked / Timed Out", results[0].error)
    }

    @Test
    fun testEvaluateAllDnsServers_whenUdpAndTcpFail_returnsInaccessible() = runBlocking {
        val pingTester = PingTester()

        val mockServers = listOf(
            DnsServer("d1", "Unreachable DNS", "10.255.255.1", "10.255.255.2", "Test")
        )

        val results = pingTester.evaluateAllDnsServers(mockServers)

        assertEquals(1, results.size)
        assertFalse(results[0].isAccessible)
        assertNull(results[0].latencyMs)
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
