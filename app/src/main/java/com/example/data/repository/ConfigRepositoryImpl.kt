package com.example.data.repository

import com.example.data.local.dao.ConfigDao
import com.example.data.local.entity.ConfigEntity
import com.example.domain.model.AwgConfig
import com.example.domain.repository.ConfigRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ConfigRepositoryImpl(
    private val configDao: ConfigDao
) : ConfigRepository {

    override fun getAllConfigs(): Flow<List<AwgConfig>> {
        return configDao.getAllConfigs().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getConfigById(id: String): AwgConfig? {
        return configDao.getConfigById(id)?.toDomain()
    }

    override suspend fun saveConfig(config: AwgConfig) {
        configDao.insertConfig(ConfigEntity.fromDomain(config))
    }

    override suspend fun updateConfig(config: AwgConfig) {
        configDao.updateConfig(ConfigEntity.fromDomain(config))
    }

    override suspend fun deleteConfigById(id: String) {
        configDao.deleteConfigById(id)
    }

    override suspend fun deleteAllConfigs() {
        configDao.deleteAllConfigs()
    }
}
