package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.ConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConfigDao {
    @Query("SELECT * FROM vpn_configs ORDER BY createdAt DESC")
    fun getAllConfigs(): Flow<List<ConfigEntity>>

    @Query("SELECT * FROM vpn_configs WHERE id = :id LIMIT 1")
    suspend fun getConfigById(id: String): ConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: ConfigEntity)

    @Update
    suspend fun updateConfig(config: ConfigEntity)

    @Query("DELETE FROM vpn_configs WHERE id = :id")
    suspend fun deleteConfigById(id: String)

    @Query("DELETE FROM vpn_configs")
    suspend fun deleteAllConfigs()
}
