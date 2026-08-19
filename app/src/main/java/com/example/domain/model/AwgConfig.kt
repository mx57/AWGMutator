package com.example.domain.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Domain model representing an AmneziaWG 2.0/3.0 obfuscated WireGuard configuration.
 */
data class AwgConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val privateKey: String,
    val address: String = "172.16.0.2/32",
    val dns: String = "1.1.1.1, 8.8.8.8, 1.0.0.1",
    val mtu: Int = 1280,
    val jc: Int = 4,
    val jmin: Int = 40,
    val jmax: Int = 70,
    val s1: Int = 0,
    val s2: Int = 0,
    val s3: Int = 0,
    val s4: Int = 0,
    val h1: Long = 1L,
    val h2: Long = 2L,
    val h3: Long = 3L,
    val h4: Long = 4L,
    val i1: String? = null,
    val i2: String? = null,
    val i3: String? = null,
    val i4: String? = null,
    val sni: String? = null,
    val peerPublicKey: String = "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=",
    val presharedKey: String? = null,
    val allowedIps: String = "0.0.0.0/0, ::/0",
    val endpoint: String = "162.159.192.13:1074",
    val persistentKeepalive: Int = 25,
    val isWarp: Boolean = false,
    val reserved: String? = null,
    val originType: String = "MANUAL", // MANUAL, WARP, EVOLUTION, IMPORTED, HYBRID
    val evolutionGeneration: Int? = null,
    val evolutionBatchId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastPingMs: Long? = null,
    val lastFitness: Double? = null
) {
    val formattedDateTime: String
        get() {
            val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            return sdf.format(Date(createdAt))
        }

    /**
     * Converts configuration into standard WireGuard / AmneziaWG .conf string format.
     */
    fun toConfString(): String {
        val builder = StringBuilder()
        builder.appendLine("[Interface]")
        builder.appendLine("PrivateKey = $privateKey")
        // Clean address string to avoid duplicate CIDR suffixes like /32/32 or /128/128
        val cleanAddr = address.split(",")
            .map { it.trim() }
            .map { addr ->
                var a = addr
                while (a.endsWith("/32/32")) a = a.replace("/32/32", "/32")
                while (a.endsWith("/128/128")) a = a.replace("/128/128", "/128")
                a
            }.joinToString(", ")
        builder.appendLine("Address = $cleanAddr")
        builder.appendLine("DNS = $dns")
        builder.appendLine("MTU = $mtu")
        builder.appendLine("S1 = $s1")
        builder.appendLine("S2 = $s2")
        builder.appendLine("S3 = $s3")
        builder.appendLine("S4 = $s4")
        builder.appendLine("Jc = $jc")
        builder.appendLine("Jmin = $jmin")
        builder.appendLine("Jmax = $jmax")
        builder.appendLine("H1 = $h1")
        builder.appendLine("H2 = $h2")
        builder.appendLine("H3 = $h3")
        builder.appendLine("H4 = $h4")
        if (!i1.isNullOrBlank()) {
            builder.appendLine("I1 = $i1")
        }
        if (!i2.isNullOrBlank()) {
            builder.appendLine("I2 = $i2")
        }
        if (!i3.isNullOrBlank()) {
            builder.appendLine("I3 = $i3")
        }
        if (!i4.isNullOrBlank()) {
            builder.appendLine("I4 = $i4")
        }
        if (!sni.isNullOrBlank()) {
            builder.appendLine("SNI = $sni")
        }
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
        val cleanEndpoint = sanitizeEndpoint(endpoint)
        builder.appendLine("Endpoint = $cleanEndpoint")
        if (persistentKeepalive > 0) {
            builder.appendLine("PersistentKeepalive = $persistentKeepalive")
        }

        return builder.toString()
    }

    /**
     * Converts configuration into clean standard WireGuard format for native GoBackend parsing.
     */
    fun toCleanWgQuickString(): String {
        val builder = StringBuilder()
        builder.appendLine("[Interface]")
        builder.appendLine("PrivateKey = $privateKey")
        val cleanAddr = address.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { addr ->
                val pure = addr.substringBefore("/").trim()
                val prefix = if (addr.contains("/")) addr.substringAfter("/") else if (pure.contains(":")) "128" else "32"
                "$pure/$prefix"
            }.joinToString(", ")
        if (cleanAddr.isNotBlank()) {
            builder.appendLine("Address = $cleanAddr")
        } else {
            builder.appendLine("Address = 172.16.0.2/32")
        }

        // Sanitize DNS: Ensure fast and reliable global DNS servers
        val rawDnsList = dns.split(",").map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("111.88.") }
        val effectiveDns = if (rawDnsList.isNotEmpty()) {
            rawDnsList.joinToString(", ")
        } else {
            "1.1.1.1, 8.8.8.8, 1.0.0.1"
        }
        builder.appendLine("DNS = $effectiveDns")

        val effectiveMtu = if (mtu in 1280..1420) mtu else 1280
        builder.appendLine("MTU = $effectiveMtu")

        builder.appendLine()
        builder.appendLine("[Peer]")
        builder.appendLine("PublicKey = $peerPublicKey")
        if (!presharedKey.isNullOrBlank()) {
            builder.appendLine("PresharedKey = $presharedKey")
        }

        val hasIpv6InAddress = cleanAddr.contains(":")
        val rawAllowed = if (allowedIps.isNotBlank()) allowedIps else "0.0.0.0/0, ::/0"
        val cleanAllowed = if (!hasIpv6InAddress) {
            // Prune IPv6 routes if interface has no IPv6 address to prevent blackholing
            rawAllowed.split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() && !it.contains("::") }
                .joinToString(", ")
                .ifBlank { "0.0.0.0/0" }
        } else {
            rawAllowed
        }
        builder.appendLine("AllowedIPs = $cleanAllowed")
        val cleanEndpoint = sanitizeEndpoint(endpoint)
        builder.appendLine("Endpoint = $cleanEndpoint")
        if (persistentKeepalive > 0) {
            builder.appendLine("PersistentKeepalive = $persistentKeepalive")
        }

        return builder.toString()
    }

    companion object {
        fun sanitizeEndpoint(raw: String?, defaultPort: Int = 1074): String {
            val trimmed = raw?.trim().orEmpty()
            if (trimmed.isBlank()) return "162.159.192.13:$defaultPort"

            // Check if IPv6 format: [2606:...]:port or [2606:...]
            if (trimmed.startsWith("[")) {
                val closeBracket = trimmed.indexOf(']')
                if (closeBracket != -1) {
                    val ip = trimmed.substring(0, closeBracket + 1)
                    val after = trimmed.substring(closeBracket + 1).trim()
                    val port = if (after.startsWith(":")) after.substring(1).toIntOrNull() else null
                    val validPort = if (port != null && port > 0) port else defaultPort
                    return "$ip:$validPort"
                }
            }

            // For IPv4 or host: e.g. 162.159.192.7 or 162.159.192.7:0 or 162.159.192.7:1074
            if (trimmed.count { it == ':' } == 1) {
                val ipOrHost = trimmed.substringBefore(":")
                val portStr = trimmed.substringAfter(":")
                val port = portStr.toIntOrNull()
                val validPort = if (port != null && port > 0) port else defaultPort
                return "$ipOrHost:$validPort"
            } else if (!trimmed.contains(":")) {
                // Plain IPv4 or hostname with no port
                return "$trimmed:$defaultPort"
            }

            // Raw IPv6 without brackets
            return "[$trimmed]:$defaultPort"
        }
    }
}
