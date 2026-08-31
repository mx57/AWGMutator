package com.example.data.remote

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Field
import java.lang.reflect.Method

class CloudflareApiTest {

    @Test
    fun testCertificatePinnerConfiguredOnDefaultClient() {
        val api = CloudflareApi()
        val clientField: Field = CloudflareApi::class.java.getDeclaredField("client")
        clientField.isAccessible = true
        val client = clientField.get(api) as OkHttpClient

        val certificatePinner = client.certificatePinner
        assertNotNull(certificatePinner)

        val pinsField: Field = certificatePinner.javaClass.getDeclaredField("pins")
        pinsField.isAccessible = true
        val pins = pinsField.get(certificatePinner) as Set<*>
        assertTrue("CertificatePinner should have pins configured", pins.isNotEmpty())
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
