package com.example.vpn

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.AwgConfig
import com.example.domain.model.VpnState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TunnelManagerTest {

    private lateinit var context: Context
    private lateinit var tunnelManager: TunnelManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        tunnelManager = TunnelManager(context)
    }

    @Test
    fun testInitialStatusIsDisconnected() {
        val status = tunnelManager.status.value
        assertEquals(VpnState.DISCONNECTED, status.state)
    }

    @Test
    fun testLogAndClearLogs() {
        tunnelManager.log("TEST_TAG", "Test message")
        assertEquals(1, tunnelManager.logs.value.size)

        val fullLogText = tunnelManager.getFormattedFullLogText()
        assertNotNull(fullLogText)

        tunnelManager.clearLogs()
        assertEquals(0, tunnelManager.logs.value.size)
    }

    @Test
    fun testUpdateBytes() {
        tunnelManager.updateBytes(100L, 200L)
        val status = tunnelManager.status.value
        assertEquals(100L, status.rxBytes)
        assertEquals(200L, status.txBytes)
    }
}
