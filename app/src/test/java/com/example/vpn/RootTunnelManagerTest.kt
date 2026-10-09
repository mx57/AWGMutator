package com.example.vpn

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.VpnState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RootTunnelManagerTest {

    private lateinit var context: Context
    private lateinit var rootTunnelManager: RootTunnelManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        rootTunnelManager = RootTunnelManager(context)
    }

    @Test
    fun testInitialStatusIsDisconnected() {
        val status = rootTunnelManager.status.value
        assertEquals(VpnState.DISCONNECTED, status.state)
        assertFalse(status.isRootTunnel)
    }

    @Test
    fun testIsRootModeEnabledPreference() {
        assertFalse(rootTunnelManager.isRootModeEnabled)
        rootTunnelManager.isRootModeEnabled = true
        assertEquals(true, rootTunnelManager.isRootModeEnabled)
    }

    @Test
    fun testDisconnectUpdatesStatusToDisconnected() = runBlocking {
        val result = rootTunnelManager.disconnect()
        assertEquals(true, result.isSuccess)
        val status = rootTunnelManager.status.value
        assertEquals(VpnState.DISCONNECTED, status.state)
        assertFalse(status.isRootTunnel)
    }
}
