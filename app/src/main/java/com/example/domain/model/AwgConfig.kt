package com.example.domain.model

import java.util.UUID

/**
 * Domain model representing an AmneziaWG 2.0/3.0 obfuscated WireGuard configuration.
 */
data class AwgConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val privateKey: String,
    val address: String = "10.0.0.2/32",
    val dns: String = "1.1.1.1, 8.8.8.8",
    val mtu: Int = 1400,
    val jc: Int = 5,
    val jmin: Int = 64,
    val jmax: Int = 512,
    val s1: Int = 17,
    val s2: Int = 33,
    val s3: Int = 11,
    val s4: Int = 8,
    val h1: Long = 1234567890L,
    val h2: Long = 2345678901L,
    val h3: Long = 3456789012L,
    val h4: Long = 4294967290L,
    val peerPublicKey: String,
    val presharedKey: String? = null,
    val allowedIps: String = "0.0.0.0/0, ::/0",
    val endpoint: String,
    val persistentKeepalive: Int = 25,
    val isWarp: Boolean = false,
    val reserved: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastPingMs: Long? = null,
    val lastFitness: Double? = null
) {
    /**
     * Converts configuration into standard WireGuard / AmneziaWG .conf string format.
     */
    fun toConfString(): String {
        val builder = StringBuilder()
        builder.appendLine("[Interface]")
        builder.appendLine("PrivateKey = $privateKey")
        builder.appendLine("Address = $address")
        builder.appendLine("DNS = $dns")
        builder.appendLine("MTU = $mtu")
        if (jc > 0) builder.appendLine("Jc = $jc")
        if (jmin > 0) builder.appendLine("Jmin = $jmin")
        if (jmax > 0) builder.appendLine("Jmax = $jmax")
        if (s1 > 0) builder.appendLine("S1 = $s1")
        if (s2 > 0) builder.appendLine("S2 = $s2")
        if (s3 > 0) builder.appendLine("S3 = $s3")
        if (s4 > 0) builder.appendLine("S4 = $s4")
        if (h1 > 0) builder.appendLine("H1 = $h1")
        if (h2 > 0) builder.appendLine("H2 = $h2")
        if (h3 > 0) builder.appendLine("H3 = $h3")
        if (h4 > 0) builder.appendLine("H4 = $h4")
        if (!reserved.isNullOrBlank()) {
            builder.appendLine("Reserved = $reserved")
        }

        builder.appendLine()
        builder.appendLine("[Peer]")
        builder.appendLine("PublicKey = $peerPublicKey")
        if (!presharedKey.isNullOrBlank()) {
            builder.appendLine("PresharedKey = $presharedKey")
        }
        builder.appendLine("AllowedIPs = $allowedIps")
        builder.appendLine("Endpoint = $endpoint")
        if (persistentKeepalive > 0) {
            builder.appendLine("PersistentKeepalive = $persistentKeepalive")
        }

        return builder.toString()
    }
}
