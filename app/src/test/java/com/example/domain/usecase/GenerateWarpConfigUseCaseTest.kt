package com.example.domain.usecase

import com.example.data.remote.CloudflareApi
import com.example.domain.model.AwgConfig
import com.example.domain.model.WarpConfig
import com.example.domain.repository.ConfigRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerateWarpConfigUseCaseTest {

    private class FakeCloudflareApi(
        private val warpResult: Result<WarpConfig>
    ) : CloudflareApi() {
        override suspend fun generateWarpConfig(
            licenseKey: String?,
            dnsOverride: String?
        ): Result<WarpConfig> {
            return warpResult
        }
    }

    private class FakeConfigRepository(
        private val shouldFailSave: Boolean = false
    ) : ConfigRepository {
        val savedConfigs = mutableListOf<AwgConfig>()

        override fun getAllConfigs(): Flow<List<AwgConfig>> = flowOf(savedConfigs)

        override suspend fun getConfigById(id: String): AwgConfig? {
            return savedConfigs.find { it.id == id }
        }

        override suspend fun saveConfig(config: AwgConfig) {
            if (shouldFailSave) {
                throw IllegalStateException("Failed to save config to database")
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

    @Test
    fun invoke_whenCloudflareApiFails_returnsFailureResultAndDoesNotSaveConfig() = runTest {
        val apiException = IllegalStateException("Cloudflare registration failed")
        val fakeApi = FakeCloudflareApi(Result.failure(apiException))
        val fakeRepository = FakeConfigRepository()
        val useCase = GenerateWarpConfigUseCase(fakeApi, fakeRepository)

        val result = useCase()

        assertTrue("Expected failure Result when Cloudflare API fails", result.isFailure)
        assertEquals(apiException, result.exceptionOrNull())
        assertTrue("Config should not be saved when API call fails", fakeRepository.savedConfigs.isEmpty())
    }

    @Test
    fun invoke_whenCloudflareApiSucceeds_returnsSuccessResultAndSavesConfig() = runTest {
        val warpConfig = WarpConfig(
            accountId = "acc_123",
            accessToken = "token_123",
            privateKey = "priv_key",
            publicKey = "pub_key",
            v4Address = "172.16.0.2/32",
            v6Address = "2606:4700:110:893c::/128",
            endpointV4 = "188.114.97.1:854",
            endpointV6 = "[2606:4700:d0::a29f:c001]:854",
            reserved = "1, 2, 3",
            peerPublicKey = "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=",
            warpPlusEnabled = false
        )
        val fakeApi = FakeCloudflareApi(Result.success(warpConfig))
        val fakeRepository = FakeConfigRepository()
        val useCase = GenerateWarpConfigUseCase(fakeApi, fakeRepository)

        val customName = "Test Cloudflare WARP"
        val customEndpoint = "188.114.96.1:854"
        val customDns = "1.1.1.1, 1.0.0.1"

        val result = useCase(
            customName = customName,
            endpoint = customEndpoint,
            dns = customDns,
            mtu = 1420
        )

        assertTrue("Expected success Result when Cloudflare API succeeds", result.isSuccess)
        val generatedConfig = result.getOrNull()
        assertNotNull(generatedConfig)
        assertEquals(customName, generatedConfig?.name)
        assertEquals(customEndpoint, generatedConfig?.endpoint)
        assertEquals(customDns, generatedConfig?.dns)
        assertEquals(1420, generatedConfig?.mtu)
        assertEquals("WARP", generatedConfig?.originType)

        assertEquals(1, fakeRepository.savedConfigs.size)
        assertEquals(generatedConfig, fakeRepository.savedConfigs.first())
    }

    @Test
    fun invoke_whenConfigRepositoryFailsToSave_returnsFailureResult() = runTest {
        val warpConfig = WarpConfig(
            accountId = "acc_123",
            accessToken = "token_123",
            privateKey = "priv_key",
            publicKey = "pub_key",
            v4Address = "172.16.0.2/32",
            v6Address = "2606:4700:110:893c::/128",
            endpointV4 = "188.114.97.1:854",
            endpointV6 = "[2606:4700:d0::a29f:c001]:854",
            reserved = "1, 2, 3",
            peerPublicKey = "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=",
            warpPlusEnabled = false
        )
        val fakeApi = FakeCloudflareApi(Result.success(warpConfig))
        val fakeRepository = FakeConfigRepository(shouldFailSave = true)
        val useCase = GenerateWarpConfigUseCase(fakeApi, fakeRepository)

        val result = useCase()

        assertTrue("Expected failure Result when repository save fails", result.isFailure)
        assertEquals("Failed to save config to database", result.exceptionOrNull()?.message)
    }
}
