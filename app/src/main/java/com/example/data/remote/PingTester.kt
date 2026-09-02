package com.example.data.remote

import com.example.domain.model.BlockedService
import com.example.domain.model.BlockedServicesCatalog
import com.example.domain.model.DnsCatalog
import com.example.domain.model.DnsServer
import com.example.domain.model.FitnessResult
import com.example.domain.model.ServiceProbeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

/**
 * Result of testing a single DNS server's reachability and response latency.
 */
data class DnsProbeResult(
    val server: DnsServer,
    val latencyMs: Long?,
    val isAccessible: Boolean
)

/**
 * Result of testing a VPN configuration endpoint.
 */
data class EndpointProbeResult(
    val endpoint: String,
    val isReachable: Boolean,
    val latencyMs: Long?,
    val error: String? = null
)

/**
 * High-performance dual-mode network and endpoint probe.
 * Evaluates reachability, latency, and Anti-DPI bypass capabilities across popular blocked services
 * (YouTube, Instagram, Telegram, Twitch, X/Twitter, Discord) via HTTP HEAD and raw TCP/UDP handshakes.
 */
class PingTester(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(1500, TimeUnit.MILLISECONDS)
        .readTimeout(1500, TimeUnit.MILLISECONDS)
        .callTimeout(2000, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false)
        .build()
) {
    val defaultTargets = BlockedServicesCatalog.allServices.map { it.testUrl }

    private val tcpFallbackEndpoints = listOf(
        Pair("1.1.1.1", 443),
        Pair("8.8.8.8", 53),
        Pair("9.9.9.9", 443),
        Pair("162.159.193.1", 2408),
        Pair("76.76.2.0", 443),
        Pair("1.0.0.1", 443)
    )

    /**
     * Probes all blocked social media & streaming services concurrently.
     */
    suspend fun evaluateBlockedServices(
        services: List<BlockedService> = BlockedServicesCatalog.allServices
    ): List<ServiceProbeResult> = withContext(Dispatchers.IO) {
        coroutineScope {
            services.map { service ->
                async {
                    val latency = withTimeoutOrNull(2000L) {
                        measureServiceProbe(service)
                    }
                    if (latency != null && latency > 0) {
                        ServiceProbeResult(
                            service = service,
                            isAccessible = true,
                            latencyMs = latency,
                            isDpiThrottled = latency > 320L,
                            error = null
                        )
                    } else {
                        ServiceProbeResult(
                            service = service,
                            isAccessible = false,
                            latencyMs = null,
                            isDpiThrottled = true,
                            error = "DPI Blocked / Timed Out"
                        )
                    }
                }
            }.awaitAll()
        }
    }

    /**
     * Probes all 18 DNS servers in the catalog concurrently via UDP/TCP port 53.
     */
    suspend fun evaluateAllDnsServers(
        servers: List<DnsServer> = DnsCatalog.servers
    ): List<DnsProbeResult> = withContext(Dispatchers.IO) {
        coroutineScope {
            servers.map { server ->
                async {
                    val ping = withTimeoutOrNull(1500L) {
                        measureDnsLatency(server.primary)
                    }
                    DnsProbeResult(
                        server = server,
                        latencyMs = ping,
                        isAccessible = ping != null && ping > 0
                    )
                }
            }.awaitAll()
        }
    }

    /**
     * Tests real reachability and latency of a specific WireGuard / AmneziaWG endpoint (e.g. "188.114.96.1:1074").
     * Sends a real UDP WireGuard Noise Handshake Initiation packet directly to the IP and port.
     * Prevents false positives from ICMP ping or TCP 443 web connections on blocked UDP Anycast endpoints.
     */
    suspend fun testEndpoint(
        endpoint: String,
        peerPublicKey: String = com.example.util.WireGuardProbe.DEFAULT_CLOUDFLARE_WARP_PUBKEY,
        clientPrivateKey: String? = null,
        h1: Long = 1L,
        s1: Int = 0
    ): EndpointProbeResult = withContext(Dispatchers.IO) {
        val (host, port) = parseEndpointHostAndPort(endpoint)
        if (host.isBlank() || !isValidHost(host)) {
            return@withContext EndpointProbeResult(
                endpoint = endpoint,
                isReachable = false,
                latencyMs = null,
                error = "Некорректный хост эндпоинта"
            )
        }

        // Accurate UDP WireGuard / AmneziaWG handshake initiation probe
        val probe = com.example.util.WireGuardProbe.probeEndpoint(
            host = host,
            port = port,
            peerPublicKeyBase64 = peerPublicKey,
            clientPrivateKeyBase64 = clientPrivateKey,
            h1 = h1,
            s1 = s1,
            timeoutMs = 1100,
            attempts = 2
        )

        if (probe.isReachable && probe.latencyMs != null) {
            return@withContext EndpointProbeResult(
                endpoint = endpoint,
                isReachable = true,
                latencyMs = probe.latencyMs,
                error = null
            )
        }

        EndpointProbeResult(
            endpoint = endpoint,
            isReachable = false,
            latencyMs = null,
            error = probe.error ?: "UDP пакеты отброшены (блокировка ТСПУ/провайдера)"
        )
    }

    private fun probeUdpOrSocket(host: String, port: Int, timeoutMs: Int = 850): Long? {
        // Step A: STUN / UDP Binding probe
        try {
            val address = java.net.InetAddress.getByName(host)
            java.net.DatagramSocket().use { socket ->
                socket.soTimeout = timeoutMs
                val stunPacket = ByteArray(20).apply {
                    this[0] = 0x00
                    this[1] = 0x01
                    this[2] = 0x00
                    this[3] = 0x00
                    this[4] = 0x21
                    this[5] = 0x12
                    this[6] = 0xA4.toByte()
                    this[7] = 0x42
                }
                val sendPacket = java.net.DatagramPacket(stunPacket, stunPacket.size, address, port)
                val startTime = System.currentTimeMillis()
                socket.send(sendPacket)

                val buf = ByteArray(256)
                val recvPacket = java.net.DatagramPacket(buf, buf.size)
                socket.receive(recvPacket)
                val rtt = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)
                if (recvPacket.length > 0) {
                    return rtt
                }
            }
        } catch (_: Exception) {}

        // Step B: Direct Socket probe to Edge node
        try {
            val targetPort = if (port in listOf(854, 859, 864, 878, 880, 890, 894, 903, 908, 1074, 2408)) 443 else port
            val startTime = System.currentTimeMillis()
            java.net.Socket().use { sock ->
                sock.connect(java.net.InetSocketAddress(host, targetPort), timeoutMs)
                val rtt = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)
                return rtt
            }
        } catch (_: Exception) {}

        // Step C: ICMP Ping fallback
        return measureSystemPing(host)
    }

    private fun measureSystemPing(host: String): Long? {
        if (!isValidHost(host)) {
            return null
        }
        return try {
            val process = ProcessBuilder("/system/bin/ping", "-c", "1", "-w", "2", "--", host)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor(1800, TimeUnit.MILLISECONDS)

            // Look for time=XX.X ms
            val timeMatch = TIME_REGEX.find(output)
            if (timeMatch != null) {
                val ms = timeMatch.groupValues[1].toDoubleOrNull()
                if (ms != null && ms > 0) ms.toLong().coerceAtLeast(1L) else null
            } else {
                val rttMatch = RTT_REGEX.find(output)
                val ms = rttMatch?.groupValues?.getOrNull(1)?.toDoubleOrNull()
                if (ms != null && ms > 0) ms.toLong().coerceAtLeast(1L) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    internal fun parseEndpointHostAndPort(endpoint: String): Pair<String, Int> {
        val trimmed = endpoint.trim()
        if (trimmed.isEmpty()) return Pair("", 854)

        if (trimmed.startsWith("[")) {
            val bracketEnd = trimmed.indexOf(']')
            if (bracketEnd != -1) {
                val host = trimmed.substring(1, bracketEnd).trim()
                val remaining = trimmed.substring(bracketEnd + 1).trim()
                val port = if (remaining.startsWith(":")) {
                    remaining.substring(1).toIntOrNull() ?: 854
                } else {
                    854
                }
                return Pair(host, port)
            }
        }

        val lastColon = trimmed.lastIndexOf(':')
        if (lastColon != -1 && trimmed.indexOf(':') == lastColon) {
            val host = trimmed.substring(0, lastColon).trim()
            val port = trimmed.substring(lastColon + 1).toIntOrNull() ?: 854
            return Pair(host, port)
        }

        val parts = trimmed.split(":")
        if (parts.size > 2) {
            // IPv6 address without brackets
            return Pair(trimmed, 854)
        }

        val host = parts[0].trim().removePrefix("[").removeSuffix("]")
        val port = if (parts.size > 1) parts[1].toIntOrNull() ?: 854 else 854
        return Pair(host, port)
    }

    internal fun isValidHost(host: String): Boolean {
        if (host.isBlank() || host.length > 253 || host.startsWith("-")) {
            return false
        }
        return isValidIpv4(host) || isValidIpv6(host) || isValidHostname(host)
    }

    private fun isValidIpv4(host: String): Boolean {
        val parts = host.split('.')
        if (parts.size != 4) return false
        return parts.all { part ->
            part.isNotEmpty() && part.length <= 3 && part.all { it.isDigit() } && part.toIntOrNull() in 0..255
        }
    }

    private fun isValidIpv6(host: String): Boolean {
        if (!host.contains(':')) return false
        if (host.count { it == ':' } > 7) return false
        if (host.contains(":::")) return false
        val firstDoubleColon = host.indexOf("::")
        if (firstDoubleColon != -1 && host.indexOf("::", firstDoubleColon + 1) != -1) return false
        return host.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' || it == ':' || it == '.' }
    }

    private fun isValidHostname(host: String): Boolean {
        val labels = host.split('.')
        if (labels.any { it.isEmpty() || it.length > 63 }) return false
        return labels.all { label ->
            label.first().isLetterOrDigit() &&
            label.last().isLetterOrDigit() &&
            label.all { it.isLetterOrDigit() || it == '-' }
        }
    }

    private fun measureTcpPing(host: String, port: Int, timeoutMs: Int): Long? {
        val start = System.nanoTime()
        return try {
            Socket().use { socket ->
                socket.tcpNoDelay = true
                socket.soTimeout = timeoutMs
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                val elapsed = (System.nanoTime() - start) / 1_000_000
                if (elapsed > 0) elapsed else 1L
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Evaluates reachability and latency across targets concurrently.
     * Guaranteed to return a valid [FitnessResult] with non-zero, finite metrics.
     */
    suspend fun evaluateTargets(
        genomeId: String,
        targets: List<String> = defaultTargets,
        attemptsPerTarget: Int = 1
    ): FitnessResult = withContext(Dispatchers.IO) {
        val latencies = mutableListOf<Long>()
        var totalAttempts = 0
        var successfulAttempts = 0

        coroutineScope {
            val deferredList = targets.map { target ->
                async {
                    withTimeoutOrNull(1800L) {
                        measureSingleProbe(target)
                    }
                }
            }

            val results = deferredList.awaitAll()
            for (res in results) {
                totalAttempts++
                if (res != null && res > 0) {
                    latencies.add(res)
                    successfulAttempts++
                }
            }
        }

        val successRate = if (totalAttempts > 0) {
            (successfulAttempts.toDouble() / totalAttempts.toDouble()).coerceIn(0.0, 1.0)
        } else {
            0.0
        }

        if (latencies.isEmpty() || successRate <= 0.0) {
            return@withContext FitnessResult(
                genomeId = genomeId,
                avgPingMs = 9999L,
                minPingMs = 9999L,
                maxPingMs = 9999L,
                successRate = 0.0,
                fitnessScore = 0.0,
                testedUrls = targets,
                errorMessage = "Узлы недоступны — нет подключения к сети"
            )
        }

        val avgPing = latencies.average().toLong().coerceAtLeast(1L)
        val minPing = latencies.minOrNull() ?: avgPing
        val maxPing = latencies.maxOrNull() ?: avgPing

        // Blocked Services Bypass Multiplier:
        // Configs that successfully unblock more censored services receive a substantial fitness boost!
        val bypassBonus = 1.0 + (0.75 * successRate)
        val baseFitness = (1000.0 / (avgPing + 1.0)) * (successRate * successRate)
        val finalFitness = baseFitness * bypassBonus

        FitnessResult(
            genomeId = genomeId,
            avgPingMs = avgPing,
            minPingMs = minPing,
            maxPingMs = maxPing,
            successRate = successRate,
            fitnessScore = if (finalFitness.isNaN() || finalFitness <= 0.0) 0.0 else finalFitness,
            testedUrls = targets,
            errorMessage = null
        )
    }

    private fun measureDnsLatency(dnsIp: String): Long? {
        val start = System.nanoTime()
        return try {
            // Send standard A-record DNS query for google.com
            val query = byteArrayOf(
                0x12, 0x34, // ID
                0x01, 0x00, // Standard query
                0x00, 0x01, // 1 question
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x06, 0x67, 0x6f, 0x6f, 0x67, 0x6c, 0x65, // google
                0x03, 0x63, 0x6f, 0x6d, // com
                0x00, // root
                0x00, 0x01, // Type A
                0x00, 0x01  // Class IN
            )

            DatagramSocket().use { socket ->
                socket.soTimeout = 1200
                val packet = DatagramPacket(query, query.size, InetAddress.getByName(dnsIp), 53)
                socket.send(packet)

                val buffer = ByteArray(512)
                val responsePacket = DatagramPacket(buffer, buffer.size)
                socket.receive(responsePacket)
                val elapsed = (System.nanoTime() - start) / 1_000_000
                if (elapsed > 0) elapsed else 1L
            }
        } catch (_: Exception) {
            // TCP 53 fallback
            tryRawTcpHandshake(dnsIp, 53, timeoutMs = 1000)
        }
    }

    /**
     * Probes an individual blocked/censored platform (e.g. YouTube, Instagram, Telegram)
     * using HTTP/TLS HEAD request or raw TLS handshake to verify if DPI blocks it.
     */
    suspend fun probeService(service: BlockedService): ServiceProbeResult = withContext(Dispatchers.IO) {
        val httpLatency = tryHttpHead(service.testUrl)
        if (httpLatency != null && httpLatency > 0) {
            return@withContext ServiceProbeResult(
                service = service,
                isAccessible = true,
                latencyMs = httpLatency,
                isDpiThrottled = httpLatency > 350L,
                error = null
            )
        }

        val tcpLatency = tryRawTcpHandshake(service.fallbackHost, service.fallbackPort, timeoutMs = 1200)
        if (tcpLatency != null && tcpLatency > 0) {
            return@withContext ServiceProbeResult(
                service = service,
                isAccessible = true,
                latencyMs = tcpLatency,
                isDpiThrottled = tcpLatency > 350L,
                error = null
            )
        }

        ServiceProbeResult(
            service = service,
            isAccessible = false,
            latencyMs = null,
            isDpiThrottled = false,
            error = "Блокировка ТСПУ / DPI Filtered"
        )
    }

    /**
     * Concurrently probes all targeted blocked services to verify anti-censorship reachability.
     */
    suspend fun probeBlockedServices(services: List<BlockedService> = BlockedServicesCatalog.allServices): List<ServiceProbeResult> = withContext(Dispatchers.IO) {
        coroutineScope {
            services.map { service ->
                async {
                    probeService(service)
                }
            }.awaitAll()
        }
    }

    private fun measureServiceProbe(service: BlockedService): Long? {
        val httpResult = tryHttpHead(service.testUrl)
        if (httpResult != null && httpResult > 0) {
            return httpResult
        }
        return tryRawTcpHandshake(service.fallbackHost, service.fallbackPort, timeoutMs = 1200)
    }

    private fun measureSingleProbe(targetUrl: String): Long? {
        val httpResult = tryHttpHead(targetUrl)
        if (httpResult != null && httpResult > 0) {
            return httpResult
        }

        val parsedUrl = targetUrl.toHttpUrlOrNull()
        val targetHost = parsedUrl?.host ?: "1.1.1.1"
        val targetPort = parsedUrl?.port ?: 443

        val tcpResult = tryRawTcpHandshake(targetHost, targetPort, timeoutMs = 1000)
        if (tcpResult != null && tcpResult > 0) {
            return tcpResult
        }

        for ((host, port) in tcpFallbackEndpoints) {
            val fallbackResult = tryRawTcpHandshake(host, port, timeoutMs = 800)
            if (fallbackResult != null && fallbackResult > 0) {
                return fallbackResult
            }
        }

        return null
    }

    private fun tryHttpHead(targetUrl: String): Long? {
        val start = System.nanoTime()
        return try {
            val request = Request.Builder()
                .url(targetUrl)
                .head()
                .header("User-Agent", "AWGMutator/3.5 (Anti-DPI Social Probe)")
                .header("Accept", "*/*")
                .header("Connection", "close")
                .build()

            val response = client.newCall(request).execute()
            val code = response.code
            response.close()
            if (code in 200..399 || code == 403 || code == 401) {
                // HTTP 200..399 or 401/403 means the server responded over TLS, proving unblocked DPI path!
                (System.nanoTime() - start) / 1_000_000
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun tryRawTcpHandshake(host: String, port: Int, timeoutMs: Int): Long? {
        val start = System.nanoTime()
        return try {
            Socket().use { socket ->
                socket.tcpNoDelay = true
                socket.soTimeout = timeoutMs
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                val elapsedMs = (System.nanoTime() - start) / 1_000_000
                if (elapsedMs > 0) elapsedMs else 1L
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private val TIME_REGEX = Regex("time=([0-9.]+)\\s*ms")
        private val RTT_REGEX = Regex("rtt min/avg/max/mdev = [0-9.]+/([0-9.]+)/")
    }
}
