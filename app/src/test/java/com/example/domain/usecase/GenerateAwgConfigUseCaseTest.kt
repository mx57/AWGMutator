package com.example.domain.usecase

import com.example.domain.model.AwgConfig
import com.example.domain.repository.ConfigRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GenerateAwgConfigUseCaseTest {

    private lateinit var fakeRepository: FakeConfigRepository
    private lateinit var useCase: GenerateAwgConfigUseCase

    @Before
    fun setUp() {
        fakeRepository = FakeConfigRepository()
        useCase = GenerateAwgConfigUseCase(fakeRepository)
    }

    @Test
    fun testGenerateDefaultConfigSuccess() = runBlocking {
        val result = useCase()
        assertTrue(result.isSuccess)

        val config = result.getOrThrow()
        assertEquals("AmneziaWG Custom", config.name)
        assertEquals("188.114.97.1:1074", config.endpoint)
        assertEquals("bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=", config.peerPublicKey)
        assertNotNull(config.privateKey)
        assertTrue(config.privateKey.isNotBlank())
        assertNotNull(config.i1)
        assertTrue(config.i1!!.startsWith("<b 0x"))

        // Check stored in repository
        val saved = fakeRepository.getConfigById(config.id)
        assertNotNull(saved)
        assertEquals(config.id, saved?.id)
    }

    @Test
    fun testGenerateConfigForPresets() = runBlocking {
        for (preset in ObfuscationPreset.values()) {
            val result = useCase(preset = preset)
            assertTrue("Failed for preset $preset", result.isSuccess)
            val config = result.getOrThrow()

            assertTrue(config.jmin <= config.jmax)
            assertTrue(config.jc >= 0)
            assertTrue(config.s1 >= 0)

            if (preset == ObfuscationPreset.VERIFIED_AWG_RUSSIAN_BYPASS || preset == ObfuscationPreset.EXTREME_ANTI_DPI) {
                assertNotNull("Preset $preset should generate I1 noise", config.i1)
            }
        }
    }

    @Test
    fun testCustomParametersOverride() = runBlocking {
        val result = useCase(
            name = "Custom Tunnel",
            customJc = 8,
            customJmin = 45,
            customJmax = 900,
            customS1 = 20,
            customS2 = 30,
            customH1 = 99999L,
            customI1 = "custom_noise_str",
            customSni = "example.com",
            isWarp = true,
            reserved = "1,2,3"
        )

        assertTrue(result.isSuccess)
        val config = result.getOrThrow()

        assertEquals("Custom Tunnel", config.name)
        assertEquals(8, config.jc)
        assertEquals(45, config.jmin)
        assertEquals(900, config.jmax)
        assertEquals(20, config.s1)
        assertEquals(30, config.s2)
        assertEquals(99999L, config.h1)
        assertEquals("custom_noise_str", config.i1)
        assertEquals("example.com", config.sni)
        assertTrue(config.isWarp)
        assertEquals("1,2,3", config.reserved)
    }

    private class FakeConfigRepository : ConfigRepository {
        private val configs = mutableMapOf<String, AwgConfig>()
        private val flow = MutableStateFlow<List<AwgConfig>>(emptyList())

        override fun getAllConfigs(): Flow<List<AwgConfig>> = flow

        override suspend fun getConfigById(id: String): AwgConfig? = configs[id]

        override suspend fun saveConfig(config: AwgConfig) {
            configs[config.id] = config
            flow.value = configs.values.toList()
        }

        override suspend fun updateConfig(config: AwgConfig) {
            saveConfig(config)
        }

        override suspend fun deleteConfigById(id: String) {
            configs.remove(id)
            flow.value = configs.values.toList()
        }

        override suspend fun deleteAllConfigs() {
            configs.clear()
            flow.value = emptyList()
        }
    }
}
