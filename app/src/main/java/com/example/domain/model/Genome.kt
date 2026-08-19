package com.example.domain.model

import java.util.UUID

/**
 * Represents an individual genetic profile encoding AmneziaWG obfuscation parameters,
 * custom handshake noise (I1), Russian whitelist SNI, and censorship-resistant DNS resolver configuration.
 */
data class Genome(
    val id: String = UUID.randomUUID().toString(),
    val jc: Int,          // 0..10
    val jmin: Int,        // 32..1024
    val jmax: Int,        // jmin..1024
    val s1: Int,          // 0..64
    val s2: Int,          // 0..64
    val s3: Int,          // 0..64
    val s4: Int,          // 0..32
    val h1: Long,         // 0..4_294_967_295
    val h2: Long,         // 0..4_294_967_295, != h1
    val h3: Long,         // 0..4_294_967_295, != h1, != h2
    val h4: Long,         // 0..4_294_967_295, != h1, != h2, != h3
    val i1: String? = null,
    val sni: String? = null,
    val endpoint: String? = null,
    val mtu: Int = 1280,  // 1280..1420
    val dns: String = "111.88.96.50, 111.88.96.51",
    var fitness: Double = 0.0,
    var avgPingMs: Long = 0L,
    var successRate: Double = 0.0,
    var generation: Int = 0
) {
    /**
     * Applies this genome's obfuscation parameters, noise payload, SNI, and evolved DNS onto a base configuration template.
     */
    fun applyToConfig(base: AwgConfig): AwgConfig {
        return base.copy(
            id = UUID.randomUUID().toString(),
            name = "${base.name} [Gen $generation - Fit: ${"%.2f".format(fitness)}]",
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
            i1 = i1 ?: base.i1,
            sni = sni ?: base.sni,
            endpoint = endpoint ?: base.endpoint,
            mtu = mtu,
            dns = dns.ifBlank { base.dns },
            lastPingMs = avgPingMs,
            lastFitness = fitness
        )
    }

    /**
     * Validates and repairs constraints:
     * - Jmax >= Jmin
     * - H1 != H2 != H3 != H4
     */
    fun validated(): Genome {
        var validJmin = jmin.coerceIn(32, 1024)
        var validJmax = jmax.coerceIn(validJmin, 1024)
        val validJc = jc.coerceIn(0, 10)
        val validS1 = s1.coerceIn(0, 64)
        val validS2 = s2.coerceIn(0, 64)
        val validS3 = s3.coerceIn(0, 64)
        val validS4 = s4.coerceIn(0, 32)
        val validMtu = mtu.coerceIn(1280, 1420)
        val validDns = dns.ifBlank { "111.88.96.50, 111.88.96.51" }

        var newH1 = h1.coerceIn(1L, 4294967295L)
        var newH2 = h2.coerceIn(1L, 4294967295L)
        if (newH2 == newH1) newH2 = (newH1 + 10007L) % 4294967295L + 1L

        var newH3 = h3.coerceIn(1L, 4294967295L)
        while (newH3 == newH1 || newH3 == newH2) {
            newH3 = (newH3 + 20011L) % 4294967295L + 1L
        }

        var newH4 = h4.coerceIn(1L, 4294967295L)
        while (newH4 == newH1 || newH4 == newH2 || newH4 == newH3) {
            newH4 = (newH4 + 30013L) % 4294967295L + 1L
        }

        return copy(
            jc = validJc,
            jmin = validJmin,
            jmax = validJmax,
            s1 = validS1,
            s2 = validS2,
            s3 = validS3,
            s4 = validS4,
            h1 = newH1,
            h2 = newH2,
            h3 = newH3,
            h4 = newH4,
            mtu = validMtu,
            dns = validDns
        )
    }
}
