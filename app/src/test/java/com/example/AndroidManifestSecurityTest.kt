package com.example

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidManifestSecurityTest {

    @Test
    fun `verify MainActivity is exported for MAIN LAUNCHER intent filter`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val packageManager = context.packageManager

        val componentName = ComponentName(context, MainActivity::class.java)
        val activityInfo = packageManager.getActivityInfo(componentName, PackageManager.GET_META_DATA)

        assertNotNull(activityInfo)
        assertTrue("MainActivity must be exported as the launcher entry point", activityInfo.exported)

        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setPackage(context.packageName)
        }
        val resolveInfos = packageManager.queryIntentActivities(launcherIntent, 0)
        assertFalse("Launcher intent should resolve MainActivity", resolveInfos.isEmpty())
        assertEquals(context.packageName, resolveInfos[0].activityInfo.packageName)
        assertEquals(MainActivity::class.java.name, resolveInfos[0].activityInfo.name)
    }

    @Test
    fun `verify GoBackend VpnService is strictly unexported`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val packageManager = context.packageManager

        val componentName = ComponentName(context, "org.amnezia.awg.backend.GoBackend\$VpnService")
        val serviceInfo = packageManager.getServiceInfo(componentName, 0)

        assertNotNull(serviceInfo)
        assertFalse("VpnService must not be exported to external applications", serviceInfo.exported)
    }

    @Test
    fun `verify FileProvider is strictly unexported`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val packageManager = context.packageManager

        val componentName = ComponentName(context, "androidx.core.content.FileProvider")
        val providerInfo = packageManager.getProviderInfo(componentName, 0)

        assertNotNull(providerInfo)
        assertFalse("FileProvider must not be exported to external applications", providerInfo.exported)
    }
}
