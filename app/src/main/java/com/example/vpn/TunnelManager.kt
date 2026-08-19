package com.example.vpn

import android.content.Context
import android.util.Log
import com.example.App
import com.example.domain.model.AwgConfig
import com.example.domain.model.VpnState
import com.example.domain.model.VpnStatus
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
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

    private val maxLogs = 150
    private val logQueue = ConcurrentLinkedDeque<String>()
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var statsJob: Job? = null

    private val goBackend: GoBackend by lazy { GoBackend(context) }

    private val wgTunnel = object : Tunnel {
        override fun getName(): String = "awg0"
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
                Tunnel.State.TOGGLE -> {
                    _status.value = current.copy(
                        state = VpnState.CONNECTING
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

    fun updateStatus(newStatus: VpnStatus) {
        _status.value = newStatus
    }

    fun updateBytes(rx: Long, tx: Long) {
        _status.value = _status.value.copy(rxBytes = rx, txBytes = tx)
    }

    fun connect(config: AwgConfig) {
        val sanitizedEndpoint = AwgConfig.sanitizeEndpoint(config.endpoint)
        log("TUN_LIFECYCLE", "Connecting via native WireGuard GoBackend to '${config.name}' (Endpoint: $sanitizedEndpoint)")

        _status.value = VpnStatus(
            state = VpnState.CONNECTING,
            activeConfigName = config.name,
            activeConfigId = config.id,
            endpoint = sanitizedEndpoint
        )

        scope.launch {
            try {
                val cleanConf = config.toCleanWgQuickString()
                log("TUN_CONF", "Generated clean WireGuard profile for native engine:\n$cleanConf")
                val stream = ByteArrayInputStream(cleanConf.toByteArray(Charsets.UTF_8))
                val wgConfig = Config.parse(stream)

                val resultingState = goBackend.setState(wgTunnel, Tunnel.State.UP, wgConfig)
                log("TUN_LIFECYCLE", "Native GoBackend setState UP completed. State: $resultingState")

                _status.value = VpnStatus(
                    state = VpnState.CONNECTED,
                    activeConfigName = config.name,
                    activeConfigId = config.id,
                    endpoint = sanitizedEndpoint,
                    connectedSince = System.currentTimeMillis()
                )

                // Start periodic traffic polling from native GoBackend statistics
                startStatsPolling()

                // Start Per-App network traffic tracking
                App.instance.appTrafficTracker.startTracking()

                // Verify real egress public IP, country, and Cloudflare WARP status
                verifyEgressConnectivity(config)

            } catch (e: Exception) {
                log("TUN_ERROR", "Failed to start native WireGuard tunnel: ${e.message}")
                _status.value = VpnStatus(
                    state = VpnState.ERROR,
                    activeConfigName = config.name,
                    activeConfigId = config.id,
                    endpoint = sanitizedEndpoint,
                    errorMessage = e.message ?: "Tunnel error"
                )
            }
        }
    }

    fun disconnect() {
        log("TUN_LIFECYCLE", "Disconnecting WireGuard tunnel...")
        statsJob?.cancel()
        statsJob = null
        App.instance.appTrafficTracker.stopTracking()

        scope.launch {
            try {
                goBackend.setState(wgTunnel, Tunnel.State.DOWN, null)
                log("TUN_LIFECYCLE", "Tunnel successfully disconnected")
            } catch (e: Exception) {
                log("TUN_ERROR", "Error during disconnect: ${e.message}")
            } finally {
                _status.value = VpnStatus(
                    state = VpnState.DISCONNECTED
                )
            }
        }
    }

    private fun startStatsPolling() {
        statsJob?.cancel()
        statsJob = scope.launch {
            while (isActive) {
                delay(1000)
                try {
                    val stats = goBackend.getStatistics(wgTunnel)
                    val rx = stats.totalRx()
                    val tx = stats.totalTx()
                    val current = _status.value
                    if (current.state == VpnState.CONNECTED) {
                        _status.value = current.copy(rxBytes = rx, txBytes = tx)
                    }
                } catch (_: Exception) {}
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
