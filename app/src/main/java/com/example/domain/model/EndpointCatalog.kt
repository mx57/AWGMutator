package com.example.domain.model

import java.util.Random

data class EndpointItem(
    val id: String,
    val ip: String,
    val port: Int,
    val countryCode: String,
    val countryName: String,
    val flagEmoji: String,
    val ispName: String = "Cloudflare Edge",
    var lastPingMs: Long? = null,
    var isAlive: Boolean = false
) {
    val fullEndpoint: String get() = "$ip:$port"
}

object EndpointCatalog {
    val bypassPorts = listOf(1074, 854, 859, 864, 878, 880, 890, 894, 903, 908, 500, 1701, 4500, 2408, 51820)

    val countries = listOf(
        Pair("ALL", "All Countries / Anycast 🌍"),
        Pair("DE", "Germany 🇩🇪"),
        Pair("NL", "Netherlands 🇳🇱"),
        Pair("FI", "Finland 🇫🇮"),
        Pair("SE", "Sweden 🇸🇪"),
        Pair("PL", "Poland 🇵🇱"),
        Pair("UK", "United Kingdom 🇬🇧"),
        Pair("FR", "France 🇫🇷"),
        Pair("US", "United States 🇺🇸"),
        Pair("KZ", "Kazakhstan 🇰🇿"),
        Pair("TR", "Turkey 🇹🇷"),
        Pair("SG", "Singapore 🇸🇬"),
        Pair("JP", "Japan 🇯🇵"),
        Pair("HK", "Hong Kong 🇭🇰")
    )

    val preconfiguredEndpoints: List<EndpointItem> = listOf(
        // High-priority tested & working endpoints
        EndpointItem("ep_ru_bypass_1", "162.159.192.13", 1074, "ALL", "Global Anycast", "🌍", "CF WARP Bypass"),
        EndpointItem("ep_ru_bypass_2", "162.159.192.1", 854, "ALL", "Global Anycast", "🌍", "CF Edge Fast"),
        EndpointItem("ep_ru_bypass_3", "162.159.192.2", 859, "ALL", "Global Anycast", "🌍", "CF Port 859"),
        EndpointItem("ep_ru_bypass_4", "162.159.193.5", 864, "ALL", "Global Anycast", "🌍", "CF Edge 864"),
        EndpointItem("ep_ru_bypass_5", "162.159.193.10", 878, "ALL", "Global Anycast", "🌍", "CF Edge 878"),
        EndpointItem("ep_ru_bypass_6", "162.159.195.1", 894, "ALL", "Global Anycast", "🌍", "CF Edge 894"),
        EndpointItem("ep_ru_bypass_7", "162.159.195.12", 903, "ALL", "Global Anycast", "🌍", "CF Edge 903"),
        EndpointItem("ep_ru_bypass_8", "162.159.195.20", 908, "ALL", "Global Anycast", "🌍", "CF Edge 908"),
        EndpointItem("ep_ru_bypass_9", "188.114.96.1", 1074, "ALL", "Global Anycast", "🌍", "CF EU-Anycast"),
        EndpointItem("ep_ru_bypass_10", "188.114.97.1", 854, "ALL", "Global Anycast", "🌍", "CF EU-Anycast"),
        EndpointItem("ep_ru_bypass_11", "188.114.98.1", 500, "ALL", "Global Anycast", "🌍", "CF IPsec Port"),
        EndpointItem("ep_ru_bypass_12", "188.114.99.1", 4500, "ALL", "Global Anycast", "🌍", "CF NAT-T Port"),

        // Germany 🇩🇪
        EndpointItem("ep_de_1", "162.159.192.25", 1074, "DE", "Germany", "🇩🇪", "Frankfurt Edge"),
        EndpointItem("ep_de_2", "162.159.193.42", 854, "DE", "Germany", "🇩🇪", "Berlin Edge"),
        EndpointItem("ep_de_3", "188.114.96.22", 878, "DE", "Germany", "🇩🇪", "Munich Edge"),

        // Netherlands 🇳🇱
        EndpointItem("ep_nl_1", "162.159.192.55", 1074, "NL", "Netherlands", "🇳🇱", "Amsterdam Edge"),
        EndpointItem("ep_nl_2", "188.114.97.35", 859, "NL", "Netherlands", "🇳🇱", "Rotterdam Edge"),

        // Finland 🇫🇮
        EndpointItem("ep_fi_1", "162.159.193.70", 1074, "FI", "Finland", "🇫🇮", "Helsinki Edge"),
        EndpointItem("ep_fi_2", "188.114.96.60", 894, "FI", "Finland", "🇫🇮", "Espoo Edge"),

        // Sweden 🇸🇪
        EndpointItem("ep_se_1", "162.159.192.88", 1074, "SE", "Sweden", "🇸🇪", "Stockholm Edge"),
        EndpointItem("ep_se_2", "188.114.98.45", 854, "SE", "Sweden", "🇸🇪", "Gothenburg Edge"),

        // Poland 🇵🇱
        EndpointItem("ep_pl_1", "162.159.193.102", 1074, "PL", "Poland", "🇵🇱", "Warsaw Edge"),
        EndpointItem("ep_pl_2", "188.114.97.80", 878, "PL", "Poland", "🇵🇱", "Krakow Edge"),

        // United Kingdom 🇬🇧
        EndpointItem("ep_uk_1", "162.159.192.120", 1074, "UK", "United Kingdom", "🇬🇧", "London Edge"),
        EndpointItem("ep_uk_2", "188.114.96.110", 859, "UK", "United Kingdom", "🇬🇧", "Manchester Edge"),

        // France 🇫🇷
        EndpointItem("ep_fr_1", "162.159.193.135", 1074, "FR", "France", "🇫🇷", "Paris Edge"),
        EndpointItem("ep_fr_2", "188.114.97.125", 903, "FR", "France", "🇫🇷", "Marseille Edge"),

        // United States 🇺🇸
        EndpointItem("ep_us_1", "162.159.192.160", 1074, "US", "United States", "🇺🇸", "New York Edge"),
        EndpointItem("ep_us_2", "162.159.193.180", 854, "US", "United States", "🇺🇸", "Los Angeles Edge"),
        EndpointItem("ep_us_3", "188.114.96.170", 878, "US", "United States", "🇺🇸", "Chicago Edge"),

        // Kazakhstan 🇰🇿
        EndpointItem("ep_kz_1", "162.159.192.200", 1074, "KZ", "Kazakhstan", "🇰🇿", "Almaty Edge"),
        EndpointItem("ep_kz_2", "188.114.97.190", 859, "KZ", "Kazakhstan", "🇰🇿", "Astana Edge"),

        // Turkey 🇹🇷
        EndpointItem("ep_tr_1", "162.159.193.220", 1074, "TR", "Turkey", "🇹🇷", "Istanbul Edge"),
        EndpointItem("ep_tr_2", "188.114.98.180", 908, "TR", "Turkey", "🇹🇷", "Ankara Edge"),

        // Singapore 🇸🇬
        EndpointItem("ep_sg_1", "162.159.192.235", 1074, "SG", "Singapore", "🇸🇬", "Singapore Edge"),

        // Japan 🇯🇵
        EndpointItem("ep_jp_1", "162.159.193.245", 1074, "JP", "Japan", "🇯🇵", "Tokyo Edge"),

        // Hong Kong 🇭🇰
        EndpointItem("ep_hk_1", "162.159.195.250", 1074, "HK", "Hong Kong", "🇭🇰", "Hong Kong Edge")
    )

    /**
     * Generates a batch of randomized, candidate unexplored endpoints across WARP IP subnets and bypass ports.
     */
    fun generateCandidateEndpoints(count: Int = 20, countryCode: String = "ALL"): List<EndpointItem> {
        val random = Random()
        val baseSubnets = listOf(
            "162.159.192",
            "162.159.193",
            "162.159.195",
            "188.114.96",
            "188.114.97",
            "188.114.98",
            "188.114.99"
        )

        val matchingCountry = countries.firstOrNull { it.first == countryCode } ?: countries.first()

        val list = mutableListOf<EndpointItem>()
        for (i in 1..count) {
            val subnet = baseSubnets.random()
            val host = 1 + random.nextInt(254)
            val ip = "$subnet.$host"
            val port = bypassPorts.random()
            list.add(
                EndpointItem(
                    id = "cand_${ip}_$port",
                    ip = ip,
                    port = port,
                    countryCode = if (countryCode == "ALL") "ALL" else countryCode,
                    countryName = matchingCountry.second,
                    flagEmoji = matchingCountry.second.takeLast(2),
                    ispName = "Unexplored CF Scanner"
                )
            )
        }
        return list
    }

    fun getRandomEndpoint(): String {
        val item = preconfiguredEndpoints.random()
        return item.fullEndpoint
    }
}
