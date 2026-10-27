package com.vatsyahub.workout.domain

import kotlin.math.round

data class SetLoad(val reps: Int, val weightKg: Double? = null)

fun formatSets(sets: List<SetLoad>, showWeight: Boolean): String {
    if (sets.isEmpty()) return ""
    val groups = mutableListOf<SetGroup>()
    for (set in sets) {
        val last = groups.lastOrNull()
        if (last != null && last.reps == set.reps && sameWeight(last.weightKg, set.weightKg)) {
            groups[groups.lastIndex] = last.copy(count = last.count + 1)
        } else {
            groups += SetGroup(1, set.reps, set.weightKg)
        }
    }
    return groups.joinToString(", ") { group ->
        val phrase = "${group.count} × ${group.reps}"
        if (showWeight && group.weightKg != null) "$phrase @ ${formatKg(group.weightKg)}" else phrase
    }
}

private data class SetGroup(val count: Int, val reps: Int, val weightKg: Double?)

private fun sameWeight(left: Double?, right: Double?): Boolean = weightCents(left) == weightCents(right)

private fun weightCents(kg: Double?): Long? = kg?.let { round(it * 100.0).toLong() }
