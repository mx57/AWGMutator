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
    val endpoint: String = "188.114.97.1:854",
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

        val cleanAddr = address.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { addr ->
                if (addr.contains("/")) addr
                else if (addr.contains(":")) "$addr/128"
                else "$addr/32"
            }.joinToString(", ")

        builder.appendLine("Address = $cleanAddr")
        if (dns.isNotBlank()) {
            builder.appendLine("DNS = $dns")
        }
        if (mtu in 1200..1500) {
            builder.appendLine("MTU = $mtu")
        }

        if (jc > 0) builder.appendLine("Jc = $jc")
        if (jmin > 0) builder.appendLine("Jmin = $jmin")
        if (jmax > 0) builder.appendLine("Jmax = $jmax")

        val isCloudflareWarpPeer = isWarp || peerPublicKey.trim() == "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo="

        if (!isCloudflareWarpPeer) {
            val effectiveS1 = if (s1 > 0) s1 else extractByteLengthFromHexPayload(i1)
            val effectiveS2 = if (s2 > 0) s2 else extractByteLengthFromHexPayload(i2)

            if (effectiveS1 > 0) builder.appendLine("S1 = $effectiveS1")
            if (effectiveS2 > 0) builder.appendLine("S2 = $effectiveS2")
            if (s3 > 0) builder.appendLine("S3 = $s3")
            if (s4 > 0) builder.appendLine("S4 = $s4")
        }

        val effectiveH1 = if ((isCloudflareWarpPeer || !reserved.isNullOrBlank()) && h1 <= 4L) {
            val calc = calculateWarpH1(reserved)
            if (calc != 1L) calc else h1
        } else {
            h1
        }
        val effectiveH2 = h2
        val effectiveH3 = h3
        val effectiveH4 = h4

        if (effectiveH1 > 0L) builder.appendLine("H1 = $effectiveH1")
        if (effectiveH2 > 0L) builder.appendLine("H2 = $effectiveH2")
        if (effectiveH3 > 0L) builder.appendLine("H3 = $effectiveH3")
        if (effectiveH4 > 0L) builder.appendLine("H4 = $effectiveH4")
        if (!isCloudflareWarpPeer) {
            if (!i1.isNullOrBlank()) builder.appendLine("I1 = ${formatHexPayload(i1)}")
            if (!i2.isNullOrBlank()) builder.appendLine("I2 = ${formatHexPayload(i2)}")
            if (!i3.isNullOrBlank()) builder.appendLine("I3 = ${formatHexPayload(i3)}")
            if (!i4.isNullOrBlank()) builder.appendLine("I4 = ${formatHexPayload(i4)}")
        }
        if (!sni.isNullOrBlank()) builder.appendLine("SNI = $sni")
        if (!reserved.isNullOrBlank()) {
            val cleanReserved = com.example.data.remote.CloudflareApi.normalizeReserved(reserved)
            builder.appendLine("Reserved = $cleanReserved")
        }

        builder.appendLine()
        builder.appendLine("[Peer]")
        builder.appendLine("PublicKey = $peerPublicKey")
        if (!presharedKey.isNullOrBlank()) {
            builder.appendLine("PresharedKey = $presharedKey")
        }
        val hasIpv6InAddress = cleanAddr.contains(":")
        val rawAllowed = if (allowedIps.isNotBlank()) allowedIps.trim() else "0.0.0.0/0, ::/0"
        val cleanAllowed = if (!hasIpv6InAddress) {
            rawAllowed.split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() && !it.contains(":") }
                .joinToString(", ")
                .ifBlank { "0.0.0.0/0" }
        } else {
            rawAllowed
        }
        builder.appendLine("AllowedIPs = $cleanAllowed")
        val cleanEndpoint = sanitizeEndpoint(endpoint, defaultPort = if (isWarp) 854 else 51820)
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
                if (addr.contains("/")) {
                    addr
                } else if (addr.contains(":")) {
                    "$addr/128"
                } else {
                    "$addr/32"
                }
            }.joinToString(", ")

        if (cleanAddr.isNotBlank()) {
            builder.appendLine("Address = $cleanAddr")
        } else {
            builder.appendLine("Address = 172.16.0.2/32")
        }

        val effectiveDns = dns.trim().ifBlank { "1.1.1.1, 8.8.8.8, 1.0.0.1" }
        builder.appendLine("DNS = $effectiveDns")

        val effectiveMtu = if (mtu in 1200..1500) mtu else 1280
        builder.appendLine("MTU = $effectiveMtu")

        builder.appendLine()
        builder.appendLine("[Peer]")
        builder.appendLine("PublicKey = $peerPublicKey")
        if (!presharedKey.isNullOrBlank()) {
            builder.appendLine("PresharedKey = $presharedKey")
        }

        val hasIpv6InAddress = cleanAddr.contains(":")
        val rawAllowed = if (allowedIps.isNotBlank()) allowedIps.trim() else "0.0.0.0/0, ::/0"
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

        val cleanEndpoint = sanitizeEndpoint(endpoint, defaultPort = if (isWarp) 854 else 51820)
        builder.appendLine("Endpoint = $cleanEndpoint")

        if (persistentKeepalive > 0) {
            builder.appendLine("PersistentKeepalive = $persistentKeepalive")
        }

        return builder.toString()
    }

    companion object {
        fun parseReservedBytes(reservedStr: String?): Triple<Int, Int, Int>? {
            if (reservedStr.isNullOrBlank()) return null
            val clean = reservedStr.trim().removePrefix("[").removeSuffix("]").trim()
            val parts = clean.split(Regex("[,\\s]+")).map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size == 3) {
                val b0 = parts[0].toIntOrNull()
                val b1 = parts[1].toIntOrNull()
                val b2 = parts[2].toIntOrNull()
                if (b0 != null && b1 != null && b2 != null) {
                    return Triple(b0 and 0xFF, b1 and 0xFF, b2 and 0xFF)
                }
            }
            try {
                val decoded = android.util.Base64.decode(clean, android.util.Base64.DEFAULT)
                if (decoded.size >= 3) {
                    return Triple(decoded[0].toInt() and 0xFF, decoded[1].toInt() and 0xFF, decoded[2].toInt() and 0xFF)
                }
            } catch (_: Exception) {}
            return null
        }

        fun calculateWarpH1(reservedStr: String?): Long {
            val triple = parseReservedBytes(reservedStr) ?: return 1L
            val (b0, b1, b2) = triple
            // Little-Endian WireGuard handshake initiation header uint32: [0x01 (Type=Initiation), b0, b1, b2]
            return 1L or (b0.toLong() shl 8) or (b1.toLong() shl 16) or (b2.toLong() shl 24)
        }

        fun formatHexPayload(v: String?): String {
            if (v.isNullOrBlank()) return ""
            val clean = v.trim()
            if (clean.startsWith("<b ") || clean.startsWith("0x")) return clean
            // If raw hex string, wrap in <b 0x...> format for AmneziaWG
            return if (clean.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
                "<b 0x$clean>"
            } else {
                clean
            }
        }

        fun extractByteLengthFromHexPayload(v: String?): Int {
            if (v.isNullOrBlank()) return 0
            val clean = v.trim().removePrefix("<b ").removeSuffix(">").removePrefix("0x").trim()
            val hexOnly = clean.takeWhile { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
            return (hexOnly.length / 2).coerceIn(0, 1420)
        }

        fun sanitizeEndpoint(raw: String?, defaultPort: Int = 51820): String {
            val trimmed = raw?.trim().orEmpty()
            if (trimmed.isBlank()) return "188.114.97.1:$defaultPort"

            // Check if IPv6 format with brackets: [2606:...]:port or [2606:...]
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

            // For IPv4 or host: e.g. 194.87.12.34:51820 or 162.159.192.7:854
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
