package com.vatsyahub.workout.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "profile")
data class Profile(
    @PrimaryKey val id: Int = 1,
    val name: String = "",
    val age: Int? = null,
    val weightKg: Double? = null,
    val heightCm: Double? = null,
    val avatar: String? = null,
)

@Serializable
@Entity(tableName = "exercises")
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val bodyPart: String,
    val usage: String,
)

@Serializable
@Entity(tableName = "workout_types")
data class WorkoutType(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Serializable
@Entity(tableName = "exercise_types", primaryKeys = ["exerciseId", "typeId"])
data class ExerciseType(
    val exerciseId: Long,
    val typeId: Long,
)

@Serializable
@Entity(tableName = "activity_kinds")
data class ActivityKind(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val shape: String,
)

@Serializable
@Entity(tableName = "activity_fields", indices = [Index("kindId")])
data class ActivityField(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kindId: Long,
    val position: Int,
    val name: String,
)

@Serializable
@Entity(tableName = "patterns", indices = [Index(value = ["fingerprint"], unique = true)])
data class HiitPattern(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fingerprint: String,
    val label: String,
)

@Serializable
@Entity(tableName = "passes", indices = [Index("patternId")])
data class HiitPass(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patternId: Long,
    val position: Int,
    val kind: String,
    val times: Int,
)

@Serializable
@Entity(tableName = "workouts", indices = [Index("performedAt")])
data class Workout(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val activityKindId: Long? = null,
    val performedAt: Long,
    val durationSec: Int? = null,
    val workSec: Int? = null,
    val restSec: Int? = null,
    val breakSec: Int? = null,
    val finished: Boolean = true,
    val distanceM: Double? = null,
    val gradient: Double? = null,
    val location: String? = null,
    val bottomM: Double? = null,
    val topM: Double? = null,
    val bothWays: Boolean? = null,
)

@Serializable
@Entity(tableName = "blocks", indices = [Index("workoutId"), Index("exerciseId")])
data class Block(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val position: Int,
    val setIndex: Int,
    val exerciseId: Long? = null,
    val nameSnapshot: String? = null,
)

@Serializable
@Entity(tableName = "set_rows", indices = [Index("blockId")])
data class SetRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val blockId: Long,
    val position: Int,
    val reps: Int,
    val weightKg: Double? = null,
)

@Serializable
@Entity(tableName = "hiit_sets", indices = [Index("workoutId")])
data class HiitSetRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val position: Int,
    val patternId: Long,
)

@Serializable
@Entity(tableName = "splits", indices = [Index("workoutId")])
data class Split(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val position: Int,
    val setIndex: Int,
    val exerciseId: Long? = null,
    val nameSnapshot: String? = null,
    val round: Int? = null,
    val phase: String,
    val seconds: Int,
)

@Serializable
@Entity(tableName = "field_values", indices = [Index("workoutId")])
data class FieldValue(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val fieldId: Long,
    val value: String,
)

@Serializable
@Entity(tableName = "active_session")
data class ActiveSession(
    @PrimaryKey val id: Int = 1,
    val mode: String,
    val workoutId: Long? = null,
    val kindId: Long? = null,
    val index: Int = 0,
    val running: Boolean = true,
    val remainingMs: Long = 0,
    val resumedAt: Long = 0,
    val accumulatedMs: Long = 0,
)

@Serializable
data class CarryFile(
    val version: Int = 1,
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
)
