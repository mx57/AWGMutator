package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "evolution_logs")
data class EvolutionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: String,
    val generation: Int,
    val genomeIndex: Int,
    val avgPingMs: Long,
    val successRate: Double,
    val fitness: Double,
    val jc: Int,
    val s1: Int,
    val s2: Int,
    val s3: Int,
    val s4: Int,
    val h1: Long,
    val h2: Long,
    val h3: Long,
    val h4: Long,
    val timestamp: Long = System.currentTimeMillis()
)
