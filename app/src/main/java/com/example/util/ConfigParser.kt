package com.example.util

import com.example.domain.model.AwgConfig
import java.util.UUID

/**
 * Robust parser for AmneziaWG 2.0/3.0 & WireGuard `.conf` configuration files.
 * Supports custom Init payload noise (I1..I4), Russian/Global SNI spoofing,
 * fine-grained AllowedIPs subnets, and legacy/WARP headers.
 */
object ConfigParser {

    private fun parseLongWithHex(v: String): Long? {
        val clean = v.trim()
        return if (clean.startsWith("0x", ignoreCase = true)) {
            clean.substring(2).toLongOrNull(16)
        } else {
            clean.toLongOrNull()
        }
    }

    private fun extractByteLengthFromHexPayload(v: String?): Int {
        if (v.isNullOrBlank()) return 0
        val clean = v.trim().removePrefix("<b ").removeSuffix(">").removePrefix("0x").trim()
        val hexOnly = clean.takeWhile { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
        return hexOnly.length / 2
    }

    /**
     * Sanitizes configuration names to remove newline injection, control characters,
     * and excessive length that could lead to log injection or formatting issues.
     */
    fun sanitizeConfigName(name: String?, fallback: String = "Imported AWG"): String {
        if (name.isNullOrBlank()) return fallback
        // Remove all ASCII/Unicode control characters (including \r, \n, \t, \0)
        val sanitized = name.replace(Regex("[\\p{C}\\r\\n\\t]"), "").trim()
        val truncated = if (sanitized.length > 100) sanitized.take(100).trim() else sanitized
        return truncated.ifBlank { fallback }
    }

    /**
     * Internal mutable state holder for parsing configuration values line-by-line.
     */
    private data class ParsedConfigData(
        var privateKey: String = "",
        var address: String = "172.16.0.2/32",
        var dns: String = "1.1.1.1, 8.8.8.8, 1.0.0.1",
        var mtu: Int = 1280,
        var jc: Int = 0,
        var jmin: Int = 0,
        var jmax: Int = 0,
        var s1: Int = 0,
        var s2: Int = 0,
        var s3: Int = 0,
        var s4: Int = 0,
        var h1: Long = 0L,
        var h2: Long = 0L,
        var h3: Long = 0L,
        var h4: Long = 0L,
        var i1: String? = null,
        var i2: String? = null,
        var i3: String? = null,
        var i4: String? = null,
        var sni: String? = null,
        var reserved: String? = null,
        var peerPublicKey: String = "",
        var presharedKey: String? = null,
        var allowedIps: String = "0.0.0.0/0, ::/0",
        var endpoint: String = "",
        var persistentKeepalive: Int = 25
    )

    /**
     * Parses raw text configuration into an [AwgConfig] domain model.
     */
    fun parse(rawContent: String, defaultName: String = "Imported AWG"): Result<AwgConfig> {
        val safeName = sanitizeConfigName(defaultName, "Imported AWG")
        return runCatching {
            val data = parseSections(rawContent)
            adjustPayloadsAndBounds(data)
            val isCloudflareWarp = isWarpConfig(data, defaultName)
            applyDefaults(data, isCloudflareWarp)
            validateConfig(data)
            buildAwgConfig(data, safeName, isCloudflareWarp)
        }
    }

    private fun parseSections(rawContent: String): ParsedConfigData {
        val data = ParsedConfigData()
        var currentSection = ""

        val lines = rawContent.lines()
        for (rawLine in lines) {
            var line = rawLine.trim()
            if (line.isBlank() || line.startsWith("#")) continue
            if (line.contains("#")) {
                line = line.substringBefore("#").trim()
            }
            if (line.isBlank()) continue

            if (line.startsWith("[") && line.endsWith("]")) {
                currentSection = line.substring(1, line.length - 1).trim().lowercase()
                continue
            }

            val equalsIdx = line.indexOf('=')
            if (equalsIdx == -1) continue

            val rawKey = line.substring(0, equalsIdx).trim()
            val key = rawKey.lowercase()
            val value = line.substring(equalsIdx + 1).trim()

            when (currentSection) {
                "interface" -> parseInterfaceField(data, key, value)
                "peer" -> parsePeerField(data, key, value)
            }
        }
        return data
    }

    private fun parseInterfaceField(data: ParsedConfigData, key: String, value: String) {
        when (key) {
            "privatekey", "private_key" -> data.privateKey = value
            "address", "addresses" -> data.address = formatAddressList(value)
            "dns" -> data.dns = value
            "mtu" -> data.mtu = value.toIntOrNull() ?: 1280
            "jc" -> data.jc = parseLongWithHex(value)?.toInt() ?: 0
            "jmin" -> data.jmin = parseLongWithHex(value)?.toInt() ?: 0
            "jmax" -> data.jmax = parseLongWithHex(value)?.toInt() ?: 0
            "s1" -> data.s1 = parseLongWithHex(value)?.toInt() ?: 0
            "s2" -> data.s2 = parseLongWithHex(value)?.toInt() ?: 0
            "s3" -> data.s3 = parseLongWithHex(value)?.toInt() ?: 0
            "s4" -> data.s4 = parseLongWithHex(value)?.toInt() ?: 0
            "h1" -> data.h1 = parseLongWithHex(value) ?: 0L
            "h2" -> data.h2 = parseLongWithHex(value) ?: 0L
            "h3" -> data.h3 = parseLongWithHex(value) ?: 0L
            "h4" -> data.h4 = parseLongWithHex(value) ?: 0L
            "i1" -> {
                data.i1 = value
                val len = extractByteLengthFromHexPayload(value)
                if (len > 0 && data.s1 == 0) data.s1 = len.coerceIn(5, 1420)
            }
            "i2" -> {
                data.i2 = value
                val len = extractByteLengthFromHexPayload(value)
                if (len > 0 && data.s2 == 0) data.s2 = len.coerceIn(5, 1420)
            }
            "i3" -> data.i3 = value
            "i4" -> data.i4 = value
            "sni" -> data.sni = value
            "reserved", "client_id", "clientid" -> data.reserved = value
        }
    }

    private fun parsePeerField(data: ParsedConfigData, key: String, value: String) {
        when (key) {
            "publickey", "public_key" -> data.peerPublicKey = value
            "presharedkey", "preshared_key" -> data.presharedKey = value
            "allowedips", "allowed_ips" -> data.allowedIps = value
            "endpoint" -> data.endpoint = value
            "persistentkeepalive", "persistent_keepalive" -> data.persistentKeepalive = value.toIntOrNull() ?: 25
        }
    }

    private fun formatAddressList(rawAddresses: String): String {
        return rawAddresses.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { addr ->
                if (addr.contains("/")) addr
                else if (addr.contains(":")) "$addr/128"
                else "$addr/32"
            }.joinToString(", ")
    }

    private fun adjustPayloadsAndBounds(data: ParsedConfigData) {
        // Calculate S1/S2 junk sizes from I1/I2 payloads if S1/S2 were 0
        if (data.s1 == 0 && !data.i1.isNullOrBlank()) {
            val len = extractByteLengthFromHexPayload(data.i1)
            if (len > 0) data.s1 = len.coerceIn(5, 1420)
        }
        if (data.s2 == 0 && !data.i2.isNullOrBlank()) {
            val len = extractByteLengthFromHexPayload(data.i2)
            if (len > 0) data.s2 = len.coerceIn(5, 1420)
        }

        // Enforce Jmin <= Jmax bounds for AmneziaWG
        if (data.jc > 0) {
            if (data.jmin == 0 && data.jmax == 0) {
                data.jmin = 40
                data.jmax = 70
            } else if (data.jmin > data.jmax) {
                val tmp = data.jmin
                data.jmin = data.jmax
                data.jmax = tmp
            }
        }
    }

    private fun isWarpConfig(data: ParsedConfigData, defaultName: String): Boolean {
        return data.peerPublicKey.trim() == "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=" ||
                data.endpoint.contains("cloudflareclient.com", ignoreCase = true) ||
                defaultName.contains("WARP", ignoreCase = true)
    }

    private fun applyDefaults(data: ParsedConfigData, isCloudflareWarp: Boolean) {
        if (isCloudflareWarp) {
            val warpH1 = AwgConfig.calculateWarpH1(data.reserved)
            if (data.h1 == 0L) {
                data.h1 = if (warpH1 != 1L) warpH1 else 1L
            }
            if (data.h2 == 0L) data.h2 = 2L
            if (data.h3 == 0L) data.h3 = 3L
            if (data.h4 == 0L) data.h4 = 4L

            data.s1 = 0
            data.s2 = 0
            data.s3 = 0
            data.s4 = 0
            data.i1 = null
            data.i2 = null
            data.i3 = null
            data.i4 = null

            if (data.jc == 0) {
                data.jc = 4
                data.jmin = 40
                data.jmax = 70
            }
            if (data.mtu == 0) data.mtu = 1280

            if (data.dns.isBlank() || data.dns.contains("0.0.0.0")) {
                data.dns = "1.1.1.1, 1.0.0.1, 8.8.8.8"
            }
        } else {
            // For private AmneziaWG servers
            if (data.dns.isBlank() || data.dns.contains("0.0.0.0")) {
                data.dns = "1.1.1.1, 8.8.8.8"
            }
        }
    }

    private fun validateConfig(data: ParsedConfigData) {
        if (data.privateKey.isBlank()) {
            throw IllegalArgumentException("Missing PrivateKey in [Interface]")
        }
        if (data.peerPublicKey.isBlank()) {
            throw IllegalArgumentException("Missing PublicKey in [Peer]")
        }
        if (data.endpoint.isBlank()) {
            throw IllegalArgumentException("Missing Endpoint in [Peer]")
        }
    }

    private fun buildAwgConfig(data: ParsedConfigData, safeName: String, isCloudflareWarp: Boolean): AwgConfig {
        val sanitizedEndpoint = AwgConfig.sanitizeEndpoint(
            data.endpoint,
            defaultPort = if (isCloudflareWarp) 854 else 51820
        )

        return AwgConfig(
            id = UUID.randomUUID().toString(),
            name = safeName,
            privateKey = data.privateKey,
            address = data.address,
            dns = data.dns,
            mtu = data.mtu,
            jc = data.jc,
            jmin = data.jmin,
            jmax = data.jmax,
            s1 = data.s1,
            s2 = data.s2,
            s3 = data.s3,
            s4 = data.s4,
            h1 = data.h1,
            h2 = data.h2,
            h3 = data.h3,
            h4 = data.h4,
            i1 = data.i1,
            i2 = data.i2,
            i3 = data.i3,
            i4 = data.i4,
            sni = data.sni,
            peerPublicKey = data.peerPublicKey,
            presharedKey = data.presharedKey,
            allowedIps = data.allowedIps,
            endpoint = sanitizedEndpoint,
            persistentKeepalive = data.persistentKeepalive,
            isWarp = isCloudflareWarp,
            reserved = data.reserved,
            originType = "IMPORTED",
            createdAt = System.currentTimeMillis()
        )
    }
}
