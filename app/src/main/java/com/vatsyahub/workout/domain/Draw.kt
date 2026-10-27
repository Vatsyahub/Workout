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

data class RerollPick(val id: Long, val shown: List<Long>)

/**
 * Keeps a rebuilt list, but does not hand a slot an exercise it has already shown
 * while another match is free.
 */
fun repairAssignment(
    assigned: List<Long>,
    histories: List<List<Long>>,
    matches: List<Long>,
    random: Random,
): List<RerollPick> {
    val options = matches.distinct().toSet()
    val ids = assigned.toMutableList()
    val counts = HashMap<Long, Int>()
    for (id in ids) if (id in options) counts[id] = (counts[id] ?: 0) + 1
    fun held(id: Long) = (counts[id] ?: 0) > 0
    fun release(id: Long) {
        val left = (counts[id] ?: 0) - 1
        if (left <= 0) counts.remove(id) else counts[id] = left
    }
    fun hold(id: Long) {
        counts[id] = (counts[id] ?: 0) + 1
    }
    for (index in ids.indices) {
        val seen = histories.getOrElse(index) { emptyList() }.filter { it in options }.toSet()
        val current = ids[index]
        if (current !in seen) continue
        if (current in options) release(current)
        val choices = options.filter { it != current && !held(it) && it !in seen }
        val replacement = if (choices.isEmpty()) null else choices.random(random)
        if (replacement == null) {
            if (current in options) hold(current)
            continue
        }
        ids[index] = replacement
        hold(replacement)
    }
    val optionList = options.toList()
    return List(ids.size) { index ->
        val seen = histories.getOrElse(index) { emptyList() }.filter { it in options }.distinct()
        val chosen = ids[index]
        if (optionList.isNotEmpty() && optionList.all { it in seen }) {
            RerollPick(chosen, listOf(chosen))
        } else {
            RerollPick(chosen, (seen + chosen).distinct())
        }
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
