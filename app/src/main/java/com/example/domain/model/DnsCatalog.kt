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
    val isEncryptedCapable: Boolean = true
) {
    val formatted: String
        get() = if (secondary.isNotBlank()) "$primary, $secondary" else primary
}

/**
 * Curated catalog of 18 ultra-fast, censorship-resistant DNS servers for Genetic Algorithm
 * mutation and Config Generator integration.
 */
object DnsCatalog {
    val servers: List<DnsServer> = listOf(
        DnsServer("cf_standard", "Cloudflare Standard", "1.1.1.1", "1.0.0.1", "Fastest worldwide anycast DNS with zero logging"),
        DnsServer("cf_security", "Cloudflare Security", "1.1.1.2", "1.0.0.2", "Blocks malware and phishing domains"),
        DnsServer("cf_family", "Cloudflare Family", "1.1.1.3", "1.0.0.3", "Blocks malware and adult content"),
        DnsServer("google", "Google Public DNS", "8.8.8.8", "8.8.4.4", "Global resilient DNS with ECS support"),
        DnsServer("quad9_sec", "Quad9 High Security", "9.9.9.9", "149.112.112.112", "Swiss non-profit DNS with threat intelligence"),
        DnsServer("quad9_unfiltered", "Quad9 Unfiltered", "9.9.9.10", "149.112.112.10", "Unfiltered Swiss DNS without domain blocking"),
        DnsServer("adguard_def", "AdGuard Standard", "94.140.14.14", "94.140.15.15", "Blocks ads, trackers, and telemetry"),
        DnsServer("adguard_unfiltered", "AdGuard Non-Filtering", "94.140.14.140", "94.140.14.141", "High speed, no adblocking rules"),
        DnsServer("controld_standard", "Control D Standard", "76.76.2.0", "76.76.10.0", "Modern uncensored Anycast DNS"),
        DnsServer("controld_malware", "Control D Malware Block", "76.76.2.2", "76.76.10.2", "AI-driven malicious domain filtering"),
        DnsServer("mullvad", "Mullvad Anti-Censorship", "194.242.2.2", "193.19.108.2", "Privacy-first Swedish DNS without logging"),
        DnsServer("nextdns", "NextDNS Global", "45.90.28.0", "45.90.30.0", "Fast low-latency global resolver"),
        DnsServer("opendns", "Cisco OpenDNS", "208.67.222.222", "208.67.220.220", "Enterprise-grade high availability resolver"),
        DnsServer("opendns_family", "OpenDNS FamilyShield", "208.67.222.123", "208.67.220.123", "Pre-configured family safety DNS"),
        DnsServer("dnssb", "DNS.SB Uncensored", "185.222.222.222", "45.11.45.11", "Fully uncensored European DNS"),
        DnsServer("cleanbrowsing", "CleanBrowsing Security", "185.228.168.9", "185.228.169.9", "Phishing and malicious URL shielding"),
        DnsServer("comodo", "Comodo Secure DNS", "8.26.56.26", "8.20.247.20", "Redundant worldwide security DNS"),
        DnsServer("level3", "Level3 / CenturyLink", "4.2.2.1", "4.2.2.2", "Tier-1 backbone global resolver")
    )

    fun getRandomDns(): String {
        val server = servers.random()
        return server.formatted
    }

    fun findByIp(ip: String): DnsServer? {
        val clean = ip.trim().split(",").firstOrNull()?.trim().orEmpty()
        return servers.firstOrNull { it.primary == clean || it.secondary == clean }
    }
}
