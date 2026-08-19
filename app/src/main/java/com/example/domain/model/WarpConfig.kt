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
        return AwgConfig(
            name = name,
            privateKey = privateKey,
            address = "$v4Address/32, $v6Address/128",
            dns = "1.1.1.1, 1.0.0.1, 2606:4700:4700::1111, 2606:4700:4700::1001",
            mtu = 1280,
            jc = 4,
            jmin = 64,
            jmax = 400,
            s1 = 15,
            s2 = 25,
            s3 = 10,
            s4 = 5,
            h1 = 1000000000L + (System.currentTimeMillis() % 500000000L),
            h2 = 2000000000L + (System.currentTimeMillis() % 500000000L),
            h3 = 3000000000L + (System.currentTimeMillis() % 500000000L),
            h4 = 4000000000L + (System.currentTimeMillis() % 200000000L),
            peerPublicKey = peerPublicKey,
            allowedIps = "0.0.0.0/0, ::/0",
            endpoint = endpointV4,
            persistentKeepalive = 25,
            isWarp = true,
            reserved = reserved
        )
    }
}
