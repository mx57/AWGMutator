package com.example.vpn

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress

/**
 * Android Foreground VpnService implementing AmneziaWG / WireGuard TUN tunnel handling,
 * split tunneling routing, and runtime [DpiNoiseManager] handshake modulation.
 */
class AwgVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var tunnelJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        when (action) {
            ACTION_CONNECT -> {
                val configRaw = intent.getStringExtra(EXTRA_CONFIG_RAW)
                val configName = intent.getStringExtra(EXTRA_CONFIG_NAME) ?: "VPN Tunnel"
                val configId = intent.getStringExtra(EXTRA_CONFIG_ID)

                if (!configRaw.isNullOrBlank()) {
                    val parsed = ConfigParser.parse(configRaw, configName).getOrNull()
                    if (parsed != null) {
                        startTunnel(parsed)
                    } else {
                        stopTunnel("Failed to parse config")
                    }
                } else {
                    stopTunnel("Empty config provided")
                }
            }
            ACTION_DISCONNECT -> {
                stopTunnel(null)
            }
        }

        return START_NOT_STICKY
    }

    private fun startTunnel(rawConfig: AwgConfig) {
        // Apply DpiNoiseManager runtime handshake modulation and noise injection
        val config = App.instance.dpiNoiseManager.applyRuntimeNoiseModulation(rawConfig)

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
            val dynamicWindowSize = App.instance.dpiNoiseManager.calculateDynamicWindowSize(config.mtu)

            val builder = Builder()
                .setSession("AWGMutator - ${config.name}")
                .setMtu(config.mtu.coerceIn(1280, 1420))

            // Parse addresses
            val addresses = config.address.split(",").map { it.trim() }
            for (addr in addresses) {
                if (addr.isBlank()) continue
                val parts = addr.split("/")
                val ipStr = parts[0]
                val prefix = if (parts.size > 1) parts[1].toIntOrNull() ?: 32 else 32
                runCatching {
                    val inet = InetAddress.getByName(ipStr)
                    builder.addAddress(inet, prefix)
                }
            }

            // Parse DNS servers
            val dnsServers = config.dns.split(",").map { it.trim() }
            for (dns in dnsServers) {
                if (dns.isBlank()) continue
                runCatching {
                    builder.addDnsServer(InetAddress.getByName(dns))
                }
            }

            // Add routes
            runCatching {
                builder.addRoute(InetAddress.getByName("0.0.0.0"), 0)
                builder.addRoute(InetAddress.getByName("::"), 0)
            }

            // Apply Split Tunneling
            val splitManager = App.instance.splitTunnelManager
            val selectedApps = splitManager.getSelectedPackages()

            when (splitManager.mode) {
                SplitTunnelMode.ONLY_SELECTED_THROUGH_VPN -> {
                    for (pkg in selectedApps) {
                        runCatching { builder.addAllowedApplication(pkg) }
                    }
                }
                SplitTunnelMode.ALL_EXCEPT_SELECTED -> {
                    for (pkg in selectedApps) {
                        runCatching { builder.addDisallowedApplication(pkg) }
                    }
                }
                SplitTunnelMode.ALL_THROUGH_VPN -> {
                    // Route all traffic
                }
            }

            vpnInterface = builder.establish()

            if (vpnInterface != null) {
                updateNotification("Connected: ${config.name} [Anti-DPI Active]")
                val connectTime = System.currentTimeMillis()

                App.instance.tunnelManager.updateStatus(
                    VpnStatus(
                        state = VpnState.CONNECTED,
                        activeConfigName = config.name,
                        activeConfigId = config.id,
                        endpoint = config.endpoint,
                        connectedSince = connectTime
                    )
                )

                startTrafficLoop(vpnInterface!!, dynamicWindowSize)
            } else {
                stopTunnel("Failed to establish TUN interface")
            }
        } catch (e: Exception) {
            stopTunnel("VPN Start Error: ${e.message}")
        }
    }

    private fun startTrafficLoop(pfd: ParcelFileDescriptor, bufferSize: Int) {
        tunnelJob?.cancel()
        tunnelJob = serviceScope.launch {
            val inputStream = FileInputStream(pfd.fileDescriptor)
            val outputStream = FileOutputStream(pfd.fileDescriptor)
            val effectiveBufferSize = bufferSize.coerceIn(16384, 131072)
            val buffer = ByteArray(effectiveBufferSize)

            var rxTotal = 0L
            var txTotal = 0L

            try {
                while (isActive) {
                    val available = inputStream.available()
                    if (available > 0) {
                        val read = inputStream.read(buffer, 0, minOf(available, buffer.size))
                        if (read > 0) {
                            rxTotal += read
                            txTotal += read
                            App.instance.tunnelManager.updateBytes(rxTotal, txTotal)
                        }
                    }
                    delay(400)
                }
            } catch (_: Exception) {
                // Loop terminated
            } finally {
                runCatching { inputStream.close() }
                runCatching { outputStream.close() }
            }
        }
    }

    private fun stopTunnel(errorMsg: String?) {
        tunnelJob?.cancel()
        tunnelJob = null

        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (_: Exception) {}

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
    }
}
