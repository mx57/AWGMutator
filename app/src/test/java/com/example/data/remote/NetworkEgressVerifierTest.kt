package com.example.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

class NetworkEgressVerifierTest {

    private lateinit var server1: MockWebServer
    private lateinit var server2: MockWebServer
    private lateinit var server3: MockWebServer
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var verifier: NetworkEgressVerifier

    @Before
    fun setUp() {
        server1 = MockWebServer()
        server2 = MockWebServer()
        server3 = MockWebServer()

        server1.start()
        server2.start()
        server3.start()

        okHttpClient = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()

        verifier = NetworkEgressVerifier(okHttpClient)
    }

    @After
    fun tearDown() {
        server1.shutdown()
        server2.shutdown()
        server3.shutdown()
    }

    @Test
    fun benchmarkSequentialVsConcurrentIpify() = runBlocking {
        // Server 1 delays 300ms and fails (500)
        server1.enqueue(MockResponse().setResponseCode(500).setBodyDelay(300, TimeUnit.MILLISECONDS))
        // Server 2 delays 300ms and fails (500)
        server2.enqueue(MockResponse().setResponseCode(500).setBodyDelay(300, TimeUnit.MILLISECONDS))
        // Server 3 delays 100ms and succeeds (200 OK)
        server3.enqueue(MockResponse().setResponseCode(200).setBody("203.0.113.195").setBodyDelay(100, TimeUnit.MILLISECONDS))

        val endpoints = listOf(
            server1.url("/").toString(),
            server2.url("/").toString(),
            server3.url("/").toString()
        )

        // Measure sequential execution
        val sequentialTime = measureTimeMillis {
            var result: String? = null
            for (url in endpoints) {
                try {
                    val request = Request.Builder().url(url).build()
                    okHttpClient.newCall(request).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val ip = resp.body?.string()?.trim()
                            if (!ip.isNullOrBlank()) {
                                result = ip
                                break
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
            assertEquals("203.0.113.195", result)
        }

        // Setup mock responses again for concurrent test
        server1.enqueue(MockResponse().setResponseCode(500).setBodyDelay(300, TimeUnit.MILLISECONDS))
        server2.enqueue(MockResponse().setResponseCode(500).setBodyDelay(300, TimeUnit.MILLISECONDS))
        server3.enqueue(MockResponse().setResponseCode(200).setBody("203.0.113.195").setBodyDelay(100, TimeUnit.MILLISECONDS))

        // Measure concurrent execution using NetworkEgressVerifier
        val concurrentTime = measureTimeMillis {
            val result = verifier.tryIpify(endpoints)
            assertEquals("203.0.113.195", result)
        }

        println("Sequential fallback IP check time: $sequentialTime ms")
        println("Concurrent fallback IP check time: $concurrentTime ms")
        println("Latency improvement: ${sequentialTime - concurrentTime} ms")
    }

    @Test
    fun testTryCloudflareTraceConcurrent() = runBlocking {
        val traceBody = """
            fl=12f34
            h=1.1.1.1
            ip=198.51.100.42
            ts=1700000000
            visit_scheme=https
            uag=AWGMutator-EgressProbe/1.0
            colo=HEL
            sliver=none
            loc=FI
            warp=plus
            gateway=off
            r2=off
            kex=X25519
        """.trimIndent()

        server1.enqueue(MockResponse().setResponseCode(500).setBodyDelay(200, TimeUnit.MILLISECONDS))
        server2.enqueue(MockResponse().setResponseCode(200).setBody(traceBody).setBodyDelay(50, TimeUnit.MILLISECONDS))

        val endpoints = listOf(
            server1.url("/trace").toString(),
            server2.url("/trace").toString()
        )

        val result = verifier.tryCloudflareTrace(endpoints)
        assertNotNull(result)
        assertEquals("198.51.100.42", result?.publicIp)
        assertEquals("FI", result?.countryCode)
        assertEquals(true, result?.isWarpActive)
    }
}
