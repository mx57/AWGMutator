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
import kotlin.coroutines.Continuation

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
    }

    @Test
    fun testRootModePreferenceToggle() {
        assertFalse(rootTunnelManager.isRootModeEnabled)
        rootTunnelManager.isRootModeEnabled = true
        assertEquals(true, rootTunnelManager.isRootModeEnabled)
    }

    @Test
    fun testCheckInterfaceUp_returnsFalseWhenInterfaceDoesNotExist() = runBlocking {
        val method = RootTunnelManager::class.java.declaredMethods.first { it.name == "checkInterfaceUp" }
        method.isAccessible = true
        // For suspend functions with no explicit arguments, reflection requires Continuation
        val isUp = kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn<Boolean> { continuation ->
            method.invoke(rootTunnelManager, continuation)
        }
        assertFalse(isUp)
    }
}
