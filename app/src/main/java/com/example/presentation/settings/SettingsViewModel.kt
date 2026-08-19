package com.example.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.App
import com.example.vpn.InstalledApp
import com.example.vpn.SplitTunnelManager
import com.example.vpn.SplitTunnelMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val splitMode: SplitTunnelMode = SplitTunnelMode.ALL_THROUGH_VPN,
    val installedApps: List<InstalledApp> = emptyList(),
    val selectedPackages: Set<String> = emptySet(),
    val isLoadingApps: Boolean = false,
    val searchQuery: String = "",
    val pingTargets: List<String> = emptyList(),
    val userMessage: String? = null
)

class SettingsViewModel(
    private val splitManager: SplitTunnelManager = App.instance.splitTunnelManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            splitMode = splitManager.mode,
            selectedPackages = splitManager.getSelectedPackages(),
            pingTargets = App.instance.pingTester.defaultTargets
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadInstalledApps()
    }

    fun loadInstalledApps() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingApps = true)
            val apps = withContext(Dispatchers.IO) {
                splitManager.getInstalledApps()
            }
            _uiState.value = _uiState.value.copy(
                installedApps = apps,
                isLoadingApps = false,
                selectedPackages = splitManager.getSelectedPackages()
            )
        }
    }

    fun setSplitMode(mode: SplitTunnelMode) {
        splitManager.mode = mode
        _uiState.value = _uiState.value.copy(splitMode = mode)
    }

    fun toggleApp(packageName: String) {
        splitManager.togglePackage(packageName)
        _uiState.value = _uiState.value.copy(
            selectedPackages = splitManager.getSelectedPackages()
        )
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(userMessage = null)
    }
}
