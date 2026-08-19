package com.example.domain.model

import android.graphics.drawable.Drawable

/**
 * Connection status of an installed application relative to the active VPN / Root configuration.
 */
enum class AppConnectionStatus {
    ROUTED_VIA_VPN,  // 🛡️ All traffic routes securely through the active AmneziaWG / WARP config
    BYPASS_DIRECT,   // 🌐 Bypassing VPN tunnel (direct ISP route via Split Tunneling)
    BLOCKED          // ⛔ Network access restricted / disallowed
}

/**
 * Real-time traffic statistics and connection routing state for an application.
 */
data class AppTrafficStat(
    val packageName: String,
    val appName: String,
    val uid: Int,
    val icon: Drawable? = null,
    val rxBytes: Long = 0L,
    val txBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val rxSpeedBytesPerSec: Long = 0L,
    val txSpeedBytesPerSec: Long = 0L,
    val connectionStatus: AppConnectionStatus = AppConnectionStatus.ROUTED_VIA_VPN,
    val isSystemApp: Boolean = false,
    val lastActiveTime: Long = System.currentTimeMillis()
)
