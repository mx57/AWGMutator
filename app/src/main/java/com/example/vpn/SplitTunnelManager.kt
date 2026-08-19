package com.example.vpn

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable

data class InstalledApp(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean,
    val icon: Drawable? = null
)

enum class SplitTunnelMode {
    ALL_THROUGH_VPN,           // Standard full tunnel
    ONLY_SELECTED_THROUGH_VPN, // Whitelist
    ALL_EXCEPT_SELECTED        // Blacklist
}

class SplitTunnelManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("awg_split_tunnel_prefs", Context.MODE_PRIVATE)

    var mode: SplitTunnelMode
        get() {
            val name = prefs.getString("split_mode", SplitTunnelMode.ALL_THROUGH_VPN.name)
            return runCatching { SplitTunnelMode.valueOf(name!!) }.getOrDefault(SplitTunnelMode.ALL_THROUGH_VPN)
        }
        set(value) {
            prefs.edit().putString("split_mode", value.name).apply()
        }

    fun getSelectedPackages(): Set<String> {
        return prefs.getStringSet("selected_apps", emptySet()) ?: emptySet()
    }

    fun setSelectedPackages(packages: Set<String>) {
        prefs.edit().putStringSet("selected_apps", packages).apply()
    }

    fun togglePackage(packageName: String) {
        val current = getSelectedPackages().toMutableSet()
        if (current.contains(packageName)) {
            current.remove(packageName)
        } else {
            current.add(packageName)
        }
        setSelectedPackages(current)
    }

    fun getInstalledApps(): List<InstalledApp> {
        val pm = context.packageManager
        // Query basic info with 0 flags to prevent ashmem IPC allocations on Android Q+
        val packages = pm.getInstalledApplications(0)
        val list = mutableListOf<InstalledApp>()

        for (appInfo in packages) {
            // Skip this app itself from listing
            if (appInfo.packageName == context.packageName) continue

            val appName = runCatching { pm.getApplicationLabel(appInfo).toString() }.getOrDefault(appInfo.packageName)
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

            list.add(
                InstalledApp(
                    packageName = appInfo.packageName,
                    appName = appName,
                    isSystemApp = isSystem,
                    icon = null
                )
            )
        }

        return list.sortedWith(
            compareBy<InstalledApp> { it.isSystemApp }
                .thenBy { it.appName.lowercase() }
        )
    }
}
