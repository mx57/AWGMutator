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
        accessToken = "token_masque_456",
        privateKey = "priv_key_masque",
        publicKey = "pub_key_masque",
        v4Address = "172.16.0.2/32",
        v6Address = "2606:4700:110:893c::/128",
        endpointV4 = "188.114.97.1:854",
        endpointV6 = "[2606:4700:d0::a29f:c001]:854",
        reserved = "1, 2, 3",
        peerPublicKey = "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=",
        warpPlusEnabled = false
    )

    @Test
    fun invoke_withDefaultParameters_generatesMasqueAndSavesAwgConfig() = runTest {
        val fakeApi = FakeCloudflareApi(Result.success(sampleWarpConfig))
        val fakeRepository = FakeConfigRepository()
        val useCase = GenerateMasqueConfigUseCase(fakeApi, fakeRepository)

        val result = useCase()

        assertTrue("Expected success Result", result.isSuccess)
        val masqueConfig = result.getOrNull()
        assertNotNull(masqueConfig)
        assertEquals("Cloudflare WARP MASQUE", masqueConfig?.name)
        assertEquals("188.114.97.1", masqueConfig?.serverIp)
        assertEquals(443, masqueConfig?.serverPort)
        assertEquals("engage.cloudflareclient.com", masqueConfig?.sni)
        assertEquals("acc_masque_123", masqueConfig?.accountId)
        assertEquals("token_masque_456", masqueConfig?.accessToken)

        assertEquals(1, fakeRepository.savedConfigs.size)
        val savedAwg = fakeRepository.savedConfigs.first()
        assertEquals("Cloudflare WARP MASQUE", savedAwg.name)
        assertEquals("MASQUE", savedAwg.originType)
        assertEquals("188.114.97.1:854", savedAwg.endpoint)
        assertEquals("engage.cloudflareclient.com", savedAwg.sni)
    }

    @Test
    fun invoke_withCustomParameters_usesOverridesAndCapturesLicenseKey() = runTest {
        val fakeApi = FakeCloudflareApi(Result.success(sampleWarpConfig))
        val fakeRepository = FakeConfigRepository()
        val useCase = GenerateMasqueConfigUseCase(fakeApi, fakeRepository)

        val customName = "Custom MASQUE Node"
        val licenseKey = "warp_plus_license_999"
        val sniOverride = "vk.com"
        val serverIp = "162.159.130.1"
        val serverPort = 8443

        val result = useCase(
            customName = customName,
            licenseKey = licenseKey,
            sniOverride = sniOverride,
            serverIp = serverIp,
            serverPort = serverPort
        )

        assertTrue("Expected success Result", result.isSuccess)
        assertEquals(licenseKey, fakeApi.capturedLicenseKey)

        val masqueConfig = result.getOrNull()
        assertNotNull(masqueConfig)
        assertEquals(customName, masqueConfig?.name)
        assertEquals(serverIp, masqueConfig?.serverIp)
        assertEquals(serverPort, masqueConfig?.serverPort)
        assertEquals("vk.com", masqueConfig?.sni)

        assertEquals(1, fakeRepository.savedConfigs.size)
        val savedAwg = fakeRepository.savedConfigs.first()
        assertEquals(customName, savedAwg.name)
        assertEquals("MASQUE", savedAwg.originType)
        assertEquals("$serverIp:854", savedAwg.endpoint)
        assertEquals("vk.com", savedAwg.sni)
    }

    @Test
    fun invoke_whenApiFails_returnsFailureAndDoesNotSaveConfig() = runTest {
        val apiException = IllegalStateException("API connection timeout")
        val fakeApi = FakeCloudflareApi(Result.failure(apiException))
        val fakeRepository = FakeConfigRepository()
        val useCase = GenerateMasqueConfigUseCase(fakeApi, fakeRepository)

        val result = useCase()

        assertTrue("Expected failure Result when Cloudflare API fails", result.isFailure)
        assertEquals(apiException, result.exceptionOrNull())
        assertTrue("Repository should remain empty on failure", fakeRepository.savedConfigs.isEmpty())
    }

    @Test
    fun invoke_whenRepositorySaveFails_returnsFailure() = runTest {
        val fakeApi = FakeCloudflareApi(Result.success(sampleWarpConfig))
        val fakeRepository = FakeConfigRepository(shouldFailSave = true)
        val useCase = GenerateMasqueConfigUseCase(fakeApi, fakeRepository)

        val result = useCase()

        assertTrue("Expected failure Result when saving to repository fails", result.isFailure)
        assertEquals("Failed to save config to database", result.exceptionOrNull()?.message)
    }
}
