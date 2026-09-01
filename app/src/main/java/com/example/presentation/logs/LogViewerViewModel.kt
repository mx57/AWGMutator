package com.example.presentation.logs

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.App
import com.example.domain.model.LogSeverity
import com.example.domain.model.TunnelLogItem
import com.example.domain.model.VpnStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class LogViewerUiState(
    val searchQuery: String = "",
    val selectedSeverity: LogSeverity? = null,
    val selectedTag: String? = null,
    val isAutoScrollEnabled: Boolean = true,
    val expandedLogId: String? = null,
    val userFeedbackMessage: String? = null
)

data class LogStatistics(
    val totalRawCount: Int = 0,
    val consolidatedCount: Int = 0,
    val errorCount: Int = 0,
    val warningCount: Int = 0,
    val antiDpiCount: Int = 0
)

class LogViewerViewModel : ViewModel() {

    val vpnStatus: StateFlow<VpnStatus> = App.instance.tunnelManager.status

    val structuredLogs: StateFlow<List<TunnelLogItem>> = App.instance.tunnelManager.structuredLogs

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

    val statistics: StateFlow<LogStatistics> = structuredLogs.combine(App.instance.tunnelManager.logs) { structured, raw ->
        LogStatistics(
            totalRawCount = raw.size,
            consolidatedCount = structured.size,
            errorCount = structured.count { it.severity == LogSeverity.ERROR },
            warningCount = structured.count { it.severity == LogSeverity.WARN },
            antiDpiCount = structured.count { it.severity == LogSeverity.ANTI_DPI }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LogStatistics())

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
        _uiState.value = _uiState.value.copy(userFeedbackMessage = "Logs cleared")
    }

    fun clearFeedbackMessage() {
        _uiState.value = _uiState.value.copy(userFeedbackMessage = null)
    }

    fun shareLogs(context: Context) {
        val fullText = App.instance.tunnelManager.getFormattedFullLogText()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "AWG Mutator Tunnel Diagnostics")
            putExtra(Intent.EXTRA_TEXT, fullText)
        }
        val chooser = Intent.createChooser(intent, "Share Diagnostic Logs")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
