package com.example.evolution

import com.example.domain.model.DnsCatalog
import com.example.domain.model.EndpointCatalog
import com.example.domain.model.EvolutionSettings
import com.example.domain.model.Genome
import com.example.domain.model.SniCatalog
import java.util.Random

/**
 * Mutates individual genes within configurable magnitude according to [EvolutionSettings]
 * (altering Jc, Jmin/Jmax, S1..S4, H1..H4, I1 payload noise, Russian SNI, Endpoints, and DNS resolvers).
 */
class MutationStrategy(
    private val mutationRate: Double = 0.20,
    private val magnitude: Double = 0.25,
    private val settings: EvolutionSettings = EvolutionSettings()
) {
    private val random = Random()

    fun mutate(genome: Genome): Genome {
        var jc = genome.jc
        var jmin = genome.jmin
        var jmax = genome.jmax
        var s1 = genome.s1
        var s2 = genome.s2
        var s3 = genome.s3
        var s4 = genome.s4
        var h1 = genome.h1
        var h2 = genome.h2
        var h3 = genome.h3
        var h4 = genome.h4
        var i1 = genome.i1
        var sni = genome.sni
        var endpoint = genome.endpoint
        var mtu = genome.mtu
        var dns = genome.dns

        fun mutateInt(value: Int, min: Int, max: Int): Int {
            if (random.nextDouble() > mutationRate) return value
            val delta = ((max - min) * magnitude * (random.nextDouble() * 2 - 1)).toInt()
            return (value + delta).coerceIn(min, max)
        }

        fun mutateLong(value: Long): Long {
            if (random.nextDouble() > mutationRate) return value
            val delta = ((random.nextDouble() * 2 - 1) * 75000000L).toLong()
            return (value + delta).coerceIn(1L, 4294967295L)
        }

        // Apply Junk Packet mutations
        if (settings.mutateJc) {
            jc = mutateInt(jc, 0, 10)
        }
        if (settings.mutateJminJmax) {
            jmin = mutateInt(jmin, 32, 512)
            val minSpread = 30 + random.nextInt(64)
            jmax = mutateInt(jmax, jmin + minSpread, 1024)
        }

        // Apply Payload Fragmentation S1..S4 mutations
        if (settings.mutateS1S2) {
            s1 = mutateInt(s1, 0, 64)
            s2 = mutateInt(s2, 0, 64)
        }
        if (settings.mutateS3S4) {
            s3 = mutateInt(s3, 0, 64)
            s4 = mutateInt(s4, 0, 32)
        }

        // Mutate Magic Headers
        if (settings.mutateHeadersH1H4) {
            h1 = mutateLong(h1)
            h2 = mutateLong(h2)
            h3 = mutateLong(h3)
            h4 = mutateLong(h4)
        }

        // Mutate MTU in safe stealth range
        if (settings.mutateMtu) {
            mtu = mutateInt(mtu, 1280, 1420)
        }

        // Mutate I1 (Init payload noise / AmneziaWG 2.0 signatures)
        if (settings.mutatePayloadNoiseI1 && random.nextDouble() <= mutationRate * 1.2) {
            val noiseLen = 32 + random.nextInt(96)
            val bytes = ByteArray(noiseLen).apply { random.nextBytes(this) }
            val hex = bytes.joinToString("") { "%02x".format(it) }
            i1 = "<b 0x$hex>"
        }

        // Mutate Russian Whitelist SNI Domain
        if (settings.mutateSni && random.nextDouble() <= mutationRate * 1.5) {
            sni = SniCatalog.getRandomRussianSni()
        }

        // Mutate Endpoint
        if (settings.mutateEndpoints && random.nextDouble() <= mutationRate * 1.2) {
            endpoint = EndpointCatalog.getRandomEndpoint()
        }

        // Mutate DNS Server Resolver
        if (settings.mutateDns && random.nextDouble() <= mutationRate * 1.25) {
            dns = DnsCatalog.getRandomDns()
        }

        return genome.copy(
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
            i1 = i1,
            sni = sni,
            endpoint = endpoint,
            mtu = mtu,
            dns = dns
        ).validated()
    }
}
