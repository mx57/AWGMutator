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
     * Parses raw text configuration into an [AwgConfig] domain model.
     */
    fun parse(rawContent: String, defaultName: String = "Imported AWG"): Result<AwgConfig> {
        return runCatching {
            var privateKey = ""
            var address = "172.16.0.2/32"
            var dns = "1.1.1.1, 8.8.8.8, 1.0.0.1"
            var mtu = 1280
            var jc = 0
            var jmin = 0
            var jmax = 0
            var s1 = 0
            var s2 = 0
            var s3 = 0
            var s4 = 0
            var h1 = 0L
            var h2 = 0L
            var h3 = 0L
            var h4 = 0L
            var i1: String? = null
            var i2: String? = null
            var i3: String? = null
            var i4: String? = null
            var sni: String? = null
            var reserved: String? = null

            var peerPublicKey = ""
            var presharedKey: String? = null
            var allowedIps = "0.0.0.0/0, ::/0"
            var endpoint = ""
            var persistentKeepalive = 25

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
                    "interface" -> {
                        when (key) {
                            "privatekey", "private_key" -> privateKey = value
                            "address", "addresses" -> {
                                address = value.split(",")
                                    .map { it.trim() }
                                    .filter { it.isNotBlank() }
                                    .map { addr ->
                                        if (addr.contains("/")) addr
                                        else if (addr.contains(":")) "$addr/128"
                                        else "$addr/32"
                                    }.joinToString(", ")
                            }
                            "dns" -> dns = value
                            "mtu" -> mtu = value.toIntOrNull() ?: 1280
                            "jc" -> jc = parseLongWithHex(value)?.toInt() ?: 0
                            "jmin" -> jmin = parseLongWithHex(value)?.toInt() ?: 0
                            "jmax" -> jmax = parseLongWithHex(value)?.toInt() ?: 0
                            "s1" -> s1 = parseLongWithHex(value)?.toInt() ?: 0
                            "s2" -> s2 = parseLongWithHex(value)?.toInt() ?: 0
                            "s3" -> s3 = parseLongWithHex(value)?.toInt() ?: 0
                            "s4" -> s4 = parseLongWithHex(value)?.toInt() ?: 0
                            "h1" -> h1 = parseLongWithHex(value) ?: 0L
                            "h2" -> h2 = parseLongWithHex(value) ?: 0L
                            "h3" -> h3 = parseLongWithHex(value) ?: 0L
                            "h4" -> h4 = parseLongWithHex(value) ?: 0L
                            "i1" -> {
                                i1 = value
                                val len = extractByteLengthFromHexPayload(value)
                                if (len > 0 && s1 == 0) s1 = len.coerceIn(5, 1420)
                            }
                            "i2" -> {
                                i2 = value
                                val len = extractByteLengthFromHexPayload(value)
                                if (len > 0 && s2 == 0) s2 = len.coerceIn(5, 1420)
                            }
                            "i3" -> i3 = value
                            "i4" -> i4 = value
                            "sni" -> sni = value
                            "reserved", "client_id", "clientid" -> reserved = value
                        }
                    }
                    "peer" -> {
                        when (key) {
                            "publickey", "public_key" -> peerPublicKey = value
                            "presharedkey", "preshared_key" -> presharedKey = value
                            "allowedips", "allowed_ips" -> allowedIps = value
                            "endpoint" -> endpoint = value
                            "persistentkeepalive", "persistent_keepalive" -> persistentKeepalive = value.toIntOrNull() ?: 25
                        }
                    }
                }
            }

            // Calculate S1/S2 junk sizes from I1/I2 payloads if S1/S2 were 0
            if (s1 == 0 && !i1.isNullOrBlank()) {
                val len = extractByteLengthFromHexPayload(i1)
                if (len > 0) s1 = len.coerceIn(5, 1420)
            }
            if (s2 == 0 && !i2.isNullOrBlank()) {
                val len = extractByteLengthFromHexPayload(i2)
                if (len > 0) s2 = len.coerceIn(5, 1420)
            }

            // Enforce Jmin <= Jmax bounds for AmneziaWG
            if (jc > 0) {
                if (jmin == 0 && jmax == 0) {
                    jmin = 40
                    jmax = 70
                } else if (jmin > jmax) {
                    val tmp = jmin
                    jmin = jmax
                    jmax = tmp
                }
            }

            // Detect Cloudflare WARP configurations
            val isCloudflareWarp = peerPublicKey.trim() == "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=" ||
                    endpoint.contains("cloudflareclient.com", ignoreCase = true)

            if (isCloudflareWarp) {
                // Cloudflare WireGuard server protocol strictly requires standard init packet format (S1=0, S2=0) unless specified.
                val warpH1 = AwgConfig.calculateWarpH1(reserved)
                if (h1 == 0L) {
                    h1 = if (warpH1 != 1L) warpH1 else 1L
                }
                if (h2 == 0L) h2 = 2L
                if (h3 == 0L) h3 = 3L
                if (h4 == 0L) h4 = 4L
                if (jc == 0) {
                    jc = 4
                    jmin = 40
                    jmax = 70
                }
                if (mtu == 0) mtu = 1280

                // Sanitize DNS: if DNS is empty, dead, or contains China Telecom / unroutable addresses (e.g. 111.88.96.50)
                if (dns.isBlank() || dns.contains("111.88.") || dns.contains("0.0.0.0")) {
                    dns = "1.1.1.1, 1.0.0.1, 8.8.8.8"
                }

                // If endpoint is on known blocked Cloudflare prefixes (162.159.192.x / 162.159.193.x in RU)
                val epClean = endpoint.trim().lowercase()
                if (epClean.startsWith("162.159.192.") || epClean.startsWith("162.159.193.") || epClean.contains("engage.cloudflareclient.com")) {
                    val port = epClean.substringAfterLast(":", "854").toIntOrNull() ?: 854
                    endpoint = "188.114.97.1:$port"
                }
            } else {
                // For private AmneziaWG servers
                if (dns.isBlank() || dns.contains("0.0.0.0")) {
                    dns = "1.1.1.1, 8.8.8.8"
                }
            }

            if (privateKey.isBlank()) {
                throw IllegalArgumentException("Missing PrivateKey in [Interface]")
            }
            if (peerPublicKey.isBlank()) {
                throw IllegalArgumentException("Missing PublicKey in [Peer]")
            }
            if (endpoint.isBlank()) {
                throw IllegalArgumentException("Missing Endpoint in [Peer]")
            }

            val sanitizedEndpoint = AwgConfig.sanitizeEndpoint(endpoint, defaultPort = if (isCloudflareWarp) 854 else 51820)

            AwgConfig(
                id = UUID.randomUUID().toString(),
                name = defaultName,
                privateKey = privateKey,
                address = address,
                dns = dns,
                mtu = mtu,
                jc = jc,
                jmin = jmin,
                jmax = jmax,
                s1 = s1,
                s2 = s2,
                s3 = s3,
                s4 = s4,
                h1 = h1,
                h2 = h2,
                h3 = h3,
                h4 = h4,
                i1 = if (isCloudflareWarp) null else i1,
                i2 = if (isCloudflareWarp) null else i2,
                i3 = if (isCloudflareWarp) null else i3,
                i4 = if (isCloudflareWarp) null else i4,
                sni = sni,
                peerPublicKey = peerPublicKey,
                presharedKey = presharedKey,
                allowedIps = allowedIps,
                endpoint = sanitizedEndpoint,
                persistentKeepalive = persistentKeepalive,
                isWarp = isCloudflareWarp,
                reserved = reserved,
                originType = "IMPORTED",
                createdAt = System.currentTimeMillis()
            )
        }
    }
}
