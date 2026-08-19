package com.example.presentation.antidpi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.App
import com.example.domain.model.AwgConfig
import com.example.domain.repository.ConfigRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Random

data class DpiVulnerability(
    val title: String,
    val severity: DpiSeverity,
    val description: String,
    val recommendation: String
)

enum class DpiSeverity {
    CRITICAL,
    WARNING,
    OPTIMAL
}

data class DpiAnalysisResult(
    val score: Int = 100,
    val rating: String = "Ultra Stealth",
    val vulnerabilities: List<DpiVulnerability> = emptyList(),
    val isWireGuardStandard: Boolean = false,
    val handshakeSignatureObscured: Boolean = true,
    val junkProtectionActive: Boolean = true,
    val headerEntropyHigh: Boolean = true
)

data class AntiDpiUiState(
    val selectedConfig: AwgConfig? = null,
    val analysis: DpiAnalysisResult = DpiAnalysisResult(),
    val isAnalyzing: Boolean = false,
    val isTestingServices: Boolean = false,
    val serviceResults: List<com.example.domain.model.ServiceProbeResult> = emptyList(),
    val userMessage: String? = null
)

class AntiDpiViewModel(
    private val configRepository: ConfigRepository = App.instance.configRepository
) : ViewModel() {

    val configs: StateFlow<List<AwgConfig>> = configRepository.getAllConfigs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(AntiDpiUiState())
    val uiState: StateFlow<AntiDpiUiState> = _uiState.asStateFlow()

    private val random = Random()

    init {
        checkBlockedServices()
    }

    fun checkBlockedServices() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isTestingServices = true)
            val results = App.instance.pingTester.evaluateBlockedServices()
            val unblocked = results.count { it.isAccessible }
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    isTestingServices = false,
                    serviceResults = results,
                    userMessage = "Tested Censored Platforms: $unblocked/${results.size} accessible"
                )
            }
        }
    }

    fun selectConfig(config: AwgConfig) {
        _uiState.value = _uiState.value.copy(selectedConfig = config)
        analyzeConfig(config)
    }

    fun analyzeConfig(config: AwgConfig) {
        val vulns = mutableListOf<DpiVulnerability>()
        var score = 100

        // 1. Check Magic Headers H1..H4
        val uniqueHeaders = setOf(config.h1, config.h2, config.h3, config.h4).size
        if (config.h1 == 1L && config.h2 == 2L && config.h3 == 3L && config.h4 == 4L) {
            score -= 35
            vulns.add(
                DpiVulnerability(
                    title = "Standard WireGuard Headers Detected",
                    severity = DpiSeverity.CRITICAL,
                    description = "Default H1-H4 header bytes (1, 2, 3, 4) match standard WireGuard. ISP DPI filters (e.g. TSPU / RKN) detect and block this in < 1 second.",
                    recommendation = "Randomize H1-H4 magic numbers to unique 32-bit integers."
                )
            )
        } else if (uniqueHeaders < 4) {
            score -= 20
            vulns.add(
                DpiVulnerability(
                    title = "Header Magic Number Collision",
                    severity = DpiSeverity.WARNING,
                    description = "H1..H4 contain duplicate values, which may cause protocol desynchronization.",
                    recommendation = "Ensure all 4 header magic values are distinct."
                )
            )
        }

        // 2. Check S1..S4 Handshake Obfuscation
        if (config.s1 == 0 || config.s2 == 0) {
            score -= 25
            vulns.add(
                DpiVulnerability(
                    title = "Zero Handshake Prefix Padding",
                    severity = DpiSeverity.CRITICAL,
                    description = "Without S1/S2 padding, handshake packet length is exactly 148 and 92 bytes, which matches standard WireGuard fingerprints.",
                    recommendation = "Set S1 to 15..45 bytes and S2 to 20..55 bytes."
                )
            )
        } else if (config.s1 < 10 || config.s2 < 12) {
            score -= 10
            vulns.add(
                DpiVulnerability(
                    title = "Handshake Padding Too Short",
                    severity = DpiSeverity.WARNING,
                    description = "S1/S2 padding below 10 bytes provides marginal entropy against statistical packet size analysis.",
                    recommendation = "Increase S1 and S2 padding above 15 bytes."
                )
            )
        }

        // 3. Check Junk Packets (Jc, Jmin, Jmax)
        if (config.jc == 0) {
            score -= 20
            vulns.add(
                DpiVulnerability(
                    title = "No Junk Packets (Jc = 0)",
                    severity = DpiSeverity.WARNING,
                    description = "Initial handshake contains zero decoy junk packets. Passive DPI can track connection initiation.",
                    recommendation = "Enable Jc between 3 and 7 packets."
                )
            )
        } else if (config.jmax <= config.jmin) {
            score -= 15
            vulns.add(
                DpiVulnerability(
                    title = "Static Junk Packet Size (Jmax <= Jmin)",
                    severity = DpiSeverity.WARNING,
                    description = "All junk packets are the exact same size, creating an easily detectable signature.",
                    recommendation = "Set Jmax at least 128 bytes greater than Jmin."
                )
            )
        }

        // 4. Check MTU
        if (config.mtu > 1420) {
            score -= 10
            vulns.add(
                DpiVulnerability(
                    title = "High MTU (${config.mtu})",
                    severity = DpiSeverity.WARNING,
                    description = "MTU above 1420 may cause packet fragmentation on LTE/5G networks, leaking header structures.",
                    recommendation = "Lower MTU to 1280-1360 for maximum stealth."
                )
            )
        }

        val clampedScore = score.coerceIn(0, 100)
        val rating = when {
            clampedScore >= 85 -> "Ultra Stealth (DPI Immune)"
            clampedScore >= 60 -> "Moderate Protection"
            else -> "High DPI Detection Risk"
        }

        _uiState.value = _uiState.value.copy(
            analysis = DpiAnalysisResult(
                score = clampedScore,
                rating = rating,
                vulnerabilities = vulns,
                isWireGuardStandard = config.h1 == 1L && config.h2 == 2L && config.h3 == 3L && config.h4 == 4L,
                handshakeSignatureObscured = config.s1 > 10 && config.s2 > 10,
                junkProtectionActive = config.jc > 0,
                headerEntropyHigh = uniqueHeaders == 4 && config.h1 > 10000L
            )
        )
    }

    fun autoPatchDpiVulnerabilities() {
        val current = _uiState.value.selectedConfig ?: configs.value.firstOrNull() ?: return

        val jmin = 64 + random.nextInt(128)
        val jmax = jmin + 128 + random.nextInt(256)
        val jc = 3 + random.nextInt(4)

        val s1 = 16 + random.nextInt(24)
        val s2 = 24 + random.nextInt(24)
        val s3 = 12 + random.nextInt(16)
        val s4 = 8 + random.nextInt(12)

        val h1 = (random.nextLong() and 0x7FFFFFFF) + 1200000L
        val h2 = (random.nextLong() and 0x7FFFFFFF) + 2400000L
        val h3 = (random.nextLong() and 0x7FFFFFFF) + 3600000L
        val h4 = (random.nextLong() and 0x7FFFFFFF) + 4800000L

        val patched = current.copy(
            name = if (current.name.contains("Anti-DPI")) current.name else "${current.name} (Stealth DPI)",
            jc = jc,
            jmin = jmin,
            jmax = jmax,
            s1 = s1,
            s2 = s2,
            s3 = s3,
            s4 = s4,
            h1 = h1,
            h2 = h2,
            h3 = h3,
            h4 = h4,
            mtu = 1360
        )

        viewModelScope.launch(Dispatchers.IO) {
            configRepository.saveConfig(patched)
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    selectedConfig = patched,
                    userMessage = "DPI vulnerabilities patched! Profile upgraded to Ultra Stealth."
                )
                analyzeConfig(patched)
            }
        }
    }

    fun applyAntiDpiPreset(presetName: String) {
        val current = _uiState.value.selectedConfig ?: configs.value.firstOrNull() ?: return

        val patched = when (presetName) {
            "TSPU_RKN" -> {
                current.copy(
                    jc = 5,
                    jmin = 128,
                    jmax = 512,
                    s1 = 28,
                    s2 = 36,
                    s3 = 24,
                    s4 = 16,
                    h1 = 1782940214L,
                    h2 = 2948192041L,
                    h3 = 3847192031L,
                    h4 = 4192847192L,
                    mtu = 1360
                )
            }
            "GFW_DEEP" -> {
                current.copy(
                    jc = 7,
                    jmin = 256,
                    jmax = 896,
                    s1 = 36,
                    s2 = 48,
                    s3 = 30,
                    s4 = 20,
                    h1 = 2049182941L,
                    h2 = 3194819204L,
                    h3 = 4019283719L,
                    h4 = 1849201948L,
                    mtu = 1280
                )
            }
            "GAMING_FAST" -> {
                current.copy(
                    jc = 2,
                    jmin = 40,
                    jmax = 120,
                    s1 = 12,
                    s2 = 16,
                    s3 = 8,
                    s4 = 4,
                    h1 = 1192847192L,
                    h2 = 2294819204L,
                    h3 = 3384719203L,
                    h4 = 4492847192L,
                    mtu = 1380
                )
            }
            else -> current
        }

        viewModelScope.launch(Dispatchers.IO) {
            configRepository.saveConfig(patched)
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    selectedConfig = patched,
                    userMessage = "Preset '$presetName' applied!"
                )
                analyzeConfig(patched)
            }
        }
    }

    fun applyNoiseProfile(profile: com.example.domain.noise.NoiseProfile) {
        val current = _uiState.value.selectedConfig ?: configs.value.firstOrNull() ?: return
        val noisyConfig = App.instance.dpiNoiseManager.injectNoiseIntoConfig(current, profile)

        viewModelScope.launch(Dispatchers.IO) {
            configRepository.saveConfig(noisyConfig)
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    selectedConfig = noisyConfig,
                    userMessage = "DpiNoiseManager applied ${profile.name} padding and modulation!"
                )
                analyzeConfig(noisyConfig)
            }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(userMessage = null)
    }
}
