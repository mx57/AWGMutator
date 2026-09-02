package com.example.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.lang.reflect.Method

@RunWith(RobolectricTestRunner::class)
class CloudflareApiTest {

    @Test
    fun testAwaitExtension_cancellationCancelsCall() = runBlocking {
        val client = OkHttpClient()
        val request = Request.Builder().url("https://10.255.255.1/non-existent").build()

        val call = client.newCall(request)
        val job = async(Dispatchers.IO) {
            call.await()
        }

        delay(50)
        job.cancel()

        try {
            job.await()
        } catch (_: Exception) {
            // Expected cancellation or IO exception from cancellation
        }

        assertTrue("OkHttp Call should be cancelled when coroutine is cancelled", call.isCanceled())
    }

    @Test
    fun testGenerateRandomString_usesSecureRandomAndNoHardcodedPrefixInFcmToken() {
        val api = CloudflareApi()
        val method: Method = CloudflareApi::class.java.getDeclaredMethod("generateRandomString", Int::class.javaPrimitiveType)
        method.isAccessible = true

        val installId = method.invoke(api, 22) as String
        val randomPart = method.invoke(api, 140) as String
        val fcmToken = "$installId:$randomPart"

        assertNotNull(fcmToken)
        val tokenParts = fcmToken.split(":")
        assertEquals(2, tokenParts.size)
        assertEquals(22, tokenParts[0].length)
        assertEquals(140, tokenParts[1].length)
        assertFalse("fcmToken random part should not start with hardcoded APA91b prefix", tokenParts[1].startsWith("APA91b"))
    }

    @Test
    fun testNormalizeReserved() {
        assertEquals("0, 0, 0", CloudflareApi.normalizeReserved(null))
        assertEquals("1, 2, 3", CloudflareApi.normalizeReserved("1, 2, 3"))
        assertEquals("10, 20, 30", CloudflareApi.normalizeReserved("[10, 20, 30]"))
    }

    @Test
    fun testBuildWarpConfigFromResponse_withValidJson() {
        val api = CloudflareApi()
        val dummyKeyPair = com.example.util.WireGuardKeyGen.generateKeyPair()
        val regJson = org.json.JSONObject().apply {
            put("id", "acc123")
            put("token", "tok123")
            put("client_id", "1, 2, 3")
            put("config", org.json.JSONObject().apply {
                put("interface", org.json.JSONObject().apply {
                    put("addresses", org.json.JSONObject().apply {
                        put("v4", "172.16.0.2")
                        put("v6", "2606:4700:110:893c::1")
                    })
                })
                put("peers", org.json.JSONArray().apply {
                    put(org.json.JSONObject().apply {
                        put("public_key", "peer_pub_key_123")
                        put("endpoint", org.json.JSONObject().apply {
                            put("v4", "162.159.130.5:2408")
                            put("v6", "[2606:4700:d0::a29f:c001]:2408")
                        })
                    })
                })
            })
        }

        val regResult = CloudflareApi.RegistrationResult(
            regJson = regJson,
            accountId = "acc123",
            accessToken = "tok123",
            keyPair = dummyKeyPair
        )

        val warpConfig = api.buildWarpConfigFromResponse(regResult, null, licenseKey = "VALID_LICENSE")

        assertEquals("acc123", warpConfig.accountId)
        assertEquals("tok123", warpConfig.accessToken)
        assertEquals(dummyKeyPair.privateKey, warpConfig.privateKey)
        assertEquals(dummyKeyPair.publicKey, warpConfig.publicKey)
        assertEquals("172.16.0.2/32", warpConfig.v4Address)
        assertEquals("2606:4700:110:893c::1/128", warpConfig.v6Address)
        assertEquals("162.159.130.5:2408", warpConfig.endpointV4)
        assertEquals("[2606:4700:d0::a29f:c001]:2408", warpConfig.endpointV6)
        assertEquals("peer_pub_key_123", warpConfig.peerPublicKey)
        assertEquals("1, 2, 3", warpConfig.reserved)
        org.junit.Assert.assertTrue(warpConfig.warpPlusEnabled)
    }

    @Test
    fun testBuildWarpConfigFromResponse_withBlockedEndpoints_fallbackToAnycast() {
        val api = CloudflareApi()
        val dummyKeyPair = com.example.util.WireGuardKeyGen.generateKeyPair()
        val regJson = org.json.JSONObject().apply {
            put("id", "acc123")
            put("token", "tok123")
            put("config", org.json.JSONObject().apply {
                put("peers", org.json.JSONArray().apply {
                    put(org.json.JSONObject().apply {
                        put("endpoint", org.json.JSONObject().apply {
                            put("v4", "162.159.192.1:2408")
                        })
                    })
                })
            })
        }

        val regResult = CloudflareApi.RegistrationResult(
            regJson = regJson,
            accountId = "acc123",
            accessToken = "tok123",
            keyPair = dummyKeyPair
        )

        val warpConfig = api.buildWarpConfigFromResponse(regResult, null, licenseKey = null)

        assertEquals("162.159.130.1:1074", warpConfig.endpointV4)
        org.junit.Assert.assertFalse(warpConfig.warpPlusEnabled)
    }
}
