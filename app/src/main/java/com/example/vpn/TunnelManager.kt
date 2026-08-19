package com.example.vpn

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.domain.model.AwgConfig
import com.example.domain.model.VpnState
import com.example.domain.model.VpnStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Manages the high-level VPN tunnel lifecycle, status broadcasts, and packet routing diagnostics log.
 */
class TunnelManager(private val context: Context) {

    private val _status = MutableStateFlow(VpnStatus())
    val status: StateFlow<VpnStatus> = _status.asStateFlow()

    private val maxLogs = 100
    private val logQueue = ConcurrentLinkedDeque<String>()
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

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
        log("TUN_LIFECYCLE", "Initiating VPN service start for config '${config.name}' (Endpoint: ${config.endpoint})")
        val intent = Intent(context, AwgVpnService::class.java).apply {
            action = AwgVpnService.ACTION_CONNECT
            putExtra(AwgVpnService.EXTRA_CONFIG_RAW, config.toConfString())
            putExtra(AwgVpnService.EXTRA_CONFIG_NAME, config.name)
            putExtra(AwgVpnService.EXTRA_CONFIG_ID, config.id)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun disconnect() {
        log("TUN_LIFECYCLE", "Initiating VPN service disconnect request")
        val intent = Intent(context, AwgVpnService::class.java).apply {
            action = AwgVpnService.ACTION_DISCONNECT
        }
        context.startService(intent)
    }
}
