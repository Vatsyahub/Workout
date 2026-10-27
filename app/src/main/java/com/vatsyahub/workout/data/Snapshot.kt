package com.vatsyahub.workout.data

import com.vatsyahub.workout.domain.Mark
import com.vatsyahub.workout.domain.PHASE_WORK
import com.vatsyahub.workout.domain.Pass
import com.vatsyahub.workout.domain.PoolExercise
import com.vatsyahub.workout.domain.WeekWorkout
import com.vatsyahub.workout.domain.climbMetres
import com.vatsyahub.workout.domain.dayText
import com.vatsyahub.workout.domain.formatKm
import com.vatsyahub.workout.domain.kmPaceSeconds
import com.vatsyahub.workout.domain.strengthLabel
import com.vatsyahub.workout.domain.swimPaceSeconds
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

data class Snapshot(
    val profile: Profile = Profile(),
    val exercises: List<Exercise> = emptyList(),
    val types: List<WorkoutType> = emptyList(),
    val links: List<ExerciseType> = emptyList(),
    val kinds: List<ActivityKind> = emptyList(),
    val fields: List<ActivityField> = emptyList(),
    val patterns: List<HiitPattern> = emptyList(),
    val passes: List<HiitPass> = emptyList(),
    val workouts: List<Workout> = emptyList(),
    val blocks: List<Block> = emptyList(),
    val sets: List<SetRow> = emptyList(),
    val hiitSets: List<HiitSetRow> = emptyList(),
    val splits: List<Split> = emptyList(),
    val values: List<FieldValue> = emptyList(),
    val active: ActiveSession? = null,
)

fun Snapshot.exercise(id: Long?): Exercise? = exercises.find { it.id == id }

fun Snapshot.displayName(exerciseId: Long?, snapshot: String?): String {
    if (!snapshot.isNullOrBlank()) return snapshot
    return exercise(exerciseId)?.name ?: "Exercise"
}

fun Snapshot.blockName(block: Block): String = displayName(block.exerciseId, block.nameSnapshot)

fun Snapshot.typesOf(exerciseId: Long): List<WorkoutType> {
    val ids = links.filter { it.exerciseId == exerciseId }.map { it.typeId }.toSet()
    return types.filter { it.id in ids }.sortedBy { it.name.lowercase() }
}

fun Snapshot.passesOf(patternId: Long): List<Pass> =
    passes.filter { it.patternId == patternId }
        .sortedBy { it.position }
        .map { Pass(it.kind, it.times) }

fun Snapshot.strengthLibrary(): List<Exercise> = exercises.filter { exercise ->
    exercise.usage == "HEAVY" || exercise.usage == "BODYWEIGHT" || exercise.usage == "BOTH"
}

fun Snapshot.pool(kind: String): List<Exercise> = when (kind) {
    "HEAVY" -> exercises.filter { it.usage == "HEAVY" || it.usage == "BOTH" }
    "BODYWEIGHT", "HIIT" -> exercises.filter { it.usage == "BODYWEIGHT" || it.usage == "BOTH" }
    "MIXED" -> strengthLibrary()
    else -> emptyList()
}

fun Snapshot.poolExercises(kind: String): List<PoolExercise> = pool(kind).map { exercise ->
    PoolExercise(exercise.id, typesOf(exercise.id).map { it.name }.toSet())
}

fun localDay(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

fun Snapshot.marks(zone: ZoneId = ZoneId.systemDefault()): List<Mark> {
    val out = ArrayList<Mark>()
    for (workout in workouts) {
        if (!workout.finished) continue
        val day = localDay(workout.performedAt, zone)
        when (workout.kind) {
            "HEAVY", "BODYWEIGHT", "MIXED" -> out += strengthMarks(workout, day)
            "HIIT" -> out += hiitMarks(workout, day)
            "OUTING" -> outingMark(workout, day)?.let { out += it }
        }
    }
    return out
}

private fun Snapshot.strengthMarks(workout: Workout, day: LocalDate): List<Mark> {
    return blocks.filter { it.workoutId == workout.id }
        .sortedBy { it.position }
        .map { block ->
            val rows = sets.filter { it.blockId == block.id }
            val top = rows.mapNotNull { it.weightKg }.maxOrNull()
            val repsAtTop = if (top == null) {
                rows.sumOf { it.reps }
            } else {
                rows.filter { it.weightKg != null && abs(it.weightKg - top) < 0.001 }.sumOf { it.reps }
            }
            val name = blockName(block)
            Mark(
                workoutId = workout.id,
                day = day,
                kind = workout.kind,
                exerciseKey = block.exerciseId?.let { "ex:$it" } ?: "name:${name.lowercase()}",
                exerciseName = name,
                topWeight = top,
                totalReps = rows.sumOf { it.reps },
                repsAtTop = repsAtTop,
            )
        }
}

private fun Snapshot.hiitMarks(workout: Workout, day: LocalDate): List<Mark> {
    val work = splits.filter { it.workoutId == workout.id && it.phase == PHASE_WORK }
    val grouped = blocks.filter { it.workoutId == workout.id }.groupBy { block ->
        block.exerciseId?.let { "ex:$it" } ?: "name:${blockName(block).lowercase()}"
    }
    return grouped.map { (key, group) ->
        val name = blockName(group.first())
        val ids = group.mapNotNull { it.exerciseId }.toSet()
        val related = work.filter { split ->
            (split.exerciseId != null && split.exerciseId in ids) ||
                (split.exerciseId == null && split.nameSnapshot == name)
        }
        Mark(
            workoutId = workout.id,
            day = day,
            kind = "HIIT",
            exerciseKey = key,
            exerciseName = name,
            workSec = related.maxOfOrNull { it.seconds } ?: workout.workSec,
        )
    }
}

private fun Snapshot.outingMark(workout: Workout, day: LocalDate): Mark? {
    val kind = kinds.find { it.id == workout.activityKindId } ?: return null
    val distance = workout.distanceM
    val duration = workout.durationSec
    val pace = if (distance == null || duration == null) {
        null
    } else if (kind.shape == "SWIM") {
        swimPaceSeconds(distance, duration)
    } else {
        kmPaceSeconds(distance, duration)
    }
    val climb = if (workout.bottomM != null && workout.topM != null) {
        climbMetres(workout.bottomM, workout.topM)
    } else {
        null
    }
    return Mark(
        workoutId = workout.id,
        day = day,
        kind = "OUTING",
        exerciseKey = "kind:${kind.id}",
        exerciseName = kind.name,
        distanceM = distance,
        durationSec = duration,
        gradient = workout.gradient,
        climbM = climb,
        paceSec = pace,
        outingName = kind.name,
        outingShape = kind.shape,
    )
}

fun Snapshot.weekWorkouts(zone: ZoneId = ZoneId.systemDefault()): List<WeekWorkout> =
    workouts.filter { it.finished }.map { WeekWorkout(it.id, localDay(it.performedAt, zone)) }

fun Snapshot.summary(workout: Workout, zone: ZoneId = ZoneId.systemDefault()): String {
    val day = dayText(localDay(workout.performedAt, zone))
    return when (workout.kind) {
        "HIIT" -> {
            val bouts = splits.count { it.workoutId == workout.id && it.phase == PHASE_WORK }
            "$day · HIIT · $bouts bouts · ${workout.workSec ?: 0}-${workout.restSec ?: 0}"
        }
        "OUTING" -> {
            val name = kinds.find { it.id == workout.activityKindId }?.name ?: "Outing"
            val distance = workout.distanceM?.let { " · ${formatKm(it)}" }.orEmpty()
            "$day · $name$distance"
        }
        else -> {
            val count = blocks.count { it.workoutId == workout.id }
            val ids = blocks.filter { it.workoutId == workout.id }.map { it.id }.toSet()
            val reps = sets.filter { it.blockId in ids }.sumOf { it.reps }
            val label = strengthLabel(workout.kind)
            "$day · $label · $count exercises · $reps reps"
        }
    }
}
