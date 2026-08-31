package com.example.vpn

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.system.measureNanoTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SplitTunnelManagerTest {

    private lateinit var context: Context
    private lateinit var splitTunnelManager: SplitTunnelManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        splitTunnelManager = SplitTunnelManager(context)

        val pm = context.packageManager
        val shadowPm = shadowOf(pm)

        // Setup launcher apps
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        for (i in 1..50) {
            val pkgName = "com.example.app$i"
            val appInfo = ApplicationInfo().apply {
                packageName = pkgName
                flags = if (i % 2 == 0) ApplicationInfo.FLAG_SYSTEM else 0
                nonLocalizedLabel = "App $i"
            }
            val packageInfo = PackageInfo().apply {
                packageName = pkgName
                applicationInfo = appInfo
            }
            shadowPm.installPackage(packageInfo)

            val resolveInfo = ResolveInfo().apply {
                activityInfo = android.content.pm.ActivityInfo().apply {
                    packageName = pkgName
                    name = "$pkgName.MainActivity"
                    this.applicationInfo = appInfo
                }
            }
            shadowPm.addResolveInfoForIntent(launcherIntent, resolveInfo)
        }

        // Setup extra non-launcher packages that are queried via getApplicationInfo
        for (i in 51..200) {
            val pkgName = "com.extra.app$i"
            val appInfo = ApplicationInfo().apply {
                packageName = pkgName
                flags = 0
                nonLocalizedLabel = "Extra App $i"
            }
            shadowPm.installPackage(PackageInfo().apply {
                packageName = pkgName
                applicationInfo = appInfo
            })
        }
    }

    @Test
    fun testGetInstalledAppsReturnsExpectedList() {
        splitTunnelManager.setSelectedPackages(setOf("com.extra.app55", "com.extra.app60"))
        val apps = splitTunnelManager.getInstalledApps()

        assertTrue(apps.any { it.packageName == "com.extra.app55" })
        assertTrue(apps.any { it.packageName == "com.extra.app60" })
        assertTrue(apps.any { it.packageName == "com.example.app1" })
        assertFalse(apps.any { it.packageName == context.packageName })
    }

    @Test
    fun testTogglePackage() {
        val pkg = "com.example.testapp"
        assertFalse(splitTunnelManager.getSelectedPackages().contains(pkg))

        splitTunnelManager.togglePackage(pkg)
        assertTrue(splitTunnelManager.getSelectedPackages().contains(pkg))

        splitTunnelManager.togglePackage(pkg)
        assertFalse(splitTunnelManager.getSelectedPackages().contains(pkg))
    }

    @Test
    fun testGetInstalledAppsPerformanceBenchmark() {
        // Build set of extra packages
        val extras = (51..200).map { "com.extra.app$it" }.toSet()
        splitTunnelManager.setSelectedPackages(extras)

        // Warm up
        repeat(5) {
            splitTunnelManager.getInstalledApps()
        }

        // Measure time across multiple runs
        val iterations = 50
        val elapsedNanos = measureNanoTime {
            repeat(iterations) {
                splitTunnelManager.getInstalledApps()
            }
        }

        val avgMs = elapsedNanos / (1_000_000.0 * iterations)
        println("BENCHMARK: getInstalledApps average execution time: %.3f ms per call".format(avgMs))
    }
}
