package com.example.domain.model

/**
 * Settings controlling which genes and Anti-DPI parameters are enabled for mutation & crossover in Evolution.
 */
data class EvolutionSettings(
    val mutateJc: Boolean = true,
    val mutateJminJmax: Boolean = true,
    val mutateS1S2: Boolean = true,
    val mutateS3S4: Boolean = true,
    val mutateHeadersH1H4: Boolean = false, // Preserve server magic headers by default to prevent handshake drops
    val mutatePayloadNoiseI1: Boolean = true,
    val mutateMtu: Boolean = true,
    val mutateDns: Boolean = true,
    val mutateEndpoints: Boolean = false, // Keep original tested endpoint by default
    val mutateSni: Boolean = true,
    val preserveServerKeys: Boolean = true,
    val populationSize: Int = 10,
    val maxGenerations: Int = 15,
    val mutationRate: Float = 0.20f,
    val selectedDnsIds: Set<String> = emptySet(),
    val selectedSniIds: Set<String> = emptySet()
)
