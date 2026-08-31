package com.example.domain.usecase

import com.example.data.remote.CloudflareApi
import com.example.domain.model.AwgConfig
import com.example.domain.model.WarpConfig
import com.example.domain.repository.ConfigRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerateHybridWarpAwgUseCaseTest {

    private class FakeCloudflareApi(
        private val resultToReturn: Result<WarpConfig>
    ) : CloudflareApi() {
        var lastCapturedLicenseKey: String? = null

        override suspend fun generateWarpConfig(
            licenseKey: String?,
            dnsOverride: String?
        ): Result<WarpConfig> {
            lastCapturedLicenseKey = licenseKey
            return resultToReturn
        }
    }

    private class FakeConfigRepository(
        private val shouldFailOnSave: Boolean = false
    ) : ConfigRepository {
        val savedConfigs = mutableListOf<AwgConfig>()

        override fun getAllConfigs(): Flow<List<AwgConfig>> = flowOf(savedConfigs)

        override suspend fun getConfigById(id: String): AwgConfig? {
            return savedConfigs.find { it.id == id }
        }

        override suspend fun saveConfig(config: AwgConfig) {
            if (shouldFailOnSave) {
                throw IllegalStateException("Database save error")
            }
            savedConfigs.add(config)
        }

        override suspend fun updateConfig(config: AwgConfig) {
            val index = savedConfigs.indexOfFirst { it.id == config.id }
            if (index != -1) {
                savedConfigs[index] = config
            }
        }

        override suspend fun deleteConfigById(id: String) {
            savedConfigs.removeAll { it.id == id }
        }

        override suspend fun deleteAllConfigs() {
            savedConfigs.clear()
        }
    }

    private fun createSampleWarpConfig(
        reserved: String = "1, 2, 3",
        endpointV4: String = "162.159.192.1:2408"
    ): WarpConfig {
        return WarpConfig(
            accountId = "acc123",
            accessToken = "token123",
            privateKey = "privKey123",
            publicKey = "pubKey123",
            v4Address = "172.16.0.2/32",
            v6Address = "2606:4700:110:893c::/128",
            endpointV4 = endpointV4,
            endpointV6 = "[2606:4700:d0::a29f:c001]:2408",
            reserved = reserved,
            peerPublicKey = "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=",
            warpPlusEnabled = false
        )
    }

    @Test
    fun invoke_success_appliesObfuscationParametersAndSavesConfig() = runTest {
        val sampleReserved = "10, 20, 30"
        val warpConfig = createSampleWarpConfig(reserved = sampleReserved, endpointV4 = "194.87.12.34:854")
        val fakeCloudflareApi = FakeCloudflareApi(Result.success(warpConfig))
        val fakeRepository = FakeConfigRepository()
        val useCase = GenerateHybridWarpAwgUseCase(fakeCloudflareApi, fakeRepository)

        val customName = "Custom Hybrid Anti-DPI"
        val licenseKey = "TEST-LICENSE-KEY"
        val customDns = "8.8.8.8, 8.8.4.4"

        val result = useCase(customName = customName, licenseKey = licenseKey, dns = customDns)

        assertTrue("Expected useCase invoke result to be success", result.isSuccess)
        val hybridConfig = result.getOrThrow()

        // Verify obfuscation & hybrid specific parameters
        val expectedH1 = AwgConfig.calculateWarpH1(sampleReserved)
        assertEquals(5, hybridConfig.jc)
        assertEquals(40, hybridConfig.jmin)
        assertEquals(80, hybridConfig.jmax)
        assertEquals(0, hybridConfig.s1)
        assertEquals(0, hybridConfig.s2)
        assertEquals(0, hybridConfig.s3)
        assertEquals(0, hybridConfig.s4)
        assertEquals(expectedH1, hybridConfig.h1)
        assertEquals(2L, hybridConfig.h2)
        assertEquals(3L, hybridConfig.h3)
        assertEquals(4L, hybridConfig.h4)
        assertEquals(1280, hybridConfig.mtu)
        assertEquals("WARP_ANTI_DPI", hybridConfig.originType)

        // Verify user inputs and base config parameters
        assertEquals(customName, hybridConfig.name)
        assertEquals(customDns, hybridConfig.dns)
        assertEquals("194.87.12.34:854", hybridConfig.endpoint)
        assertEquals(licenseKey, fakeCloudflareApi.lastCapturedLicenseKey)

        // Verify configuration saved in repository
        assertEquals(1, fakeRepository.savedConfigs.size)
        assertEquals(hybridConfig, fakeRepository.savedConfigs.first())
    }

    @Test
    fun invoke_blankEndpoint_usesFallbackEndpoint() = runTest {
        val warpConfig = createSampleWarpConfig(endpointV4 = "")
        val fakeCloudflareApi = FakeCloudflareApi(Result.success(warpConfig))
        val fakeRepository = FakeConfigRepository()
        val useCase = GenerateHybridWarpAwgUseCase(fakeCloudflareApi, fakeRepository)

        val result = useCase()

        assertTrue(result.isSuccess)
        val hybridConfig = result.getOrThrow()
        assertEquals("188.114.97.1:854", hybridConfig.endpoint)
    }

    @Test
    fun invoke_defaultArguments_usesDefaults() = runTest {
        val warpConfig = createSampleWarpConfig()
        val fakeCloudflareApi = FakeCloudflareApi(Result.success(warpConfig))
        val fakeRepository = FakeConfigRepository()
        val useCase = GenerateHybridWarpAwgUseCase(fakeCloudflareApi, fakeRepository)

        val result = useCase()

        assertTrue(result.isSuccess)
        val hybridConfig = result.getOrThrow()
        assertEquals("WARP + AmneziaWG Anti-DPI", hybridConfig.name)
        assertEquals("1.1.1.1, 1.0.0.1", hybridConfig.dns)
        assertEquals(null, fakeCloudflareApi.lastCapturedLicenseKey)
    }

    @Test
    fun invoke_cloudflareApiFails_returnsFailureAndDoesNotSaveConfig() = runTest {
        val apiException = IllegalStateException("Cloudflare registration failed")
        val fakeCloudflareApi = FakeCloudflareApi(Result.failure(apiException))
        val fakeRepository = FakeConfigRepository()
        val useCase = GenerateHybridWarpAwgUseCase(fakeCloudflareApi, fakeRepository)

        val result = useCase()

        assertTrue("Expected result to be failure when API fails", result.isFailure)
        assertEquals(apiException.message, result.exceptionOrNull()?.message)
        assertTrue("Repository should remain empty when API fails", fakeRepository.savedConfigs.isEmpty())
    }

    @Test
    fun invoke_repositorySaveFails_returnsFailure() = runTest {
        val warpConfig = createSampleWarpConfig()
        val fakeCloudflareApi = FakeCloudflareApi(Result.success(warpConfig))
        val fakeRepository = FakeConfigRepository(shouldFailOnSave = true)
        val useCase = GenerateHybridWarpAwgUseCase(fakeCloudflareApi, fakeRepository)

        val result = useCase()

        assertTrue("Expected result to be failure when repository save fails", result.isFailure)
        assertEquals("Database save error", result.exceptionOrNull()?.message)
    }
}
