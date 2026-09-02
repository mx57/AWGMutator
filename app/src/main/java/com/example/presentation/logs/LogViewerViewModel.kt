package com.example.presentation.logs

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.App
import com.example.domain.model.DiagnosticActionType
import com.example.domain.model.LogDiagnosticReport
import com.example.domain.model.LogSeverity
import com.example.domain.model.TunnelLogItem
import com.example.domain.model.VpnState
import com.example.domain.model.VpnStatus
import com.example.domain.usecase.GenerateHybridWarpAwgUseCase
import com.example.domain.usecase.ParseDiagnosticLogsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LogViewerUiState(
    val searchQuery: String = "",
    val selectedSeverity: LogSeverity? = null,
    val selectedTag: String? = null,
    val isAutoScrollEnabled: Boolean = true,
    val expandedLogId: String? = null,
    val userFeedbackMessage: String? = null,
    val activeTab: Int = 0, // 0 = Diagnostic Console, 1 = Raw Logs
    val isPastedLogDialogOpen: Boolean = false,
    val customLogAnalysisReport: LogDiagnosticReport? = null
)

data class LogStatistics(
    val totalRawCount: Int = 0,
    val consolidatedCount: Int = 0,
    val errorCount: Int = 0,
    val warningCount: Int = 0,
    val antiDpiCount: Int = 0
)

class LogViewerViewModel : ViewModel() {

    private val parseDiagnosticLogsUseCase = ParseDiagnosticLogsUseCase()
    private val generateHybridWarpAwgUseCase = GenerateHybridWarpAwgUseCase(
        cloudflareApi = App.instance.cloudflareApi,
        configRepository = App.instance.configRepository
    )

    val vpnStatus: StateFlow<VpnStatus> = App.instance.tunnelManager.status

    val structuredLogs: StateFlow<List<TunnelLogItem>> = App.instance.tunnelManager.structuredLogs
    val rawLogs: StateFlow<List<String>> = App.instance.tunnelManager.logs

    private val _uiState = MutableStateFlow(LogViewerUiState())
    val uiState: StateFlow<LogViewerUiState> = _uiState.asStateFlow()

    val filteredLogs: StateFlow<List<TunnelLogItem>> = combine(
        structuredLogs,
        _uiState
    ) { logs, state ->
        logs.filter { item ->
            val matchesQuery = state.searchQuery.isBlank() ||
                    item.message.contains(state.searchQuery, ignoreCase = true) ||
                    item.tag.contains(state.searchQuery, ignoreCase = true) ||
                    item.timestamp.contains(state.searchQuery, ignoreCase = true)

            val matchesSeverity = state.selectedSeverity == null || item.severity == state.selectedSeverity

            val matchesTag = state.selectedTag == null || item.tag.equals(state.selectedTag, ignoreCase = true)

            matchesQuery && matchesSeverity && matchesTag
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val statistics: StateFlow<LogStatistics> = structuredLogs.combine(rawLogs) { structured, raw ->
        LogStatistics(
            totalRawCount = raw.size,
            consolidatedCount = structured.size,
            errorCount = structured.count { it.severity == LogSeverity.ERROR },
            warningCount = structured.count { it.severity == LogSeverity.WARN },
            antiDpiCount = structured.count { it.severity == LogSeverity.ANTI_DPI }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LogStatistics())

    val liveDiagnosticReport: StateFlow<LogDiagnosticReport> = combine(
        rawLogs,
        structuredLogs,
        vpnStatus
    ) { raw, structured, status ->
        parseDiagnosticLogsUseCase(
            logLines = raw,
            structuredLogs = structured,
            currentConfigName = status.activeConfigName,
            currentEndpoint = status.endpoint,
            currentTx = status.txBytes,
            currentRx = status.rxBytes
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        LogDiagnosticReport(summaryVerdict = "Инициализация консоли диагностики...")
    )

    fun setActiveTab(tab: Int) {
        _uiState.value = _uiState.value.copy(activeTab = tab)
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun setSeverityFilter(severity: LogSeverity?) {
        _uiState.value = _uiState.value.copy(
            selectedSeverity = if (_uiState.value.selectedSeverity == severity) null else severity
        )
    }

    fun setTagFilter(tag: String?) {
        _uiState.value = _uiState.value.copy(
            selectedTag = if (_uiState.value.selectedTag == tag) null else tag
        )
    }

    fun toggleAutoScroll() {
        _uiState.value = _uiState.value.copy(isAutoScrollEnabled = !_uiState.value.isAutoScrollEnabled)
    }

    fun toggleExpandLog(id: String) {
        _uiState.value = _uiState.value.copy(
            expandedLogId = if (_uiState.value.expandedLogId == id) null else id
        )
    }

    fun clearLogs() {
        App.instance.tunnelManager.clearLogs()
        _uiState.value = _uiState.value.copy(
            customLogAnalysisReport = null,
            userFeedbackMessage = "Журнал логов очищен"
        )
    }

    fun clearFeedbackMessage() {
        _uiState.value = _uiState.value.copy(userFeedbackMessage = null)
    }

    fun setPastedLogDialogVisible(visible: Boolean) {
        _uiState.value = _uiState.value.copy(isPastedLogDialogOpen = visible)
    }

    fun analyzeCustomLogText(logText: String) {
        if (logText.isBlank()) return
        val lines = logText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val report = parseDiagnosticLogsUseCase(
            logLines = lines,
            structuredLogs = emptyList()
        )
        _uiState.value = _uiState.value.copy(
            customLogAnalysisReport = report,
            isPastedLogDialogOpen = false,
            activeTab = 0,
            userFeedbackMessage = "Проанализировано ${lines.size} строк лога"
        )
    }

    fun clearCustomLogReport() {
        _uiState.value = _uiState.value.copy(customLogAnalysisReport = null)
    }

    fun applyAction(actionType: DiagnosticActionType, payload: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val repository = App.instance.configRepository
            val allConfigs = repository.getAllConfigs().firstOrNull() ?: emptyList()
            val activeConfig = allConfigs.firstOrNull { it.id == vpnStatus.value.activeConfigId }
                ?: allConfigs.firstOrNull()

            if (activeConfig == null && actionType != DiagnosticActionType.REGENERATE_WARP_ACCOUNT) {
                _uiState.value = _uiState.value.copy(userFeedbackMessage = "Нет активного профиля для применения фикса")
                return@launch
            }

            when (actionType) {
                DiagnosticActionType.APPLY_ENDPOINT -> {
                    if (activeConfig != null) {
                        val targetEndpoint = payload ?: "162.159.130.1:1074"
                        val updated = activeConfig.copy(endpoint = targetEndpoint)
                        repository.updateConfig(updated)
                        App.instance.tunnelManager.log("DIAG_FIX", "Применен чистый эндпоинт: $targetEndpoint")
                        if (vpnStatus.value.state == VpnState.CONNECTED || vpnStatus.value.state == VpnState.CONNECTING) {
                            App.instance.tunnelManager.disconnect()
                            delay(500)
                            App.instance.tunnelManager.connect(updated)
                        }
                        _uiState.value = _uiState.value.copy(userFeedbackMessage = "Применен чистый эндпоинт: $targetEndpoint")
                    }
                }
                DiagnosticActionType.SWITCH_IPV4_ONLY -> {
                    if (activeConfig != null) {
                        val cleanIpv4Address = activeConfig.address.split(",")
                            .map { it.trim() }
                            .firstOrNull { !it.contains(":") } ?: "172.16.0.2/32"
                        val updated = activeConfig.copy(
                            address = cleanIpv4Address,
                            allowedIps = "0.0.0.0/0"
                        )
                        repository.updateConfig(updated)
                        App.instance.tunnelManager.log("DIAG_FIX", "Отключен IPv6 (IPv4-Only активирован)")
                        if (vpnStatus.value.state == VpnState.CONNECTED || vpnStatus.value.state == VpnState.CONNECTING) {
                            App.instance.tunnelManager.disconnect()
                            delay(500)
                            App.instance.tunnelManager.connect(updated)
                        }
                        _uiState.value = _uiState.value.copy(userFeedbackMessage = "Успешно включен режим IPv4-Only (устранен IPv6 Blackhole)")
                    }
                }
                DiagnosticActionType.REPAIR_MTU_1280 -> {
                    if (activeConfig != null) {
                        val mtu = payload?.toIntOrNull() ?: 1280
                        val updated = activeConfig.copy(mtu = mtu)
                        repository.updateConfig(updated)
                        App.instance.tunnelManager.log("DIAG_FIX", "Установлен безопасный MTU: $mtu")
                        if (vpnStatus.value.state == VpnState.CONNECTED || vpnStatus.value.state == VpnState.CONNECTING) {
                            App.instance.tunnelManager.disconnect()
                            delay(500)
                            App.instance.tunnelManager.connect(updated)
                        }
                        _uiState.value = _uiState.value.copy(userFeedbackMessage = "Установлен безопасный MTU $mtu")
                    }
                }
                DiagnosticActionType.REGENERATE_WARP_ACCOUNT -> {
                    _uiState.value = _uiState.value.copy(userFeedbackMessage = "Регистрация нового WARP профиля в Cloudflare...")
                    val targetEndpoint = payload ?: "162.159.130.1:1074"
                    val result = generateHybridWarpAwgUseCase(
                        customName = "WARP Clean Anycast",
                        dns = "1.1.1.1, 1.0.0.1, 8.8.8.8"
                    )
                    if (result.isSuccess) {
                        val newConfig = result.getOrThrow().copy(endpoint = targetEndpoint)
                        repository.updateConfig(newConfig)
                        App.instance.tunnelManager.log("DIAG_FIX", "Зарегистрирован новый авторизованный WARP профиль с client_id")
                        App.instance.tunnelManager.disconnect()
                        delay(500)
                        App.instance.tunnelManager.connect(newConfig)
                        _uiState.value = _uiState.value.copy(userFeedbackMessage = "Создан и запущен новый авторизованный WARP профиль!")
                    } else {
                        _uiState.value = _uiState.value.copy(
                            userFeedbackMessage = "Ошибка регистрации: ${result.exceptionOrNull()?.localizedMessage}"
                        )
                    }
                }
                DiagnosticActionType.RECONNECT -> {
                    if (activeConfig != null) {
                        App.instance.tunnelManager.disconnect()
                        delay(500)
                        App.instance.tunnelManager.connect(activeConfig)
                        _uiState.value = _uiState.value.copy(userFeedbackMessage = "Переподключение...")
                    }
                }
                DiagnosticActionType.NONE -> {}
            }
        }
    }

    fun shareLogs(context: Context) {
        val fullText = App.instance.tunnelManager.getFormattedFullLogText()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "AWG Mutator Tunnel Diagnostics & Bottleneck Report")
            putExtra(Intent.EXTRA_TEXT, fullText)
        }
        val chooser = Intent.createChooser(intent, "Share Diagnostic Logs")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
