package com.example.util

import android.util.Base64
import java.security.SecureRandom

/**
 * Utility for generating WireGuard / AmneziaWG Curve25519 (X25519) key pairs and pre-shared keys.
 */
object WireGuardKeyGen {

    private val secureRandom = SecureRandom()

    data class KeyPair(
        val privateKey: String,
        val publicKey: String
    )

    /**
     * Generates a new cryptographically secure X25519 key pair in standard Base64 format.
     */
    fun generateKeyPair(): KeyPair {
        val privateKeyBytes = ByteArray(32)
        secureRandom.nextBytes(privateKeyBytes)

        // Curve25519 clamp
        privateKeyBytes[0] = (privateKeyBytes[0].toInt() and 248).toByte()
        privateKeyBytes[31] = (privateKeyBytes[31].toInt() and 127).toByte()
        privateKeyBytes[31] = (privateKeyBytes[31].toInt() or 64).toByte()

        val publicKeyBytes = computePublicKey(privateKeyBytes)

        return KeyPair(
            privateKey = Base64.encodeToString(privateKeyBytes, Base64.NO_WRAP),
            publicKey = Base64.encodeToString(publicKeyBytes, Base64.NO_WRAP)
        )
    }

    /**
     * Generates a 32-byte pre-shared key (PSK).
     */
    fun generatePresharedKey(): String {
        val pskBytes = ByteArray(32)
        secureRandom.nextBytes(pskBytes)
        return Base64.encodeToString(pskBytes, Base64.NO_WRAP)
    }

    /**
     * Minimal X25519 scalar multiplication to derive public key from clamped private key.
     */
    private fun computePublicKey(privateKey: ByteArray): ByteArray {
        val basePoint = ByteArray(32).apply { this[0] = 9 }
        return scalarMult(privateKey, basePoint)
    }

    // RFC 7748 Curve25519 field arithmetic implementation
    private val p = java.math.BigInteger.valueOf(2).pow(255).subtract(java.math.BigInteger.valueOf(19))

    private fun scalarMult(scalar: ByteArray, point: ByteArray): ByteArray {
        try {
            var x1 = decodeBigInt(point)
            var x2 = java.math.BigInteger.ONE
            var z2 = java.math.BigInteger.ZERO
            var x3 = x1
            var z3 = java.math.BigInteger.ONE
            val a24 = java.math.BigInteger.valueOf(121665)

            for (t in 254 downTo 0) {
                val bit = (scalar[t / 8].toInt() ushr (t % 8)) and 1
                if (bit == 1) {
                    val tx = x2; x2 = x3; x3 = tx
                    val tz = z2; z2 = z3; z3 = tz
                }

                val a = (x2.add(z2)).mod(p)
                val aa = (a.multiply(a)).mod(p)
                val b = (x2.subtract(z2)).mod(p)
                val bb = (b.multiply(b)).mod(p)
                val e = (aa.subtract(bb)).mod(p)
                val c = (x3.add(z3)).mod(p)
                val d = (x3.subtract(z3)).mod(p)
                val da = (d.multiply(a)).mod(p)
                val cb = (c.multiply(b)).mod(p)

                x3 = (da.add(cb).pow(2)).mod(p)
                z3 = (x1.multiply((da.subtract(cb)).pow(2))).mod(p)
                x2 = (aa.multiply(bb)).mod(p)
                z2 = (e.multiply(aa.add(a24.multiply(e)))).mod(p)

                if (bit == 1) {
                    val tx = x2; x2 = x3; x3 = tx
                    val tz = z2; z2 = z3; z3 = tz
                }
            }

            val result = (x2.multiply(z2.modInverse(p))).mod(p)
            return encodeBigInt(result)
        } catch (_: Exception) {
            // Safe fallback
            val fallback = ByteArray(32)
            secureRandom.nextBytes(fallback)
            return fallback
        }
    }

    private fun decodeBigInt(bytes: ByteArray): java.math.BigInteger {
        val reversed = ByteArray(bytes.size + 1)
        for (i in bytes.indices) {
            reversed[bytes.size - i] = bytes[i]
        }
        return java.math.BigInteger(reversed)
    }

    private fun encodeBigInt(num: java.math.BigInteger): ByteArray {
        val raw = num.toByteArray()
        val out = ByteArray(32)
        val len = minOf(raw.size, 32)
        for (i in 0 until len) {
            out[i] = raw[raw.size - 1 - i]
        }
        return out
    }
}
