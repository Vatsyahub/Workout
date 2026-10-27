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
    "shoulders" to MuscleRole("shoulders", 1, 1),
    "side deltoids" to MuscleRole("side delts", 1, 2),
    "rear deltoids" to MuscleRole("rear delts", 1, 3),
    "biceps" to MuscleRole("biceps", 2, 0),
    "brachialis" to MuscleRole("brachialis", 2, 1),
    "triceps" to MuscleRole("triceps", 2, 2),
    "arms" to MuscleRole("arms", 2, 3),
    "forearms" to MuscleRole("forearms", 3, 0),
    "grip" to MuscleRole("forearms", 3, 0),
    "calves" to MuscleRole("calves", 3, 1),
    "soleus" to MuscleRole("soleus", 3, 2),
    "ankles" to MuscleRole("calves", 3, 1),
    "abs" to MuscleRole("abs", 3, 3),
    "core" to MuscleRole("abs", 3, 3),
    "obliques" to MuscleRole("obliques", 3, 4),
    "hip flexors" to MuscleRole("hip flexors", 3, 5),
    "hip adductors" to MuscleRole("adductors", 3, 6),
    "inner thighs" to MuscleRole("adductors", 3, 6),
    "hip abductors" to MuscleRole("glute medius", 3, 7),
).toMap()

private fun roleOf(muscle: String): MuscleRole =
    muscleRoles[muscle.trim().lowercase()] ?: MuscleRole(muscle.trim().lowercase().ifBlank { "other" }, 4, 0)

private fun roleByKey(key: String): MuscleRole =
    muscleRoles.values.firstOrNull { it.key == key } ?: MuscleRole(key, 4, 0)

fun largestMuscle(muscles: List<String>): String {
    if (muscles.isEmpty()) return "other"
    return muscles.map { roleOf(it) }.minWith(compareBy({ it.band }, { it.rank })).key
}

fun lineUse(sessionKind: String, usage: String): String = when (sessionKind) {
    "BODYWEIGHT" -> "BODYWEIGHT"
    else -> if (sessionKind == "HEAVY" || usage != "BODYWEIGHT") "HEAVY" else "BODYWEIGHT"
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
    return fixSpine(placeDependents(sequenced), sessionKind).let { placeDependents(it) }
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
    val push = items.filter { sideOf(it) == Side.PUSH }
    val pull = items.filter { sideOf(it) == Side.PULL }
    if (push.isEmpty() || pull.isEmpty()) return items
    val others = items.filter { sideOf(it) == Side.OTHER }
    val mixed = ArrayList<DrawCard>()
    var i = 0
    var j = 0
    var wantPush = true
    while (i < push.size || j < pull.size) {
        if (wantPush && i < push.size) mixed += push[i++]
        else if (!wantPush && j < pull.size) mixed += pull[j++]
        else if (i < push.size) mixed += push[i++]
        else mixed += pull[j++]
        wantPush = !wantPush
    }
    if (others.isEmpty()) return mixed
    return mixed + others
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
    val moving = items.filter { predicate(it) && !anchor(it) }
    if (moving.isEmpty()) return
    items.removeAll(moving.toSet())
    val at = items.indexOfLast(anchor)
    if (at < 0) {
        items.addAll(0, moving)
        return
    }
    items.addAll(at + 1, moving)
}

private fun fixSpine(items: List<DrawCard>, sessionKind: String): List<DrawCard> {
    val spine = items.filter { isHeavySpine(it, sessionKind) }
    val load = items.filter { isHeavySquatOrHinge(it, sessionKind) }
    if (spine.isEmpty() || load.isEmpty()) return items
    val special = (spine + load).map { it.key }.toSet()
    val others = items.filter { it.key !in special }
    if (others.isEmpty()) return spine + items.filter { it.key !in spine.map { spineCard -> spineCard.key }.toSet() }
    val result = others.toMutableList()
    val earliest = items.indexOfFirst { it.key in special }.coerceIn(0, result.size)
    result.addAll(earliest, spine)
    if (isHeavySpine(result.last(), sessionKind)) {
        val separator = result.indexOfFirst { !isHeavySpine(it, sessionKind) }
        if (separator in 0 until result.lastIndex) {
            result.add(result.removeAt(separator))
        }
    }
    result.addAll(load)
    return separateSpineLoads(result, sessionKind)
}

private fun separateSpineLoads(items: List<DrawCard>, sessionKind: String): List<DrawCard> {
    val result = items.toMutableList()
    repeat(result.size) {
        for (index in 0 until result.lastIndex) {
            if (!spineConflict(result[index], result[index + 1], sessionKind)) continue
            val donor = (index + 2 until result.size).firstOrNull { !spineConflict(result[index], result[it], sessionKind) && !isHeavySpine(result[it], sessionKind) && !isHeavySquatOrHinge(result[it], sessionKind) }
            if (donor != null) {
                result.add(index + 1, result.removeAt(donor))
            } else if (isHeavySquatOrHinge(result[index], sessionKind) && isHeavySpine(result[index + 1], sessionKind)) {
                val card = result.removeAt(index + 1)
                result.add(index, card)
            }
        }
    }
    return result
}

private fun spineConflict(left: DrawCard, right: DrawCard, sessionKind: String): Boolean {
    val spineLeft = isHeavySpine(left, sessionKind)
    val spineRight = isHeavySpine(right, sessionKind)
    val loadLeft = isHeavySquatOrHinge(left, sessionKind)
    val loadRight = isHeavySquatOrHinge(right, sessionKind)
    return (spineLeft && loadRight) || (loadLeft && spineRight)
}

private fun isHeavySpine(card: DrawCard, sessionKind: String): Boolean =
    lineUse(sessionKind, card.usage) == "HEAVY" &&
        (largestMuscle(card.muscles) == "spinal" || card.muscles.any { it.equals("Lower Back", true) || it.equals("Spinal Erectors", true) })

private fun isHeavySquatOrHinge(card: DrawCard, sessionKind: String): Boolean =
    lineUse(sessionKind, card.usage) == "HEAVY" && (isSquat(card.name) || isHinge(card))

private fun isHinge(card: DrawCard): Boolean {
    val name = card.name.lowercase()
    return name.contains("romanian") || name.contains("good morning") || name.contains("rdl") ||
        (name.contains("deadlift") && card.muscles.any { it.equals("Hamstrings", true) })
}

private fun isCurl(card: DrawCard) = card.name.contains("curl", true)
private fun isRowOrPullUp(name: String): Boolean {
    val text = name.lowercase()
    return text.contains("row") || text.contains("pull-up") || text.contains("pull up") ||
        text.contains("pulldown") || text.contains("pull-down") || text.contains("chin-up") || text.contains("chin up")
}
private fun isArmExtension(card: DrawCard): Boolean {
    val name = card.name.lowercase()
    if (name.contains("leg extension") || name.contains("back extension")) return false
    return name.contains("pushdown") || name.contains("push-down") || name.contains("triceps extension") || name.contains("skull")
}
private fun isPress(name: String): Boolean {
    val text = name.lowercase()
    return text.contains("press") || text.contains("push-up") || text.contains("push up") || text.contains("bench")
}
private fun isLegExtension(card: DrawCard) = card.name.contains("leg extension", true)
private fun isSquat(name: String) = name.contains("squat", true)
private fun isLateralRaise(card: DrawCard) = card.name.contains("lateral raise", true)
private fun isOverheadPress(name: String): Boolean {
    val text = name.lowercase()
    return text.contains("overhead press") || text.contains("shoulder press") || text.contains("military press") || text.contains("ohp")
}
