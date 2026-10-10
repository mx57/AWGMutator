package com.example.data.remote

import com.example.domain.model.NetworkEgressResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Verifies outbound network reachability and internet exit (egress) through the active VPN / Root tunnel.
 * Queries Cloudflare trace, DNS resolvers, and IP probe services to determine the external IP,
 * country of egress, WARP activation status, and round-trip egress latency.
 */
class NetworkEgressVerifier(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4000, TimeUnit.MILLISECONDS)
        .readTimeout(4000, TimeUnit.MILLISECONDS)
        .callTimeout(5000, TimeUnit.MILLISECONDS)
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
                            log("EGRESS_PROBE", "Cloudflare Trace Probe Success ($url) -> Public IP=$ip, Loc=$loc, WARP=$warp")
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
                    } else {
                        log("EGRESS_PROBE", "Cloudflare Trace Probe HTTP ${response.code} from $url")
                    }
                }
            } catch (e: Exception) {
                log("EGRESS_PROBE", "Cloudflare Trace Probe failed for $url: ${e.message}")
            }
        }
        return null
    }

    private fun tryIpify(): String? {
        val ipEndpoints = listOf(
            "https://api.ipify.org",
            "https://icanhazip.com",
            "https://checkip.amazonaws.com",
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

    internal suspend fun checkDnsResolution(
        servers: List<String> = listOf("1.1.1.1", "8.8.8.8", "77.88.8.8")
    ): Boolean = coroutineScope {
        val query = byteArrayOf(
            0x12, 0x34, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00,
            0x06, 0x67, 0x6f, 0x6f, 0x67, 0x6c, 0x65,
            0x03, 0x63, 0x6f, 0x6d,
            0x00, 0x00, 0x01, 0x00, 0x01
        )
        val channel = Channel<Boolean>(servers.size)
        val activeSockets = ConcurrentHashMap.newKeySet<DatagramSocket>()
        val jobs = servers.map { server ->
            launch(Dispatchers.IO) {
                val ok = probeSingleDnsServer(server, query, activeSockets)
                channel.send(ok)
            }
        }
        var completed = 0
        var success = false
        while (completed < servers.size) {
            val res = channel.receive()
            completed++
            if (res) {
                success = true
                jobs.forEach { it.cancel() }
                activeSockets.forEach { runCatching { it.close() } }
                break
            }
        }
        activeSockets.forEach { runCatching { it.close() } }
        success
    }

    private fun probeSingleDnsServer(
        serverSpec: String,
        query: ByteArray,
        activeSockets: MutableSet<DatagramSocket>
    ): Boolean {
        var socket: DatagramSocket? = null
        return try {
            val host: String
            val port: Int
            if (serverSpec.contains(":")) {
                val parts = serverSpec.split(":")
                host = parts[0]
                port = parts[1].toIntOrNull() ?: 53
            } else {
                host = serverSpec
                port = 53
            }
            val ds = DatagramSocket()
            socket = ds
            activeSockets.add(ds)
            ds.soTimeout = 2000
            val pkt = DatagramPacket(query, query.size, InetAddress.getByName(host), port)
            ds.send(pkt)
            val respBuf = ByteArray(512)
            val respPkt = DatagramPacket(respBuf, respBuf.size)
            ds.receive(respPkt)
            val ok = respPkt.length > 12
            if (ok) {
                log("DNS_PROBE", "UDP $serverSpec DNS probe -> Received ${respPkt.length}B (Functional=true)")
                true
            } else false
        } catch (e: Exception) {
            log("DNS_PROBE", "UDP $serverSpec DNS probe failed: ${e.message}")
            false
        } finally {
            socket?.let {
                activeSockets.remove(it)
                runCatching { if (!it.isClosed) it.close() }
            }
        }
    }

    private fun log(tag: String, message: String) {
        runCatching {
            com.example.App.instance.tunnelManager.log(tag, message)
        }
    }
}
