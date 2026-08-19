package com.example.domain.model

/**
 * State of the built-in VPN client tunnel.
 */
enum class VpnState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    ERROR
}

data class VpnStatus(
    val state: VpnState = VpnState.DISCONNECTED,
    val activeConfigName: String? = null,
    val activeConfigId: String? = null,
    val endpoint: String? = null,
    val rxBytes: Long = 0L,
    val txBytes: Long = 0L,
    val connectedSince: Long? = null,
    val currentPingMs: Long? = null,
    val isRootTunnel: Boolean = false,
    val egressIp: String? = null,
    val egressCountry: String? = null,
    val isEgressVerified: Boolean = false,
    val isWarpActive: Boolean = false,
    val errorMessage: String? = null
)
