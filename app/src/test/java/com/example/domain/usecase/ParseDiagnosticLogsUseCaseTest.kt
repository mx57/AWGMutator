package com.example.domain.usecase

import com.example.domain.model.BottleneckSeverity
import com.example.domain.model.BottleneckType
import org.junit.Assert.*
import org.junit.Test

class ParseDiagnosticLogsUseCaseTest {

    private val useCase = ParseDiagnosticLogsUseCase()

    @Test
    fun testDetectHandshakeDropBottleneck() {
        val userLogs = listOf(
            "[04:32:53.731] [DEVICE_INFO] Device: Xiaomi 23049RAD8C, Android 16 (API 36)",
            "[04:32:53.732] [TUN_LIFECYCLE] Initiating connection to 'Cloudflare WARP Auto' (Target Endpoint: 188.114.97.1:1074)",
            "[04:32:53.736] [TUN_CONF] Generated AmneziaWG configuration:",
            "[Interface]",
            "Address = 172.16.0.2/32",
            "DNS = 1.1.1.1, 1.0.0.1",
            "MTU = 1280",
            "H1 = 1960542209",
            "[Peer]",
            "Endpoint = 188.114.97.1:1074",
            "[04:32:55.298] [TUN_STATS_POLL] Periodic Traffic Poll: Tx=148 B, Rx=0 B (ZeroRxCycles=3)",
            "[04:32:55.300] [TUN_WARN] Tx=148 B sent, but low Rx=0 B received. Server may be dropping data or handshake unacknowledged."
        )

        val report = useCase(
            logLines = userLogs,
            currentTx = 148L,
            currentRx = 0L
        )

        assertEquals("Cloudflare WARP Auto", report.activeConfigName)
        assertEquals("188.114.97.1:1074", report.targetEndpoint)
        assertEquals(148L, report.txBytes)
        assertEquals(0L, report.rxBytes)
        assertFalse(report.isHandshakeSucceeded)
        assertFalse(report.isInternetFunctional)

        val handshakeBottleneck = report.bottlenecks.firstOrNull { it.type == BottleneckType.TSPU_HANDSHAKE_DROP }
        assertNotNull(handshakeBottleneck)
        assertEquals(BottleneckSeverity.CRITICAL, handshakeBottleneck?.severity)
        assertTrue(report.recommendedEndpoints.isNotEmpty())
    }

    @Test
    fun testDetectIpv6Blackhole() {
        val logs = listOf(
            "Address = 172.16.0.2/32, 2606:4700:110:834a::1/128",
            "AllowedIPs = 0.0.0.0/0, ::/0",
            "[DNS_PROBE] UDP 1.1.1.1:53 DNS probe failed: Poll timed out",
            "[EGRESS_PROBE] Cloudflare Trace Probe failed for https://1.1.1.1/cdn-cgi/trace: timeout"
        )

        val report = useCase(
            logLines = logs,
            currentTx = 2000L,
            currentRx = 1500L
        )

        val ipv6Bottleneck = report.bottlenecks.firstOrNull { it.type == BottleneckType.IPV6_BLACKHOLE }
        assertNotNull(ipv6Bottleneck)
        assertEquals(BottleneckSeverity.WARNING, ipv6Bottleneck?.severity)
    }
}
