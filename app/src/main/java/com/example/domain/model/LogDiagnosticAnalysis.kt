package com.example.domain.model

enum class BottleneckSeverity {
    CRITICAL,
    WARNING,
    INFO,
    OPTIMIZATION
}

enum class BottleneckType {
    TSPU_HANDSHAKE_DROP,       // Tx > 0, Rx = 0 (ISP/TSPU dropping UDP handshake)
    WARP_AUTH_REJECTED,        // Rx > 0 but Egress fails (Missing or invalid client_id / Reserved)
    IPV6_BLACKHOLE,            // IPv6 enabled on IPv4-only carrier network
    DNS_BLOCKED,               // UDP 53 DNS queries timing out
    EGRESS_TIMEOUT,            // Tunnel up, but HTTPS/HTTP trace times out
    MTU_FRAGMENTATION,         // High MTU (>1280) on mobile networks causing packet drops
    OBFUSCATION_MISMATCH       // H1-H4 / Jc parameters mismatched
}

data class ConnectionBottleneck(
    val id: String,
    val type: BottleneckType,
    val severity: BottleneckSeverity,
    val title: String,
    val summary: String,
    val technicalDetails: String,
    val detectedInLog: String? = null,
    val recommendedFix: String,
    val actionType: DiagnosticActionType = DiagnosticActionType.NONE,
    val actionPayload: String? = null
)

data class HandshakeStageStatus(
    val stageName: String,
    val description: String,
    val isSuccess: Boolean,
    val isCurrentOrFailed: Boolean,
    val details: String
)

data class LogDiagnosticReport(
    val activeConfigName: String? = null,
    val targetEndpoint: String? = null,
    val txBytes: Long = 0,
    val rxBytes: Long = 0,
    val zeroRxCycles: Int = 0,
    val isHandshakeSucceeded: Boolean = false,
    val isInternetFunctional: Boolean = false,
    val overallHealthScore: Int = 0, // 0 - 100
    val summaryVerdict: String = "",
    val stages: List<HandshakeStageStatus> = emptyList(),
    val bottlenecks: List<ConnectionBottleneck> = emptyList(),
    val recommendedEndpoints: List<String> = emptyList()
)
