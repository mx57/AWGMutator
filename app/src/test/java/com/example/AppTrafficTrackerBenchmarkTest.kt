package com.example

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import com.example.vpn.AppTrafficTracker
import com.example.vpn.SplitTunnelManager
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.system.measureNanoTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AppTrafficTrackerBenchmarkTest {

    private lateinit var context: Context
    private lateinit var tracker: AppTrafficTracker

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val pm = context.packageManager

        // Create 1000 app infos and simulate getApplicationLabel calls if needed
        val splitTunnelManager = SplitTunnelManager(context)
        tracker = AppTrafficTracker(context, splitTunnelManager)
    }

    @Test
    fun benchmarkCacheInstalledApps() {
        // Measure 100 iterations of cacheInstalledApps directly via reflection or by calling startTracking
        val field = AppTrafficTracker::class.java.getDeclaredMethod("cacheInstalledApps")
        field.isAccessible = true

        // Warmup
        for (i in 0 until 10) {
            field.invoke(tracker)
        }

        val iterations = 100
        val totalNs = measureNanoTime {
            for (i in 0 until iterations) {
                field.invoke(tracker)
            }
        }

        val avgMs = (totalNs.toDouble() / iterations) / 1_000_000.0
        println("Benchmark AppTrafficTracker.cacheInstalledApps average time over $iterations runs: $avgMs ms")
    }
}
