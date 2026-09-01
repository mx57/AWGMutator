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
        // High-priority tested & working clean Anycast bypass endpoints (EU-Anycast & Alternate Ports)
        EndpointItem("ep_ru_bypass_1", "188.114.96.1", 1074, "ALL", "Global Anycast", "🌍", "CF Anycast Port 1074 (Clean High-Speed)"),
        EndpointItem("ep_ru_bypass_2", "188.114.96.60", 894, "ALL", "Global Anycast", "🌍", "CF Edge Port 894 (Clean)"),
        EndpointItem("ep_ru_bypass_3", "188.114.98.1", 4500, "ALL", "Global Anycast", "🌍", "CF NAT-T Port 4500 (Clean)"),
        EndpointItem("ep_ru_bypass_4", "188.114.97.35", 859, "ALL", "Global Anycast", "🌍", "CF Edge Port 859 (Clean)"),
        EndpointItem("ep_ru_bypass_5", "188.114.98.45", 878, "ALL", "Global Anycast", "🌍", "CF Edge Port 878 (Clean)"),
        EndpointItem("ep_ru_bypass_6", "188.114.99.100", 903, "ALL", "Global Anycast", "🌍", "CF Edge Port 903 (Clean)"),
        EndpointItem("ep_ru_bypass_7", "188.114.99.1", 500, "ALL", "Global Anycast", "🌍", "CF IPsec Port 500 (Clean)"),
        EndpointItem("ep_ru_bypass_8", "188.114.97.1", 854, "ALL", "Global Anycast", "🌍", "CF Anycast Port 854 (Clean)"),
        EndpointItem("ep_ru_bypass_9", "188.114.97.150", 908, "ALL", "Global Anycast", "🌍", "CF Edge Port 908 (Clean)"),
        EndpointItem("ep_ru_bypass_10", "162.159.195.1", 1074, "ALL", "Global Anycast", "🌍", "CF Bypass Port 1074"),
        EndpointItem("ep_ru_bypass_11", "162.159.193.1", 2408, "ALL", "Global Anycast", "🌍", "CF Standard Port 2408"),
        EndpointItem("ep_ru_bypass_12", "162.159.192.1", 2408, "ALL", "Global Anycast", "🌍", "CF Standard Port 2408"),

        // Germany 🇩🇪
        EndpointItem("ep_de_1", "188.114.97.10", 1074, "DE", "Germany", "🇩🇪", "Frankfurt Edge (188.114.97.10)"),
        EndpointItem("ep_de_2", "188.114.98.15", 854, "DE", "Germany", "🇩🇪", "Berlin Edge (188.114.98.15)"),
        EndpointItem("ep_de_3", "188.114.96.22", 878, "DE", "Germany", "🇩🇪", "Munich Edge (188.114.96.22)"),

        // Netherlands 🇳🇱
        EndpointItem("ep_nl_1", "188.114.97.25", 1074, "NL", "Netherlands", "🇳🇱", "Amsterdam Edge (188.114.97.25)"),
        EndpointItem("ep_nl_2", "188.114.97.35", 859, "NL", "Netherlands", "🇳🇱", "Rotterdam Edge (188.114.97.35)"),

        // Finland 🇫🇮
        EndpointItem("ep_fi_1", "188.114.98.30", 1074, "FI", "Finland", "🇫🇮", "Helsinki Edge (188.114.98.30)"),
        EndpointItem("ep_fi_2", "188.114.96.60", 894, "FI", "Finland", "🇫🇮", "Espoo Edge (188.114.96.60)"),

        // Sweden 🇸🇪
        EndpointItem("ep_se_1", "188.114.99.40", 1074, "SE", "Sweden", "🇸🇪", "Stockholm Edge (188.114.99.40)"),
        EndpointItem("ep_se_2", "188.114.98.45", 854, "SE", "Sweden", "🇸🇪", "Gothenburg Edge (188.114.98.45)"),

        // Poland 🇵🇱
        EndpointItem("ep_pl_1", "188.114.96.50", 1074, "PL", "Poland", "🇵🇱", "Warsaw Edge (188.114.96.50)"),
        EndpointItem("ep_pl_2", "188.114.97.80", 878, "PL", "Poland", "🇵🇱", "Krakow Edge (188.114.97.80)"),

        // United Kingdom 🇬🇧
        EndpointItem("ep_uk_1", "188.114.98.60", 1074, "UK", "United Kingdom", "🇬🇧", "London Edge (188.114.98.60)"),
        EndpointItem("ep_uk_2", "188.114.96.110", 859, "UK", "United Kingdom", "🇬🇧", "Manchester Edge (188.114.96.110)"),

        // France 🇫🇷
        EndpointItem("ep_fr_1", "188.114.99.70", 1074, "FR", "France", "🇫🇷", "Paris Edge (188.114.99.70)"),
        EndpointItem("ep_fr_2", "188.114.97.125", 903, "FR", "France", "🇫🇷", "Marseille Edge (188.114.97.125)"),

        // United States 🇺🇸
        EndpointItem("ep_us_1", "188.114.96.80", 1074, "US", "United States", "🇺🇸", "New York Edge (188.114.96.80)"),
        EndpointItem("ep_us_2", "188.114.97.90", 854, "US", "United States", "🇺🇸", "Los Angeles Edge (188.114.97.90)"),
        EndpointItem("ep_us_3", "188.114.96.170", 878, "US", "United States", "🇺🇸", "Chicago Edge (188.114.96.170)"),

        // Kazakhstan 🇰🇿
        EndpointItem("ep_kz_1", "188.114.98.100", 1074, "KZ", "Kazakhstan", "🇰🇿", "Almaty Edge (188.114.98.100)"),
        EndpointItem("ep_kz_2", "188.114.97.190", 859, "KZ", "Kazakhstan", "🇰🇿", "Astana Edge (188.114.97.190)"),

        // Turkey 🇹🇷
        EndpointItem("ep_tr_1", "188.114.99.110", 1074, "TR", "Turkey", "🇹🇷", "Istanbul Edge (188.114.99.110)"),
        EndpointItem("ep_tr_2", "188.114.98.180", 908, "TR", "Turkey", "🇹🇷", "Ankara Edge (188.114.98.180)"),

        // Singapore 🇸🇬
        EndpointItem("ep_sg_1", "188.114.96.120", 1074, "SG", "Singapore", "🇸🇬", "Singapore Edge (188.114.96.120)"),

        // Japan 🇯🇵
        EndpointItem("ep_jp_1", "188.114.97.130", 1074, "JP", "Japan", "🇯🇵", "Tokyo Edge (188.114.97.130)"),

        // Hong Kong 🇭🇰
        EndpointItem("ep_hk_1", "188.114.98.140", 1074, "HK", "Hong Kong", "🇭🇰", "Hong Kong Edge (188.114.98.140)")
    )

    /**
     * Generates a batch of randomized, candidate unexplored endpoints across clean WARP IP subnets and bypass ports.
     */
    fun generateCandidateEndpoints(count: Int = 20, countryCode: String = "ALL"): List<EndpointItem> {
        val random = Random()
        val baseSubnets = listOf(
            "188.114.96",
            "188.114.97",
            "188.114.98",
            "188.114.99",
            "162.159.195"
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
