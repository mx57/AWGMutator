package com.example.evolution

import com.example.data.local.entity.EvolutionLogEntity
import com.example.data.remote.PingTester
import com.example.domain.model.AwgConfig
import com.example.domain.repository.EvolutionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneticAlgorithmTest {

    private class FakeEvolutionRepository : EvolutionRepository {
        val loggedEntries = mutableListOf<EvolutionLogEntity>()

        override fun getRecentLogs(): Flow<List<EvolutionLogEntity>> = flowOf(loggedEntries)
        override suspend fun getLogsForSession(sessionId: String): List<EvolutionLogEntity> =
            loggedEntries.filter { it.sessionId == sessionId }

        override suspend fun recordLog(log: EvolutionLogEntity) {
            loggedEntries.add(log)
        }

        override suspend fun clearLogs() {
            loggedEntries.clear()
        }
    }

    @Test
    fun testRunEvolutionCompletesAndReturnsBestGenome() = runTest {
        val pingTester = PingTester()
        val fakeRepo = FakeEvolutionRepository()
        val algorithm = GeneticAlgorithm(pingTester, fakeRepo)

        val seedConfigs = listOf(
            AwgConfig(name = "Test Seed 1", privateKey = "a".repeat(43) + "=", jc = 3, jmin = 40, jmax = 70, s1 = 15, s2 = 15),
            AwgConfig(name = "Test Seed 2", privateKey = "b".repeat(43) + "=", jc = 5, jmin = 50, jmax = 80, s1 = 20, s2 = 20)
        )

        val result = algorithm.runEvolution(
            seedConfigs = seedConfigs,
            populationSize = 4,
            maxGenerations = 2,
            targetUrls = emptyList()
        )

        assertNotNull(result)
        assertEquals(EvolutionPhase.COMPLETED, algorithm.progress.value.phase)
        assertTrue(fakeRepo.loggedEntries.isNotEmpty())
    }

    @Test
    fun testStopCancelsEvolutionRun() = runTest {
        val pingTester = PingTester()
        val fakeRepo = FakeEvolutionRepository()
        val algorithm = GeneticAlgorithm(pingTester, fakeRepo)

        algorithm.stop()
        assertEquals(EvolutionPhase.CANCELLED, algorithm.progress.value.phase)
    }
}
