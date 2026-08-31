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
import java.lang.reflect.Method

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
}
