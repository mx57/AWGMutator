package com.example.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.data.local.dao.ConfigDao
import com.example.data.local.dao.EvolutionDao
import com.example.data.local.entity.ConfigEntity
import com.example.data.local.entity.EvolutionLogEntity

@Database(
    entities = [ConfigEntity::class, EvolutionLogEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun configDao(): ConfigDao
    abstract fun evolutionDao(): EvolutionDao
}
