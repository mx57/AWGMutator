package com.example.vpn

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.TrafficStats
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import com.example.App
import com.example.domain.model.AppConnectionStatus
import com.example.domain.model.AppTrafficStat
import com.example.domain.model.VpnState
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
import java.util.concurrent.ConcurrentHashMap

data class CachedAppMeta(
    val uid: Int,
    val packageName: String,
    val appName: String,
    val isSystem: Boolean,
    val isLabelResolved: Boolean = false
)

/**
 * Per-App Network Traffic Tracker for the active VPN Tunnel.
 * Captures and displays real-time byte consumption and speeds ONLY for traffic passing through the VPN.
 * If the tunnel is not connected or no traffic passes through it, no apps are displayed.
 * Includes a persistent enable/disable toggle.
 */
class AppTrafficTracker(
    private val context: Context,
    private val splitTunnelManager: SplitTunnelManager
) {
    private val prefs = context.getSharedPreferences("app_traffic_tracker_prefs", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var trackingJob: Job? = null

    private val _isMonitoringEnabled = MutableStateFlow(prefs.getBoolean("is_monitoring_enabled", true))
    val isMonitoringEnabled: StateFlow<Boolean> = _isMonitoringEnabled.asStateFlow()

    private val _appStats = MutableStateFlow<List<AppTrafficStat>>(emptyList())
    val appStats: StateFlow<List<AppTrafficStat>> = _appStats.asStateFlow()

    private val _hasUsagePermission = MutableStateFlow(hasUsageStatsPermission(context))
    val hasUsagePermission: StateFlow<Boolean> = _hasUsagePermission.asStateFlow()

    private val appMetaCache = ConcurrentHashMap<Int, CachedAppMeta>()
    private val previousRxMap = ConcurrentHashMap<Int, Long>()
    private val previousTxMap = ConcurrentHashMap<Int, Long>()
    private val baselineRxMap = ConcurrentHashMap<Int, Long>()
    private val baselineTxMap = ConcurrentHashMap<Int, Long>()

    private var lastSampleTime = System.currentTimeMillis()
    private var sessionStartTime = System.currentTimeMillis()

    init {
        if (_isMonitoringEnabled.value) {
            startTracking()
        }
    }

    fun setMonitoringEnabled(enabled: Boolean) {
        _isMonitoringEnabled.value = enabled
        prefs.edit().putBoolean("is_monitoring_enabled", enabled).apply()
        if (enabled) {
            startTracking()
        } else {
            stopTracking()
            _appStats.value = emptyList()
        }
    }

    fun checkPermission(): Boolean {
        val granted = hasUsageStatsPermission(context)
        _hasUsagePermission.value = granted
        return granted
    }

    companion object {
        fun hasUsageStatsPermission(context: Context): Boolean {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            }
            return mode == AppOpsManager.MODE_ALLOWED
        }

        fun openUsageAccessSettings(context: Context) {
            try {
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {}
            }
        }
    }

    private val networkStatsManager: NetworkStatsManager? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager
        } else {
            null
        }
    }

    fun startTracking() {
        if (trackingJob?.isActive == true) return
        sessionStartTime = System.currentTimeMillis()
        cacheInstalledApps()
        resetSessionBaseline()
        trackingJob = scope.launch {
            while (isActive) {
                sampleTunnelTraffic()
                delay(1500)
            }
        }
    }

    fun stopTracking() {
        trackingJob?.cancel()
        trackingJob = null
    }

    private fun cacheInstalledApps() {
        try {
            val pm = context.packageManager
            val installedApps = pm.getInstalledApplications(0)
            for (appInfo in installedApps) {
                val pkg = appInfo.packageName ?: continue
                if (pkg == context.packageName) continue

                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val existing = appMetaCache[appInfo.uid]

                appMetaCache[appInfo.uid] = CachedAppMeta(
                    uid = appInfo.uid,
                    packageName = pkg,
                    appName = existing?.appName ?: "",
                    isSystem = isSystem
                )
            }
        } catch (_: Exception) {
            // Fallback
        }
    }

    private fun resolveAppMeta(uid: Int): CachedAppMeta {
        val existing = appMetaCache[uid]
        if (existing != null) return existing

        val pm = context.packageManager
        val pkgName = try {
            pm.getPackagesForUid(uid)?.firstOrNull() ?: "uid.$uid"
        } catch (_: Exception) {
            "uid.$uid"
        }

        val isSystem = try {
            val appInfo = pm.getApplicationInfo(pkgName, 0)
            (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        } catch (_: Exception) {
            false
        }

        val meta = CachedAppMeta(
            uid = uid,
            packageName = pkgName,
            appName = "",
            isSystem = isSystem
        )
        appMetaCache[uid] = meta
        return meta
    }

    private fun ensureAppNameResolved(meta: CachedAppMeta): CachedAppMeta {
        if (meta.appName.isNotBlank()) return meta

        val resolvedName = fetchAppName(meta.packageName)
        val updated = meta.copy(
            appName = if (resolvedName.isNotBlank()) resolvedName else formatAppName(meta.packageName)
        )
        appMetaCache[meta.uid] = updated
        return updated
    }

    private fun fetchAppName(packageName: String): String {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val label = pm.getApplicationLabel(appInfo).toString()
            if (label.isNotBlank()) label else formatAppName(packageName)
        } catch (_: Exception) {
            formatAppName(packageName)
        }
    }

    private fun formatAppName(packageName: String): String {
        return when {
            packageName.contains("youtube", ignoreCase = true) -> "YouTube"
            packageName.contains("telegram", ignoreCase = true) -> "Telegram"
            packageName.contains("chrome", ignoreCase = true) -> "Google Chrome"
            packageName.contains("whatsapp", ignoreCase = true) -> "WhatsApp"
            packageName.contains("instagram", ignoreCase = true) -> "Instagram"
            packageName.contains("spotify", ignoreCase = true) -> "Spotify"
            packageName.contains("discord", ignoreCase = true) -> "Discord"
            packageName.contains("tiktok", ignoreCase = true) || packageName.contains("musically", ignoreCase = true) -> "TikTok"
            packageName.contains("vending", ignoreCase = true) -> "Google Play Store"
            packageName.contains("gms", ignoreCase = true) -> "Google Play Services"
            packageName.contains("browser", ignoreCase = true) -> "Browser"
            packageName.startsWith("uid.") -> "System Service (${packageName.substringAfter("uid.")})"
            else -> packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }

    fun resetSessionBaseline() {
        sessionStartTime = System.currentTimeMillis()
        previousRxMap.clear()
        previousTxMap.clear()
        baselineRxMap.clear()
        baselineTxMap.clear()

        val uids = appMetaCache.keys().toList()
        for (uid in uids) {
            val (rx, tx) = queryRawByteCounters(uid)
            if (rx > 0L) baselineRxMap[uid] = rx
            if (tx > 0L) baselineTxMap[uid] = tx
        }
    }

    suspend fun refreshOnce() = withContext(Dispatchers.IO) {
        checkPermission()
        sampleTunnelTraffic()
    }

    /**
     * Reads direct cumulative bytes from TrafficStats for the given UID.
     */
    private fun queryRawByteCounters(uid: Int): Pair<Long, Long> {
        val rx = TrafficStats.getUidRxBytes(uid)
        val tx = TrafficStats.getUidTxBytes(uid)
        val safeRx = if (rx == TrafficStats.UNSUPPORTED.toLong() || rx < 0L) 0L else rx
        val safeTx = if (tx == TrafficStats.UNSUPPORTED.toLong() || tx < 0L) 0L else tx
        return Pair(safeRx, safeTx)
    }

    /**
     * Queries VPN-only traffic buckets via NetworkStatsManager.
     */
    private fun queryVpnNetworkStats(): Map<Int, Pair<Long, Long>> {
        val nsm = networkStatsManager ?: return emptyMap()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return emptyMap()
        if (!hasUsageStatsPermission(context)) return emptyMap()

        val results = HashMap<Int, Pair<Long, Long>>()
        val now = System.currentTimeMillis()
        val startTime = sessionStartTime.coerceAtLeast(0L)

        try {
            // Query specifically for TYPE_VPN interface traffic
            val stats = nsm.querySummary(ConnectivityManager.TYPE_VPN, null, startTime, now)
            val bucket = NetworkStats.Bucket()
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                val uid = bucket.uid
                if (uid <= 0 || uid == Process.myUid()) continue

                val rx = bucket.rxBytes
                val tx = bucket.txBytes
                if (rx > 0L || tx > 0L) {
                    val current = results[uid] ?: Pair(0L, 0L)
                    results[uid] = Pair(current.first + rx, current.second + tx)
                }
            }
            stats.close()
        } catch (_: SecurityException) {
            _hasUsagePermission.value = false
        } catch (_: Exception) {
            // Ignore
        }
        return results
    }

    private fun sampleTunnelTraffic() {
        if (!_isMonitoringEnabled.value) {
            _appStats.value = emptyList()
            return
        }

        // Check if VPN is connected
        val isVpnConnected = try {
            App.instance.tunnelManager.status.value.state == VpnState.CONNECTED
        } catch (_: Exception) {
            false
        }

        // If VPN is not connected, do NOT display any app traffic (tunnel is inactive)
        if (!isVpnConnected) {
            _appStats.value = emptyList()
            return
        }

        if (appMetaCache.isEmpty()) {
            cacheInstalledApps()
        }

        val currentTime = System.currentTimeMillis()
        val deltaSeconds = ((currentTime - lastSampleTime) / 1000.0).coerceIn(0.4, 5.0)

        val splitMode = splitTunnelManager.mode
        val selectedPackages = splitTunnelManager.getSelectedPackages()

        // 1. Gather stats from VPN NetworkStatsManager
        val vpnNsmMap = queryVpnNetworkStats()

        // 2. Discover all candidate UIDs
        val candidateUids = mutableSetOf<Int>()
        candidateUids.addAll(vpnNsmMap.keys)
        candidateUids.addAll(previousRxMap.keys)
        for ((uid, _) in appMetaCache) {
            candidateUids.add(uid)
        }

        val list = mutableListOf<AppTrafficStat>()

        for (uid in candidateUids) {
            if (uid == Process.myUid()) continue

            val appMeta = resolveAppMeta(uid)
            val isSelected = selectedPackages.contains(appMeta.packageName)

            // Determine routing status through VPN
            val status = when (splitMode) {
                SplitTunnelMode.ALL_THROUGH_VPN -> AppConnectionStatus.ROUTED_VIA_VPN
                SplitTunnelMode.ONLY_SELECTED_THROUGH_VPN -> {
                    if (isSelected) AppConnectionStatus.ROUTED_VIA_VPN else AppConnectionStatus.BYPASS_DIRECT
                }
                SplitTunnelMode.ALL_EXCEPT_SELECTED -> {
                    if (isSelected) AppConnectionStatus.BYPASS_DIRECT else AppConnectionStatus.ROUTED_VIA_VPN
                }
            }

            // ONLY track apps that are routed through the VPN tunnel
            if (status != AppConnectionStatus.ROUTED_VIA_VPN) {
                continue
            }

            val (trafficRx, trafficTx) = queryRawByteCounters(uid)
            val (nsmRx, nsmTx) = vpnNsmMap[uid] ?: Pair(0L, 0L)

            val baseRx = baselineRxMap[uid] ?: 0L
            val baseTx = baselineTxMap[uid] ?: 0L

            // Calculate session bytes routed via VPN
            val sessionRx = if (nsmRx > 0L) {
                nsmRx
            } else if (baseRx > 0L && trafficRx >= baseRx) {
                trafficRx - baseRx
            } else {
                0L
            }

            val sessionTx = if (nsmTx > 0L) {
                nsmTx
            } else if (baseTx > 0L && trafficTx >= baseTx) {
                trafficTx - baseTx
            } else {
                0L
            }

            val prevRx = previousRxMap[uid] ?: sessionRx
            val prevTx = previousTxMap[uid] ?: sessionTx

            val rxDelta = (sessionRx - prevRx).coerceAtLeast(0L)
            val txDelta = (sessionTx - prevTx).coerceAtLeast(0L)

            val rxSpeed = (rxDelta / deltaSeconds).toLong()
            val txSpeed = (txDelta / deltaSeconds).toLong()

            previousRxMap[uid] = sessionRx
            previousTxMap[uid] = sessionTx

            val totalBytes = sessionRx + sessionTx
            val totalSpeed = rxSpeed + txSpeed

            // STRICT FILTER: Only include apps that actually generated traffic through the tunnel (> 0 KB / >= 1024 Bytes) or have active speed
            if (totalBytes >= 1024L || totalSpeed > 0L) {
                val resolvedMeta = ensureAppNameResolved(appMeta)
                list.add(
                    AppTrafficStat(
                        packageName = resolvedMeta.packageName,
                        appName = resolvedMeta.appName,
                        uid = uid,
                        rxBytes = sessionRx,
                        txBytes = sessionTx,
                        totalBytes = totalBytes,
                        rxSpeedBytesPerSec = rxSpeed,
                        txSpeedBytesPerSec = txSpeed,
                        connectionStatus = status,
                        isSystemApp = resolvedMeta.isSystem,
                        lastActiveTime = currentTime
                    )
                )
            }
        }

        lastSampleTime = currentTime

        // Sort: Active live speed first, then highest total session volume, then app name
        val sorted = list.sortedWith(
            compareByDescending<AppTrafficStat> { it.rxSpeedBytesPerSec + it.txSpeedBytesPerSec }
                .thenByDescending { it.totalBytes }
                .thenBy { it.appName.lowercase() }
        )

        _appStats.value = sorted
    }
}
