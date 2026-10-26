package com.vatsyahub.workout.domain

import kotlin.random.Random

data class PoolExercise(val id: Long, val types: Set<String>)

fun drawBodyPart(ids: List<Long>, count: Int, random: Random): List<Long> {
    if (ids.isEmpty() || count <= 0) return emptyList()
    return ids.distinct().shuffled(random).take(count)
}

fun rerollOne(ids: List<Long>, current: Long, random: Random): Long {
    val others = ids.distinct().filter { it != current }
    if (others.isEmpty()) return current
    return others.random(random)
}

fun rerollAll(current: List<Long>, pool: List<Long>, random: Random): List<Long> {
    val options = pool.distinct()
    if (options.size <= 1) return current
    return current.map { line ->
        val choices = options.filter { it != line }
        (choices.ifEmpty { options }).random(random)
    }
}

/**
 * Fills one HIIT set. Prefers a workout type not already used in the set.
 * Exercises stay unique inside the set. The same exercise may be drawn again in a later set.
 */
fun drawSet(pool: List<PoolExercise>, count: Int, random: Random): List<Long> {
    if (pool.isEmpty() || count <= 0) return emptyList()
    val picked = ArrayList<Long>(count)
    val usedTypes = HashSet<String>()
    val cap = minOf(count, pool.size)
    repeat(cap) {
        val remaining = pool.filter { it.id !in picked }
        val fresh = remaining.filter { exercise -> exercise.types.any { it !in usedTypes } }
        val bag = fresh.ifEmpty { remaining }
        val choice = bag.random(random)
        picked += choice.id
        val cover = choice.types.firstOrNull { it !in usedTypes }
        if (cover != null) usedTypes += cover
    }
    return picked
}
