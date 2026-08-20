package com.example.presentation.dashboard

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.App
import com.example.domain.model.AppTrafficStat
import com.example.domain.model.AwgConfig
import com.example.domain.model.BlockedServicesCatalog
import com.example.domain.model.NetworkEgressResult
import com.example.domain.model.ServiceProbeResult
import com.example.domain.model.VpnState
import com.example.domain.model.VpnStatus
import com.example.domain.repository.ConfigRepository
import com.example.domain.usecase.GenerateHybridWarpAwgUseCase
import com.example.domain.usecase.GenerateWarpConfigUseCase
import com.example.util.RootRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DashboardUiState(
    val isGeneratingWarp: Boolean = false,
    val isTestingSpeed: Boolean = false,
    val isTestingServices: Boolean = false,
    val isVerifyingEgress: Boolean = false,
    val selectedConfig: AwgConfig? = null,
    val measuredPingMs: Long? = null,
    val egressResult: NetworkEgressResult? = null,
    val serviceResults: List<ServiceProbeResult> = emptyList(),
    val userMessage: String? = null,
    val vpnPrepareIntent: Intent? = null
)

class DashboardViewModel(
    private val configRepository: ConfigRepository = App.instance.configRepository,
    private val generateWarpUseCase: GenerateWarpConfigUseCase = GenerateWarpConfigUseCase(
        App.instance.cloudflareApi,
        App.instance.configRepository
    ),
    private val generateHybridUseCase: GenerateHybridWarpAwgUseCase = GenerateHybridWarpAwgUseCase(
        App.instance.cloudflareApi,
        App.instance.configRepository
    )
) : ViewModel() {

    // Merge standard VPN and Root VPN statuses
    val vpnStatus: StateFlow<VpnStatus> = if (App.instance.rootTunnelManager.isRootModeEnabled) {
        App.instance.rootTunnelManager.status
    } else {
        App.instance.tunnelManager.status
    }

    val appStats: StateFlow<List<AppTrafficStat>> = App.instance.appTrafficTracker.appStats

    val isTrafficMonitoringEnabled: StateFlow<Boolean> = App.instance.appTrafficTracker.isMonitoringEnabled

    val tunnelLogs: StateFlow<List<String>> = App.instance.tunnelManager.logs

    val configs: StateFlow<List<AwgConfig>> = configRepository.getAllConfigs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        // Initial service status check
        checkBlockedServices()
    }

    fun selectConfig(config: AwgConfig) {
        _uiState.value = _uiState.value.copy(selectedConfig = config)
    }

    fun toggleVpn(context: Context) {
        val isRootMode = App.instance.rootTunnelManager.isRootModeEnabled
        val currentStatus = vpnStatus.value

        if (currentStatus.state == VpnState.CONNECTED || currentStatus.state == VpnState.CONNECTING) {
            viewModelScope.launch {
                if (isRootMode) {
                    App.instance.rootTunnelManager.disconnect()
                }
                App.instance.tunnelManager.disconnect()
            }
        } else {
            val configToUse = _uiState.value.selectedConfig ?: configs.value.firstOrNull()
            if (configToUse != null) {
                if (isRootMode) {
                    viewModelScope.launch {
                        val isRootAvailable = RootRunner.isRootAvailable()
                        if (isRootAvailable) {
                            val res = App.instance.rootTunnelManager.connect(configToUse)
                            if (res.isFailure) {
                                _uiState.value = _uiState.value.copy(
                                    userMessage = "Root error: ${res.exceptionOrNull()?.message}"
                                )
                            } else {
                                verifyNetworkEgress()
                                checkBlockedServices()
                            }
                        } else {
                            // Device is not rooted, fallback automatically to standard Android VpnService
                            _uiState.value = _uiState.value.copy(
                                userMessage = "Root (su) not found on device. Connecting via standard Android VPN..."
                            )
                            startStandardVpn(context, configToUse)
                        }
                    }
                } else {
                    startStandardVpn(context, configToUse)
                }
            } else {
                // Auto-generate WARP config if none exist
                _uiState.value = _uiState.value.copy(
                    userMessage = "Generating WARP config for instant connection..."
                )
                generateQuickWarp()
            }
        }
    }

    private fun startStandardVpn(context: Context, config: AwgConfig) {
        val prepareIntent = VpnService.prepare(context)
        if (prepareIntent == null) {
            // VPN permission already granted
            App.instance.tunnelManager.connect(config)
            verifyNetworkEgress()
            checkBlockedServices()
        } else {
            // Launch OS system VPN dialog
            _uiState.value = _uiState.value.copy(vpnPrepareIntent = prepareIntent)
        }
    }

    fun startVpnDirectly() {
        val configToUse = _uiState.value.selectedConfig ?: configs.value.firstOrNull()
        if (configToUse != null) {
            App.instance.tunnelManager.connect(configToUse)
            verifyNetworkEgress()
            checkBlockedServices()
        }
    }

    fun clearVpnPrepareIntent() {
        _uiState.value = _uiState.value.copy(vpnPrepareIntent = null)
    }

    fun onVpnPermissionDenied() {
        _uiState.value = _uiState.value.copy(
            userMessage = "VPN permission was not granted. Please allow the VPN connection prompt."
        )
    }

    fun verifyNetworkEgress() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isVerifyingEgress = true)
            val egress = App.instance.networkEgressVerifier.verifyEgress()
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    isVerifyingEgress = false,
                    egressResult = egress,
                    userMessage = if (egress.isFunctional) {
                        "Internet Exit Verified: IP ${egress.publicIp} [${egress.countryCode}] • Latency ${egress.latencyMs}ms"
                    } else {
                        "Exit Check: ${egress.errorMessage ?: "No internet access detected"}"
                    }
                )
            }
        }
    }

    fun checkBlockedServices() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isTestingServices = true)
            val results = BlockedServicesCatalog.allServices.map { service ->
                val ok = App.instance.networkEgressVerifier.probeUrl(service.testUrl)
                ServiceProbeResult(
                    service = service,
                    isAccessible = ok
                )
            }
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    isTestingServices = false,
                    serviceResults = results
                )
            }
        }
    }

    fun runSpeedPingCheck() {
        val activeConfig = _uiState.value.selectedConfig ?: configs.value.firstOrNull() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isTestingSpeed = true)
            val pingResult = App.instance.pingTester.testEndpoint(activeConfig.endpoint)
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    isTestingSpeed = false,
                    measuredPingMs = pingResult.latencyMs,
                    userMessage = "Endpoint ${activeConfig.endpoint}: Latency ${pingResult.latencyMs}ms"
                )
            }
        }
    }

    fun generateQuickWarp() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGeneratingWarp = true)
            val result = generateWarpUseCase()
            if (result.isSuccess) {
                val newConfig = result.getOrNull()
                _uiState.value = _uiState.value.copy(
                    isGeneratingWarp = false,
                    selectedConfig = newConfig,
                    userMessage = "WARP profile generated successfully! Ready to connect."
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isGeneratingWarp = false,
                    userMessage = "Error generating WARP: ${result.exceptionOrNull()?.message}"
                )
            }
        }
    }

    fun generateHybridAntiDpi() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGeneratingWarp = true)
            val result = generateHybridUseCase()
            if (result.isSuccess) {
                val newConfig = result.getOrNull()
                _uiState.value = _uiState.value.copy(
                    isGeneratingWarp = false,
                    selectedConfig = newConfig,
                    userMessage = "WARP + Anti-DPI profile created with customized handshake noise parameters!"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isGeneratingWarp = false,
                    userMessage = "Error generating Hybrid profile: ${result.exceptionOrNull()?.message}"
                )
            }
        }
    }

    fun fixBlockedEndpointAndReconnect(context: Context) {
        val currentConfig = _uiState.value.selectedConfig ?: configs.value.firstOrNull() ?: return
        viewModelScope.launch {
            val cleanEndpoint = "188.114.97.1:854"
            val cleanDns = if (currentConfig.dns.isBlank() || currentConfig.dns.contains("111.88")) "1.1.1.1, 1.0.0.1" else currentConfig.dns
            val updatedConfig = currentConfig.copy(
                endpoint = cleanEndpoint,
                dns = cleanDns
            )
            configRepository.updateConfig(updatedConfig)
            _uiState.value = _uiState.value.copy(
                selectedConfig = updatedConfig,
                userMessage = "Эндпоинт обновлен на $cleanEndpoint. Выполняется переподключение..."
            )
            App.instance.tunnelManager.disconnect()
            kotlinx.coroutines.delay(700)
            App.instance.tunnelManager.connect(updatedConfig)
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(userMessage = null)
    }

    fun clearTunnelLogs() {
        App.instance.tunnelManager.clearLogs()
    }

    fun setTrafficMonitoringEnabled(enabled: Boolean) {
        App.instance.appTrafficTracker.setMonitoringEnabled(enabled)
    }
}
