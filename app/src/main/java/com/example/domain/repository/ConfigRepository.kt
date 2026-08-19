package com.example.domain.repository

import com.example.domain.model.AwgConfig
import kotlinx.coroutines.flow.Flow

interface ConfigRepository {
    fun getAllConfigs(): Flow<List<AwgConfig>>
    suspend fun getConfigById(id: String): AwgConfig?
    suspend fun saveConfig(config: AwgConfig)
    suspend fun updateConfig(config: AwgConfig)
    suspend fun deleteConfigById(id: String)
    suspend fun deleteAllConfigs()
}
