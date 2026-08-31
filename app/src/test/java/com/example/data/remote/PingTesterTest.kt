package com.example.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.system.measureNanoTime

class PingTesterTest {

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
