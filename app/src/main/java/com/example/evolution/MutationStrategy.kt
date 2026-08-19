package com.example.evolution

import com.example.domain.model.DnsCatalog
import com.example.domain.model.Genome
import java.util.Random

/**
 * Mutates individual genes within configurable magnitude with specific Anti-DPI pattern shifts
 * (frequently altering Jmin/Jmax randomization spreads, payload fragmentation offsets, and DNS resolvers).
 */
class MutationStrategy(
    private val mutationRate: Double = 0.20,
    private val magnitude: Double = 0.25
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
            return (value + delta).coerceIn(1000000L, 4294967295L)
        }

        // Apply Junk Packet dynamic pattern mutations
        jc = mutateInt(jc, 1, 9)
        jmin = mutateInt(jmin, 32, 512)
        // Ensure Jmax maintains sufficient spread above Jmin for entropy
        val minSpread = 48 + random.nextInt(96)
        jmax = mutateInt(jmax, jmin + minSpread, 1024)

        // Apply Payload Fragmentation S1..S4 mutations
        s1 = mutateInt(s1, 10, 64)
        s2 = mutateInt(s2, 14, 64)
        s3 = mutateInt(s3, 8, 64)
        s4 = mutateInt(s4, 4, 32)

        // Mutate Magic Headers
        h1 = mutateLong(h1)
        h2 = mutateLong(h2)
        h3 = mutateLong(h3)
        h4 = mutateLong(h4)

        // Mutate MTU in safe stealth range
        mtu = mutateInt(mtu, 1280, 1400)

        // Mutate DNS Server Resolver with probability
        if (random.nextDouble() <= mutationRate * 1.25) {
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
            mtu = mtu,
            dns = dns
        ).validated()
    }
}
