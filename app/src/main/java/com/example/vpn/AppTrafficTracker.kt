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
import com.example.domain.model.AppConnectionStatus
import com.example.domain.model.AppTrafficStat
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
    val isSystem: Boolean
)

/**
 * Per-App Network Traffic Tracker utilizing Android [NetworkStatsManager] and [TrafficStats]
 * to accurately capture and display real-time byte consumption and transfer speeds.
 * Only applications that have actually consumed traffic (> 0 KB) or have active transfer speeds are reported.
 */
class AppTrafficTracker(
    private val context: Context,
    private val splitTunnelManager: SplitTunnelManager
) {
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var trackingJob: Job? = null

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
        // Start background polling immediately
        startTracking()
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
                sampleTraffic()
                delay(1500)
            }
        }
    }

    fun stopTracking() {
        // We keep tracking active for live dashboard telemetry
    }

    private fun cacheInstalledApps() {
        try {
            val pm = context.packageManager
            val installedApps = pm.getInstalledApplications(0)
            for (appInfo in installedApps) {
                val pkg = appInfo.packageName ?: continue
                if (pkg == context.packageName) continue

                val appName = try {
                    pm.getApplicationLabel(appInfo).toString()
                } catch (_: Exception) {
                    formatAppName(pkg)
                }
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                appMetaCache[appInfo.uid] = CachedAppMeta(
                    uid = appInfo.uid,
                    packageName = pkg,
                    appName = if (appName.isNotBlank()) appName else formatAppName(pkg),
                    isSystem = isSystem
                )
            }
        } catch (_: Exception) {
            // Package manager query error fallback
        }
    }

    private fun resolveAppMeta(uid: Int): CachedAppMeta {
        appMetaCache[uid]?.let { return it }

        val pm = context.packageManager
        val packages = try {
            pm.getPackagesForUid(uid)
        } catch (_: Exception) {
            null
        }

        val pkgName = packages?.firstOrNull() ?: "uid.$uid"
        val (appName, isSystem) = try {
            val appInfo = pm.getApplicationInfo(pkgName, 0)
            val label = pm.getApplicationLabel(appInfo).toString()
            val system = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            Pair(if (label.isNotBlank()) label else formatAppName(pkgName), system)
        } catch (_: Exception) {
            Pair(formatAppName(pkgName), false)
        }

        val meta = CachedAppMeta(
            uid = uid,
            packageName = pkgName,
            appName = appName,
            isSystem = isSystem
        )
        appMetaCache[uid] = meta
        return meta
    }

    private fun formatAppName(packageName: String): String {
        return when {
            packageName.contains("youtube", ignoreCase = true) -> "YouTube"
            packageName.contains("chrome", ignoreCase = true) -> "Google Chrome"
            packageName.contains("telegram", ignoreCase = true) -> "Telegram"
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
        val uids = appMetaCache.keys().toList()
        for (uid in uids) {
            val (rx, tx) = queryRawByteCounters(uid)
            if (rx > 0L) baselineRxMap[uid] = rx
            if (tx > 0L) baselineTxMap[uid] = tx
        }
    }

    suspend fun refreshOnce() = withContext(Dispatchers.IO) {
        checkPermission()
        sampleTraffic()
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
     * Queries all active network buckets via NetworkStatsManager across Wi-Fi, Mobile, VPN, and Ethernet.
     */
    private fun queryNetworkStatsManagerAll(): Map<Int, Pair<Long, Long>> {
        val nsm = networkStatsManager ?: return emptyMap()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return emptyMap()
        if (!hasUsageStatsPermission(context)) return emptyMap()

        val results = HashMap<Int, Pair<Long, Long>>()
        val now = System.currentTimeMillis()
        // Query from session start or past 24 hours to capture all recent active traffic
        val startTime = (sessionStartTime - 3600_000L).coerceAtLeast(0L)

        val networkTypes = listOf(
            ConnectivityManager.TYPE_WIFI,
            ConnectivityManager.TYPE_MOBILE,
            ConnectivityManager.TYPE_VPN,
            9 // ConnectivityManager.TYPE_ETHERNET
        )

        for (netType in networkTypes) {
            try {
                val stats = nsm.querySummary(netType, null, startTime, now)
                val bucket = NetworkStats.Bucket()
                while (stats.hasNextBucket()) {
                    stats.getNextBucket(bucket)
                    val uid = bucket.uid
                    // Skip system root / invalid / our own app UID
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
                // Ignore per-interface errors
            }
        }
        return results
    }

    private fun sampleTraffic() {
        if (appMetaCache.isEmpty()) {
            cacheInstalledApps()
        }

        val currentTime = System.currentTimeMillis()
        val deltaSeconds = ((currentTime - lastSampleTime) / 1000.0).coerceIn(0.4, 5.0)

        val splitMode = splitTunnelManager.mode
        val selectedPackages = splitTunnelManager.getSelectedPackages()

        // 1. Gather stats from NetworkStatsManager
        val nsmMap = queryNetworkStatsManagerAll()

        // 2. Discover all candidate UIDs (from NSM results, previously active UIDs, and cached apps)
        val candidateUids = mutableSetOf<Int>()
        candidateUids.addAll(nsmMap.keys)
        candidateUids.addAll(previousRxMap.keys)
        for ((uid, _) in appMetaCache) {
            candidateUids.add(uid)
        }

        val list = mutableListOf<AppTrafficStat>()

        for (uid in candidateUids) {
            if (uid == Process.myUid()) continue

            val (trafficRx, trafficTx) = queryRawByteCounters(uid)
            val (nsmRx, nsmTx) = nsmMap[uid] ?: Pair(0L, 0L)

            // Select the most comprehensive counter available
            val rawRx = maxOf(trafficRx, nsmRx)
            val rawTx = maxOf(trafficTx, nsmTx)

            val baseRx = baselineRxMap[uid] ?: 0L
            val baseTx = baselineTxMap[uid] ?: 0L

            val sessionRx = if (baseRx in 1..rawRx) (rawRx - baseRx) else rawRx
            val sessionTx = if (baseTx in 1..rawTx) (rawTx - baseTx) else rawTx

            val prevRx = previousRxMap[uid] ?: rawRx
            val prevTx = previousTxMap[uid] ?: rawTx

            val rxDelta = (rawRx - prevRx).coerceAtLeast(0L)
            val txDelta = (rawTx - prevTx).coerceAtLeast(0L)

            val rxSpeed = (rxDelta / deltaSeconds).toLong()
            val txSpeed = (txDelta / deltaSeconds).toLong()

            previousRxMap[uid] = rawRx
            previousTxMap[uid] = rawTx

            val totalBytes = maxOf(sessionRx + sessionTx, rawRx + rawTx)
            val totalSpeed = rxSpeed + txSpeed

            // STRICT FILTER: Only include apps that actually generated traffic (> 0 KB / >= 1024 Bytes) or have live speed
            if (totalBytes >= 1024L || totalSpeed > 0L) {
                val appMeta = resolveAppMeta(uid)
                val isSelected = selectedPackages.contains(appMeta.packageName)

                val status = when (splitMode) {
                    SplitTunnelMode.ALL_THROUGH_VPN -> AppConnectionStatus.ROUTED_VIA_VPN
                    SplitTunnelMode.ONLY_SELECTED_THROUGH_VPN -> {
                        if (isSelected) AppConnectionStatus.ROUTED_VIA_VPN else AppConnectionStatus.BYPASS_DIRECT
                    }
                    SplitTunnelMode.ALL_EXCEPT_SELECTED -> {
                        if (isSelected) AppConnectionStatus.BYPASS_DIRECT else AppConnectionStatus.ROUTED_VIA_VPN
                    }
                }

                list.add(
                    AppTrafficStat(
                        packageName = appMeta.packageName,
                        appName = appMeta.appName,
                        uid = uid,
                        rxBytes = sessionRx.coerceAtLeast(rxDelta),
                        txBytes = sessionTx.coerceAtLeast(txDelta),
                        totalBytes = totalBytes,
                        rxSpeedBytesPerSec = rxSpeed,
                        txSpeedBytesPerSec = txSpeed,
                        connectionStatus = status,
                        isSystemApp = appMeta.isSystem,
                        lastActiveTime = currentTime
                    )
                )
            }
        }

        lastSampleTime = currentTime

        // Sort: Active live speed first, then highest total volume, then app name
        val sorted = list.sortedWith(
            compareByDescending<AppTrafficStat> { it.rxSpeedBytesPerSec + it.txSpeedBytesPerSec }
                .thenByDescending { it.totalBytes }
                .thenBy { it.appName.lowercase() }
        )

        _appStats.value = sorted
    }
}
