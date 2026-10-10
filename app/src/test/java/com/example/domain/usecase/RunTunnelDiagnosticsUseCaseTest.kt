package com.example.domain.usecase

import com.example.domain.model.AwgConfig
import com.example.domain.model.DiagnosticActionType
import com.example.domain.model.DiagnosticStatus
import com.example.util.WireGuardProbe
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RunTunnelDiagnosticsUseCaseTest {

    private val useCase = RunTunnelDiagnosticsUseCase()

    @Test
    fun testExecuteWithNullConfig() = runBlocking {
        val reports = useCase.execute(null).toList()

        assertTrue("Flow should emit multiple progress reports", reports.size >= 2)

        val initialReport = reports.first()
        assertTrue(initialReport.isRunning)
        assertEquals(1, initialReport.currentStepIndex)
        assertEquals(5, initialReport.totalSteps)

        val finalReport = reports.last()
        assertFalse("Final report should set isRunning to false", finalReport.isRunning)
        assertEquals(5, finalReport.currentStepIndex)
        assertEquals(5, finalReport.steps.size)
        assertTrue(finalReport.testedEndpoints.isNotEmpty())

        // Account stage check for null config: isWarp is false, non-WARP branch -> SUCCESS
        val stage2Step = finalReport.steps[1]
        assertEquals("step_account", stage2Step.id)
        assertEquals(DiagnosticStatus.SUCCESS, stage2Step.status)
    }

    @Test
    fun testExecuteWithWarpConfigMissingReservedOrPrivateKey() = runBlocking {
        val configMissingReserved = AwgConfig(
            name = "Incomplete WARP",
            privateKey = "dGVzdFByaXZhdGVLZXkxMjM0NTY3ODkwMTIzNDU2Nw==",
            isWarp = true,
            reserved = ""
        )

        val reports = useCase.execute(configMissingReserved).toList()
        val finalReport = reports.last()

        val stage2Step = finalReport.steps[1]
        assertEquals("step_account", stage2Step.id)
        assertEquals(DiagnosticStatus.ERROR, stage2Step.status)
        assertEquals(DiagnosticActionType.REGENERATE_WARP_ACCOUNT, stage2Step.recommendedAction)
        assertNotNull(stage2Step.actionLabel)
        assertTrue(stage2Step.resultText?.contains("Reserved") == true)
    }

    @Test
    fun testExecuteWithValidWarpConfig() = runBlocking {
        val validWarpConfig = AwgConfig(
            name = "Valid WARP Profile",
            privateKey = "dGVzdFByaXZhdGVLZXkxMjM0NTY3ODkwMTIzNDU2Nw==",
            isWarp = true,
            reserved = "10, 20, 30",
            peerPublicKey = WireGuardProbe.DEFAULT_CLOUDFLARE_WARP_PUBKEY
        )

        val reports = useCase.execute(validWarpConfig).toList()
        val finalReport = reports.last()

        val stage2Step = finalReport.steps[1]
        assertEquals("step_account", stage2Step.id)
        assertEquals(DiagnosticStatus.SUCCESS, stage2Step.status)
        assertEquals(DiagnosticActionType.NONE, stage2Step.recommendedAction)
        assertTrue(stage2Step.resultText?.contains("10, 20, 30") == true)
    }

    @Test
    fun testExecuteWithIpv6Configured() = runBlocking {
        val configWithIpv6 = AwgConfig(
            name = "IPv6 Config",
            privateKey = "dGVzdFByaXZhdGVLZXkxMjM0NTY3ODkwMTIzNDU2Nw==",
            address = "172.16.0.2/32, 2606:4700:110:893c::1/128",
            allowedIps = "0.0.0.0/0, ::/0"
        )

        val reports = useCase.execute(configWithIpv6).toList()
        val finalReport = reports.last()

        val stage3Step = finalReport.steps[2]
        assertEquals("step_ipv6", stage3Step.id)
        assertEquals(DiagnosticStatus.WARNING, stage3Step.status)
        assertEquals(DiagnosticActionType.SWITCH_IPV4_ONLY, stage3Step.recommendedAction)
        assertNotNull(stage3Step.actionLabel)
    }

    @Test
    fun testExecuteWithIpv4OnlyConfigured() = runBlocking {
        val configIpv4Only = AwgConfig(
            name = "IPv4 Only Config",
            privateKey = "dGVzdFByaXZhdGVLZXkxMjM0NTY3ODkwMTIzNDU2Nw==",
            address = "172.16.0.2/32",
            allowedIps = "0.0.0.0/0"
        )

        val reports = useCase.execute(configIpv4Only).toList()
        val finalReport = reports.last()

        val stage3Step = finalReport.steps[2]
        assertEquals("step_ipv6", stage3Step.id)
        assertEquals(DiagnosticStatus.SUCCESS, stage3Step.status)
        assertEquals(DiagnosticActionType.NONE, stage3Step.recommendedAction)
        assertNull(stage3Step.actionLabel)
    }

    @Test
    fun testExecuteWithCustomAmneziaWgConfig() = runBlocking {
        val customConfig = AwgConfig(
            name = "Private Server",
            privateKey = "dGVzdFByaXZhdGVLZXkxMjM0NTY3ODkwMTIzNDU2Nw==",
            isWarp = false,
            h1 = 123456L,
            jc = 5,
            s1 = 15
        )

        val reports = useCase.execute(customConfig).toList()
        val finalReport = reports.last()

        // Stage 2 for custom non-WARP config should be SUCCESS
        val stage2Step = finalReport.steps[1]
        assertEquals(DiagnosticStatus.SUCCESS, stage2Step.status)
        assertTrue(stage2Step.resultText?.contains("H1=123456") == true)
        assertTrue(stage2Step.resultText?.contains("Jc=5") == true)
    }
}
