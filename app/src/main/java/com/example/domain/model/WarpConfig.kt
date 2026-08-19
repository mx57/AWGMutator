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
            allowedIps = "1.0.0.0/8, 2.0.0.0/7, 4.0.0.0/6, 8.0.0.0/7, 11.0.0.0/8, 12.0.0.0/6, 16.0.0.0/4, 32.0.0.0/3, 64.0.0.0/3, 96.0.0.0/4, 112.0.0.0/5, 120.0.0.0/6, 124.0.0.0/7, 126.0.0.0/8, 128.0.0.0/3, 160.0.0.0/5, 168.0.0.0/8, 169.0.0.0/9, 169.128.0.0/10, 169.192.0.0/11, 169.224.0.0/12, 169.240.0.0/13, 169.248.0.0/14, 169.252.0.0/15, 169.255.0.0/16, 170.0.0.0/7, 172.0.0.0/12, 172.32.0.0/11, 172.64.0.0/10, 172.128.0.0/9, 173.0.0.0/8, 174.0.0.0/7, 176.0.0.0/4, 192.0.0.0/9, 192.128.0.0/11, 192.160.0.0/13, 192.169.0.0/16, 192.170.0.0/15, 192.172.0.0/14, 192.176.0.0/12, 192.192.0.0/10, 193.0.0.0/8, 194.0.0.0/7, 196.0.0.0/6, 200.0.0.0/5, 208.0.0.0/4, 224.0.0.0/4, ::/1, 8000::/2, c000::/3, e000::/4, f000::/5, f800::/6, fe00::/9, fec0::/10, ff00::/8",
            endpoint = endpointV4,
            persistentKeepalive = 25,
            isWarp = true,
            reserved = reserved
        )
    }
}
