package com.example.util

import com.example.domain.model.AwgConfig
import java.util.UUID

/**
 * Parser for AmneziaWG / WireGuard `.conf` configuration files.
 */
object ConfigParser {

    /**
     * Parses raw text configuration into an [AwgConfig] domain model.
     */
    fun parse(rawContent: String, defaultName: String = "Imported AWG"): Result<AwgConfig> {
        return runCatching {
            var privateKey = ""
            var address = "10.0.0.2/32"
            var dns = "1.1.1.1"
            var mtu = 1400
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
                            "address" -> address = value
                            "dns" -> dns = value
                            "mtu" -> mtu = value.toIntOrNull() ?: 1400
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

            val isWarp = !reserved.isNullOrBlank() || endpoint.contains("162.159.")

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
