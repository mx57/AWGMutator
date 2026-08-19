package com.example.evolution

import com.example.domain.model.DnsCatalog
import com.example.domain.model.Genome
import java.util.Random
import java.util.UUID

/**
 * Manages generation and tournament selection of Genomes with DNS server integration.
 */
class Population(
    val size: Int = 12,
    val tournamentSize: Int = 3,
    val eliteCount: Int = 2
) {
    private val random = Random()

    fun createInitialPopulation(seedGenome: Genome? = null): List<Genome> {
        val list = mutableListOf<Genome>()
        if (seedGenome != null) {
            list.add(seedGenome.copy(id = UUID.randomUUID().toString(), generation = 1).validated())
        }

        while (list.size < size) {
            val jmin = 64 + random.nextInt(256)
            val jmax = jmin + random.nextInt(512)
            val jc = random.nextInt(10)
            val s1 = random.nextInt(64)
            val s2 = random.nextInt(64)
            val s3 = random.nextInt(64)
            val s4 = random.nextInt(32)
            val mtu = 1280 + random.nextInt(141)

            val h1 = (random.nextLong() and 0x7FFFFFFF) + 1000000L
            val h2 = (random.nextLong() and 0x7FFFFFFF) + 2000000L
            val h3 = (random.nextLong() and 0x7FFFFFFF) + 3000000L
            val h4 = (random.nextLong() and 0x7FFFFFFF) + 4000000L
            val dns = DnsCatalog.getRandomDns()

            val genome = Genome(
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
                dns = dns,
                generation = 1
            ).validated()
            list.add(genome)
        }
        return list
    }

    /**
     * Tournament selection: randomly picks [tournamentSize] candidates and selects the fittest.
     */
    fun selectParent(genomes: List<Genome>): Genome {
        var best: Genome = genomes[random.nextInt(genomes.size)]
        for (i in 1 until tournamentSize) {
            val candidate = genomes[random.nextInt(genomes.size)]
            if (candidate.fitness > best.fitness) {
                best = candidate
            }
        }
        return best
    }
}
