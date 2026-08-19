package com.example.presentation.configs

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.App
import com.example.domain.model.AwgConfig
import com.example.domain.repository.ConfigRepository
import com.example.domain.usecase.GenerateAwgConfigUseCase
import com.example.domain.usecase.GenerateHybridWarpAwgUseCase
import com.example.domain.usecase.GenerateWarpConfigUseCase
import com.example.domain.usecase.ObfuscationPreset
import com.example.util.ConfigParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class ConfigsUiState(
    val isGenerating: Boolean = false,
    val showAddDialog: Boolean = false,
    val showWarpDialog: Boolean = false,
    val showImportDialog: Boolean = false,
    val searchQuery: String = "",
    val activeQrConfig: AwgConfig? = null,
    val activeExportConfig: AwgConfig? = null,
    val testingConfigId: String? = null,
    val userMessage: String? = null
)

class ConfigsViewModel(
    private val configRepository: ConfigRepository = App.instance.configRepository,
    private val generateAwgUseCase: GenerateAwgConfigUseCase = GenerateAwgConfigUseCase(App.instance.configRepository),
    private val generateWarpUseCase: GenerateWarpConfigUseCase = GenerateWarpConfigUseCase(
        App.instance.cloudflareApi,
        App.instance.configRepository
    ),
    private val generateHybridUseCase: GenerateHybridWarpAwgUseCase = GenerateHybridWarpAwgUseCase(
        App.instance.cloudflareApi,
        App.instance.configRepository
    )
) : ViewModel() {

    val configs: StateFlow<List<AwgConfig>> = configRepository.getAllConfigs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(ConfigsUiState())
    val uiState: StateFlow<ConfigsUiState> = _uiState.asStateFlow()

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun showAddDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showAddDialog = show)
    }

    fun showWarpDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showWarpDialog = show)
    }

    fun showImportDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showImportDialog = show)
    }

    fun showQrDialog(config: AwgConfig?) {
        _uiState.value = _uiState.value.copy(activeQrConfig = config)
    }

    fun showExportDialog(config: AwgConfig?) {
        _uiState.value = _uiState.value.copy(activeExportConfig = config)
    }

    fun deleteConfig(configId: String) {
        viewModelScope.launch {
            configRepository.deleteConfigById(configId)
            _uiState.value = _uiState.value.copy(userMessage = "Configuration deleted")
        }
    }

    fun duplicateConfig(config: AwgConfig) {
        viewModelScope.launch {
            val duplicated = config.copy(
                id = UUID.randomUUID().toString(),
                name = "${config.name} (Copy)"
            )
            configRepository.saveConfig(duplicated)
            _uiState.value = _uiState.value.copy(userMessage = "Profile duplicated!")
        }
    }

    fun testConfigEndpoint(config: AwgConfig) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(testingConfigId = config.id)
            val result = App.instance.pingTester.testEndpoint(config.endpoint)
            if (result.isReachable && result.latencyMs != null) {
                val updated = config.copy(lastPingMs = result.latencyMs)
                configRepository.saveConfig(updated)
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        testingConfigId = null,
                        userMessage = "✓ Endpoint '${config.endpoint}' alive! Real latency: ${result.latencyMs}ms"
                    )
                }
            } else {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        testingConfigId = null,
                        userMessage = "✗ Endpoint '${config.endpoint}' unreachable: ${result.error ?: "Timeout"}"
                    )
                }
            }
        }
    }

    fun shareConfigFile(context: Context, config: AwgConfig) {
        val confText = config.toConfString()
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "${config.name}.conf")
            putExtra(Intent.EXTRA_TEXT, confText)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share ${config.name}.conf"))
    }

    fun createCustomAwg(
        name: String,
        endpoint: String,
        peerKey: String,
        dns: String,
        preset: ObfuscationPreset?,
        jc: Int,
        jmin: Int,
        jmax: Int,
        s1: Int,
        s2: Int,
        s3: Int,
        s4: Int,
        h1: Long,
        h2: Long,
        h3: Long,
        h4: Long,
        mtu: Int
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isGenerating = true)
            val result = generateAwgUseCase(
                name = name.ifBlank { "Custom AWG" },
                endpoint = endpoint.ifBlank { "192.168.1.1:51820" },
                peerPublicKey = peerKey,
                dns = dns.ifBlank { "1.1.1.1, 1.0.0.1" },
                preset = preset,
                customJc = jc,
                customJmin = jmin,
                customJmax = jmax,
                customS1 = s1,
                customS2 = s2,
                customS3 = s3,
                customS4 = s4,
                customH1 = h1,
                customH2 = h2,
                customH3 = h3,
                customH4 = h4,
                mtu = mtu
            )
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    isGenerating = false,
                    showAddDialog = false,
                    userMessage = if (result.isSuccess) "AmneziaWG config created!" else "Failed: ${result.exceptionOrNull()?.localizedMessage}"
                )
            }
        }
    }

    fun generateAdvancedWarp(
        name: String,
        licenseKey: String?,
        dnsPreset: String,
        endpointPreset: String,
        injectAntiDpi: Boolean
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isGenerating = true)
            val result = if (injectAntiDpi) {
                generateHybridUseCase(
                    customName = name.ifBlank { "WARP + Anti-DPI Obfuscation" },
                    licenseKey = licenseKey,
                    dns = dnsPreset
                )
            } else {
                generateWarpUseCase(
                    customName = name.ifBlank { "Cloudflare WARP Profile" },
                    licenseKey = licenseKey,
                    dns = dnsPreset,
                    endpoint = endpointPreset.ifBlank { null }
                )
            }

            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    isGenerating = false,
                    showWarpDialog = false,
                    userMessage = if (result.isSuccess) "WARP profile registered with mirror auto-failover!" else "Note: ${result.exceptionOrNull()?.localizedMessage}"
                )
            }
        }
    }

    fun importConfig(rawText: String, name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val parsedResult = ConfigParser.parse(rawText, name.ifBlank { "Imported Config" })
            withContext(Dispatchers.Main) {
                if (parsedResult.isSuccess) {
                    configRepository.saveConfig(parsedResult.getOrThrow())
                    _uiState.value = _uiState.value.copy(
                        showImportDialog = false,
                        userMessage = "Configuration imported successfully!"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        userMessage = "Parse error: ${parsedResult.exceptionOrNull()?.localizedMessage}"
                    )
                }
            }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(userMessage = null)
    }
}
