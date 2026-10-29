package com.vatsyahub.workout.ui

import com.vatsyahub.workout.data.Exercise
import com.vatsyahub.workout.data.Snapshot
import com.vatsyahub.workout.data.typesOf
import com.vatsyahub.workout.domain.DrawCard
import com.vatsyahub.workout.domain.SetLoad
import com.vatsyahub.workout.domain.formatSets
import com.vatsyahub.workout.domain.lineUse
import com.vatsyahub.workout.domain.musclesOf
import com.vatsyahub.workout.domain.repsFor
import com.vatsyahub.workout.domain.showsWeight

internal fun previewKind(usage: String, pageKind: String): String = when (usage) {
    "BODYWEIGHT" -> "BODYWEIGHT"
    "HEAVY" -> "HEAVY"
    else -> if (pageKind == "BODYWEIGHT") "BODYWEIGHT" else "HEAVY"
}

/** Saved sets from a finished log when he has them, otherwise the prescribed sets. */
internal fun previewSetLine(snap: Snapshot, exercise: Exercise, kind: String): String {
    val showWeight = showsWeight(kind, exercise.usage)
    val saved = lastSavedSets(snap, exercise.id, kind)
    if (saved != null) return formatSets(saved, showWeight)
    val card = DrawCard(
        id = exercise.id,
        name = exercise.name,
        muscles = musclesOf(exercise.bodyPart),
        types = snap.typesOf(exercise.id).map { it.name }.toSet(),
        usage = exercise.usage,
    )
    return formatSets(repsFor(card, listOf(card), kind, null).map { SetLoad(it) }, false)
}

private fun lastSavedSets(snap: Snapshot, exerciseId: Long, kind: String): List<SetLoad>? {
    val usage = snap.exercises.find { it.id == exerciseId }?.usage
    val asHeavy = lineUse(kind, usage ?: "HEAVY") == "HEAVY"
    val kinds = if (asHeavy) setOf("HEAVY", "MIXED") else setOf("BODYWEIGHT", "MIXED")
    val sessions = snap.workouts.filter { it.finished && it.kind in kinds }.sortedByDescending { it.performedAt }
    for (workout in sessions) {
        val block = snap.blocks.filter { it.workoutId == workout.id && it.exerciseId == exerciseId }
            .maxByOrNull { it.position } ?: continue
        val sets = snap.sets.filter { it.blockId == block.id }.sortedBy { it.position }
        if (sets.isEmpty()) continue
        val usedHeavy = when (workout.kind) {
            "HEAVY" -> true
            "BODYWEIGHT" -> false
            else -> when (usage) {
                "HEAVY" -> true
                "BODYWEIGHT" -> false
                else -> sets.any { it.weightKg != null }
            }
        }
        if (usedHeavy != asHeavy) continue
        return sets.map { SetLoad(it.reps, it.weightKg) }
    }
    return null
}
