package com.example.presentation.antidpi

import com.example.domain.model.AwgConfig
import com.example.domain.repository.ConfigRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeConfigRepository : ConfigRepository {
    override fun getAllConfigs(): Flow<List<AwgConfig>> = flowOf(emptyList())
    override suspend fun getConfigById(id: String): AwgConfig? = null
    override suspend fun saveConfig(config: AwgConfig) {}
    override suspend fun updateConfig(config: AwgConfig) {}
    override suspend fun deleteConfigById(id: String) {}
    override suspend fun deleteAllConfigs() {}
}

class AntiDpiViewModelTest {

    private lateinit var viewModel: AntiDpiViewModel

    @Before
    fun setUp() {
        viewModel = AntiDpiViewModel(configRepository = FakeConfigRepository())
    }

    @Test
    fun testAnalyzeStandardWireGuardConfig() {
        val standardWgConfig = AwgConfig(
            id = "1",
            name = "Standard WG",
            privateKey = "dummyKey",
            h1 = 1L,
            h2 = 2L,
            h3 = 3L,
            h4 = 4L,
            s1 = 0,
            s2 = 0,
            jc = 0,
            jmin = 40,
            jmax = 40,
            mtu = 1420
        )

        viewModel.analyzeConfig(standardWgConfig)

        val analysis = viewModel.uiState.value.analysis
        assertTrue(analysis.isWireGuardStandard)
        assertFalse(analysis.handshakeSignatureObscured)
        assertFalse(analysis.junkProtectionActive)
        assertFalse(analysis.headerEntropyHigh)
        assertEquals("High DPI Detection Risk", analysis.rating)
        // Score deductions:
        // standard headers: -35
        // zero s1/s2: -25
        // jc = 0: -20 (else if for jmax <= jmin is skipped)
        // Total score = 100 - 80 = 20
        assertEquals(20, analysis.score)
        assertEquals(3, analysis.vulnerabilities.size)
    }

    @Test
    fun testAnalyzeStealthAntiDpiConfig() {
        val stealthConfig = AwgConfig(
            id = "2",
            name = "Stealth AWG",
            privateKey = "dummyKey",
            h1 = 12345678L,
            h2 = 23456789L,
            h3 = 34567890L,
            h4 = 45678901L,
            s1 = 20,
            s2 = 25,
            jc = 5,
            jmin = 40,
            jmax = 200,
            mtu = 1360
        )

        viewModel.analyzeConfig(stealthConfig)

        val analysis = viewModel.uiState.value.analysis
        assertFalse(analysis.isWireGuardStandard)
        assertTrue(analysis.handshakeSignatureObscured)
        assertTrue(analysis.junkProtectionActive)
        assertTrue(analysis.headerEntropyHigh)
        assertEquals(100, analysis.score)
        assertEquals("Ultra Stealth (DPI Immune)", analysis.rating)
        assertTrue(analysis.vulnerabilities.isEmpty())
    }

    @Test
    fun testHeaderCollisionDetection() {
        val duplicateHeaderConfig = AwgConfig(
            id = "3",
            name = "Duplicate Headers",
            privateKey = "dummyKey",
            h1 = 1000L,
            h2 = 1000L,
            h3 = 2000L,
            h4 = 3000L,
            s1 = 20,
            s2 = 20,
            jc = 3,
            jmin = 50,
            jmax = 150,
            mtu = 1360
        )

        viewModel.analyzeConfig(duplicateHeaderConfig)

        val analysis = viewModel.uiState.value.analysis
        assertFalse(analysis.isWireGuardStandard)
        assertFalse(analysis.headerEntropyHigh)
        assertEquals(80, analysis.score)
        assertEquals("Moderate Protection", analysis.rating)
        assertEquals(1, analysis.vulnerabilities.size)
        assertEquals("Header Magic Number Collision", analysis.vulnerabilities[0].title)
    }

    @Test
    fun testHighMtuDeduction() {
        val highMtuConfig = AwgConfig(
            id = "4",
            name = "High MTU",
            privateKey = "dummyKey",
            h1 = 12345678L,
            h2 = 23456789L,
            h3 = 34567890L,
            h4 = 45678901L,
            s1 = 20,
            s2 = 25,
            jc = 5,
            jmin = 40,
            jmax = 200,
            mtu = 1500
        )

        viewModel.analyzeConfig(highMtuConfig)

        val analysis = viewModel.uiState.value.analysis
        assertEquals(90, analysis.score)
        assertEquals("Ultra Stealth (DPI Immune)", analysis.rating)
        assertEquals(1, analysis.vulnerabilities.size)
        assertEquals("High MTU (1500)", analysis.vulnerabilities[0].title)
    }
}
