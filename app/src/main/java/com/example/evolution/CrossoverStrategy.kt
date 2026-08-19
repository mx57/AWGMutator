package com.example.evolution

import com.example.domain.model.Genome
import java.util.Random
import java.util.UUID

/**
 * Uniform crossover strategy for combining genes from two parent genomes including DNS.
 */
class CrossoverStrategy(
    private val crossoverRate: Double = 0.7
) {
    private val random = Random()

    fun crossover(parentA: Genome, parentB: Genome, generation: Int): Pair<Genome, Genome> {
        if (random.nextDouble() > crossoverRate) {
            return Pair(
                parentA.copy(id = UUID.randomUUID().toString(), generation = generation),
                parentB.copy(id = UUID.randomUUID().toString(), generation = generation)
            )
        }

        fun <T> pick(a: T, b: T): T = if (random.nextBoolean()) a else b

        val child1 = Genome(
            id = UUID.randomUUID().toString(),
            jc = pick(parentA.jc, parentB.jc),
            jmin = pick(parentA.jmin, parentB.jmin),
            jmax = pick(parentA.jmax, parentB.jmax),
            s1 = pick(parentA.s1, parentB.s1),
            s2 = pick(parentA.s2, parentB.s2),
            s3 = pick(parentA.s3, parentB.s3),
            s4 = pick(parentA.s4, parentB.s4),
            h1 = pick(parentA.h1, parentB.h1),
            h2 = pick(parentA.h2, parentB.h2),
            h3 = pick(parentA.h3, parentB.h3),
            h4 = pick(parentA.h4, parentB.h4),
            mtu = pick(parentA.mtu, parentB.mtu),
            dns = pick(parentA.dns, parentB.dns),
            generation = generation
        ).validated()

        val child2 = Genome(
            id = UUID.randomUUID().toString(),
            jc = pick(parentB.jc, parentA.jc),
            jmin = pick(parentB.jmin, parentA.jmin),
            jmax = pick(parentB.jmax, parentA.jmax),
            s1 = pick(parentB.s1, parentA.s1),
            s2 = pick(parentB.s2, parentA.s2),
            s3 = pick(parentB.s3, parentA.s3),
            s4 = pick(parentB.s4, parentA.s4),
            h1 = pick(parentB.h1, parentA.h1),
            h2 = pick(parentB.h2, parentA.h2),
            h3 = pick(parentB.h3, parentA.h3),
            h4 = pick(parentB.h4, parentA.h4),
            mtu = pick(parentB.mtu, parentA.mtu),
            dns = pick(parentB.dns, parentA.dns),
            generation = generation
        ).validated()

        return Pair(child1, child2)
    }
}
