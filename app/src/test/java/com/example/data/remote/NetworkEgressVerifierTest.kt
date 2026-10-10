package com.example.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NetworkEgressVerifierTest {

    @Test
    fun testProbeUrl_success() {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("OK".toResponseBody("text/plain".toMediaType()))
                    .build()
            }
            .build()

        val verifier = NetworkEgressVerifier(client)
        assertTrue(verifier.probeUrl("https://example.com"))
    }

    @Test
    fun testProbeUrl_failure_throwsException() {
        val client = OkHttpClient.Builder()
            .addInterceptor {
                throw IOException("Network error")
            }
            .build()

        val verifier = NetworkEgressVerifier(client)
        assertFalse(verifier.probeUrl("https://example.com"))
    }

    @Test
    fun testVerifyEgress_cloudflareTraceSuccess() = runBlocking {
        val traceBody = """
            fl=12f34
            h=1.1.1.1
            ip=203.0.113.195
            ts=1700000000.000
            visit_scheme=https
            uag=AWGMutator-EgressProbe/1.0
            colo=HKG
            sliver=none
            loc=HK
            warp=on
            gateway=off
            r4=true
            r6=false
        """.trimIndent()

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(traceBody.toResponseBody("text/plain".toMediaType()))
                    .build()
            }
            .build()

        val verifier = NetworkEgressVerifier(client)
        val result = verifier.verifyEgress()

        assertTrue(result.isFunctional)
        assertEquals("203.0.113.195", result.publicIp)
        assertEquals("HK", result.countryCode)
        assertTrue(result.isWarpActive)
    }

    @Test
    fun testVerifyEgress_ipifyFallbackWhenCloudflareFails() = runBlocking {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                if (url.contains("cdn-cgi/trace")) {
                    throw IOException("Cloudflare blocked")
                } else if (url.contains("ipify.org")) {
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("198.51.100.42\n".toResponseBody("text/plain".toMediaType()))
                        .build()
                } else {
                    throw IOException("Other endpoint failed")
                }
            }
            .build()

        val verifier = NetworkEgressVerifier(client)
        val result = verifier.verifyEgress()

        assertTrue(result.isFunctional)
        assertEquals("198.51.100.42", result.publicIp)
        assertEquals("Global", result.countryCode)
        assertFalse(result.isWarpActive)
    }

    @Test
    fun testVerifyEgress_exceptionLoggingOnIpifyFailure() = runBlocking {
        val client = OkHttpClient.Builder()
            .addInterceptor {
                throw IOException("Simulated network down")
            }
            .build()

        val verifier = NetworkEgressVerifier(client)
        val result = verifier.verifyEgress()

        assertNotNull(result)
    }
}
