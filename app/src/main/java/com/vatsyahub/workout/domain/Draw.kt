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

data class SlotReroll(val ids: List<Long>, val shown: Set<Long>)

/**
 * Replaces the exercise at [index] and leaves every other slot where it is.
 * Unseen matches are used first. After every match has been shown once, the next
 * draw may repeat one, and the count starts again from that exercise.
 * The same exercise is not drawn on the next roll while another match exists.
 */
fun rerollInSlot(
    ids: List<Long>,
    index: Int,
    pool: List<Long>,
    shown: Set<Long>,
    random: Random,
): SlotReroll? {
    if (index !in ids.indices) return null
    val current = ids[index]
    val options = pool.distinct()
    val known = (shown + current).intersect(options.toSet())
    val unseen = options.filter { it !in known }
    val next = when {
        options.isEmpty() -> current
        unseen.isNotEmpty() -> unseen.random(random)
        else -> {
            val others = options.filter { it != current }
            if (others.isEmpty()) current else others.random(random)
        }
    }
    val nextShown = if (unseen.isNotEmpty()) known + next else setOf(next)
    val updated = ids.toMutableList()
    updated[index] = next
    return SlotReroll(updated, nextShown)
}

fun rerollAll(current: List<Long>, pool: List<Long>, random: Random): List<Long> {
    val options = pool.distinct()
    if (options.size <= 1) return current
    return current.map { line ->
        val choices = options.filter { it != line }
        (choices.ifEmpty { options }).random(random)
    }
}

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
