package com.example.util

import com.example.domain.model.AwgConfig
import java.util.UUID

/**
 * Robust parser for AmneziaWG 2.0/3.0 & WireGuard `.conf` configuration files.
 * Supports custom Init payload noise (I1..I4), Russian/Global SNI spoofing,
 * fine-grained AllowedIPs subnets, and legacy/WARP headers.
 */
object ConfigParser {

    /**
     * Parses raw text configuration into an [AwgConfig] domain model.
     */
    fun parse(rawContent: String, defaultName: String = "Imported AWG"): Result<AwgConfig> {
        return runCatching {
            var privateKey = ""
            var address = "172.16.0.2/32"
            var dns = "111.88.96.50, 111.88.96.51"
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
                val line = rawLine.trim()
                if (line.startsWith("#") || line.isBlank()) continue

                if (line.startsWith("[") && line.endsWith("]")) {
                    currentSection = line.substring(1, line.length - 1).trim().lowercase()
                    continue
                }

                val equalsIdx = line.indexOf('=')
                if (equalsIdx == -1) continue

                val key = line.substring(0, equalsIdx).trim().lowercase()
                val value = line.substring(equalsIdx + 1).trim()

                when (currentSection) {
                    "interface" -> {
                        when (key) {
                            "privatekey" -> privateKey = value
                            "address" -> {
                                address = if (!value.contains("/")) "$value/32" else value
                            }
                            "dns" -> dns = value
                            "mtu" -> mtu = value.toIntOrNull() ?: 1280
                            "jc" -> jc = value.toIntOrNull() ?: 0
                            "jmin" -> jmin = value.toIntOrNull() ?: 0
                            "jmax" -> jmax = value.toIntOrNull() ?: 0
                            "s1" -> s1 = value.toIntOrNull() ?: 0
                            "s2" -> s2 = value.toIntOrNull() ?: 0
                            "s3" -> s3 = value.toIntOrNull() ?: 0
                            "s4" -> s4 = value.toIntOrNull() ?: 0
                            "h1" -> h1 = value.toLongOrNull() ?: 0L
                            "h2" -> h2 = value.toLongOrNull() ?: 0L
                            "h3" -> h3 = value.toLongOrNull() ?: 0L
                            "h4" -> h4 = value.toLongOrNull() ?: 0L
                            "i1" -> i1 = value
                            "i2" -> i2 = value
                            "i3" -> i3 = value
                            "i4" -> i4 = value
                            "sni" -> sni = value
                            "reserved" -> reserved = value
                        }
                    }
                    "peer" -> {
                        when (key) {
                            "publickey" -> peerPublicKey = value
                            "presharedkey" -> presharedKey = value
                            "allowedips" -> allowedIps = value
                            "endpoint" -> endpoint = value
                            "persistentkeepalive" -> persistentKeepalive = value.toIntOrNull() ?: 25
                        }
                    }
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

            val isWarp = !reserved.isNullOrBlank() || endpoint.contains("162.159.") || endpoint.contains("188.114.")

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
                i1 = i1,
                i2 = i2,
                i3 = i3,
                i4 = i4,
                sni = sni,
                peerPublicKey = peerPublicKey,
                presharedKey = presharedKey,
                allowedIps = allowedIps,
                endpoint = endpoint,
                persistentKeepalive = persistentKeepalive,
                isWarp = isWarp,
                reserved = reserved
            )
        }
    }
}
