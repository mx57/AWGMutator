package com.example.data.remote

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.system.measureTimeMillis

@RunWith(RobolectricTestRunner::class)
class NetworkEgressVerifierTest {

    @Test
    fun testVerifyEgress_withCloudflareTraceSuccess() = runBlocking {
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val url = chain.request().url.toString()
                if (url.contains("cdn-cgi/trace")) {
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("ip=203.0.113.1\nloc=US\nwarp=on\n".toResponseBody("text/plain".toMediaType()))
                        .build()
                } else {
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(500)
                        .message("Error")
                        .body("".toResponseBody("text/plain".toMediaType()))
                        .build()
                }
            })
            .build()

        val verifier = NetworkEgressVerifier(client)
        val result = verifier.verifyEgress()

        assertTrue(result.isFunctional)
        assertEquals("203.0.113.1", result.publicIp)
        assertEquals("US", result.countryCode)
        assertTrue(result.isWarpActive)
    }

    @Test
    fun testVerifyEgress_withFallbackToIpify() = runBlocking {
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val url = chain.request().url.toString()
                if (url.contains("cdn-cgi/trace")) {
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(500)
                        .message("Server Error")
                        .body("".toResponseBody("text/plain".toMediaType()))
                        .build()
                } else if (url.contains("ipify.org")) {
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("198.51.100.42".toResponseBody("text/plain".toMediaType()))
                        .build()
                } else {
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(500)
                        .message("Error")
                        .body("".toResponseBody("text/plain".toMediaType()))
                        .build()
                }
            })
            .build()

        val verifier = NetworkEgressVerifier(client)
        val result = verifier.verifyEgress()

        assertTrue(result.isFunctional)
        assertEquals("198.51.100.42", result.publicIp)
    }

    @Test
    fun testCloudflareTrace_simulatedParallelLatencyBenchmark() = runBlocking {
        val simulatedDelayMs = 100L

        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val url = chain.request().url.toString()
                Thread.sleep(simulatedDelayMs)
                if (url.contains("1.1.1.1")) {
                    // First endpoint fails
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(500)
                        .message("Error")
                        .body("".toResponseBody("text/plain".toMediaType()))
                        .build()
                } else {
                    // Second endpoint succeeds
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("ip=203.0.113.1\nloc=DE\nwarp=off\n".toResponseBody("text/plain".toMediaType()))
                        .build()
                }
            })
            .build()

        val verifier = NetworkEgressVerifier(client)
        val elapsedTime = measureTimeMillis {
            val result = verifier.verifyEgress()
            assertTrue(result.isFunctional)
            assertEquals("203.0.113.1", result.publicIp)
        }

        println("Egress verification took: ${elapsedTime} ms (with $simulatedDelayMs ms per-request latency)")
    }
}
