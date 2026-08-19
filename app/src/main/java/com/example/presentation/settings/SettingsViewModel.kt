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
    val isRootModeEnabled: Boolean = false,
    val isRootDeviceAvailable: Boolean = false,
    val pingTargets: List<String> = emptyList(),
    val userMessage: String? = null
)

class SettingsViewModel(
    private val splitManager: SplitTunnelManager = App.instance.splitTunnelManager,
    private val rootTunnelManager: com.example.vpn.RootTunnelManager = App.instance.rootTunnelManager
) : ViewModel() {

    val appStats: StateFlow<List<com.example.domain.model.AppTrafficStat>> = App.instance.appTrafficTracker.appStats

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            splitMode = splitManager.mode,
            selectedPackages = splitManager.getSelectedPackages(),
            isRootModeEnabled = rootTunnelManager.isRootModeEnabled,
            pingTargets = App.instance.pingTester.defaultTargets
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadInstalledApps()
        checkRootAvailability()
        viewModelScope.launch {
            App.instance.appTrafficTracker.refreshOnce()
        }
    }

    private fun checkRootAvailability() {
        viewModelScope.launch {
            val available = com.example.util.RootRunner.isRootAvailable()
            _uiState.value = _uiState.value.copy(isRootDeviceAvailable = available)
        }
    }

    fun setRootModeEnabled(enabled: Boolean) {
        rootTunnelManager.isRootModeEnabled = enabled
        _uiState.value = _uiState.value.copy(isRootModeEnabled = enabled)
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
