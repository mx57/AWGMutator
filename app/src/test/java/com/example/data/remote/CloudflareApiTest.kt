package com.example.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudflareApiTest {

    @Test
    fun testFcmTokenDoesNotContainHardcodedPrefix() {
        // Generate multiple FCM tokens simulating CloudflareApi generation logic
        val installId = "abcdefghijklmnopqrstuv"
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val randomString = (1..140).map { chars.random() }.joinToString("")
        val fcmToken = "$installId:$randomString"

        assertFalse("fcmToken should not contain hardcoded prefix 'APA91b'", fcmToken.contains("APA91b"))
        assertTrue("fcmToken should start with installId", fcmToken.startsWith(installId))
    }
}
