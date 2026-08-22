package com.example.util

import org.amnezia.awg.crypto.KeyPair

/**
 * Utility for generating WireGuard / AmneziaWG Curve25519 (X25519) key pairs and pre-shared keys
 * using official WireGuard crypto implementation.
 */
object WireGuardKeyGen {

    data class KeyPairData(
        val privateKey: String,
        val publicKey: String
    )

    /**
     * Generates a new cryptographically valid X25519 key pair in standard Base64 format.
     */
    fun generateKeyPair(): KeyPairData {
        val kp = KeyPair()
        return KeyPairData(
            privateKey = kp.privateKey.toBase64(),
            publicKey = kp.publicKey.toBase64()
        )
    }

    /**
     * Generates a valid 32-byte pre-shared key (PSK) in Base64 format.
     */
    fun generatePresharedKey(): String {
        return KeyPair().privateKey.toBase64()
    }
}

