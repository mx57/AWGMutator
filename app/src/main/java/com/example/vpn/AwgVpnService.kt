package com.example.vpn

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.net.wifi.WifiManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.App
import com.example.MainActivity
import com.example.R
import com.example.domain.model.AwgConfig
import com.example.domain.model.VpnState
import com.example.domain.model.VpnStatus
import com.example.util.ConfigParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Socket

/**
 * Android Foreground VpnService implementing AmneziaWG / WireGuard TUN tunnel handling,
 * real [TunPacketRouter] IPv4/IPv6 TCP/UDP/ICMP forwarding, and runtime [DpiNoiseManager] handshake modulation.
 * Provides detailed lifecycle and routing table verification diagnostics.
 */
class AwgVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var packetRouter: TunPacketRouter? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onCreate() {
        super.onCreate()
        activeService = this
        App.instance.tunnelManager.log("VPN_LIFECYCLE", "AwgVpnService created and registered as active service")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        App.instance.tunnelManager.log("VPN_LIFECYCLE", "onStartCommand received action: $action")

        when (action) {
            ACTION_CONNECT -> {
                val configRaw = intent.getStringExtra(EXTRA_CONFIG_RAW)
                val rawConfigName = intent.getStringExtra(EXTRA_CONFIG_NAME)
                val configName = ConfigParser.sanitizeConfigName(rawConfigName, "VPN Tunnel")

                if (!configRaw.isNullOrBlank()) {
                    val parsed = ConfigParser.parse(configRaw, configName).getOrNull()
                    if (parsed != null) {
                        App.instance.tunnelManager.connect(parsed)
                    }
                }
            }
            ACTION_DISCONNECT -> {
                App.instance.tunnelManager.disconnect()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_STICKY
    }

    private fun acquireWakeLocks() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AWGMutator:VpnWakeLock")?.apply {
                acquire(10 * 60 * 60 * 1000L /* 10 hours max safety timeout */)
            }
        }
        if (wifiLock == null) {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifiLock = wm?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "AWGMutator:VpnWifiLock")?.apply {
                acquire()
            }
        }
    }

    private fun releaseWakeLocks() {
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
            wakeLock = null
        }
        runCatching {
            if (wifiLock?.isHeld == true) wifiLock?.release()
            wifiLock = null
        }
    }

    private fun startTunnel(rawConfig: AwgConfig) {
        acquireWakeLocks()
        App.instance.tunnelManager.log("VPN_ROUTING", "Starting TUN initialization for '${rawConfig.name}'")

        val config = rawConfig
        App.instance.tunnelManager.log(
            "ANTI_DPI",
            "Obfuscation parameters: Jc=${config.jc}, Jmin=${config.jmin}, Jmax=${config.jmax}, S1=${config.s1}, S2=${config.s2}, H1=${config.h1}, H2=${config.h2}, H3=${config.h3}, H4=${config.h4}"
        )

        val notification = createNotification("Connecting to ${config.name} (Anti-DPI)...")
        startForeground(NOTIFICATION_ID, notification)

        App.instance.tunnelManager.updateStatus(
            VpnStatus(
                state = VpnState.CONNECTING,
                activeConfigName = config.name,
                activeConfigId = config.id,
                endpoint = config.endpoint
            )
        )

        try {
            val effectiveMtu = config.mtu.coerceIn(1280, 1420)
            val builder = Builder()
                .setSession("AWGMutator - ${config.name}")
                .setMtu(effectiveMtu)
                .setBlocking(true)

            App.instance.tunnelManager.log("VPN_ROUTING", "TUN MTU configured: $effectiveMtu bytes (Blocking I/O: true)")

            // 1. Assign Interface IP Addresses
            configureAddresses(builder, config.address)

            // 2. Configure DNS Servers
            configureDnsServers(builder, config.dns)

            // 3. Routing Table Modifications (AllowedIPs / Default Gateway Routes)
            configureRoutes(builder, config.allowedIps)

            // 4. Exclude AWGMutator app itself to prevent socket routing feedback loops
            runCatching {
                builder.addDisallowedApplication(packageName)
                App.instance.tunnelManager.log("VPN_ROUTING", "Disallowed self-package '$packageName' to prevent TUN routing loop")
            }

            // 5. Apply Split-Tunneling Routing Policies
            applySplitTunneling(builder)

            // 6. Bind Underlying Network to active physical connection (Android 9+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                setUnderlyingNetworks(null) // null tells OS to automatically follow default physical network
                App.instance.tunnelManager.log("VPN_ROUTING", "Underlying network policy bound to active system default adapter")
            }

            vpnInterface = builder.establish()

            if (vpnInterface != null) {
                onTunnelEstablished(config, vpnInterface!!.fileDescriptor)
            } else {
                App.instance.tunnelManager.log("VPN_ERROR", "Failed to establish TUN interface (builder.establish() returned null)")
                stopTunnel("Failed to establish TUN interface")
            }
        } catch (e: Exception) {
            App.instance.tunnelManager.log("VPN_ERROR", "TUN Initialization Exception: ${e.message}")
            stopTunnel("VPN Start Error: ${e.message}")
        }
    }

    private fun configureAddresses(builder: Builder, addressString: String) {
        val addresses = addressString.split(",").map { it.trim() }
        var hasIpv4Address = false
        var hasIpv6Address = false

        for (addr in addresses) {
            if (addr.isBlank()) continue
            val parts = addr.split("/")
            val ipStr = parts[0].trim()
            val prefix = if (parts.size > 1) parts[1].trim().toIntOrNull() ?: if (ipStr.contains(":")) 64 else 32 else if (ipStr.contains(":")) 64 else 32
            runCatching {
                val inet = InetAddress.getByName(ipStr)
                builder.addAddress(inet, prefix)
                if (ipStr.contains(":")) hasIpv6Address = true else hasIpv4Address = true
                App.instance.tunnelManager.log("VPN_ROUTING", "Assigned TUN Address: $ipStr/$prefix")
            }
        }

        if (!hasIpv4Address) {
            runCatching {
                builder.addAddress(InetAddress.getByName("10.2.0.2"), 32)
                App.instance.tunnelManager.log("VPN_ROUTING", "Assigned default fallback IPv4: 10.2.0.2/32")
            }
        }
        if (!hasIpv6Address) {
            runCatching {
                builder.addAddress(InetAddress.getByName("fd00:1:1::2"), 64)
                App.instance.tunnelManager.log("VPN_ROUTING", "Assigned default fallback IPv6: fd00:1:1::2/64")
            }
        }
    }

    private fun configureDnsServers(builder: Builder, dnsString: String) {
        val dnsServers = dnsString.split(",").map { it.trim() }.filter { it.isNotBlank() }
        if (dnsServers.isNotEmpty()) {
            for (dns in dnsServers) {
                runCatching {
                    builder.addDnsServer(InetAddress.getByName(dns))
                    App.instance.tunnelManager.log("VPN_ROUTING", "Configured DNS Server: $dns")
                }
            }
        } else {
            runCatching {
                builder.addDnsServer(InetAddress.getByName("1.1.1.1"))
                builder.addDnsServer(InetAddress.getByName("8.8.8.8"))
                App.instance.tunnelManager.log("VPN_ROUTING", "Configured default DNS Servers: 1.1.1.1, 8.8.8.8")
            }
        }
    }

    private fun configureRoutes(builder: Builder, allowedIpsString: String) {
        val routes = allowedIpsString.split(",").map { it.trim() }.filter { it.isNotBlank() }
        if (routes.isEmpty() || routes.any { it == "0.0.0.0/0" }) {
            runCatching {
                builder.addRoute(InetAddress.getByName("0.0.0.0"), 0)
                App.instance.tunnelManager.log("VPN_ROUTING", "Route Table: Added IPv4 default route 0.0.0.0/0 -> TUN")
            }
            runCatching {
                builder.addRoute(InetAddress.getByName("::"), 0)
                App.instance.tunnelManager.log("VPN_ROUTING", "Route Table: Added IPv6 default route ::/0 -> TUN")
            }
        } else {
            for (cidr in routes) {
                val parts = cidr.split("/")
                if (parts.isNotEmpty()) {
                    val ip = parts[0].trim()
                    val mask = if (parts.size > 1) parts[1].trim().toIntOrNull() ?: if (ip.contains(":")) 64 else 32 else if (ip.contains(":")) 64 else 32
                    runCatching {
                        builder.addRoute(InetAddress.getByName(ip), mask)
                        App.instance.tunnelManager.log("VPN_ROUTING", "Route Table: Added custom route $ip/$mask -> TUN")
                    }
                }
            }
        }
    }

    private fun applySplitTunneling(builder: Builder) {
        val splitManager = App.instance.splitTunnelManager
        val selectedApps = splitManager.getSelectedPackages()

        when (splitManager.mode) {
            SplitTunnelMode.ONLY_SELECTED_THROUGH_VPN -> {
                App.instance.tunnelManager.log("VPN_ROUTING", "Split-Tunnel Policy: Whitelist (${selectedApps.size} apps routed via VPN)")
                for (pkg in selectedApps) {
                    runCatching {
                        builder.addAllowedApplication(pkg)
                        App.instance.tunnelManager.log("VPN_ROUTING", "  -> Allowed App: $pkg")
                    }
                }
            }
            SplitTunnelMode.ALL_EXCEPT_SELECTED -> {
                App.instance.tunnelManager.log("VPN_ROUTING", "Split-Tunnel Policy: Blacklist (${selectedApps.size} apps bypassing VPN)")
                for (pkg in selectedApps) {
                    runCatching {
                        builder.addDisallowedApplication(pkg)
                        App.instance.tunnelManager.log("VPN_ROUTING", "  -> Bypassed App: $pkg")
                    }
                }
            }
            SplitTunnelMode.ALL_THROUGH_VPN -> {
                App.instance.tunnelManager.log("VPN_ROUTING", "Split-Tunnel Policy: Full Tunnel (All device apps routed through VPN)")
            }
        }
    }

    private fun onTunnelEstablished(config: AwgConfig, fd: java.io.FileDescriptor) {
        App.instance.tunnelManager.log("VPN_LIFECYCLE", "TUN interface established successfully! (Descriptor: $fd)")
        updateNotification("Active: ${config.name} [Anti-DPI Guard]")

        // Start User-space packet router with diagnostic inspection
        packetRouter?.stop()
        packetRouter = TunPacketRouter(
            vpnService = this,
            fileDescriptor = fd,
            config = config,
            onTrafficUpdate = { rx, tx ->
                App.instance.tunnelManager.updateBytes(rx, tx)
            }
        )
        packetRouter?.start()
        App.instance.tunnelManager.log("VPN_ROUTING", "Packet router worker thread active, listening for TUN IPv4/IPv6 frames")

        // Start Per-App traffic monitoring
        App.instance.appTrafficTracker.startTracking()

        // Mark VPN as CONNECTED
        App.instance.tunnelManager.updateStatus(
            VpnStatus(
                state = VpnState.CONNECTED,
                activeConfigName = config.name,
                activeConfigId = config.id,
                endpoint = config.endpoint,
                connectedSince = System.currentTimeMillis()
            )
        )

        // Perform real egress and internet exit reachability check in background
        serviceScope.launch {
            App.instance.tunnelManager.log("VPN_VERIFY", "Verifying egress public IP and tunnel connectivity...")
            val egress = App.instance.networkEgressVerifier.verifyEgress()
            val pingResult = App.instance.pingTester.testEndpoint(config.endpoint)

            App.instance.tunnelManager.log(
                "VPN_VERIFY",
                "Egress probe completed: Public IP=${egress.publicIp ?: "N/A"}, Country=${egress.countryCode ?: "N/A"}, Latency=${egress.latencyMs ?: pingResult.latencyMs}ms, Functional=${egress.isFunctional}"
            )

            val current = App.instance.tunnelManager.status.value
            if (current.state == VpnState.CONNECTED) {
                App.instance.tunnelManager.updateStatus(
                    current.copy(
                        egressIp = egress.publicIp,
                        egressCountry = egress.countryCode,
                        isEgressVerified = egress.isFunctional,
                        currentPingMs = egress.latencyMs ?: pingResult.latencyMs
                    )
                )
                if (egress.isFunctional) {
                    updateNotification("Connected: ${config.name} (${egress.publicIp ?: "Online"})")
                }
            }
        }
    }

    private fun stopTunnel(errorMsg: String?) {
        App.instance.tunnelManager.log("VPN_LIFECYCLE", "Stopping VPN tunnel. Reason: ${errorMsg ?: "User disconnect"}")
        App.instance.appTrafficTracker.stopTracking()
        packetRouter?.stop()
        packetRouter = null

        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (_: Exception) {}

        releaseWakeLocks()

        App.instance.tunnelManager.updateStatus(
            VpnStatus(
                state = if (errorMsg != null) VpnState.ERROR else VpnState.DISCONNECTED,
                errorMessage = errorMsg
            )
        )

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        App.instance.tunnelManager.log("VPN_LIFECYCLE", "AwgVpnService onDestroy")
        if (activeService == this) {
            activeService = null
        }
        serviceScope.cancel()
        stopTunnel(null)
    }

    private fun createNotification(text: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, App.CHANNEL_VPN_STATUS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("AWGMutator Anti-DPI VPN")
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val manager = getSystemService(NOTIFICATION_SERVICE) as? android.app.NotificationManager
        manager?.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val ACTION_CONNECT = "com.example.vpn.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.example.vpn.ACTION_DISCONNECT"
        const val EXTRA_CONFIG_RAW = "extra_config_raw"
        const val EXTRA_CONFIG_NAME = "extra_config_name"
        const val EXTRA_CONFIG_ID = "extra_config_id"
        const val NOTIFICATION_ID = 1001

        @Volatile
        var activeService: AwgVpnService? = null

        fun protectSocket(socket: Socket): Boolean {
            val res = activeService?.protect(socket) ?: false
            if (res) {
                App.instance.tunnelManager.log("SOCKET_PROTECT", "Protected TCP Socket fd -> bypasses TUN")
            }
            return res
        }

        fun protectDatagramSocket(socket: DatagramSocket): Boolean {
            val res = activeService?.protect(socket) ?: false
            if (res) {
                App.instance.tunnelManager.log("SOCKET_PROTECT", "Protected UDP DatagramSocket fd -> bypasses TUN")
            }
            return res
        }
    }
}
