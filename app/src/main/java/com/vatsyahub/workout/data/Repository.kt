package com.vatsyahub.workout.data

import androidx.room.withTransaction
import com.vatsyahub.workout.domain.Pass
import com.vatsyahub.workout.domain.fingerprint
import com.vatsyahub.workout.domain.patternLabel
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Repository(private val db: WorkoutDatabase) {
    private val dao = db.dao()
    private val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = true }
    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun data(): Flow<Snapshot> = callbackFlow {
        val observer = object : androidx.room.InvalidationTracker.Observer(
            "profile",
            "exercises",
            "workout_types",
            "exercise_types",
            "activity_kinds",
            "activity_fields",
            "patterns",
            "passes",
            "workouts",
            "blocks",
            "set_rows",
            "hiit_sets",
            "splits",
            "field_values",
            "active_session",
            "plans",
        ) {
            override fun onInvalidated(tables: Set<String>) {
                launch(Dispatchers.IO) { send(load()) }
            }
        }
        db.invalidationTracker.addObserver(observer)
        launch(Dispatchers.IO) { send(load()) }
        awaitClose { db.invalidationTracker.removeObserver(observer) }
    }.conflate()

    private suspend fun load(): Snapshot = Snapshot(
        profile = dao.profile() ?: Profile(),
        exercises = dao.exercises(),
        types = dao.types(),
        links = dao.links(),
        kinds = dao.kinds(),
        fields = dao.fields(),
        patterns = dao.patterns(),
        passes = dao.passes(),
        workouts = dao.workouts(),
        blocks = dao.blocks(),
        sets = dao.sets(),
        hiitSets = dao.hiitSets(),
        splits = dao.splits(),
        values = dao.values(),
        active = dao.active(),
        plans = dao.plans(),
    )

    suspend fun saveProfile(profile: Profile) {
        dao.insertProfile(profile.copy(id = 1))
    }

    suspend fun editProfile(
        name: String? = null,
        age: Int? = null,
        setAge: Boolean = false,
        weightKg: Double? = null,
        setWeight: Boolean = false,
        heightCm: Double? = null,
        setHeight: Boolean = false,
        avatar: String? = null,
        setAvatar: Boolean = false,
    ) {
        val current = dao.profile() ?: Profile()
        dao.insertProfile(
            current.copy(
                name = name ?: current.name,
                age = if (setAge) age else current.age,
                weightKg = if (setWeight) weightKg else current.weightKg,
                heightCm = if (setHeight) heightCm else current.heightCm,
                avatar = if (setAvatar) avatar else current.avatar,
            ),
        )
    }

    suspend fun seedIfEmpty() {
        if (dao.exerciseCount() > 0) return
        db.withTransaction {
            if (dao.exerciseCount() > 0) return@withTransaction
            for (row in StarterLibrary.rows) {
                val id = dao.insertExercise(
                    Exercise(name = row.name, bodyPart = row.muscles, usage = row.usage),
                )
                for (typeName in row.types.split(',').map { it.trim() }.filter { it.isNotEmpty() }) {
                    linkType(id, typeName)
                }
            }
        }
    }


    suspend fun addExercise(name: String, bodyPart: String, usage: String, typeNames: List<String>): Long =
        db.withTransaction {
            val id = dao.insertExercise(Exercise(name = name.trim(), bodyPart = bodyPart.trim(), usage = usage))
            for (typeName in typeNames.map { it.trim() }.filter { it.isNotEmpty() }.distinct()) {
                linkType(id, typeName)
            }
            id
        }

    suspend fun updateExercise(id: Long, name: String, bodyPart: String, usage: String) {
        db.withTransaction {
            val current = dao.exercises().find { it.id == id } ?: return@withTransaction
            val next = name.trim()
            if (next != current.name) {
                dao.snapshotBlockNames(id, current.name)
                dao.snapshotSplitNames(id, current.name)
            }
            dao.updateExercise(id, next, bodyPart.trim(), usage)
        }
    }

    suspend fun addType(exerciseId: Long, typeName: String) {
        db.withTransaction { linkType(exerciseId, typeName.trim()) }
    }

    suspend fun removeType(exerciseId: Long, typeId: Long) {
        dao.deleteLink(exerciseId, typeId)
    }

    suspend fun setTypes(exerciseId: Long, typeNames: List<String>) {
        db.withTransaction {
            dao.deleteLinks(exerciseId)
            val seen = LinkedHashSet<String>()
            for (typeName in typeNames) {
                val trimmed = typeName.trim()
                if (trimmed.isEmpty() || !seen.add(trimmed.lowercase())) continue
                linkType(exerciseId, trimmed)
            }
        }
    }

    private suspend fun linkType(exerciseId: Long, typeName: String) {
        if (typeName.isBlank()) return
        val existing = dao.types().find { it.name.equals(typeName, ignoreCase = true) }
        val typeId = existing?.id ?: dao.insertType(WorkoutType(name = typeName))
        dao.insertLink(ExerciseType(exerciseId, typeId))
    }

    suspend fun deleteExercise(id: Long) {
        db.withTransaction {
            val name = dao.exercises().find { it.id == id }?.name ?: return@withTransaction
            dao.detachBlocks(id, name)
            dao.detachSplits(id, name)
            dao.deleteLinks(id)
            dao.deleteExercise(id)
        }
    }

    suspend fun addKind(name: String): Long {
        val trimmed = name.trim()
        val shape = com.vatsyahub.workout.domain.shapeForKindName(trimmed)
        return dao.insertKind(ActivityKind(name = trimmed, shape = shape))
    }

    suspend fun addField(kindId: Long, name: String): Long {
        val position = dao.fields().count { it.kindId == kindId }
        return dao.insertField(ActivityField(kindId = kindId, position = position, name = name.trim()))
    }

    suspend fun ensurePattern(passes: List<Pass>): Long {
        if (passes.isEmpty()) return ensurePattern(com.vatsyahub.workout.domain.DefaultPasses)
        val key = fingerprint(passes)
        dao.patternByFingerprint(key)?.let { return it.id }
        return db.withTransaction {
            dao.patternByFingerprint(key)?.id ?: run {
                val id = dao.insertPattern(HiitPattern(fingerprint = key, label = patternLabel(passes)))
                passes.forEachIndexed { index, pass ->
                    dao.insertPass(HiitPass(patternId = id, position = index, kind = pass.kind, times = pass.times))
                }
                id
            }
        }
    }

    suspend fun saveStrength(
        kind: String,
        day: LocalDate,
        durationSec: Int?,
        lines: List<Pair<Long, List<Pair<Int, Double?>>>>,
    ): Long = db.withTransaction {
        val workoutId = dao.insertWorkout(
            Workout(kind = kind, performedAt = epoch(day), durationSec = durationSec, finished = true),
        )
        lines.forEachIndexed { index, (exerciseId, sets) ->
            val blockId = dao.insertBlock(
                Block(workoutId = workoutId, position = index, setIndex = 0, exerciseId = exerciseId),
            )
            sets.forEachIndexed { setIndex, (reps, weight) ->
                dao.insertSet(SetRow(blockId = blockId, position = setIndex, reps = reps, weightKg = weight))
            }
        }
        workoutId
    }

    suspend fun updateSet(id: Long, reps: Int, weightKg: Double?) {
        dao.updateSet(id, reps, weightKg)
    }

    suspend fun addSet(blockId: Long, reps: Int = 8, weightKg: Double? = null): Long {
        val position = dao.sets().filter { it.blockId == blockId }.maxOfOrNull { it.position + 1 } ?: 0
        return dao.insertSet(SetRow(blockId = blockId, position = position, reps = reps, weightKg = weightKg))
    }

    suspend fun deleteSet(id: Long) {
        dao.deleteSet(id)
    }

    suspend fun addBlock(workoutId: Long, exerciseId: Long, setIndex: Int = 0): Long = db.withTransaction {
        val position = dao.blocks().filter { it.workoutId == workoutId }.maxOfOrNull { it.position + 1 } ?: 0
        val blockId = dao.insertBlock(
            Block(workoutId = workoutId, position = position, setIndex = setIndex, exerciseId = exerciseId),
        )
        val kind = dao.workouts().find { it.id == workoutId }?.kind
        if (kind == "HEAVY" || kind == "BODYWEIGHT" || kind == "MIXED") {
            repeat(3) { index ->
                dao.insertSet(SetRow(blockId = blockId, position = index, reps = 8, weightKg = null))
            }
        }
        blockId
    }

    suspend fun deleteBlock(blockId: Long) {
        db.withTransaction {
            dao.deleteSetsForBlock(blockId)
            dao.deleteBlock(blockId)
        }
    }

    suspend fun updateDuration(id: Long, seconds: Int?) {
        dao.updateDuration(id, seconds)
    }

    suspend fun setFavourite(id: Long, favourite: Boolean) {
        dao.updateFavourite(id, favourite)
    }

    suspend fun updateDay(id: Long, day: LocalDate) {
        val current = dao.workouts().find { it.id == id } ?: return
        val time = java.time.Instant.ofEpochMilli(current.performedAt).atZone(zone).toLocalTime()
        val millis = day.atTime(time).atZone(zone).toInstant().toEpochMilli()
        dao.updatePerformedAt(id, millis)
    }

    suspend fun updateHiitTimes(id: Long, work: Int, rest: Int, breakSec: Int) {
        dao.updateHiitTimes(id, work, rest, breakSec)
    }

    suspend fun updateSplit(id: Long, seconds: Int) {
        dao.updateSplit(id, seconds)
    }

    suspend fun deleteSplit(id: Long) {
        dao.deleteSplit(id)
    }

    suspend fun deleteSplitAt(workoutId: Long, position: Int) {
        dao.deleteSplitAt(workoutId, position)
    }

    suspend fun updateOuting(
        id: Long,
        distance: Double?,
        gradient: Double?,
        location: String?,
        bottom: Double?,
        top: Double?,
        bothWays: Boolean?,
        duration: Int?,
    ) {
        dao.updateOuting(id, distance, gradient, location, bottom, top, bothWays, duration)
    }

    suspend fun setField(workoutId: Long, fieldId: Long, value: String) {
        dao.deleteValue(workoutId, fieldId)
        if (value.isNotBlank()) dao.insertValue(FieldValue(workoutId = workoutId, fieldId = fieldId, value = value))
    }

    suspend fun deleteWorkout(id: Long) {
        db.withTransaction {
            dao.deleteSplits(id)
            dao.deleteSetsForWorkout(id)
            dao.deleteHiitSets(id)
            dao.deleteValues(id)
            dao.deleteBlocks(id)
            dao.deleteWorkout(id)
            val active = dao.active()
            if (active?.workoutId == id) dao.clearActive()
        }
    }

    suspend fun replaceBlockSets(blockId: Long, sets: List<Pair<Int, Double?>>) {
        db.withTransaction {
            dao.deleteSetsForBlock(blockId)
            sets.forEachIndexed { index, (reps, weight) ->
                dao.insertSet(SetRow(blockId = blockId, position = index, reps = reps, weightKg = weight))
            }
        }
    }

    suspend fun startHiit(
        day: LocalDate,
        work: Int,
        rest: Int,
        breakSec: Int,
        sets: List<Pair<List<Pass>, List<Long>>>,
        warmupSec: Int = 120,
        cooldownSec: Int = 300,
    ): Long = db.withTransaction {
        val workoutId = dao.insertWorkout(
            Workout(
                kind = "HIIT",
                performedAt = epoch(day),
                workSec = work,
                restSec = rest,
                breakSec = breakSec,
                warmupSec = warmupSec,
                cooldownSec = cooldownSec,
                finished = false,
            ),
        )
        var position = 0
        sets.forEachIndexed { setIndex, (passes, exercises) ->
            val patternId = ensurePattern(passes)
            dao.insertHiitSet(HiitSetRow(workoutId = workoutId, position = setIndex, patternId = patternId))
            exercises.forEach { exerciseId ->
                dao.insertBlock(
                    Block(
                        workoutId = workoutId,
                        position = position++,
                        setIndex = setIndex,
                        exerciseId = exerciseId,
                    ),
                )
            }
        }
        workoutId
    }

    suspend fun saveManualHiit(
        day: LocalDate,
        work: Int,
        rest: Int,
        breakSec: Int,
        sets: List<ManualHiitSet>,
        warmupSec: Int = 120,
        cooldownSec: Int = 300,
    ): Long = db.withTransaction {
        val workoutId = startHiit(
            day,
            work,
            rest,
            breakSec,
            sets.map { it.passes to it.exerciseIds },
            warmupSec,
            cooldownSec,
        )
        var position = 0
        sets.forEachIndexed { setIndex, set ->
            val sequence = com.vatsyahub.workout.domain.expand(set.exerciseIds.size, set.passes)
            val seen = IntArray(set.exerciseIds.size)
            sequence.forEachIndexed { bout, slot ->
                val exerciseId = set.exerciseIds.getOrNull(slot)
                seen[slot] = seen.getOrElse(slot) { 0 } + 1
                dao.insertSplit(
                    Split(
                        workoutId = workoutId,
                        position = position++,
                        setIndex = setIndex,
                        exerciseId = exerciseId,
                        round = seen[slot],
                        phase = "WORK",
                        seconds = set.splitSeconds.getOrElse(bout) { work },
                    ),
                )
            }
            if (setIndex != sets.lastIndex && sequence.isNotEmpty()) {
                dao.insertSplit(
                    Split(
                        workoutId = workoutId,
                        position = position++,
                        setIndex = setIndex,
                        phase = "BREAK",
                        seconds = set.breakSeconds,
                    ),
                )
            }
        }
        dao.updateFinished(workoutId, true)
        workoutId
    }

    suspend fun appendHiitExercise(workoutId: Long, setIndex: Int, exerciseId: Long) {
        db.withTransaction {
            val position = dao.blocks().filter { it.workoutId == workoutId }.maxOfOrNull { it.position + 1 } ?: 0
            dao.insertBlock(
                Block(workoutId = workoutId, position = position, setIndex = setIndex, exerciseId = exerciseId),
            )
            val work = dao.workouts().find { it.id == workoutId }?.workSec ?: 25
            val splitAt = dao.splits().filter { it.workoutId == workoutId }.maxOfOrNull { it.position + 1 } ?: 0
            dao.insertSplit(
                Split(
                    workoutId = workoutId,
                    position = splitAt,
                    setIndex = setIndex,
                    exerciseId = exerciseId,
                    phase = "WORK",
                    seconds = work,
                ),
            )
        }
    }

    suspend fun recordSplit(workoutId: Long, position: Int, cue: StoredCue) {
        dao.deleteSplitAt(workoutId, position)
        dao.insertSplit(
            Split(
                workoutId = workoutId,
                position = position,
                setIndex = cue.setIndex,
                exerciseId = cue.exerciseId,
                round = cue.round,
                phase = cue.phase,
                seconds = cue.seconds,
            ),
        )
    }

    suspend fun finish(workoutId: Long) {
        dao.updateFinished(workoutId, true)
    }

    suspend fun saveActive(session: ActiveSession) {
        dao.insertActive(session.copy(id = 1))
    }

    suspend fun clearActive() {
        dao.clearActive()
    }

    suspend fun savePlan(kind: String, title: String, body: String): Long =
        dao.insertPlan(
            SavedPlan(
                kind = kind,
                title = title,
                savedAt = System.currentTimeMillis(),
                body = body,
            ),
        )

    suspend fun saveOuting(
        kindId: Long,
        day: LocalDate,
        durationSec: Int?,
        distanceM: Double?,
        gradient: Double?,
        location: String?,
        bottomM: Double?,
        topM: Double?,
        bothWays: Boolean?,
        fields: Map<Long, String>,
    ): Long = db.withTransaction {
        val id = dao.insertWorkout(
            Workout(
                kind = "OUTING",
                activityKindId = kindId,
                performedAt = epoch(day),
                durationSec = durationSec,
                finished = true,
                distanceM = distanceM,
                gradient = gradient,
                location = location,
                bottomM = bottomM,
                topM = topM,
                bothWays = bothWays,
            ),
        )
        fields.forEach { (fieldId, value) ->
            if (value.isNotBlank()) dao.insertValue(FieldValue(workoutId = id, fieldId = fieldId, value = value))
        }
        id
    }

    suspend fun exportText(): String {
        val snap = load()
        return json.encodeToString(
            CarryFile(
                profile = snap.profile,
                exercises = snap.exercises,
                types = snap.types,
                links = snap.links,
                kinds = snap.kinds,
                fields = snap.fields,
                patterns = snap.patterns,
                passes = snap.passes,
                workouts = snap.workouts.filter { it.finished },
                blocks = snap.blocks,
                sets = snap.sets,
                hiitSets = snap.hiitSets,
                splits = snap.splits,
                values = snap.values,
                plans = snap.plans,
            ),
        )
    }

    suspend fun importText(text: String): Boolean {
        val carry = try {
            json.decodeFromString<CarryFile>(text)
        } catch (_: Exception) {
            return false
        }
        if (carry.version != 1) return false
        db.withTransaction {
            dao.clearActive()
            dao.clearPlans()
            dao.clearSplits()
            dao.clearSets()
            dao.clearHiitSets()
            dao.clearValues()
            dao.clearBlocks()
            dao.clearWorkouts()
            dao.clearPasses()
            dao.clearPatterns()
            dao.clearFields()
            dao.clearKinds()
            dao.clearLinks()
            dao.clearTypes()
            dao.clearExercises()
            dao.insertProfile(carry.profile.copy(id = 1))
            if (carry.exercises.isNotEmpty()) dao.insertExercises(carry.exercises)
            if (carry.types.isNotEmpty()) dao.insertTypes(carry.types)
            if (carry.links.isNotEmpty()) dao.insertLinks(carry.links)
            if (carry.kinds.isNotEmpty()) dao.insertKinds(carry.kinds)
            if (carry.fields.isNotEmpty()) dao.insertFields(carry.fields)
            if (carry.patterns.isNotEmpty()) dao.insertPatterns(carry.patterns)
            if (carry.passes.isNotEmpty()) dao.insertPasses(carry.passes)
            if (carry.workouts.isNotEmpty()) dao.insertWorkouts(carry.workouts)
            if (carry.blocks.isNotEmpty()) dao.insertBlocks(carry.blocks)
            if (carry.sets.isNotEmpty()) dao.insertSets(carry.sets)
            if (carry.hiitSets.isNotEmpty()) dao.insertHiitSets(carry.hiitSets)
            if (carry.splits.isNotEmpty()) dao.insertSplits(carry.splits)
            if (carry.values.isNotEmpty()) dao.insertValues(carry.values)
            if (carry.plans.isNotEmpty()) dao.insertPlans(carry.plans)
        }
        listOf(
            "exercises",
            "workout_types",
            "activity_kinds",
            "activity_fields",
            "patterns",
            "passes",
            "workouts",
            "blocks",
            "set_rows",
            "hiit_sets",
            "splits",
            "field_values",
            "plans",
        ).forEach { bumpSequence(it) }
        return true
    }

    private fun bumpSequence(table: String) {
        val sqlite = db.openHelper.writableDatabase
        sqlite.query("SELECT MAX(id) FROM $table").use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) {
                sqlite.execSQL(
                    "INSERT OR REPLACE INTO sqlite_sequence(name, seq) VALUES(?, ?)",
                    arrayOf<Any>(table, cursor.getLong(0)),
                )
            }
        }
    }

    private fun epoch(day: LocalDate): Long {
        val today = LocalDate.now(zone)
        val time = if (day == today) LocalTime.now(zone) else LocalTime.NOON
        return day.atTime(time).atZone(zone).toInstant().toEpochMilli()
    }
}

data class ManualHiitSet(
    val passes: List<Pass>,
    val exerciseIds: List<Long>,
    val splitSeconds: List<Int>,
    val breakSeconds: Int,
)

data class StoredCue(
    val phase: String,
    val setIndex: Int,
    val exerciseId: Long?,
    val round: Int?,
    val seconds: Int,
)
