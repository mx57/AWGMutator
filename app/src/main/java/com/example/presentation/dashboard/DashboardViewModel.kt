package com.example.presentation.dashboard

import android.content.Context
import android.net.VpnService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.App
import com.example.domain.model.AwgConfig
import com.example.domain.model.BlockedServicesCatalog
import com.example.domain.model.ServiceProbeResult
import com.example.domain.model.VpnState
import com.example.domain.model.VpnStatus
import com.example.domain.repository.ConfigRepository
import com.example.domain.usecase.GenerateHybridWarpAwgUseCase
import com.example.domain.usecase.GenerateWarpConfigUseCase
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
    val selectedConfig: AwgConfig? = null,
    val measuredPingMs: Long? = null,
    val serviceResults: List<ServiceProbeResult> = emptyList(),
    val userMessage: String? = null
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

    val vpnStatus: StateFlow<VpnStatus> = App.instance.tunnelManager.status

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
        val currentStatus = vpnStatus.value
        if (currentStatus.state == VpnState.CONNECTED || currentStatus.state == VpnState.CONNECTING) {
            App.instance.tunnelManager.disconnect()
        } else {
            val configToUse = _uiState.value.selectedConfig ?: configs.value.firstOrNull()
            if (configToUse != null) {
                val prepareIntent = VpnService.prepare(context)
                if (prepareIntent == null) {
                    App.instance.tunnelManager.connect(configToUse)
                    // Trigger service reachability check after connecting
                    checkBlockedServices()
                } else {
                    _uiState.value = _uiState.value.copy(
                        userMessage = "VPN permission required. Please grant permission."
                    )
                }
            } else {
                _uiState.value = _uiState.value.copy(
                    userMessage = "No config available. Generate WARP or create an AWG config first."
                )
            }
        }
    }

    fun generateQuickWarp() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isGeneratingWarp = true)
            val result = generateWarpUseCase("Cloudflare WARP (Auto)")
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    val created = result.getOrThrow()
                    _uiState.value = _uiState.value.copy(
                        isGeneratingWarp = false,
                        selectedConfig = created,
                        userMessage = "Cloudflare WARP config created successfully!"
                    )
                } else {
                    val msg = result.exceptionOrNull()?.localizedMessage ?: "Unknown error"
                    _uiState.value = _uiState.value.copy(
                        isGeneratingWarp = false,
                        userMessage = "WARP generation note: $msg"
                    )
                }
            }
        }
    }

    fun generateHybridAntiDpi() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isGeneratingWarp = true)
            val result = generateHybridUseCase("WARP + Anti-DPI Obfuscation")
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    val created = result.getOrThrow()
                    _uiState.value = _uiState.value.copy(
                        isGeneratingWarp = false,
                        selectedConfig = created,
                        userMessage = "Hybrid Anti-DPI profile created!"
                    )
                } else {
                    val msg = result.exceptionOrNull()?.localizedMessage ?: "Unknown error"
                    _uiState.value = _uiState.value.copy(
                        isGeneratingWarp = false,
                        userMessage = "Hybrid generation note: $msg"
                    )
                }
            }
        }
    }

    fun checkBlockedServices() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isTestingServices = true)
            val results = App.instance.pingTester.evaluateBlockedServices(BlockedServicesCatalog.allServices)
            val unblockedCount = results.count { it.isAccessible }
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    isTestingServices = false,
                    serviceResults = results,
                    userMessage = "Checked ${results.size} blocked services: $unblockedCount/${results.size} unblocked"
                )
            }
        }
    }

    fun runSpeedPingCheck() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isTestingSpeed = true)
            val fitnessResult = App.instance.pingTester.evaluateTargets(
                genomeId = "manual_check",
                targets = App.instance.pingTester.defaultTargets,
                attemptsPerTarget = 1
            )
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    isTestingSpeed = false,
                    measuredPingMs = if (fitnessResult.successRate > 0) fitnessResult.avgPingMs else null,
                    userMessage = if (fitnessResult.successRate > 0) {
                        "Ping tested: ${fitnessResult.avgPingMs} ms (${(fitnessResult.successRate * 100).toInt()}% reachability)"
                    } else {
                        "Ping check: host unreachable"
                    }
                )
            }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(userMessage = null)
    }
}
