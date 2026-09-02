package com.example.domain.model

enum class DiagnosticStatus {
    IDLE,
    RUNNING,
    SUCCESS,
    WARNING,
    ERROR
}

enum class DiagnosticActionType {
    NONE,
    APPLY_ENDPOINT,
    SWITCH_IPV4_ONLY,
    REGENERATE_WARP_ACCOUNT,
    REPAIR_MTU_1280,
    RECONNECT
}

data class DiagnosticStep(
    val id: String,
    val title: String,
    val description: String,
    val status: DiagnosticStatus = DiagnosticStatus.IDLE,
    val resultText: String? = null,
    val details: String? = null,
    val latencyMs: Long? = null,
    val recommendedAction: DiagnosticActionType = DiagnosticActionType.NONE,
    val actionLabel: String? = null,
    val actionPayload: String? = null
)

data class EndpointProbeDetail(
    val endpoint: String,
    val port: Int,
    val isWorking: Boolean,
    val latencyMs: Long?,
    val error: String?
)

data class TunnelDiagnosticReport(
    val isRunning: Boolean = false,
    val currentStepIndex: Int = 0,
    val totalSteps: Int = 5,
    val overallVerdict: String = "Готов к запуску диагностики",
    val isHealthy: Boolean = false,
    val steps: List<DiagnosticStep> = emptyList(),
    val testedEndpoints: List<EndpointProbeDetail> = emptyList(),
    val bestEndpoint: String? = null,
    val bestEndpointLatencyMs: Long? = null
)
