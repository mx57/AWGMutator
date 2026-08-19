package com.example.presentation.configs

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.App
import com.example.domain.model.AwgConfig
import com.example.domain.model.DnsCatalog
import com.example.domain.model.EndpointCatalog
import com.example.domain.model.EndpointItem
import com.example.domain.model.SniCatalog
import com.example.domain.repository.ConfigRepository
import com.example.domain.usecase.EndpointScannerUseCase
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
    val showScannerDialog: Boolean = false,
    val showDnsSelectionDialog: Boolean = false,
    val showSniSelectionDialog: Boolean = false,
    val searchQuery: String = "",
    val activeQrConfig: AwgConfig? = null,
    val activeExportConfig: AwgConfig? = null,
    val testingConfigId: String? = null,
    val userMessage: String? = null,
    val selectedCountry: String = "ALL",
    val isScanningEndpoints: Boolean = false,
    val discoveredEndpoints: List<EndpointItem> = emptyList(),
    val selectedDnsList: List<String> = listOf("cu_uncensored", "cf_standard", "google"),
    val selectedSniDomain: String = "vk.com"
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
    ),
    private val scannerUseCase: EndpointScannerUseCase = EndpointScannerUseCase(App.instance.pingTester)
) : ViewModel() {

    val configs: StateFlow<List<AwgConfig>> = configRepository.getAllConfigs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(ConfigsUiState())
    val uiState: StateFlow<ConfigsUiState> = _uiState.asStateFlow()

    init {
        // Preload endpoints
        _uiState.value = _uiState.value.copy(discoveredEndpoints = EndpointCatalog.preconfiguredEndpoints)
    }

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

    fun showScannerDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showScannerDialog = show)
    }

    fun showDnsSelectionDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showDnsSelectionDialog = show)
    }

    fun showSniSelectionDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showSniSelectionDialog = show)
    }

    fun toggleDnsSelection(dnsId: String) {
        val current = _uiState.value.selectedDnsList.toMutableList()
        if (current.contains(dnsId)) {
            if (current.size > 1) current.remove(dnsId)
        } else {
            current.add(dnsId)
        }
        _uiState.value = _uiState.value.copy(selectedDnsList = current)
    }

    fun selectSni(domain: String) {
        _uiState.value = _uiState.value.copy(selectedSniDomain = domain, showSniSelectionDialog = false)
    }

    fun selectCountry(country: String) {
        _uiState.value = _uiState.value.copy(selectedCountry = country)
        scanCountryEndpoints(country)
    }

    fun scanCountryEndpoints(country: String = _uiState.value.selectedCountry) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScanningEndpoints = true)
            val results = scannerUseCase.scanCountryEndpoints(country)
            _uiState.value = _uiState.value.copy(
                isScanningEndpoints = false,
                discoveredEndpoints = results,
                userMessage = "Scanned ${results.size} endpoints for $country"
            )
        }
    }

    fun discoverNewUnknownEndpoints() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScanningEndpoints = true)
            val newCandidates = scannerUseCase.discoverNewEndpoints(count = 20, countryCode = _uiState.value.selectedCountry)
            val combined = (newCandidates + _uiState.value.discoveredEndpoints).distinctBy { it.fullEndpoint }
                .sortedWith(compareBy({ !it.isAlive }, { it.lastPingMs ?: 9999L }))
            _uiState.value = _uiState.value.copy(
                isScanningEndpoints = false,
                discoveredEndpoints = combined,
                userMessage = "Discovered ${newCandidates.count { it.isAlive }} live new endpoints!"
            )
        }
    }

    fun applyEndpointToConfig(config: AwgConfig, endpoint: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = config.copy(endpoint = endpoint)
            configRepository.saveConfig(updated)
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    showScannerDialog = false,
                    userMessage = "Updated ${config.name} endpoint to $endpoint"
                )
            }
        }
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
        i1: String?,
        sni: String?,
        mtu: Int
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isGenerating = true)
            val result = generateAwgUseCase(
                name = name.ifBlank { "Custom AWG" },
                endpoint = endpoint.ifBlank { "162.159.192.13:1074" },
                peerPublicKey = peerKey.ifBlank { "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=" },
                dns = dns.ifBlank { "111.88.96.50, 111.88.96.51" },
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
                customI1 = i1,
                customSni = sni,
                mtu = mtu
            )
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    isGenerating = false,
                    showAddDialog = false,
                    userMessage = if (result.isSuccess) "AmneziaWG Russian Bypass config created!" else "Failed: ${result.exceptionOrNull()?.localizedMessage}"
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
                    customName = name.ifBlank { "WARP + Russian Anti-DPI Obfuscation" },
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
                    userMessage = if (result.isSuccess) "WARP profile generated successfully with auto-failover!" else "Note: ${result.exceptionOrNull()?.localizedMessage}"
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
