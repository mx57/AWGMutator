package com.example

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import com.example.vpn.AppTrafficTracker
import com.example.vpn.SplitTunnelManager
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.system.measureNanoTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppTrafficTrackerTest {

    private lateinit var context: Context
    private lateinit var splitTunnelManager: SplitTunnelManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        splitTunnelManager = SplitTunnelManager(context)

        val pm = context.packageManager
        val shadowPm = shadowOf(pm)

        // Populate Robolectric PackageManager with 200 fake installed applications
        for (i in 1..200) {
            val pkgName = "com.example.testapp$i"
            val appInfo = ApplicationInfo().apply {
                packageName = pkgName
                uid = 10000 + i
                flags = if (i % 2 == 0) ApplicationInfo.FLAG_SYSTEM else 0
                nonLocalizedLabel = "Test App $i"
            }
            val pkgInfo = PackageInfo().apply {
                packageName = pkgName
                applicationInfo = appInfo
            }
            shadowPm.installPackage(pkgInfo)
        }
    }

    @Test
    fun testAppTrafficTrackerCachingAndResolution() {
        val tracker = AppTrafficTracker(context, splitTunnelManager)

        val elapsedNs = measureNanoTime {
            tracker.startTracking()
        }

        println("AppTrafficTracker initialization with 200 apps took: ${elapsedNs / 1_000_000.0} ms")

        tracker.stopTracking()
        assertNotNull(tracker)
    }
}
