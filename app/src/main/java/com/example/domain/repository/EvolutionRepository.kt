package com.example.domain.repository

import com.example.data.local.entity.EvolutionLogEntity
import kotlinx.coroutines.flow.Flow

interface EvolutionRepository {
    fun getRecentLogs(): Flow<List<EvolutionLogEntity>>
    suspend fun getLogsForSession(sessionId: String): List<EvolutionLogEntity>
    suspend fun recordLog(log: EvolutionLogEntity)
    suspend fun clearLogs()
}
