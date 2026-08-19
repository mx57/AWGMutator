package com.example.presentation.evolution

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.App
import com.example.domain.model.AwgConfig
import com.example.domain.model.BlockedServicesCatalog
import com.example.domain.model.DnsCatalog
import com.example.domain.model.EvolutionSettings
import com.example.domain.model.Genome
import com.example.domain.model.ServiceCategory
import com.example.domain.model.SniCatalog
import com.example.domain.repository.ConfigRepository
import com.example.evolution.EvolutionProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class EvolutionTargetProfile(val label: String) {
    ALL_BLOCKED_PLATFORMS("All Censored Platforms (YouTube, Insta, TG, Twitch)"),
    VIDEO_STREAMING_ONLY("Video & Streaming (YouTube, Twitch, TikTok)"),
    MESSENGERS_SOCIAL_ONLY("Social & Messengers (Telegram, Instagram, X)")
}

data class EvolutionScreenState(
    val selectedBaseConfig: AwgConfig? = null,
    val targetProfile: EvolutionTargetProfile = EvolutionTargetProfile.ALL_BLOCKED_PLATFORMS,
    val populationSize: Int = 8,
    val maxGenerations: Int = 10,
    val userMessage: String? = null,
    val evolutionSettings: EvolutionSettings = EvolutionSettings(
        mutateJc = true,
        mutateJminJmax = true,
        mutateS1S2 = true,
        mutateS3S4 = true,
        mutateHeadersH1H4 = true,
        mutatePayloadNoiseI1 = true,
        mutateMtu = true,
        mutateDns = true,
        mutateEndpoints = true,
        mutateSni = true,
        selectedDnsIds = setOf("cu_uncensored", "cf_standard", "google", "quad9_unfiltered", "adguard_unfiltered")
    ),
    val showSettingsDialog: Boolean = false,
    val showDnsSelectionDialog: Boolean = false,
    val showSniSelectionDialog: Boolean = false
)

class EvolutionViewModel(
    private val configRepository: ConfigRepository = App.instance.configRepository
) : ViewModel() {

    val evolutionProgress: StateFlow<EvolutionProgress> = App.instance.geneticAlgorithm.progress

    val configs: StateFlow<List<AwgConfig>> = configRepository.getAllConfigs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _screenState = MutableStateFlow(EvolutionScreenState())
    val screenState: StateFlow<EvolutionScreenState> = _screenState.asStateFlow()

    private var evolutionJob: Job? = null

    fun selectBaseConfig(config: AwgConfig) {
        _screenState.value = _screenState.value.copy(selectedBaseConfig = config)
    }

    fun selectTargetProfile(profile: EvolutionTargetProfile) {
        _screenState.value = _screenState.value.copy(targetProfile = profile)
    }

    fun updatePopulationSize(size: Int) {
        _screenState.value = _screenState.value.copy(populationSize = size.coerceIn(4, 24))
    }

    fun updateMaxGenerations(gens: Int) {
        _screenState.value = _screenState.value.copy(maxGenerations = gens.coerceIn(2, 30))
    }

    fun toggleSettingDialog(show: Boolean) {
        _screenState.value = _screenState.value.copy(showSettingsDialog = show)
    }

    fun toggleDnsDialog(show: Boolean) {
        _screenState.value = _screenState.value.copy(showDnsSelectionDialog = show)
    }

    fun toggleSniDialog(show: Boolean) {
        _screenState.value = _screenState.value.copy(showSniSelectionDialog = show)
    }

    fun toggleDnsSelection(dnsId: String) {
        val current = _screenState.value.evolutionSettings.selectedDnsIds.toMutableSet()
        if (current.contains(dnsId)) {
            current.remove(dnsId)
        } else {
            current.add(dnsId)
        }
        _screenState.value = _screenState.value.copy(
            evolutionSettings = _screenState.value.evolutionSettings.copy(selectedDnsIds = current)
        )
    }

    fun toggleSniSelection(sniId: String) {
        val current = _screenState.value.evolutionSettings.selectedSniIds.toMutableSet()
        if (current.contains(sniId)) {
            current.remove(sniId)
        } else {
            current.add(sniId)
        }
        _screenState.value = _screenState.value.copy(
            evolutionSettings = _screenState.value.evolutionSettings.copy(selectedSniIds = current)
        )
    }

    fun updateEvolutionSettings(transform: (EvolutionSettings) -> EvolutionSettings) {
        _screenState.value = _screenState.value.copy(
            evolutionSettings = transform(_screenState.value.evolutionSettings)
        )
    }

    fun startEvolution() {
        val baseConfig = _screenState.value.selectedBaseConfig ?: configs.value.firstOrNull()
        if (baseConfig == null) {
            _screenState.value = _screenState.value.copy(
                userMessage = "Please select or create a base configuration to evolve."
            )
            return
        }

        val targetUrls = when (_screenState.value.targetProfile) {
            EvolutionTargetProfile.ALL_BLOCKED_PLATFORMS -> BlockedServicesCatalog.allServices.map { it.testUrl }
            EvolutionTargetProfile.VIDEO_STREAMING_ONLY -> BlockedServicesCatalog.allServices
                .filter { it.category == ServiceCategory.VIDEO_STREAMING }
                .map { it.testUrl }
            EvolutionTargetProfile.MESSENGERS_SOCIAL_ONLY -> BlockedServicesCatalog.allServices
                .filter { it.category == ServiceCategory.MESSENGER || it.category == ServiceCategory.SOCIAL_NETWORK }
                .map { it.testUrl }
        }

        evolutionJob?.cancel()
        evolutionJob = viewModelScope.launch(Dispatchers.Default) {
            App.instance.geneticAlgorithm.runEvolution(
                baseConfig = baseConfig,
                populationSize = _screenState.value.populationSize,
                maxGenerations = _screenState.value.maxGenerations,
                targetUrls = targetUrls,
                settings = _screenState.value.evolutionSettings
            )
        }
    }

    fun stopEvolution() {
        App.instance.geneticAlgorithm.stop()
        evolutionJob?.cancel()
        evolutionJob = null
    }

    fun applyEvolvedConfig(genome: Genome) {
        val base = _screenState.value.selectedBaseConfig ?: configs.value.firstOrNull() ?: return
        val evolvedConfig = genome.applyToConfig(base)
        viewModelScope.launch(Dispatchers.IO) {
            configRepository.saveConfig(evolvedConfig)
            withContext(Dispatchers.Main) {
                App.instance.tunnelManager.connect(evolvedConfig)
                _screenState.value = _screenState.value.copy(
                    userMessage = "Evolved config applied and connected!"
                )
            }
        }
    }

    fun saveEvolvedConfig(genome: Genome) {
        val base = _screenState.value.selectedBaseConfig ?: configs.value.firstOrNull() ?: return
        val evolvedConfig = genome.applyToConfig(base)
        viewModelScope.launch(Dispatchers.IO) {
            configRepository.saveConfig(evolvedConfig)
            withContext(Dispatchers.Main) {
                _screenState.value = _screenState.value.copy(
                    userMessage = "Evolved config saved to list!"
                )
            }
        }
    }

    fun clearMessage() {
        _screenState.value = _screenState.value.copy(userMessage = null)
    }
}
