package com.example.data.remote

import androidx.test.core.app.ApplicationProvider
import com.example.App
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NetworkEgressVerifierTest {

    private lateinit var app: App

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext<App>()
    }

    private fun createMockClient(
        handler: (requestUrl: String) -> Response?
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val request = chain.request()
                val url = request.url.toString()
                handler(url) ?: throw IOException("Simulated network failure for $url")
            })
            .build()
    }

    private fun createMockResponse(
        request: okhttp3.Request,
        code: Int,
        body: String
    ): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code in 200..299) "OK" else "Error")
            .body(body.toResponseBody("text/plain".toMediaType()))
            .build()
    }

    @Test
    fun testProbeUrl_success() {
        val client = createMockClient { url ->
            if (url == "https://example.com/ok") {
                val request = okhttp3.Request.Builder().url(url).build()
                createMockResponse(request, 200, "OK")
            } else null
        }
        val verifier = NetworkEgressVerifier(client)
        assertTrue(verifier.probeUrl("https://example.com/ok"))
    }

    @Test
    fun testProbeUrl_failure() {
        val client = createMockClient { url ->
            if (url == "https://example.com/fail") {
                val request = okhttp3.Request.Builder().url(url).build()
                createMockResponse(request, 500, "Internal Server Error")
            } else null
        }
        val verifier = NetworkEgressVerifier(client)
        assertFalse(verifier.probeUrl("https://example.com/fail"))
    }

    @Test
    fun testProbeUrl_exceptionHandled() {
        val client = createMockClient { null }
        val verifier = NetworkEgressVerifier(client)
        assertFalse(verifier.probeUrl("https://example.com/network-error"))
    }

    @Test
    fun testVerifyEgress_cloudflareTraceSuccess() = runBlocking {
        val tracePayload = """
            fl=123f12
            h=1.1.1.1
            ip=203.0.113.195
            ts=1700000000
            visit_scheme=https
            uag=AWGMutator-EgressProbe/1.0
            colo=HEL
            sliver=none
            loc=FI
            warp=on
            gateway=off
            r組織=Cloudflare
        """.trimIndent()

        val client = createMockClient { url ->
            if (url.contains("/cdn-cgi/trace")) {
                val request = okhttp3.Request.Builder().url(url).build()
                createMockResponse(request, 200, tracePayload)
            } else null
        }

        val verifier = NetworkEgressVerifier(client)
        val result = verifier.verifyEgress()

        assertTrue(result.isFunctional)
        assertEquals("203.0.113.195", result.publicIp)
        assertEquals("FI", result.countryCode)
        assertEquals("on", result.warpStatus)
        assertTrue(result.isWarpActive)
        assertNotNull(result.latencyMs)
        assertTrue((result.latencyMs ?: 0L) >= 1L)
    }

    @Test
    fun testVerifyEgress_ipifyFallbackSuccess() = runBlocking {
        val client = createMockClient { url ->
            if (url.contains("api.ipify.org")) {
                val request = okhttp3.Request.Builder().url(url).build()
                createMockResponse(request, 200, "198.51.100.42\n")
            } else null
        }

        val verifier = NetworkEgressVerifier(client)
        val result = verifier.verifyEgress()

        assertTrue(result.isFunctional)
        assertEquals("198.51.100.42", result.publicIp)
        assertEquals("Global", result.countryCode)
        assertFalse(result.isWarpActive)
        assertNotNull(result.latencyMs)
        assertTrue((result.latencyMs ?: 0L) >= 1L)
    }

    @Test
    fun testVerifyEgress_dnsFallbackOrFailure() = runBlocking {
        val client = createMockClient { null }

        val verifier = NetworkEgressVerifier(client)
        val result = verifier.verifyEgress()

        if (result.isFunctional) {
            // DNS resolution succeeded in test environment
            assertEquals("DNS Exit Active", result.publicIp)
            assertEquals("OK", result.countryCode)
            assertTrue(result.dnsReachable)
        } else {
            // DNS resolution failed in test environment
            assertFalse(result.isFunctional)
            assertNotNull(result.errorMessage)
            assertTrue(result.errorMessage!!.contains("No internet egress detected"))
        }
    }
}
