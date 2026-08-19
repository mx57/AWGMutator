package com.example.vpn

import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.domain.model.AwgConfig
import com.example.domain.model.VpnState
import com.example.domain.model.VpnStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TunnelManager(private val context: Context) {

    private val _status = MutableStateFlow(VpnStatus())
    val status: StateFlow<VpnStatus> = _status.asStateFlow()

    fun updateStatus(newStatus: VpnStatus) {
        _status.value = newStatus
    }

    fun updateBytes(rx: Long, tx: Long) {
        _status.value = _status.value.copy(rxBytes = rx, txBytes = tx)
    }

    fun connect(config: AwgConfig) {
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
        val intent = Intent(context, AwgVpnService::class.java).apply {
            action = AwgVpnService.ACTION_DISCONNECT
        }
        context.startService(intent)
    }
}
