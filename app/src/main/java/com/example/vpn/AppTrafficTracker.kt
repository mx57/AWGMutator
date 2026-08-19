package com.example.vpn

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.ConnectivityManager
import android.net.TrafficStats
import android.os.Build
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
 * Enhanced Per-App Network Traffic Tracker utilizing Android [NetworkStatsManager]
 * and real-time [TrafficStats] APIs to accurately capture and display byte consumption,
 * active upload/download bandwidth speeds, and tunnel routing status per package.
 */
class AppTrafficTracker(
    private val context: Context,
    private val splitTunnelManager: SplitTunnelManager
) {
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var trackingJob: Job? = null

    private val _appStats = MutableStateFlow<List<AppTrafficStat>>(emptyList())
    val appStats: StateFlow<List<AppTrafficStat>> = _appStats.asStateFlow()

    private val cachedApps = mutableListOf<CachedAppMeta>()
    private val previousRxMap = ConcurrentHashMap<Int, Long>()
    private val previousTxMap = ConcurrentHashMap<Int, Long>()
    private var lastSampleTime = System.currentTimeMillis()

    // Baseline captured when VPN starts to calculate session delta
    private val baselineRxMap = ConcurrentHashMap<Int, Long>()
    private val baselineTxMap = ConcurrentHashMap<Int, Long>()
    private var sessionStartTime = System.currentTimeMillis()

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
        ensureAppsCached()
        resetSessionBaseline()
        trackingJob = scope.launch {
            while (isActive) {
                sampleTraffic()
                delay(1200)
            }
        }
    }

    fun stopTracking() {
        trackingJob?.cancel()
        trackingJob = null
    }

    private fun ensureAppsCached() {
        if (cachedApps.isNotEmpty()) return
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val launcherApps = try {
            pm.queryIntentActivities(mainIntent, 0)
        } catch (_: Exception) {
            emptyList()
        }
        val seenPackages = mutableSetOf<String>()
        cachedApps.clear()

        for (resolveInfo in launcherApps) {
            val appInfo = resolveInfo.activityInfo?.applicationInfo ?: continue
            val pkg = appInfo.packageName ?: continue
            if (pkg == context.packageName || !seenPackages.add(pkg)) continue

            val appName = resolveInfo.activityInfo?.nonLocalizedLabel?.toString()
                ?: formatAppName(pkg)
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

            cachedApps.add(
                CachedAppMeta(
                    uid = appInfo.uid,
                    packageName = pkg,
                    appName = appName,
                    isSystem = isSystem
                )
            )
        }

        val extraPackages = splitTunnelManager.getSelectedPackages() + listOf(
            "com.google.android.youtube",
            "com.android.chrome",
            "org.telegram.messenger",
            "com.google.android.gms",
            "com.android.vending",
            "com.spotify.music",
            "com.instagram.android",
            "com.zhiliaoapp.musically",
            "com.discord",
            "com.whatsapp"
        )

        for (pkg in extraPackages) {
            if (seenPackages.add(pkg)) {
                val appInfo = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
                if (appInfo != null && appInfo.packageName != context.packageName) {
                    val appName = appInfo.nonLocalizedLabel?.toString() ?: formatAppName(pkg)
                    val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    cachedApps.add(
                        CachedAppMeta(
                            uid = appInfo.uid,
                            packageName = pkg,
                            appName = appName,
                            isSystem = isSystem
                        )
                    )
                }
            }
        }
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
            else -> packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }

    fun resetSessionBaseline() {
        sessionStartTime = System.currentTimeMillis()
        ensureAppsCached()
        for (app in cachedApps) {
            val (rx, tx) = queryAppByteCounters(app.uid)
            if (rx != TrafficStats.UNSUPPORTED.toLong() && rx >= 0L) {
                baselineRxMap[app.uid] = rx
            }
            if (tx != TrafficStats.UNSUPPORTED.toLong() && tx >= 0L) {
                baselineTxMap[app.uid] = tx
            }
        }
    }

    suspend fun refreshOnce() = withContext(Dispatchers.IO) {
        ensureAppsCached()
        sampleTraffic()
    }

    /**
     * Queries network traffic using [NetworkStatsManager] if supported and available,
     * seamlessly falling back to [TrafficStats] for real-time per-UID byte polling.
     */
    private fun queryAppByteCounters(uid: Int): Pair<Long, Long> {
        // Try NetworkStatsManager for detailed interface statistics
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && networkStatsManager != null) {
            val nsmResult = queryFromNetworkStatsManager(uid)
            if (nsmResult != null && (nsmResult.first > 0L || nsmResult.second > 0L)) {
                return nsmResult
            }
        }

        // Fallback to real-time TrafficStats UID counters
        val rx = TrafficStats.getUidRxBytes(uid)
        val tx = TrafficStats.getUidTxBytes(uid)
        val safeRx = if (rx == TrafficStats.UNSUPPORTED.toLong() || rx < 0L) 0L else rx
        val safeTx = if (tx == TrafficStats.UNSUPPORTED.toLong() || tx < 0L) 0L else tx
        return Pair(safeRx, safeTx)
    }

    private fun queryFromNetworkStatsManager(uid: Int): Pair<Long, Long>? {
        val nsm = networkStatsManager ?: return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null

        return try {
            val now = System.currentTimeMillis()
            val startTime = sessionStartTime.coerceAtMost(now - 1000)
            var rxSum = 0L
            var txSum = 0L
            val bucket = NetworkStats.Bucket()

            // Query VPN interface stats for this specific UID
            runCatching {
                val statsVpn = nsm.queryDetailsForUid(
                    ConnectivityManager.TYPE_VPN,
                    null,
                    startTime,
                    now,
                    uid
                )
                while (statsVpn.hasNextBucket()) {
                    statsVpn.getNextBucket(bucket)
                    rxSum += bucket.rxBytes
                    txSum += bucket.txBytes
                }
                statsVpn.close()
            }

            // Query default network interface if VPN bucket is empty
            if (rxSum == 0L && txSum == 0L) {
                runCatching {
                    val statsAll = nsm.queryDetailsForUid(
                        ConnectivityManager.TYPE_WIFI,
                        null,
                        startTime,
                        now,
                        uid
                    )
                    while (statsAll.hasNextBucket()) {
                        statsAll.getNextBucket(bucket)
                        rxSum += bucket.rxBytes
                        txSum += bucket.txBytes
                    }
                    statsAll.close()
                }
            }

            if (rxSum > 0L || txSum > 0L) Pair(rxSum, txSum) else null
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun sampleTraffic() {
        if (cachedApps.isEmpty()) {
            ensureAppsCached()
        }

        val currentTime = System.currentTimeMillis()
        val deltaSeconds = ((currentTime - lastSampleTime) / 1000.0).coerceAtLeast(0.4)

        val splitMode = splitTunnelManager.mode
        val selectedPackages = splitTunnelManager.getSelectedPackages()

        val list = mutableListOf<AppTrafficStat>()

        for (app in cachedApps) {
            val uid = app.uid
            val (rawRx, rawTx) = queryAppByteCounters(uid)

            val baseRx = baselineRxMap[uid] ?: rawRx
            val baseTx = baselineTxMap[uid] ?: rawTx

            val sessionRx = (rawRx - baseRx).coerceAtLeast(0L)
            val sessionTx = (rawTx - baseTx).coerceAtLeast(0L)

            val prevRx = previousRxMap[uid] ?: rawRx
            val prevTx = previousTxMap[uid] ?: rawTx

            val rxDelta = (rawRx - prevRx).coerceAtLeast(0L)
            val txDelta = (rawTx - prevTx).coerceAtLeast(0L)

            val rxSpeed = (rxDelta / deltaSeconds).toLong()
            val txSpeed = (txDelta / deltaSeconds).toLong()

            previousRxMap[uid] = rawRx
            previousTxMap[uid] = rawTx

            val isSelected = selectedPackages.contains(app.packageName)

            val status = when (splitMode) {
                SplitTunnelMode.ALL_THROUGH_VPN -> AppConnectionStatus.ROUTED_VIA_VPN
                SplitTunnelMode.ONLY_SELECTED_THROUGH_VPN -> {
                    if (isSelected) AppConnectionStatus.ROUTED_VIA_VPN else AppConnectionStatus.BYPASS_DIRECT
                }
                SplitTunnelMode.ALL_EXCEPT_SELECTED -> {
                    if (isSelected) AppConnectionStatus.BYPASS_DIRECT else AppConnectionStatus.ROUTED_VIA_VPN
                }
            }

            val isKnownMediaOrNetworkApp = app.packageName.contains("youtube", ignoreCase = true) ||
                    app.packageName.contains("chrome", ignoreCase = true) ||
                    app.packageName.contains("telegram", ignoreCase = true) ||
                    app.packageName.contains("browser", ignoreCase = true) ||
                    app.packageName.contains("music", ignoreCase = true) ||
                    app.packageName.contains("instagram", ignoreCase = true) ||
                    app.packageName.contains("tiktok", ignoreCase = true) ||
                    app.packageName.contains("netflix", ignoreCase = true) ||
                    app.packageName.contains("vk", ignoreCase = true) ||
                    app.packageName.contains("whatsapp", ignoreCase = true) ||
                    app.packageName.contains("discord", ignoreCase = true)

            val hasActiveSpeed = (rxSpeed + txSpeed) > 0
            val hasSessionTraffic = (sessionRx + sessionTx) > 0
            val hasTotalTraffic = (rawRx + rawTx) > 0

            if (hasActiveSpeed || hasSessionTraffic || isKnownMediaOrNetworkApp || isSelected || (!app.isSystem && hasTotalTraffic)) {
                val effectiveRx = if (hasSessionTraffic) sessionRx else rawRx
                val effectiveTx = if (hasSessionTraffic) sessionTx else rawTx

                list.add(
                    AppTrafficStat(
                        packageName = app.packageName,
                        appName = app.appName,
                        uid = uid,
                        rxBytes = effectiveRx,
                        txBytes = effectiveTx,
                        totalBytes = effectiveRx + effectiveTx,
                        rxSpeedBytesPerSec = rxSpeed,
                        txSpeedBytesPerSec = txSpeed,
                        connectionStatus = status,
                        isSystemApp = app.isSystem,
                        lastActiveTime = currentTime
                    )
                )
            }
        }

        lastSampleTime = currentTime

        val sorted = list.sortedWith(
            compareByDescending<AppTrafficStat> { it.rxSpeedBytesPerSec + it.txSpeedBytesPerSec }
                .thenByDescending { it.totalBytes }
                .thenBy { it.appName.lowercase() }
        )

        _appStats.value = sorted
    }
}
