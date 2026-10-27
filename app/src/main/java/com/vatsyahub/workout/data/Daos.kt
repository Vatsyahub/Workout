package com.vatsyahub.workout.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM profile WHERE id = 1")
    suspend fun profile(): Profile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: Profile)

    @Query("SELECT * FROM exercises ORDER BY name COLLATE NOCASE")
    suspend fun exercises(): List<Exercise>

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun exerciseCount(): Int

    @Insert
    suspend fun insertExercise(exercise: Exercise): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercises(rows: List<Exercise>)

    @Query("UPDATE exercises SET name = :name, bodyPart = :bodyPart, usage = :usage WHERE id = :id")
    suspend fun updateExercise(id: Long, name: String, bodyPart: String, usage: String)

    @Query("DELETE FROM exercises WHERE id = :id")
    suspend fun deleteExercise(id: Long)

    @Query("SELECT * FROM workout_types ORDER BY name COLLATE NOCASE")
    suspend fun types(): List<WorkoutType>

    @Insert
    suspend fun insertType(type: WorkoutType): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTypes(rows: List<WorkoutType>)

    @Query("SELECT * FROM exercise_types")
    suspend fun links(): List<ExerciseType>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLink(link: ExerciseType)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLinks(rows: List<ExerciseType>)

    @Query("DELETE FROM exercise_types WHERE exerciseId = :exerciseId")
    suspend fun deleteLinks(exerciseId: Long)

    @Query("DELETE FROM exercise_types WHERE exerciseId = :exerciseId AND typeId = :typeId")
    suspend fun deleteLink(exerciseId: Long, typeId: Long)

    @Query("SELECT * FROM activity_kinds ORDER BY name COLLATE NOCASE")
    suspend fun kinds(): List<ActivityKind>

    @Insert
    suspend fun insertKind(kind: ActivityKind): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKinds(rows: List<ActivityKind>)

    @Query("SELECT * FROM activity_fields ORDER BY position")
    suspend fun fields(): List<ActivityField>

    @Insert
    suspend fun insertField(field: ActivityField): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFields(rows: List<ActivityField>)

    @Query("SELECT * FROM patterns")
    suspend fun patterns(): List<HiitPattern>

    @Query("SELECT * FROM patterns WHERE fingerprint = :fingerprint LIMIT 1")
    suspend fun patternByFingerprint(fingerprint: String): HiitPattern?

    @Insert
    suspend fun insertPattern(pattern: HiitPattern): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPatterns(rows: List<HiitPattern>)

    @Query("SELECT * FROM passes ORDER BY position")
    suspend fun passes(): List<HiitPass>

    @Insert
    suspend fun insertPass(pass: HiitPass): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPasses(rows: List<HiitPass>)

    @Query("SELECT * FROM workouts")
    suspend fun workouts(): List<Workout>

    @Insert
    suspend fun insertWorkout(workout: Workout): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkouts(rows: List<Workout>)

    @Query("UPDATE workouts SET performedAt = :performedAt WHERE id = :id")
    suspend fun updatePerformedAt(id: Long, performedAt: Long)

    @Query("UPDATE workouts SET durationSec = :seconds WHERE id = :id")
    suspend fun updateDuration(id: Long, seconds: Int?)

    @Query("UPDATE workouts SET workSec = :work, restSec = :rest, breakSec = :breakSec WHERE id = :id")
    suspend fun updateHiitTimes(id: Long, work: Int?, rest: Int?, breakSec: Int?)

    @Query(
        """
        UPDATE workouts SET distanceM = :distance, gradient = :gradient, location = :location,
        bottomM = :bottom, topM = :top, bothWays = :bothWays, durationSec = :duration WHERE id = :id
        """,
    )
    suspend fun updateOuting(
        id: Long,
        distance: Double?,
        gradient: Double?,
        location: String?,
        bottom: Double?,
        top: Double?,
        bothWays: Boolean?,
        duration: Int?,
    )

    @Query("UPDATE workouts SET finished = :finished WHERE id = :id")
    suspend fun updateFinished(id: Long, finished: Boolean)

    @Query("UPDATE workouts SET favourite = :favourite WHERE id = :id")
    suspend fun updateFavourite(id: Long, favourite: Boolean)

    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun deleteWorkout(id: Long)

    @Query("SELECT * FROM blocks")
    suspend fun blocks(): List<Block>

    @Insert
    suspend fun insertBlock(block: Block): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlocks(rows: List<Block>)

    @Query("DELETE FROM blocks WHERE id = :id")
    suspend fun deleteBlock(id: Long)

    @Query("DELETE FROM blocks WHERE workoutId = :workoutId")
    suspend fun deleteBlocks(workoutId: Long)

    @Query("UPDATE blocks SET nameSnapshot = COALESCE(nameSnapshot, :name) WHERE exerciseId = :exerciseId")
    suspend fun snapshotBlockNames(exerciseId: Long, name: String)

    @Query("UPDATE blocks SET nameSnapshot = COALESCE(nameSnapshot, :name), exerciseId = NULL WHERE exerciseId = :exerciseId")
    suspend fun detachBlocks(exerciseId: Long, name: String)

    @Query("SELECT * FROM set_rows")
    suspend fun sets(): List<SetRow>

    @Insert
    suspend fun insertSet(row: SetRow): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSets(rows: List<SetRow>)

    @Query("UPDATE set_rows SET reps = :reps, weightKg = :weightKg WHERE id = :id")
    suspend fun updateSet(id: Long, reps: Int, weightKg: Double?)

    @Query("DELETE FROM set_rows WHERE id = :id")
    suspend fun deleteSet(id: Long)

    @Query("DELETE FROM set_rows WHERE blockId = :blockId")
    suspend fun deleteSetsForBlock(blockId: Long)

    @Query("DELETE FROM set_rows WHERE blockId IN (SELECT id FROM blocks WHERE workoutId = :workoutId)")
    suspend fun deleteSetsForWorkout(workoutId: Long)

    @Query("SELECT * FROM hiit_sets")
    suspend fun hiitSets(): List<HiitSetRow>

    @Insert
    suspend fun insertHiitSet(row: HiitSetRow): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHiitSets(rows: List<HiitSetRow>)

    @Query("DELETE FROM hiit_sets WHERE workoutId = :workoutId")
    suspend fun deleteHiitSets(workoutId: Long)

    @Query("SELECT * FROM splits")
    suspend fun splits(): List<Split>

    @Insert
    suspend fun insertSplit(split: Split): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSplits(rows: List<Split>)

    @Query("UPDATE splits SET seconds = :seconds WHERE id = :id")
    suspend fun updateSplit(id: Long, seconds: Int)

    @Query("DELETE FROM splits WHERE id = :id")
    suspend fun deleteSplit(id: Long)

    @Query("DELETE FROM splits WHERE workoutId = :workoutId AND position = :position")
    suspend fun deleteSplitAt(workoutId: Long, position: Int)

    @Query("DELETE FROM splits WHERE workoutId = :workoutId")
    suspend fun deleteSplits(workoutId: Long)

    @Query("UPDATE splits SET nameSnapshot = COALESCE(nameSnapshot, :name) WHERE exerciseId = :exerciseId")
    suspend fun snapshotSplitNames(exerciseId: Long, name: String)

    @Query("UPDATE splits SET nameSnapshot = COALESCE(nameSnapshot, :name), exerciseId = NULL WHERE exerciseId = :exerciseId")
    suspend fun detachSplits(exerciseId: Long, name: String)

    @Query("SELECT * FROM field_values")
    suspend fun values(): List<FieldValue>

    @Insert
    suspend fun insertValue(value: FieldValue): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertValues(rows: List<FieldValue>)

    @Query("DELETE FROM field_values WHERE workoutId = :workoutId AND fieldId = :fieldId")
    suspend fun deleteValue(workoutId: Long, fieldId: Long)

    @Query("DELETE FROM field_values WHERE workoutId = :workoutId")
    suspend fun deleteValues(workoutId: Long)

    @Query("SELECT * FROM active_session WHERE id = 1")
    suspend fun active(): ActiveSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActive(session: ActiveSession)

    @Query("DELETE FROM active_session")
    suspend fun clearActive()

    @Query("SELECT * FROM plans ORDER BY savedAt DESC, id DESC")
    suspend fun plans(): List<SavedPlan>

    @Insert
    suspend fun insertPlan(plan: SavedPlan): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlans(rows: List<SavedPlan>)

    @Query("DELETE FROM plans")
    suspend fun clearPlans()

    @Query("DELETE FROM splits")
    suspend fun clearSplits()

    @Query("DELETE FROM set_rows")
    suspend fun clearSets()

    @Query("DELETE FROM hiit_sets")
    suspend fun clearHiitSets()

    @Query("DELETE FROM blocks")
    suspend fun clearBlocks()

    @Query("DELETE FROM field_values")
    suspend fun clearValues()

    @Query("DELETE FROM workouts")
    suspend fun clearWorkouts()

    @Query("DELETE FROM passes")
    suspend fun clearPasses()

    @Query("DELETE FROM patterns")
    suspend fun clearPatterns()

    @Query("DELETE FROM activity_fields")
    suspend fun clearFields()

    @Query("DELETE FROM activity_kinds")
    suspend fun clearKinds()

    @Query("DELETE FROM exercise_types")
    suspend fun clearLinks()

    @Query("DELETE FROM workout_types")
    suspend fun clearTypes()

    @Query("DELETE FROM exercises")
    suspend fun clearExercises()
}
