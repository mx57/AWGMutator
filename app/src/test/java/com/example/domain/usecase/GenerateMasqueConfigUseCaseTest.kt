package com.example.domain.usecase

import com.example.data.remote.CloudflareApi
import com.example.domain.model.AwgConfig
import com.example.domain.model.MasqueConfig
import com.example.domain.model.WarpConfig
import com.example.domain.repository.ConfigRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerateMasqueConfigUseCaseTest {

    private class FakeCloudflareApi(
        private val warpResult: Result<WarpConfig>
    ) : CloudflareApi() {
        var capturedLicenseKey: String? = null

        override suspend fun generateWarpConfig(
            licenseKey: String?,
            dnsOverride: String?
        ): Result<WarpConfig> {
            capturedLicenseKey = licenseKey
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

    private val sampleWarpConfig = WarpConfig(
        accountId = "acc_masque_123",
        accessToken = "token_masque_123",
        privateKey = "priv_key_masque",
        publicKey = "pub_key_masque",
        v4Address = "172.16.0.2/32",
        v6Address = "2606:4700:110:893c::/128",
        endpointV4 = "188.114.97.1:854",
        endpointV6 = "[2606:4700:d0::a29f:c001]:854",
        reserved = "1,2,3",
        peerPublicKey = "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=",
        warpPlusEnabled = true
    )

    @Test
    fun invoke_defaultParameters_generatesAndSavesMasqueConfigSuccessfully() = runTest {
        val fakeApi = FakeCloudflareApi(Result.success(sampleWarpConfig))
        val fakeRepository = FakeConfigRepository()
        val useCase = GenerateMasqueConfigUseCase(fakeApi, fakeRepository)

        val result = useCase()

        assertTrue("Expected success Result for default parameters", result.isSuccess)
        val masqueConfig = result.getOrNull()
        assertNotNull(masqueConfig)
        assertEquals("Cloudflare WARP MASQUE", masqueConfig?.name)
        assertEquals("engage.cloudflareclient.com", masqueConfig?.server)
        assertEquals(443, masqueConfig?.serverPort)
        assertEquals("188.114.97.1", masqueConfig?.serverIp)
        assertEquals("engage.cloudflareclient.com", masqueConfig?.sni)
        assertEquals(sampleWarpConfig.accountId, masqueConfig?.accountId)
        assertEquals(sampleWarpConfig.accessToken, masqueConfig?.accessToken)
        assertEquals(sampleWarpConfig.v4Address, masqueConfig?.v4Address)
        assertEquals(sampleWarpConfig.v6Address, masqueConfig?.v6Address)
        assertEquals(sampleWarpConfig.privateKey, masqueConfig?.privateKey)
        assertEquals(sampleWarpConfig.publicKey, masqueConfig?.publicKey)
        assertEquals(sampleWarpConfig.reserved, masqueConfig?.reserved)

        // Verify AWG representation saved in repository
        assertEquals(1, fakeRepository.savedConfigs.size)
        val savedAwgConfig = fakeRepository.savedConfigs.first()
        assertEquals("Cloudflare WARP MASQUE", savedAwgConfig.name)
        assertEquals("MASQUE", savedAwgConfig.originType)
        assertEquals("188.114.97.1:854", savedAwgConfig.endpoint)
        assertEquals("engage.cloudflareclient.com", savedAwgConfig.sni)
    }

    @Test
    fun invoke_customParameters_usesCustomValuesAndHandlesSniOverride() = runTest {
        val fakeApi = FakeCloudflareApi(Result.success(sampleWarpConfig))
        val fakeRepository = FakeConfigRepository()
        val useCase = GenerateMasqueConfigUseCase(fakeApi, fakeRepository)

        val result = useCase(
            customName = "My Custom MASQUE",
            licenseKey = "license_xyz_123",
            sniOverride = "custom.cloudflare.com",
            serverIp = "172.64.100.1",
            serverPort = 8443
        )

        assertTrue("Expected success Result for custom parameters", result.isSuccess)
        val masqueConfig = result.getOrNull()
        assertNotNull(masqueConfig)
        assertEquals("My Custom MASQUE", masqueConfig?.name)
        assertEquals("172.64.100.1", masqueConfig?.serverIp)
        assertEquals(8443, masqueConfig?.serverPort)
        assertEquals("custom.cloudflare.com", masqueConfig?.sni)

        assertEquals("license_xyz_123", fakeApi.capturedLicenseKey)

        assertEquals(1, fakeRepository.savedConfigs.size)
        val savedAwgConfig = fakeRepository.savedConfigs.first()
        assertEquals("My Custom MASQUE", savedAwgConfig.name)
        assertEquals("MASQUE", savedAwgConfig.originType)
        assertEquals("172.64.100.1:854", savedAwgConfig.endpoint)
        assertEquals("custom.cloudflare.com", savedAwgConfig.sni)
    }

    @Test
    fun invoke_blankSniOverride_fallsBackToDefaultSni() = runTest {
        val fakeApi = FakeCloudflareApi(Result.success(sampleWarpConfig))
        val fakeRepository = FakeConfigRepository()
        val useCase = GenerateMasqueConfigUseCase(fakeApi, fakeRepository)

        val result = useCase(
            sniOverride = "   "
        )

        assertTrue(result.isSuccess)
        val masqueConfig = result.getOrNull()
        assertNotNull(masqueConfig)
        assertEquals("engage.cloudflareclient.com", masqueConfig?.sni)

        val savedAwgConfig = fakeRepository.savedConfigs.first()
        assertEquals("engage.cloudflareclient.com", savedAwgConfig.sni)
    }

    @Test
    fun invoke_cloudflareApiFails_returnsFailureResultAndDoesNotSaveConfig() = runTest {
        val apiException = IllegalStateException("Cloudflare registration error")
        val fakeApi = FakeCloudflareApi(Result.failure(apiException))
        val fakeRepository = FakeConfigRepository()
        val useCase = GenerateMasqueConfigUseCase(fakeApi, fakeRepository)

        val result = useCase()

        assertTrue("Expected failure Result when Cloudflare API fails", result.isFailure)
        assertEquals(apiException, result.exceptionOrNull())
        assertTrue("Config should not be saved when API call fails", fakeRepository.savedConfigs.isEmpty())
    }

    @Test
    fun invoke_repositorySaveFails_returnsFailureResult() = runTest {
        val fakeApi = FakeCloudflareApi(Result.success(sampleWarpConfig))
        val fakeRepository = FakeConfigRepository(shouldFailSave = true)
        val useCase = GenerateMasqueConfigUseCase(fakeApi, fakeRepository)

        val result = useCase()

        assertTrue("Expected failure Result when repository save fails", result.isFailure)
        assertEquals("Failed to save config to database", result.exceptionOrNull()?.message)
    }
}
