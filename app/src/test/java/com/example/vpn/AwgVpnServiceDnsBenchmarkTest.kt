package com.example.vpn

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import kotlin.system.measureTimeMillis

class AwgVpnServiceDnsBenchmarkTest {

    @Test
    fun testDnsResolution_sequentialVsConcurrentDispatchersIO() {
        val dnsList = listOf(
            "one.one.one.one",
            "dns.google",
            "dns.adguard.com",
            "dns.quad9.net",
            "cloudflare-dns.com",
            "1.1.1.1",
            "8.8.8.8"
        )

        // 1. Sequential resolution (legacy approach)
        val sequentialResults = mutableListOf<Pair<String, InetAddress>>()
        val sequentialTime = measureTimeMillis {
            for (dns in dnsList) {
                runCatching {
                    sequentialResults.add(dns to InetAddress.getByName(dns))
                }
            }
        }

        // 2. Concurrent resolution on Dispatchers.IO (optimized approach)
        val concurrentResults: List<Pair<String, InetAddress>>
        val concurrentTime = measureTimeMillis {
            concurrentResults = runBlocking(Dispatchers.IO) {
                dnsList.map { dns ->
                    async {
                        runCatching {
                            dns to InetAddress.getByName(dns)
                        }.getOrNull()
                    }
                }.awaitAll().filterNotNull()
            }
        }

        println("Sequential DNS resolution time (hostnames & IPs): ${sequentialTime}ms")
        println("Concurrent Dispatchers.IO DNS resolution time (hostnames & IPs): ${concurrentTime}ms")

        assertEquals("Resolved list count should match", sequentialResults.size, concurrentResults.size)
        for (i in sequentialResults.indices) {
            assertEquals("Order should be preserved for element $i", sequentialResults[i].first, concurrentResults[i].first)
            assertEquals("InetAddress should match for element $i", sequentialResults[i].second, concurrentResults[i].second)
        }
    }
}
