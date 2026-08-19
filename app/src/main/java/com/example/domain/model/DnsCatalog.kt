package com.example.domain.model

/**
 * Represents a DNS Server profile with IPv4 addresses, provider name, and description.
 */
data class DnsServer(
    val id: String,
    val name: String,
    val primary: String,
    val secondary: String,
    val description: String,
    val country: String = "Global",
    val isEncryptedCapable: Boolean = true
) {
    val formatted: String
        get() = if (secondary.isNotBlank()) "$primary, $secondary" else primary
}

/**
 * Curated catalog of 30+ ultra-fast, censorship-resistant DNS servers for Genetic Algorithm
 * mutation, multi-DNS selection, and Config Generator integration.
 */
object DnsCatalog {
    val servers: List<DnsServer> = listOf(
        DnsServer("cf_standard", "Cloudflare Standard (1.1.1.1)", "1.1.1.1", "1.0.0.1", "Fastest worldwide anycast DNS with zero logging", "Global"),
        DnsServer("google", "Google Public DNS", "8.8.8.8", "8.8.4.4", "Global resilient DNS with ECS support", "Global"),
        DnsServer("quad9_unfiltered", "Quad9 Unfiltered", "9.9.9.10", "149.112.112.10", "Unfiltered Swiss DNS without domain blocking", "CH"),
        DnsServer("quad9_clean", "Quad9 ECS Enabled", "9.9.9.11", "149.112.112.11", "Optimized routing with EDNS Client Subnet", "CH"),
        DnsServer("adguard_def", "AdGuard Standard", "94.140.14.14", "94.140.15.15", "Blocks ads, trackers, and telemetry", "CY"),
        DnsServer("adguard_unfiltered", "AdGuard Non-Filtering", "94.140.14.140", "94.140.14.141", "High speed, no adblocking rules", "CY"),
        DnsServer("adguard_family", "AdGuard Family", "94.140.14.15", "94.140.15.16", "Family-safe browsing with ad-blocking", "CY"),
        DnsServer("controld_standard", "Control D Standard", "76.76.2.0", "76.76.10.0", "Modern uncensored Anycast DNS", "CA"),
        DnsServer("controld_malware", "Control D Malware Block", "76.76.2.2", "76.76.10.2", "AI-driven malicious domain filtering", "CA"),
        DnsServer("mullvad", "Mullvad Anti-Censorship", "194.242.2.2", "193.19.108.2", "Privacy-first Swedish DNS without logging", "SE"),
        DnsServer("nextdns", "NextDNS Global", "45.90.28.0", "45.90.30.0", "Fast low-latency global resolver", "US"),
        DnsServer("opendns", "Cisco OpenDNS", "208.67.222.222", "208.67.220.220", "Enterprise-grade high availability resolver", "US"),
        DnsServer("opendns_family", "OpenDNS FamilyShield", "208.67.222.123", "208.67.220.123", "Pre-configured family safety DNS", "US"),
        DnsServer("dnssb", "DNS.SB Uncensored", "185.222.222.222", "45.11.45.11", "Fully uncensored European DNS", "EU"),
        DnsServer("cleanbrowsing", "CleanBrowsing Security", "185.228.168.9", "185.228.169.9", "Phishing and malicious URL shielding", "US"),
        DnsServer("comodo", "Comodo Secure DNS", "8.26.56.26", "8.20.247.20", "Redundant worldwide security DNS", "US"),
        DnsServer("level3", "Level3 / CenturyLink", "4.2.2.1", "4.2.2.2", "Tier-1 backbone global resolver", "US"),
        DnsServer("yandex_basic", "Yandex DNS Basic", "77.88.8.8", "77.88.8.1", "Fast Russian domestic DNS", "RU"),
        DnsServer("yandex_safe", "Yandex Safe DNS", "77.88.8.88", "77.88.8.2", "Malware blocking Russian DNS", "RU"),
        DnsServer("ali_dns", "Alibaba Public DNS", "223.5.5.5", "223.6.6.6", "Ultra-low latency Asia-Pacific DNS", "CN"),
        DnsServer("tencent_dns", "Tencent DNSPod", "119.29.29.29", "1.12.12.12", "High availability Anycast DNS", "CN"),
        DnsServer("dns_watch", "DNS.WATCH Germany", "84.200.69.80", "84.200.70.40", "Fast, free, uncensored and independent", "DE"),
        DnsServer("cira_shield", "CIRA Canadian Shield", "149.112.121.10", "149.112.122.10", "Community cybersecurity resolver", "CA"),
        DnsServer("open_nic", "OpenNIC Public", "185.121.177.177", "169.239.202.202", "Decentralized open alternative DNS", "EU"),
        DnsServer("he_net", "Hurricane Electric DNS", "74.82.42.42", "", "Tier-1 internet backbone resolver", "US"),
        DnsServer("neustar", "Neustar UltraDNS", "156.154.70.1", "156.154.71.1", "Global enterprise DNS network", "US")
    )

    fun getRandomDns(): String {
        val server = servers.random()
        return server.formatted
    }

    fun findByIp(ip: String): DnsServer? {
        val clean = ip.trim().split(",").firstOrNull()?.trim().orEmpty()
        return servers.firstOrNull { it.primary == clean || it.secondary == clean }
    }

    fun formatMultiple(selectedIds: List<String>): String {
        if (selectedIds.isEmpty()) return "1.1.1.1, 1.0.0.1"
        val selectedServers = servers.filter { it.id in selectedIds }
        val allIps = mutableListOf<String>()
        selectedServers.forEach { server ->
            if (server.primary.isNotBlank() && !allIps.contains(server.primary)) allIps.add(server.primary)
            if (server.secondary.isNotBlank() && !allIps.contains(server.secondary)) allIps.add(server.secondary)
        }
        return allIps.joinToString(", ")
    }
}
