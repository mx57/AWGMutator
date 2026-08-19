package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.EvolutionLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EvolutionDao {
    @Query("SELECT * FROM evolution_logs ORDER BY timestamp DESC LIMIT 200")
    fun getRecentLogs(): Flow<List<EvolutionLogEntity>>

    @Query("SELECT * FROM evolution_logs WHERE sessionId = :sessionId ORDER BY id ASC")
    suspend fun getLogsForSession(sessionId: String): List<EvolutionLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: EvolutionLogEntity)

    @Query("DELETE FROM evolution_logs")
    suspend fun clearLogs()
}
