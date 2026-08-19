package com.example.data.remote

import com.example.domain.model.NetworkEgressResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * Verifies outbound network reachability and internet exit (egress) through the active VPN / Root tunnel.
 * Queries Cloudflare trace, DNS resolvers, and IP probe services to determine the external IP,
 * country of egress, WARP activation status, and round-trip egress latency.
 */
class NetworkEgressVerifier(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(2500, TimeUnit.MILLISECONDS)
        .readTimeout(2500, TimeUnit.MILLISECONDS)
        .callTimeout(3500, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .build()
) {

    /**
     * Executes a comprehensive egress connectivity check.
     */
    suspend fun verifyEgress(): NetworkEgressResult = withContext(Dispatchers.IO) {
        val start = System.nanoTime()

        // 1. Try Cloudflare Trace first (provides IP, Location, WARP flag in single fast payload)
        val traceResult = tryCloudflareTrace()
        if (traceResult != null) {
            val latency = (System.nanoTime() - start) / 1_000_000
            return@withContext traceResult.copy(
                latencyMs = latency.coerceAtLeast(1L),
                dnsReachable = checkDnsResolution()
            )
        }

        // 2. Fallback to Ipify / Icanhazip for simple IP check
        val ipifyResult = tryIpify()
        if (ipifyResult != null) {
            val latency = (System.nanoTime() - start) / 1_000_000
            return@withContext NetworkEgressResult(
                isFunctional = true,
                publicIp = ipifyResult,
                countryCode = "Global",
                isWarpActive = false,
                dnsReachable = checkDnsResolution(),
                latencyMs = latency.coerceAtLeast(1L),
                testedAt = System.currentTimeMillis()
            )
        }

        // 3. Fallback to raw DNS check
        val dnsOk = checkDnsResolution()
        if (dnsOk) {
            val latency = (System.nanoTime() - start) / 1_000_000
            return@withContext NetworkEgressResult(
                isFunctional = true,
                publicIp = "DNS Exit Active",
                countryCode = "OK",
                dnsReachable = true,
                latencyMs = latency.coerceAtLeast(1L),
                testedAt = System.currentTimeMillis()
            )
        }

        NetworkEgressResult(
            isFunctional = false,
            errorMessage = "No internet egress detected. Tunnel may be throttled or blocked."
        )
    }

    fun probeUrl(url: String): Boolean {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:120.0) Gecko/120.0 Firefox/120.0")
                .build()
            client.newCall(request).execute().use { response ->
                response.isSuccessful || response.code in 200..399
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun tryCloudflareTrace(): NetworkEgressResult? {
        val endpoints = listOf(
            "https://1.1.1.1/cdn-cgi/trace",
            "https://www.cloudflare.com/cdn-cgi/trace",
            "https://cloudflare-dns.com/cdn-cgi/trace"
        )

        for (url in endpoints) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "AWGMutator-EgressProbe/1.0")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        val lines = body.lines().associate { line ->
                            val parts = line.split("=", limit = 2)
                            if (parts.size == 2) parts[0].trim() to parts[1].trim() else "" to ""
                        }

                        val ip = lines["ip"]
                        val loc = lines["loc"]
                        val warp = lines["warp"]

                        if (!ip.isNullOrBlank()) {
                            return NetworkEgressResult(
                                isFunctional = true,
                                publicIp = ip,
                                countryCode = loc ?: "CF",
                                cityOrIsp = "Cloudflare Edge ($loc)",
                                warpStatus = warp,
                                isWarpActive = warp == "on" || warp == "plus",
                                testedAt = System.currentTimeMillis()
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                // Try next endpoint
            }
        }
        return null
    }

    private fun tryIpify(): String? {
        val ipEndpoints = listOf(
            "https://api.ipify.org",
            "https://icanhazip.com",
            "https://ifconfig.me/ip"
        )
        for (url in ipEndpoints) {
            try {
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val ip = resp.body?.string()?.trim()
                        if (!ip.isNullOrBlank() && (ip.contains(".") || ip.contains(":"))) {
                            return ip
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return null
    }

    private fun checkDnsResolution(): Boolean {
        return try {
            val query = byteArrayOf(
                0x12, 0x34, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00,
                0x06, 0x67, 0x6f, 0x6f, 0x67, 0x6c, 0x65,
                0x03, 0x63, 0x6f, 0x6d,
                0x00, 0x00, 0x01, 0x00, 0x01
            )
            DatagramSocket().use { ds ->
                ds.soTimeout = 1500
                val pkt = DatagramPacket(query, query.size, InetAddress.getByName("1.1.1.1"), 53)
                ds.send(pkt)
                val respBuf = ByteArray(512)
                val respPkt = DatagramPacket(respBuf, respBuf.size)
                ds.receive(respPkt)
                respPkt.length > 12
            }
        } catch (_: Exception) {
            false
        }
    }
}
