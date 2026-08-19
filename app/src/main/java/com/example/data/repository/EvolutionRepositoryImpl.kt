package com.example.data.repository

import com.example.data.local.dao.EvolutionDao
import com.example.data.local.entity.EvolutionLogEntity
import com.example.domain.repository.EvolutionRepository
import kotlinx.coroutines.flow.Flow

class EvolutionRepositoryImpl(
    private val evolutionDao: EvolutionDao
) : EvolutionRepository {

    override fun getRecentLogs(): Flow<List<EvolutionLogEntity>> {
        return evolutionDao.getRecentLogs()
    }

    override suspend fun getLogsForSession(sessionId: String): List<EvolutionLogEntity> {
        return evolutionDao.getLogsForSession(sessionId)
    }

    override suspend fun recordLog(log: EvolutionLogEntity) {
        evolutionDao.insertLog(log)
    }

    override suspend fun clearLogs() {
        evolutionDao.clearLogs()
    }
}
