package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WireGuardProbeTest {

    @Test
    fun testProbeEndpoint_invalidHostOrPort_returnsError() {
        val invalidHostResult = WireGuardProbe.probeEndpoint(
            host = "",
            port = 1074
        )
        assertFalse(invalidHostResult.isReachable)
        assertEquals("Некорректный адрес эндпоинта", invalidHostResult.error)

        val invalidPortResult = WireGuardProbe.probeEndpoint(
            host = "127.0.0.1",
            port = 0
        )
        assertFalse(invalidPortResult.isReachable)
        assertEquals("Некорректный адрес эндпоинта", invalidPortResult.error)
    }

    @Test
    fun testProbeEndpoint_invalidPeerKey_returnsError() {
        val invalidBase64Result = WireGuardProbe.probeEndpoint(
            host = "127.0.0.1",
            port = 1074,
            peerPublicKeyBase64 = "!!!invalid_base64!!!"
        )
        assertFalse(invalidBase64Result.isReachable)
        assertEquals("Некорректный публичный ключ пира", invalidBase64Result.error)

        val invalidLengthResult = WireGuardProbe.probeEndpoint(
            host = "127.0.0.1",
            port = 1074,
            peerPublicKeyBase64 = android.util.Base64.encodeToString(ByteArray(16), android.util.Base64.NO_WRAP)
        )
        assertFalse(invalidLengthResult.isReachable)
        assertEquals("Длина ключа пира должна быть 32 байта", invalidLengthResult.error)
    }

    @Test
    fun testAeadEncrypt_reflection_executesSuccessfullyWithLogging() {
        val method = WireGuardProbe::class.java.getDeclaredMethod(
            "aeadEncrypt",
            ByteArray::class.java,
            Long::class.javaPrimitiveType,
            ByteArray::class.java,
            ByteArray::class.java
        )
        method.isAccessible = true

        val key = ByteArray(32) { it.toByte() }
        val plaintext = "Hello WireGuard".toByteArray()
        val ad = "Additional Data".toByteArray()

        val encrypted = method.invoke(WireGuardProbe, key, 0L, plaintext, ad) as ByteArray
        assertNotNull(encrypted)
        // Encrypted length should be plaintext.size + 16 (tag size)
        assertEquals(plaintext.size + 16, encrypted.size)
    }
}
