package com.vatsyahub.workout.domain

import kotlin.random.Random

data class DrawCard(
    val id: Long,
    val name: String,
    val muscles: List<String>,
    val types: Set<String>,
    val usage: String,
    val key: Long = id,
)

private data class MuscleRole(val key: String, val band: Int, val rank: Int)

private val muscleRoles: Map<String, MuscleRole> = listOf(
    "chest" to MuscleRole("pecs", 0, 0),
    "upper chest" to MuscleRole("pecs", 0, 0),
    "lower chest" to MuscleRole("pecs", 0, 0),
    "pecs" to MuscleRole("pecs", 0, 0),
    "lats" to MuscleRole("lats", 0, 1),
    "back" to MuscleRole("lats", 0, 1),
    "upper back" to MuscleRole("rhomboids", 0, 2),
    "rhomboids" to MuscleRole("rhomboids", 0, 2),
    "traps" to MuscleRole("rhomboids", 0, 2),
    "trapezius" to MuscleRole("rhomboids", 0, 2),
    "quadriceps" to MuscleRole("quads", 0, 3),
    "quads" to MuscleRole("quads", 0, 3),
    "legs" to MuscleRole("quads", 0, 3),
    "hamstrings" to MuscleRole("hamstrings", 0, 4),
    "glutes" to MuscleRole("glutes", 0, 5),
    "lower back" to MuscleRole("spinal", 0, 6),
    "spinal erectors" to MuscleRole("spinal", 0, 6),
    "front deltoids" to MuscleRole("front delts", 1, 0),
    "front delts" to MuscleRole("front delts", 1, 0),
    "shoulders" to MuscleRole("shoulders", 1, 1),
    "side deltoids" to MuscleRole("side delts", 1, 2),
    "side delts" to MuscleRole("side delts", 1, 2),
    "rear deltoids" to MuscleRole("rear delts", 1, 3),
    "rear delts" to MuscleRole("rear delts", 1, 3),
    "biceps" to MuscleRole("biceps", 2, 0),
    "brachialis" to MuscleRole("brachialis", 2, 1),
    "triceps" to MuscleRole("triceps", 2, 2),
    "arms" to MuscleRole("arms", 2, 3),
    "forearms" to MuscleRole("forearms", 3, 0),
    "grip" to MuscleRole("forearms", 3, 0),
    "calves" to MuscleRole("calves", 3, 1),
    "gastrocnemius" to MuscleRole("calves", 3, 1),
    "soleus" to MuscleRole("soleus", 3, 2),
    "ankles" to MuscleRole("calves", 3, 1),
    "abs" to MuscleRole("abs", 3, 3),
    "core" to MuscleRole("abs", 3, 3),
    "obliques" to MuscleRole("obliques", 3, 4),
    "hip flexors" to MuscleRole("hip flexors", 3, 5),
    "hip adductors" to MuscleRole("adductors", 3, 6),
    "adductors" to MuscleRole("adductors", 3, 6),
    "inner thighs" to MuscleRole("adductors", 3, 6),
    "hip abductors" to MuscleRole("glute medius", 3, 7),
    "glute medius" to MuscleRole("glute medius", 3, 7),
    "erectors" to MuscleRole("spinal", 0, 6),
).toMap()

private fun roleOf(muscle: String): MuscleRole =
    muscleRoles[muscle.trim().lowercase()] ?: MuscleRole(muscle.trim().lowercase().ifBlank { "other" }, 4, 0)

private fun roleByKey(key: String): MuscleRole =
    muscleRoles.values.firstOrNull { it.key == key } ?: MuscleRole(key, 4, 0)

fun largestMuscle(muscles: List<String>): String {
    if (muscles.isEmpty()) return "other"
    val ranked = muscles.map { roleOf(it) }
    val bestBand = ranked.minOf { it.band }
    return ranked.first { it.band == bestBand }.key
}

fun lineUse(sessionKind: String, usage: String): String = when (sessionKind) {
    "HEAVY" -> "HEAVY"
    "BODYWEIGHT" -> "BODYWEIGHT"
    else -> if (usage == "HEAVY") "HEAVY" else "BODYWEIGHT"
}

fun drawnOrder(cards: List<DrawCard>, sessionKind: String, dragged: Boolean): List<DrawCard> =
    if (dragged) cards else orderStrength(cards, sessionKind)

fun orderStrength(cards: List<DrawCard>, sessionKind: String): List<DrawCard> {
    if (cards.size <= 1) return cards
    val sequenced = ArrayList<DrawCard>()
    for (band in cards.groupBy { roleByKey(largestMuscle(it.muscles)).band }.keys.sorted()) {
        val group = cards.filter { roleByKey(largestMuscle(it.muscles)).band == band }
        val sorted = group.sortedWith(
            compareBy(
                { roleByKey(largestMuscle(it.muscles)).rank },
                { if (lineUse(sessionKind, it.usage) == "HEAVY") 0 else 1 },
                { if (it.muscles.size > 1) 0 else 1 },
                { it.name.lowercase() },
                { it.key },
            ),
        )
        sequenced += alternatePushPull(sorted)
    }
    return fixSpine(placeDependents(sequenced), sessionKind)
}

fun spreadType(pool: List<DrawCard>, count: Int, random: Random): List<DrawCard> {
    if (count <= 0 || pool.isEmpty()) return emptyList()
    val used = HashSet<Long>()
    val taken = ArrayList<DrawCard>()
    fun remaining() = pool.filter { it.key !in used }
    fun take(roleKey: String): Boolean {
        val options = remaining().filter { largestMuscle(it.muscles) == roleKey }
        if (options.isEmpty()) return false
        val pick = options.random(random)
        used += pick.key
        taken += pick
        return true
    }
    fun roles(band: Int): List<MuscleRole> =
        remaining().map { roleByKey(largestMuscle(it.muscles)) }.filter { it.band == band }.distinctBy { it.key }
    val larges = roles(0).sortedBy { it.rank }
    for (role in larges) {
        if (taken.size >= count) return taken
        take(role.key)
    }
    val largeKeys = larges.map { it.key }.toSet()
    val smalls = (1..4).flatMap { band -> roles(band) }.sortedBy { smallPriority(it, largeKeys) }
    for (role in smalls) {
        if (taken.size >= count) return taken
        take(role.key)
    }
    for (role in roles(0).sortedBy { it.rank }) {
        if (taken.size >= count) return taken
        take(role.key)
    }
    while (taken.size < count) {
        val before = taken.size
        for (band in 0..4) {
            for (role in roles(band).sortedWith(compareBy({ if (band == 0) 0 else 1 }, { it.rank }))) {
                if (taken.size >= count) return taken
                take(role.key)
            }
        }
        if (taken.size == before) break
    }
    return taken
}

fun takeExact(pool: List<DrawCard>, count: Int, random: Random, used: Set<Long>): List<DrawCard> {
    if (count <= 0) return emptyList()
    return pool.filter { it.key !in used }.distinctBy { it.key }.shuffled(random).take(count)
}

fun repsFor(card: DrawCard, ordered: List<DrawCard>, sessionKind: String, history: List<Int>?): List<Int> {
    if (!history.isNullOrEmpty()) return history
    val reps = defaultReps(card, ordered, sessionKind)
    return List(3) { reps }
}

fun defaultReps(card: DrawCard, ordered: List<DrawCard>, sessionKind: String): Int {
    val use = lineUse(sessionKind, card.usage)
    val muscle = largestMuscle(card.muscles)
    val main = roleByKey(muscle).band == 0 && ordered.firstOrNull {
        largestMuscle(it.muscles) == muscle && lineUse(sessionKind, it.usage) == use
    }?.key == card.key
    return when {
        use == "HEAVY" && main -> 6
        use == "HEAVY" -> 10
        main -> 8
        else -> 12
    }
}

private fun smallPriority(role: MuscleRole, larges: Set<String>): Int {
    val pull = larges.any { it == "lats" || it == "rhomboids" }
    val push = larges.any { it == "pecs" }
    val legs = larges.any { it == "quads" || it == "hamstrings" || it == "glutes" }
    return when (role.key) {
        "biceps", "brachialis" -> if (pull) 0 else 20
        "triceps" -> if (push) 1 else 21
        "front delts", "shoulders", "side delts", "rear delts" -> 10
        "calves", "soleus" -> if (legs) 12 else 31
        "arms" -> 22
        "forearms" -> 30
        else -> 40 + role.rank
    }
}

private enum class Side { PUSH, PULL, OTHER }

private fun sideOf(card: DrawCard): Side {
    val types = card.types.map { it.lowercase() }.toSet()
    val name = card.name.lowercase()
    val push = "push" in types || listOf("press", "push-up", "push up", "fly").any { name.contains(it) }
    val pull = "pull" in types || listOf("row", "pull-up", "pull up", "pulldown", "pull-down", "chin-up", "chin up", "curl").any { name.contains(it) }
    return when {
        push && !pull -> Side.PUSH
        pull && !push -> Side.PULL
        push && pull -> when (largestMuscle(card.muscles)) {
            "pecs", "front delts", "shoulders", "side delts", "triceps" -> Side.PUSH
            "lats", "rhomboids", "biceps", "brachialis" -> Side.PULL
            else -> Side.OTHER
        }
        else -> Side.OTHER
    }
}

private fun alternatePushPull(items: List<DrawCard>): List<DrawCard> {
    if (items.none { sideOf(it) == Side.PUSH } || items.none { sideOf(it) == Side.PULL }) return items
    val remaining = items.toMutableList()
    val mixed = ArrayList<DrawCard>()
    while (remaining.isNotEmpty()) {
        val next = remaining.first()
        val last = mixed.lastOrNull()?.let { sideOf(it) }
        val nextSide = sideOf(next)
        if ((last == Side.PUSH || last == Side.PULL) && nextSide == last) {
            val opposite = if (last == Side.PUSH) Side.PULL else Side.PUSH
            val candidate = remaining.firstOrNull { sideOf(it) == opposite }
            if (candidate != null) {
                mixed += candidate
                remaining.remove(candidate)
                continue
            }
        }
        mixed += remaining.removeAt(0)
    }
    return mixed
}

private fun placeDependents(items: List<DrawCard>): List<DrawCard> {
    val moved = items.toMutableList()
    moveAfter(moved, ::isCurl) { isRowOrPullUp(it.name) }
    moveAfter(moved, ::isArmExtension) { isPress(it.name) }
    moveAfter(moved, ::isLegExtension) { isSquat(it.name) }
    moveAfter(moved, ::isLateralRaise) { isOverheadPress(it.name) }
    return moved
}

private fun moveAfter(items: MutableList<DrawCard>, predicate: (DrawCard) -> Boolean, anchor: (DrawCard) -> Boolean) {
    if (items.none(anchor)) return
    val lastAnchor = items.indexOfLast(anchor)
    val moving = items.filter { card ->
        predicate(card) && !anchor(card) && items.indexOf(card) < lastAnchor
    }
    if (moving.isEmpty()) return
    items.removeAll(moving.toSet())
    val at = items.indexOfLast(anchor)
    items.addAll(at + 1, moving)
}

private fun fixSpine(items: List<DrawCard>, sessionKind: String): List<DrawCard> {
    val result = items.toMutableList()
    if (result.size < 2) return result
    if (result.none { isHeavySpine(it, sessionKind) } || result.none { isHeavySquatOrHinge(it, sessionKind) }) return result
    for (spine in result.filter { isHeavySpine(it, sessionKind) }) {
        val at = result.indexOf(spine)
        val earlier = (0 until at).firstOrNull { isHeavySquatOrHinge(result[it], sessionKind) } ?: continue
        result.removeAt(at)
        result.add(earlier, spine)
    }
    var guard = 0
    while (guard++ < result.size * 2) {
        val pair = (0 until result.lastIndex).firstOrNull { spineConflict(result[it], result[it + 1], sessionKind) } ?: break
        if (isHeavySquatOrHinge(result[pair], sessionKind) && isHeavySpine(result[pair + 1], sessionKind)) {
            val spine = result.removeAt(pair + 1)
            result.add(pair, spine)
        }
        val at = (0 until result.lastIndex).firstOrNull { spineConflict(result[it], result[it + 1], sessionKind) } ?: break
        if (!separatePair(result, at, sessionKind)) break
    }
    return result
}

private fun separatePair(result: MutableList<DrawCard>, pair: Int, sessionKind: String): Boolean {
    val earlier = (pair - 1 downTo 0).firstOrNull { index ->
        val card = result[index]
        !isHeavySpine(card, sessionKind) && !isHeavySquatOrHinge(card, sessionKind)
    }
    if (earlier != null) {
        val slice = result.subList(earlier, pair).toList()
        repeat(slice.size) { result.removeAt(earlier) }
        val spineAt = (0 until result.lastIndex).first { spineConflict(result[it], result[it + 1], sessionKind) }
        result.addAll(spineAt + 1, slice)
        return true
    }
    val later = (pair + 2 until result.size).firstOrNull { index ->
        val card = result[index]
        !isHeavySpine(card, sessionKind) && !isHeavySquatOrHinge(card, sessionKind) && canSeparate(card, result)
    } ?: return false
    val card = result.removeAt(later)
    val spineAt = (0 until result.lastIndex).first { spineConflict(result[it], result[it + 1], sessionKind) }
    result.add(spineAt + 1, card)
    return true
}

private fun canSeparate(card: DrawCard, items: List<DrawCard>): Boolean {
    if (isCurl(card) && items.any { isRowOrPullUp(it.name) }) return false
    if (isArmExtension(card) && items.any { isPress(it.name) }) return false
    if (isLegExtension(card) && items.any { isSquat(it.name) }) return false
    if (isLateralRaise(card) && items.any { isOverheadPress(it.name) }) return false
    return true
}

private fun spineConflict(left: DrawCard, right: DrawCard, sessionKind: String): Boolean {
    val spineLeft = isHeavySpine(left, sessionKind)
    val spineRight = isHeavySpine(right, sessionKind)
    val loadLeft = isHeavySquatOrHinge(left, sessionKind)
    val loadRight = isHeavySquatOrHinge(right, sessionKind)
    return (spineLeft && loadRight) || (loadLeft && spineRight)
}

private fun isHeavySpine(card: DrawCard, sessionKind: String): Boolean =
    lineUse(sessionKind, card.usage) == "HEAVY" && largestMuscle(card.muscles) == "spinal" && !isHinge(card) && !isSquat(card.name)

private fun isHeavySquatOrHinge(card: DrawCard, sessionKind: String): Boolean =
    lineUse(sessionKind, card.usage) == "HEAVY" && (isSquat(card.name) || isHinge(card))

private fun isHinge(card: DrawCard): Boolean {
    val name = card.name.lowercase()
    return name.contains("romanian") || name.contains("good morning") || name.contains("rdl") ||
        (name.contains("deadlift") && card.muscles.any { it.equals("Hamstrings", true) })
}

private fun isCurl(card: DrawCard): Boolean {
    val name = card.name.lowercase()
    if (!name.contains("curl")) return false
    if (name.contains("leg curl") || name.contains("hamstring") || name.contains("nordic")) return false
    return largestMuscle(card.muscles) !in setOf("hamstrings", "quads", "glutes", "calves")
}
private fun isRowOrPullUp(name: String): Boolean {
    val text = name.lowercase()
    return text.contains("row") || text.contains("pull-up") || text.contains("pull up") ||
        text.contains("pulldown") || text.contains("pull-down") || text.contains("chin-up") || text.contains("chin up")
}
private fun isArmExtension(card: DrawCard): Boolean {
    val name = card.name.lowercase()
    if (name.contains("leg extension") || name.contains("back extension")) return false
    if (largestMuscle(card.muscles) == "spinal") return false
    return name.contains("pushdown") || name.contains("push-down") || name.contains("triceps extension") ||
        name.contains("skull") || name.contains("kickback")
}
private fun isPress(name: String): Boolean {
    val text = name.lowercase()
    if (text.contains("leg press")) return false
    return text.contains("press") || text.contains("push-up") || text.contains("push up") || text.contains("pushup")
}
private fun isLegExtension(card: DrawCard) = card.name.contains("leg extension", true)
private fun isSquat(name: String) = name.contains("squat", true)
private fun isLateralRaise(card: DrawCard) = card.name.contains("lateral raise", true)
private fun isOverheadPress(name: String): Boolean {
    val text = name.lowercase()
    return text.contains("overhead press") || text.contains("shoulder press") || text.contains("military press") || text.contains("ohp")
}
