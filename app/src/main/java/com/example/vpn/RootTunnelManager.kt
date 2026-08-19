package com.example.vpn

import android.content.Context
import com.example.domain.model.AwgConfig
import com.example.domain.model.VpnState
import com.example.domain.model.VpnStatus
import com.example.util.RootRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manages Root-level VPN / Kernel Tunnel operations without requiring Android VpnService.
 * Uses `ip`, `wg` / `awg`, and `iptables` / `ip rule` for direct kernel WireGuard bypass.
 */
class RootTunnelManager(private val context: Context) {

    private val _status = MutableStateFlow(VpnStatus())
    val status: StateFlow<VpnStatus> = _status.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var statsJob: Job? = null
    private val ifaceName = "awg0"
    private val tableId = "51820"
    private val fwMark = "0x51820"

    var isRootModeEnabled: Boolean
        get() = context.getSharedPreferences("awg_root_prefs", Context.MODE_PRIVATE)
            .getBoolean("root_mode_enabled", false)
        set(value) {
            context.getSharedPreferences("awg_root_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("root_mode_enabled", value)
                .apply()
        }

    /**
     * Checks whether root mode is enabled in settings and accessible.
     */
    suspend fun isRootReady(): Boolean = isRootModeEnabled && RootRunner.isRootAvailable()

    /**
     * Connects and starts the tunnel via Root commands.
     */
    suspend fun connect(config: AwgConfig): Result<Unit> = withContext(Dispatchers.IO) {
        if (!RootRunner.isRootAvailable()) {
            return@withContext Result.failure(Exception("Root access (su) is not available on this device."))
        }

        _status.value = _status.value.copy(
            state = VpnState.CONNECTING,
            activeConfigName = config.name,
            activeConfigId = config.id
        )

        // Write temp conf file
        val confFile = File(context.cacheDir, "root_awg.conf")
        confFile.writeText(config.toConfString())
        val confPath = confFile.absolutePath

        // 1. Clean previous interface if any
        teardownInterface()

        val primaryDns = config.dns.split(",").firstOrNull()?.trim() ?: "111.88.96.50"
        val ipAddr = config.address.let { if (it.contains("/")) it else "$it/32" }

        val commands = mutableListOf<String>()

        // 2. Create interface
        commands.add("ip link add dev $ifaceName type wireguard || ip link add dev $ifaceName type amneziawg || true")
        commands.add("ip address add $ipAddr dev $ifaceName || true")
        commands.add("ip link set mtu ${config.mtu} dev $ifaceName || true")

        // 3. Configure WG/AWG
        commands.add("wg setconf $ifaceName $confPath || awg setconf $ifaceName $confPath || true")
        commands.add("ip link set up dev $ifaceName")

        // 4. Policy Routing
        commands.add("ip rule add not fwmark $fwMark table $tableId priority 1000 || true")
        commands.add("ip rule add table main suppress_prefixlength 0 priority 990 || true")
        commands.add("ip route add default dev $ifaceName table $tableId || true")

        // 5. iptables MSS clamping and DNS redirect via isolated chains
        commands.add("iptables -t nat -N AWG_OUTPUT || true")
        commands.add("iptables -t nat -F AWG_OUTPUT || true")
        commands.add("iptables -t nat -C OUTPUT -j AWG_OUTPUT 2>/dev/null || iptables -t nat -I OUTPUT -j AWG_OUTPUT || true")
        commands.add("iptables -t nat -A AWG_OUTPUT -p udp --dport 53 -j DNAT --to-destination $primaryDns:53 || true")
        commands.add("iptables -t nat -A AWG_OUTPUT -p tcp --dport 53 -j DNAT --to-destination $primaryDns:53 || true")

        commands.add("iptables -t mangle -N AWG_POSTROUTING || true")
        commands.add("iptables -t mangle -F AWG_POSTROUTING || true")
        commands.add("iptables -t mangle -C POSTROUTING -j AWG_POSTROUTING 2>/dev/null || iptables -t mangle -I POSTROUTING -j AWG_POSTROUTING || true")
        commands.add("iptables -t mangle -A AWG_POSTROUTING -p tcp --tcp-flags SYN,RST SYN -o $ifaceName -j TCPMSS --clamp-mss-to-pmtu || true")

        val res = RootRunner.execute(*commands.toTypedArray())

        if (res.isSuccess || checkInterfaceUp()) {
            _status.value = _status.value.copy(
                state = VpnState.CONNECTED,
                activeConfigName = config.name,
                activeConfigId = config.id,
                endpoint = config.endpoint,
                connectedSince = System.currentTimeMillis(),
                isRootTunnel = true
            )
            com.example.App.instance.appTrafficTracker.startTracking()
            startStatsMonitor()

            // Verify internet egress for root tunnel
            scope.launch {
                val egress = com.example.App.instance.networkEgressVerifier.verifyEgress()
                _status.value = _status.value.copy(
                    egressIp = egress.publicIp,
                    egressCountry = egress.countryCode,
                    isEgressVerified = egress.isFunctional,
                    currentPingMs = egress.latencyMs
                )
            }

            Result.success(Unit)
        } else {
            teardownInterface()
            _status.value = _status.value.copy(state = VpnState.DISCONNECTED)
            Result.failure(Exception("Failed to establish root tunnel: ${res.stderr.ifBlank { res.stdout }}"))
        }
    }

    /**
     * Disconnects and removes root interface & iptables rules.
     */
    suspend fun disconnect(): Result<Unit> = withContext(Dispatchers.IO) {
        _status.value = _status.value.copy(state = VpnState.DISCONNECTING)
        statsJob?.cancel()
        statsJob = null
        com.example.App.instance.appTrafficTracker.stopTracking()

        teardownInterface()

        _status.value = _status.value.copy(
            state = VpnState.DISCONNECTED,
            activeConfigName = null,
            activeConfigId = null,
            isRootTunnel = false,
            isEgressVerified = false
        )
        Result.success(Unit)
    }

    private suspend fun teardownInterface() {
        RootRunner.execute(
            // 1. Remove isolated iptables rules without flushing system tables
            "iptables -t nat -D OUTPUT -j AWG_OUTPUT || true",
            "iptables -t nat -F AWG_OUTPUT || true",
            "iptables -t nat -X AWG_OUTPUT || true",
            "iptables -t mangle -D POSTROUTING -j AWG_POSTROUTING || true",
            "iptables -t mangle -F AWG_POSTROUTING || true",
            "iptables -t mangle -X AWG_POSTROUTING || true",
            // 2. Clean policy routing
            "ip route flush table $tableId || true",
            "ip rule del not fwmark $fwMark table $tableId priority 1000 || true",
            "ip rule del table main suppress_prefixlength 0 priority 990 || true",
            // 3. Remove interface
            "ip link set down dev $ifaceName || true",
            "ip link delete dev $ifaceName || true"
        )
    }

    private suspend fun checkInterfaceUp(): Boolean {
        val check = RootRunner.execute("ip link show dev $ifaceName")
        return check.isSuccess && check.stdout.contains(ifaceName)
    }

    private fun startStatsMonitor() {
        statsJob?.cancel()
        statsJob = scope.launch {
            while (isActive) {
                delay(2000)
                try {
                    val stats = RootRunner.execute("cat /sys/class/net/$ifaceName/statistics/rx_bytes", "cat /sys/class/net/$ifaceName/statistics/tx_bytes")
                    if (stats.isSuccess) {
                        val lines = stats.stdout.lines()
                        val rx = lines.getOrNull(0)?.trim()?.toLongOrNull() ?: 0L
                        val tx = lines.getOrNull(1)?.trim()?.toLongOrNull() ?: 0L
                        _status.value = _status.value.copy(rxBytes = rx, txBytes = tx)
                    }
                } catch (_: Exception) {}
            }
        }
    }
}
