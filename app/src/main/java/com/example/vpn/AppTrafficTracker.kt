package com.example.vpn

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.TrafficStats
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

private data class CachedAppMeta(
    val uid: Int,
    val packageName: String,
    val appName: String,
    val isSystem: Boolean
)

/**
 * Tracks per-application network traffic consumption, live upload/download transfer rates,
 * and routing status through the active VPN / Root WireGuard configuration.
 *
 * Uses cached app metadata to avoid repeated PackageManager ashmem IPC allocations on Android Q+.
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

    // Baseline captured when VPN starts to show per-session stats
    private val baselineRxMap = ConcurrentHashMap<Int, Long>()
    private val baselineTxMap = ConcurrentHashMap<Int, Long>()

    fun startTracking() {
        if (trackingJob?.isActive == true) return
        ensureAppsCached()
        resetSessionBaseline()
        trackingJob = scope.launch {
            while (isActive) {
                sampleTraffic()
                delay(1500)
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
        val packages = pm.getInstalledApplications(0)
        cachedApps.clear()
        for (app in packages) {
            if (app.packageName == context.packageName) continue
            val appName = runCatching { pm.getApplicationLabel(app).toString() }.getOrDefault(app.packageName)
            val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            cachedApps.add(
                CachedAppMeta(
                    uid = app.uid,
                    packageName = app.packageName,
                    appName = appName,
                    isSystem = isSystem
                )
            )
        }
    }

    fun resetSessionBaseline() {
        ensureAppsCached()
        for (app in cachedApps) {
            val rx = TrafficStats.getUidRxBytes(app.uid)
            val tx = TrafficStats.getUidTxBytes(app.uid)
            if (rx != TrafficStats.UNSUPPORTED.toLong()) {
                baselineRxMap[app.uid] = rx
            }
            if (tx != TrafficStats.UNSUPPORTED.toLong()) {
                baselineTxMap[app.uid] = tx
            }
        }
    }

    suspend fun refreshOnce() = withContext(Dispatchers.IO) {
        ensureAppsCached()
        sampleTraffic()
    }

    private fun sampleTraffic() {
        if (cachedApps.isEmpty()) {
            ensureAppsCached()
        }

        val currentTime = System.currentTimeMillis()
        val deltaSeconds = ((currentTime - lastSampleTime) / 1000.0).coerceAtLeast(0.5)

        val splitMode = splitTunnelManager.mode
        val selectedPackages = splitTunnelManager.getSelectedPackages()

        val list = mutableListOf<AppTrafficStat>()

        for (app in cachedApps) {
            val uid = app.uid
            val rawRx = TrafficStats.getUidRxBytes(uid)
            val rawTx = TrafficStats.getUidTxBytes(uid)

            if (rawRx == TrafficStats.UNSUPPORTED.toLong() || rawTx == TrafficStats.UNSUPPORTED.toLong()) {
                continue
            }

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

            // Only add apps that have active traffic or are user/whitelisted apps
            if (sessionRx > 0 || sessionTx > 0 || !app.isSystem || isSelected) {
                list.add(
                    AppTrafficStat(
                        packageName = app.packageName,
                        appName = app.appName,
                        uid = uid,
                        rxBytes = sessionRx,
                        txBytes = sessionTx,
                        totalBytes = sessionRx + sessionTx,
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

        // Sort apps: active transfer rate first, then total traffic, then app name
        val sorted = list.sortedWith(
            compareByDescending<AppTrafficStat> { it.rxSpeedBytesPerSec + it.txSpeedBytesPerSec }
                .thenByDescending { it.totalBytes }
                .thenBy { it.appName.lowercase() }
        )

        _appStats.value = sorted
    }
}
