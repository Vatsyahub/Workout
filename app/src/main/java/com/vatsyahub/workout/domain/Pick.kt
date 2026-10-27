package com.vatsyahub.workout.domain

data class PickCandidate(val id: Long, val name: String, val usage: String)

fun matchExercises(exercises: List<PickCandidate>, query: String, limit: Int = 5): List<PickCandidate> {
    val needle = query.trim().lowercase()
    if (needle.isEmpty() || limit <= 0) return emptyList()
    return exercises.mapNotNull { exercise ->
        val name = exercise.name.lowercase()
        val rank = when {
            name.startsWith(needle) -> 0
            name.contains(needle) -> 1
            else -> return@mapNotNull null
        }
        rank to exercise
    }.sortedWith(compareBy({ it.first }, { it.second.name.lowercase() }, { it.second.id }))
        .take(limit)
        .map { it.second }
}

fun classifyPickedKind(usages: List<String>, startedAs: String): String {
    val hasHeavy = usages.any { it == "HEAVY" }
    val hasBody = usages.any { it == "BODYWEIGHT" }
    return when {
        hasHeavy && hasBody -> "MIXED"
        hasHeavy -> "HEAVY"
        hasBody -> "BODYWEIGHT"
        startedAs == "BODYWEIGHT" -> "BODYWEIGHT"
        startedAs == "MIXED" -> "MIXED"
        else -> "HEAVY"
    }
}

fun strengthLabel(kind: String): String = when (kind) {
    "HEAVY" -> "Heavy"
    "MIXED" -> "Mixed"
    else -> "Bodyweight"
}

fun showsWeight(kind: String, usage: String?): Boolean = when (kind) {
    "HEAVY" -> true
    "BODYWEIGHT" -> false
    else -> usage != "BODYWEIGHT"
}
