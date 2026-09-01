package com.example.domain.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Domain model representing Cloudflare WARP MASQUE (CONNECT-IP / CONNECT-UDP over HTTP/3) configuration,
 * compatible with sing-box (v1.8+ / LxBox), mihomo, and Clash.Meta.
 */
data class MasqueConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Cloudflare WARP MASQUE",
    val server: String = "engage.cloudflareclient.com",
    val serverPort: Int = 443,
    val serverIp: String = "188.114.97.1",
    val sni: String = "engage.cloudflareclient.com",
    val accountId: String,
    val accessToken: String,
    val v4Address: String = "172.16.0.2/32",
    val v6Address: String = "2606:4700:110:893c::/128",
    val privateKey: String? = null,
    val publicKey: String? = null,
    val reserved: String? = null,
    val cfClientVersion: String = "a-6.30-3900",
    val alpn: List<String> = listOf("h3"),
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * Exports as sing-box outbound JSON object.
     */
    fun toSingBoxOutboundJson(): JSONObject {
        return JSONObject().apply {
            put("type", "masque")
            put("tag", name)
            put("server", serverIp.ifBlank { server })
            put("server_port", serverPort)
            put("tls", JSONObject().apply {
                put("enabled", true)
                put("server_name", sni)
                put("alpn", JSONArray(alpn))
                put("insecure", false)
            })
            put("headers", JSONObject().apply {
                put("Authorization", "Bearer $accessToken")
                put("CF-Client-Version", cfClientVersion)
                if (!accountId.isBlank()) {
                    put("WARP-Account-ID", accountId)
                }
            })
            put("local_address", JSONArray().apply {
                val cleanV4 = if (v4Address.contains("/")) v4Address else "$v4Address/32"
                val cleanV6 = if (v6Address.contains("/")) v6Address else "$v6Address/128"
                put(cleanV4)
                put(cleanV6)
            })
        }
    }

    /**
     * Exports full ready-to-run sing-box configuration (with Tun Inbound, MASQUE Outbound, and Anti-DPI DNS).
     */
    fun toFullSingBoxConfig(): String {
        val root = JSONObject().apply {
            put("log", JSONObject().apply {
                put("level", "info")
                put("timestamp", true)
            })
            put("dns", JSONObject().apply {
                put("servers", JSONArray().apply {
                    put(JSONObject().apply {
                        put("tag", "doh-cloudflare")
                        put("address", "https://1.1.1.1/dns-query")
                        put("detour", "direct")
                    })
                    put(JSONObject().apply {
                        put("tag", "dns-local")
                        put("address", "local")
                        put("detour", "direct")
                    })
                })
                put("strategy", "prefer_ipv4")
            })
            put("inbounds", JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "tun")
                    put("tag", "tun-in")
                    put("interface_name", "tun0")
                    put("inet4_address", "172.19.0.1/30")
                    put("auto_route", true)
                    put("strict_route", true)
                    put("stack", "system")
                    put("sniff", true)
                })
            })
            put("outbounds", JSONArray().apply {
                put(toSingBoxOutboundJson())
                put(JSONObject().apply {
                    put("type", "direct")
                    put("tag", "direct")
                })
                put(JSONObject().apply {
                    put("type", "dns")
                    put("tag", "dns-out")
                })
            })
        }
        return root.toString(2)
    }

    /**
     * Exports as universal MASQUE URI.
     */
    fun toUri(): String {
        val encodedToken = android.net.Uri.encode(accessToken)
        val encodedSni = android.net.Uri.encode(sni)
        return "masque://$encodedToken@$serverIp:$serverPort?sni=$encodedSni&v4=${android.net.Uri.encode(v4Address)}&v6=${android.net.Uri.encode(v6Address)}#${android.net.Uri.encode(name)}"
    }
}
