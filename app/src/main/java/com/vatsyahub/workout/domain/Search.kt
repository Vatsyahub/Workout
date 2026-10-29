package com.vatsyahub.workout.domain

data class SearchExercise(
    val id: Long,
    val name: String,
    val muscles: String,
    val types: List<String>,
)

data class SearchFilters(
    val name: String = "",
    val bodyPart: String = "",
    val workoutType: String = "",
)

/**
 * Name, body part, and workout type apply together.
 * A blank name keeps every exercise that passes the other filters.
 * A name ranks a prefix match ahead of a contains match.
 */
fun filterExercises(exercises: List<SearchExercise>, filters: SearchFilters): List<SearchExercise> {
    val needle = filters.name.trim().lowercase()
    val part = filters.bodyPart.trim()
    val type = filters.workoutType.trim()
    return exercises.mapNotNull { exercise ->
        if (part.isNotEmpty() && !matchesBodyPart(exercise.muscles, part)) return@mapNotNull null
        if (type.isNotEmpty() && exercise.types.none { it.equals(type, ignoreCase = true) }) return@mapNotNull null
        val name = exercise.name.lowercase()
        val rank = when {
            needle.isEmpty() -> 0
            name.startsWith(needle) -> 0
            name.contains(needle) -> 1
            else -> return@mapNotNull null
        }
        rank to exercise
    }.sortedWith(compareBy({ it.first }, { it.second.name.lowercase() }, { it.second.id }))
        .map { it.second }
}
