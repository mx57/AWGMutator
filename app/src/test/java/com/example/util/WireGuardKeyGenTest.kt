package com.example.util

import org.amnezia.awg.crypto.KeyFormatException
import org.amnezia.awg.crypto.KeyPair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class WireGuardKeyGenTest {

    @Test
    fun testGenerateKeyPair_returnsValidBase64EncodedKeys() {
        val keyPair = WireGuardKeyGen.generateKeyPair()

        assertNotNull("Private key should not be null", keyPair.privateKey)
        assertNotNull("Public key should not be null", keyPair.publicKey)

        val privateKeyBytes = Base64.getDecoder().decode(keyPair.privateKey)
        val publicKeyBytes = Base64.getDecoder().decode(keyPair.publicKey)

        assertEquals("Curve25519 private key must be 32 bytes", 32, privateKeyBytes.size)
        assertEquals("Curve25519 public key must be 32 bytes", 32, publicKeyBytes.size)
    }

    @Test
    fun testGenerateKeyPair_publicAndPrivateKeyConsistency() {
        val keyPair = WireGuardKeyGen.generateKeyPair()

        // Re-derive public key from generated private key using AmneziaWG KeyPair
        val rederivedKeyPair = KeyPair(org.amnezia.awg.crypto.Key.fromBase64(keyPair.privateKey))

        assertEquals(
            "Re-derived public key should match generated public key",
            keyPair.publicKey,
            rederivedKeyPair.publicKey.toBase64()
        )
    }

    @Test
    fun testGenerateKeyPair_producesUniqueKeysOnMultipleCalls() {
        val keyPair1 = WireGuardKeyGen.generateKeyPair()
        val keyPair2 = WireGuardKeyGen.generateKeyPair()

        assertNotEquals("Consecutive private keys must be unique", keyPair1.privateKey, keyPair2.privateKey)
        assertNotEquals("Consecutive public keys must be unique", keyPair1.publicKey, keyPair2.publicKey)
    }

    @Test
    fun testGeneratePresharedKey_returnsValidBase64Encoded32ByteKey() {
        val presharedKey = WireGuardKeyGen.generatePresharedKey()

        assertNotNull("Preshared key should not be null", presharedKey)
        val pskBytes = Base64.getDecoder().decode(presharedKey)
        assertEquals("Preshared key must be 32 bytes", 32, pskBytes.size)
    }

    @Test
    fun testGeneratePresharedKey_producesUniqueKeysOnMultipleCalls() {
        val psk1 = WireGuardKeyGen.generatePresharedKey()
        val psk2 = WireGuardKeyGen.generatePresharedKey()

        assertNotEquals("Consecutive preshared keys must be unique", psk1, psk2)
    }

    @Test(expected = KeyFormatException::class)
    fun testInvalidKeyDecoding_throwsKeyFormatException() {
        org.amnezia.awg.crypto.Key.fromBase64("invalid_key_data")
    }

    @Test
    fun testKeyPairData_dataClassPropertiesAndEquality() {
        val keyPair1 = WireGuardKeyGen.KeyPairData("priv1", "pub1")
        val keyPair2 = WireGuardKeyGen.KeyPairData("priv1", "pub1")
        val keyPair3 = WireGuardKeyGen.KeyPairData("priv2", "pub2")

        assertEquals("Data class instances with same properties should be equal", keyPair1, keyPair2)
        assertNotEquals("Data class instances with different properties should not be equal", keyPair1, keyPair3)

        val (priv, pub) = keyPair1
        assertEquals("priv1", priv)
        assertEquals("pub1", pub)

        val copiedPair = keyPair1.copy(privateKey = "priv2")
        assertEquals("priv2", copiedPair.privateKey)
        assertEquals("pub1", copiedPair.publicKey)

        assertTrue(keyPair1.toString().contains("priv1"))
        assertTrue(keyPair1.toString().contains("pub1"))
    }
}
