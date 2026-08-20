package com.example.domain.model

/**
 * Domain model representing a Cloudflare WARP account and generated connection credentials.
 */
data class WarpConfig(
    val accountId: String,
    val accessToken: String,
    val privateKey: String,
    val publicKey: String,
    val v4Address: String,
    val v6Address: String,
    val endpointV4: String = "162.159.193.1:2408",
    val endpointV6: String = "[2606:4700:d0::a29f:c001]:2408",
    val reserved: String, // 3-byte client ID encoded in base64 or dec
    val peerPublicKey: String = "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=",
    val warpPlusEnabled: Boolean = false,
    val quotaBytes: Long = 0L
) {
    fun toAwgConfig(name: String = "Cloudflare WARP"): AwgConfig {
        val cleanV4 = if (v4Address.contains("/")) v4Address else "$v4Address/32"
        val cleanV6 = if (v6Address.contains("/")) v6Address else "$v6Address/128"
        val formattedV4 = cleanV4.replace(Regex("(/32)+"), "/32")
        val formattedV6 = cleanV6.replace(Regex("(/128)+"), "/128")

        return AwgConfig(
            name = name,
            privateKey = privateKey,
            address = "$formattedV4, $formattedV6",
            dns = "1.1.1.1, 1.0.0.1",
            mtu = 1280,
            jc = 4,
            jmin = 40,
            jmax = 70,
            s1 = 15,
            s2 = 25,
            s3 = 10,
            s4 = 5,
            h1 = 1L,
            h2 = 2L,
            h3 = 3L,
            h4 = 4L,
            peerPublicKey = peerPublicKey,
            allowedIps = "0.0.0.0/0, ::/0",
            endpoint = endpointV4,
            persistentKeepalive = 25,
            isWarp = true,
            reserved = reserved
        )
    }
}
