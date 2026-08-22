package com.example.vpn

import android.content.Context
import android.util.Log
import com.example.App
import com.example.domain.model.AwgConfig
import com.example.domain.model.VpnState
import com.example.domain.model.VpnStatus
import org.amnezia.awg.backend.GoBackend
import org.amnezia.awg.backend.NoopTunnelActionHandler
import org.amnezia.awg.backend.Tunnel
import org.amnezia.awg.config.Config
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Manages the native WireGuard / AmneziaWG tunnel lifecycle via [GoBackend],
 * real-time traffic byte statistics polling, egress public IP verification,
 * and packet routing diagnostics logs.
 */
class TunnelManager(private val context: Context) {

    private val _status = MutableStateFlow(VpnStatus())
    val status: StateFlow<VpnStatus> = _status.asStateFlow()

    private val maxLogs = 500
    private val logQueue = ConcurrentLinkedDeque<String>()
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var statsJob: Job? = null
    private var watchdogJob: Job? = null

    private val goBackend: GoBackend by lazy { GoBackend(context, NoopTunnelActionHandler()) }

    private val wgTunnel = object : Tunnel {
        override fun getName(): String = "awg0"
        override fun isIpv4ResolutionPreferred(): Boolean = true
        override fun onStateChange(newState: Tunnel.State) {
            log("WG_STATE", "Tunnel state changed to: $newState")
            val current = _status.value
            when (newState) {
                Tunnel.State.UP -> {
                    _status.value = current.copy(
                        state = VpnState.CONNECTED,
                        connectedSince = System.currentTimeMillis()
                    )
                }
                Tunnel.State.DOWN -> {
                    _status.value = current.copy(
                        state = VpnState.DISCONNECTED
                    )
                }
            }
        }
    }

    fun log(tag: String, message: String) {
        val timestamp = timeFormat.format(Date())
        val formatted = "[$timestamp] [$tag] $message"
        Log.i(tag, message)

        logQueue.addLast(formatted)
        while (logQueue.size > maxLogs) {
            logQueue.pollFirst()
        }
        _logs.value = logQueue.toList()
    }

    fun clearLogs() {
        logQueue.clear()
        _logs.value = emptyList()
    }

    fun getFormattedFullLogText(): String {
        val sb = StringBuilder()
        sb.appendLine("=== AWG MUTATOR / WIREGUARD DIAGNOSTIC LOG ===")
        sb.appendLine("Generated At: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        sb.appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (${android.os.Build.DEVICE})")
        sb.appendLine("Android OS: ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
        sb.appendLine("Current VPN State: ${_status.value.state}")
        sb.appendLine("Active Config: ${_status.value.activeConfigName ?: "None"}")
        sb.appendLine("Active Endpoint: ${_status.value.endpoint ?: "N/A"}")
        sb.appendLine("Total Rx: ${_status.value.rxBytes} B, Total Tx: ${_status.value.txBytes} B")
        sb.appendLine("Egress IP: ${_status.value.egressIp ?: "N/A"} (${_status.value.egressCountry ?: "N/A"})")
        sb.appendLine("WARP Detected: ${_status.value.isWarpActive}")
        sb.appendLine("================ LOG ENTRIES ================")
        logQueue.forEach { line ->
            sb.appendLine(line)
        }
        sb.appendLine("================ END OF LOG ================")
        return sb.toString()
    }

    fun updateStatus(newStatus: VpnStatus) {
        _status.value = newStatus
    }

    fun updateBytes(rx: Long, tx: Long) {
        _status.value = _status.value.copy(rxBytes = rx, txBytes = tx)
    }

    fun connect(config: AwgConfig) {
        val sanitizedEndpoint = AwgConfig.sanitizeEndpoint(config.endpoint, defaultPort = if (config.isWarp) 854 else 51820)
        log("DEVICE_INFO", "Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
        log("TUN_LIFECYCLE", "Initiating connection to '${config.name}' (Target Endpoint: $sanitizedEndpoint)")

        _status.value = VpnStatus(
            state = VpnState.CONNECTING,
            activeConfigName = config.name,
            activeConfigId = config.id,
            endpoint = sanitizedEndpoint
        )

        scope.launch {
            try {
                val effectiveDns = if (config.dns.isBlank()) {
                    "1.1.1.1, 8.8.8.8, 1.0.0.1"
                } else {
                    config.dns
                }

                val effectiveConfig = config.copy(dns = effectiveDns)

                val confText = effectiveConfig.toConfString()
                log("TUN_CONF", "Generated AmneziaWG configuration:\n$confText")

                // Strip extended attributes (I1-I4, SNI, S3, S4, Reserved) not supported by native Config.parse
                val extendedKeys = setOf("reserved", "sni", "i1", "i2", "i3", "i4", "s3", "s4")
                val nativeConf = confText.lines().filterNot { line ->
                    val key = line.substringBefore("=").trim().lowercase()
                    key in extendedKeys
                }.joinToString("\n")

                val stream = ByteArrayInputStream(nativeConf.toByteArray(Charsets.UTF_8))
                val awgConfig = try {
                    Config.parse(stream)
                } catch (pe: Exception) {
                    log("TUN_ERROR", "Configuration parse error: ${pe.message}")
                    log("TUN_ERROR", Log.getStackTraceString(pe))
                    throw pe
                }

                val iface = awgConfig.`interface`
                val peer = awgConfig.peers.firstOrNull()
                log("TUN_PARSED_DIAG", "Parsed AWG Interface: Addresses=${iface.addresses}, DNS=${iface.dnsServers}, MTU=${iface.mtu.orElse(0)}")
                log(
                    "TUN_PARSED_DIAG",
                    "AWG Parameters: Jc=${iface.junkPacketCount.orElse(0)}, Jmin=${iface.junkPacketMinSize.orElse(0)}, Jmax=${iface.junkPacketMaxSize.orElse(0)}, S1=${iface.initPacketJunkSize.orElse(0)}, S2=${iface.responsePacketJunkSize.orElse(0)}, H1=${iface.initPacketMagicHeader.orElse(0L)}, H2=${iface.responsePacketMagicHeader.orElse(0L)}, H3=${iface.underloadPacketMagicHeader.orElse(0L)}, H4=${iface.transportPacketMagicHeader.orElse(0L)}"
                )
                if (peer != null) {
                    log("TUN_PARSED_DIAG", "Parsed Peer: Endpoint=${peer.endpoint.orElse(null)}, AllowedIPs=${peer.allowedIps}")
                }

                log("TUN_LIFECYCLE", "Calling native AmneziaWG GoBackend setState(UP)...")
                val resultingState = goBackend.setState(wgTunnel, Tunnel.State.UP, awgConfig)
                log("TUN_LIFECYCLE", "GoBackend setState UP returned: $resultingState")

                _status.value = VpnStatus(
                    state = VpnState.CONNECTED,
                    activeConfigName = config.name,
                    activeConfigId = config.id,
                    endpoint = sanitizedEndpoint,
                    connectedSince = System.currentTimeMillis()
                )

                // Start periodic traffic polling from native GoBackend statistics
                startStatsPolling(config)

                // Start Per-App network traffic tracking
                App.instance.appTrafficTracker.startTracking()

                // Verify real egress public IP, country, and Cloudflare WARP status
                delay(1200)
                verifyEgressConnectivity(config)

            } catch (e: Exception) {
                log("TUN_ERROR", "Exception starting WireGuard tunnel: ${e.message}")
                log("TUN_ERROR", Log.getStackTraceString(e))
                _status.value = VpnStatus(
                    state = VpnState.ERROR,
                    activeConfigName = config.name,
                    activeConfigId = config.id,
                    endpoint = sanitizedEndpoint,
                    errorMessage = e.message ?: "Tunnel startup error"
                )
            }
        }
    }

    fun disconnect() {
        log("TUN_LIFECYCLE", "Disconnecting WireGuard tunnel...")
        statsJob?.cancel()
        statsJob = null
        watchdogJob?.cancel()
        watchdogJob = null
        App.instance.appTrafficTracker.stopTracking()

        scope.launch {
            try {
                goBackend.setState(wgTunnel, Tunnel.State.DOWN, null)
                log("TUN_LIFECYCLE", "Tunnel successfully disconnected")
            } catch (e: Exception) {
                log("TUN_ERROR", "Error during disconnect: ${e.message}")
                log("TUN_ERROR", Log.getStackTraceString(e))
            } finally {
                _status.value = VpnStatus(
                    state = VpnState.DISCONNECTED
                )
            }
        }
    }

    private fun startStatsPolling(config: AwgConfig) {
        statsJob?.cancel()
        watchdogJob?.cancel()

        var zeroRxCycles = 0
        var handshakeReported = false

        statsJob = scope.launch {
            while (isActive) {
                delay(1500)
                try {
                    val stats = goBackend.getStatistics(wgTunnel)
                    val rx = stats.totalRx()
                    val tx = stats.totalTx()
                    val current = _status.value

                    if (current.state == VpnState.CONNECTED) {
                        _status.value = current.copy(rxBytes = rx, txBytes = tx)

                        log("TUN_STATS_POLL", "Periodic Traffic Poll: Tx=$tx B, Rx=$rx B (ZeroRxCycles=$zeroRxCycles)")

                        if (tx > 0 && rx > 0 && !handshakeReported) {
                            handshakeReported = true
                            log("TUN_TRAFFIC", "Handshake established! Active traffic flow: Tx=$tx B, Rx=$rx B")
                        } else if (tx > 0 && rx < 100L) {
                            zeroRxCycles++
                            if (zeroRxCycles % 3 == 0) {
                                log("TUN_WARN", "Tx=$tx B sent, but low Rx=$rx B received. Server may be dropping data or handshake unacknowledged. Target: ${config.endpoint}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    log("TUN_STATS_ERR", "Failed to query stats: ${e.message}")
                }
            }
        }
    }

    suspend fun verifyEgressConnectivity(config: AwgConfig) {
        log("VPN_VERIFY", "Verifying real egress public IP and WARP status...")
        val egress = App.instance.networkEgressVerifier.verifyEgress()
        val pingResult = App.instance.pingTester.testEndpoint(config.endpoint)

        log(
            "VPN_VERIFY",
            "Egress probe completed: IP=${egress.publicIp ?: "N/A"}, Country=${egress.countryCode ?: "N/A"}, WARP=${egress.isWarpActive}, Latency=${egress.latencyMs ?: pingResult.latencyMs}ms, Functional=${egress.isFunctional}"
        )

        val current = _status.value
        if (current.state == VpnState.CONNECTED) {
            _status.value = current.copy(
                egressIp = egress.publicIp,
                egressCountry = egress.countryCode,
                isEgressVerified = egress.isFunctional,
                isWarpActive = egress.isWarpActive,
                currentPingMs = egress.latencyMs ?: pingResult.latencyMs
            )
        }
    }
}
