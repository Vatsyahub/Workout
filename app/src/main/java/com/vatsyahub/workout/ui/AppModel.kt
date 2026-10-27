package com.vatsyahub.workout.ui

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vatsyahub.workout.WorkoutApp
import com.vatsyahub.workout.data.ActiveSession
import com.vatsyahub.workout.data.ManualHiitSet
import com.vatsyahub.workout.data.Profile
import com.vatsyahub.workout.data.Snapshot
import com.vatsyahub.workout.data.StoredCue
import com.vatsyahub.workout.data.Workout
import com.vatsyahub.workout.data.blockName
import com.vatsyahub.workout.data.passesOf
import com.vatsyahub.workout.data.pool
import com.vatsyahub.workout.data.poolExercises
import com.vatsyahub.workout.data.typesOf
import com.vatsyahub.workout.domain.DefaultPasses
import com.vatsyahub.workout.domain.EACH
import com.vatsyahub.workout.domain.PASS
import com.vatsyahub.workout.domain.PHASE_WORK
import com.vatsyahub.workout.domain.Pass
import com.vatsyahub.workout.domain.catchUp
import com.vatsyahub.workout.domain.drawBodyPart
import com.vatsyahub.workout.domain.drawSet
import com.vatsyahub.workout.domain.expand
import com.vatsyahub.workout.domain.fingerprint
import com.vatsyahub.workout.domain.LoggedLoad
import com.vatsyahub.workout.domain.LoggedSession
import com.vatsyahub.workout.domain.SetLoad
import com.vatsyahub.workout.domain.repeatStrength
import com.vatsyahub.workout.domain.rerollAll
import com.vatsyahub.workout.domain.rerollOne
import com.vatsyahub.workout.domain.shapeForKindName
import com.vatsyahub.workout.domain.timeline
import com.vatsyahub.workout.domain.withFavourite
import java.time.LocalDate
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface Screen {
    data object Home : Screen
    data object Workout : Screen
    data object AddKind : Screen
    data object LogType : Screen
    data object Build : Screen
    data object Strength : Screen
    data object StrengthTime : Screen
    data object Hiit : Screen
    data object PatternPick : Screen
    data object PatternEdit : Screen
    data class SetMenu(val index: Int) : Screen
    data object Clock : Screen
    data object Picker : Screen
    data object OutingTimer : Screen
    data class OutingReady(val name: String) : Screen
    data object OutingForm : Screen
    data object Logs : Screen
    data class Month(val year: Int, val month: Int) : Screen
    data class LogDetail(val id: Long) : Screen
    data object Exercises : Screen
    data object AddExercise : Screen
    data class Exercise(val id: Long) : Screen
    data object Settings : Screen
    data object Profile : Screen
    data object Carry : Screen
    data object Reports : Screen
    data object Weekly : Screen
    data object ReportExercises : Screen
    data class ExerciseReport(val key: String, val title: String) : Screen
    data object Bests : Screen
}

sealed interface Picker {
    data class StrengthSwap(val line: Int) : Picker
    data class HiitSwap(val set: Int, val line: Int) : Picker
    data class AddStrength(val workoutId: Long?) : Picker
    data class AddHiit(val set: Int, val workoutId: Long?) : Picker
    data class BodyPart(val index: Int) : Picker
}

data class BuildState(val kind: String, val parts: List<PartAsk>, val mode: String = "PART")

data class ClockUi(
    val workoutId: Long,
    val remainingSec: Int,
    val running: Boolean,
    val now: String,
    val next: String,
)

data class TimerUi(val kindId: Long, val elapsedSec: Int, val running: Boolean)

data class ResolvedCue(
    val phase: String,
    val setIndex: Int,
    val exerciseId: Long?,
    val name: String,
    val round: Int?,
    val seconds: Int,
)

class AppModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as WorkoutApp).repository
    private val tones = Tones()
    private val gate = Mutex()

    val snap = repo.data().stateIn(viewModelScope, SharingStarted.Eagerly, Snapshot())
    val stack = mutableStateListOf<Screen>(Screen.Home)

    var build by mutableStateOf(BuildState("HEAVY", emptyList()))
    var strength by mutableStateOf<StrengthDraft?>(null)
    var hiit by mutableStateOf<HiitDraft?>(null)
    var outing by mutableStateOf<OutingDraft?>(null)
    var picker by mutableStateOf<Picker?>(null)
    var patternSet by mutableStateOf<Int?>(null)
    var clock by mutableStateOf<ClockUi?>(null)
    var timer by mutableStateOf<TimerUi?>(null)
    var carryNote by mutableStateOf<String?>(null)

    private var cues: List<ResolvedCue> = emptyList()
    private var cueIndex = 0
    private var remainingMs = 0L
    private var runningClock = false
    private var cueEndElapsed = 0L
    private var clockWorkoutId = 0L
    private var clockJob: kotlinx.coroutines.Job? = null

    private var timerKind = 0L
    private var timerRunning = false
    private var timerAccumulated = 0L
    private var timerResumedWall = 0L
    private var timerJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            val loaded = repo.data().first()
            restore(loaded)
        }
    }

    fun open(screen: Screen) {
        val existing = stack.indexOf(screen)
        if (existing >= 0) {
            while (stack.lastIndex > existing) stack.removeAt(stack.lastIndex)
            return
        }
        stack += screen
    }

    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    fun showHome() {
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    fun tab(screen: Screen) {
        if (stack.size == 1 && stack.first() == screen) return
        stack.clear()
        stack.add(screen)
    }

    fun openNamedOuting(name: String) {
        if (busy()) return
        open(Screen.OutingReady(name))
    }

    fun startNamedOuting(name: String) {
        if (busy()) return
        viewModelScope.launch {
            val id = snap.value.kinds.find { it.name.equals(name, ignoreCase = true) }?.id ?: repo.addKind(name)
            startOuting(id)
        }
    }

    fun logNamedOuting(name: String) {
        if (busy()) return
        viewModelScope.launch {
            val existing = snap.value.kinds.find { it.name.equals(name, ignoreCase = true) }
            val id = existing?.id ?: repo.addKind(name)
            val shape = existing?.shape ?: shapeForKindName(name)
            outing = OutingDraft(
                kindId = id,
                day = LocalDate.now(),
                durationSec = null,
                distanceM = null,
                gradient = null,
                location = null,
                bottomM = null,
                topM = null,
                bothWays = if (shape == "HIKE") false else null,
                fields = emptyMap(),
            )
            open(Screen.OutingForm)
        }
    }

    fun showLog(id: Long) {
        showHome()
        stack += Screen.LogDetail(id)
    }

    fun openWorkout() {
        when {
            clock != null -> open(Screen.Clock)
            timer != null -> open(Screen.OutingTimer)
            else -> open(Screen.Workout)
        }
    }

    private fun busy(): Boolean {
        if (clock != null) {
            open(Screen.Clock)
            return true
        }
        if (timer != null) {
            open(Screen.OutingTimer)
            return true
        }
        return false
    }

    fun beginEmptyStrength(kind: String) {
        if (busy()) return
        strength = StrengthDraft(kind, LocalDate.now(), null, emptyList())
        open(Screen.Strength)
    }

    fun beginBuild(kind: String) {
        if (busy()) return
        build = buildFor(kind, "PART")
        open(Screen.Build)
    }

    fun setBuildMode(mode: String) {
        build = buildFor(build.kind, mode)
    }

    private fun buildFor(kind: String, mode: String): BuildState {
        val pool = snap.value.pool(kind)
        val names = if (mode == "TYPE") {
            pool.flatMap { exercise -> snap.value.typesOf(exercise.id).map { it.name } }
        } else {
            pool.map { it.bodyPart }
        }.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() }
        return BuildState(kind, names.map { PartAsk(it, 0) }, mode)
    }

    fun choosePart(index: Int, part: String) {
        val parts = build.parts.toMutableList()
        if (index !in parts.indices) return
        parts[index] = parts[index].copy(bodyPart = part)
        build = build.copy(parts = parts)
    }

    fun setPartCount(index: Int, count: Int) {
        val parts = build.parts.toMutableList()
        if (index !in parts.indices) return
        parts[index] = parts[index].copy(count = count.coerceAtLeast(0))
        build = build.copy(parts = parts)
    }

    fun addPart() {
        build = build.copy(parts = build.parts + PartAsk("", 1))
    }

    fun removePart(index: Int) {
        if (build.parts.size <= 1) return
        build = build.copy(parts = build.parts.filterIndexed { i, _ -> i != index })
    }

    fun drawBuild() {
        val pool = snap.value.pool(build.kind)
        val lines = ArrayList<StrengthLine>()
        for (ask in build.parts) {
            if (ask.bodyPart.isBlank() || ask.count < 1) continue
            val ids = if (build.mode == "TYPE") {
                pool.filter { exercise ->
                    snap.value.typesOf(exercise.id).any { it.name.equals(ask.bodyPart, true) }
                }.map { it.id }
            } else {
                pool.filter { it.bodyPart.equals(ask.bodyPart, ignoreCase = true) }.map { it.id }
            }
            drawBodyPart(ids, ask.count, Random).forEach { id ->
                lines += StrengthLine(id, List(3) { DraftSet(8, null) }, ask.bodyPart, build.mode)
            }
        }
        if (lines.isEmpty()) return
        strength = StrengthDraft(build.kind, LocalDate.now(), null, lines)
        open(Screen.Strength)
    }

    fun setDraftDay(day: LocalDate) {
        strength = strength?.copy(day = day)
    }

    fun setDraftDuration(seconds: Int?) {
        strength = strength?.copy(durationSec = seconds)
    }

    fun setDraftReps(line: Int, set: Int, reps: Int) {
        editDraft(line, set) { it.copy(reps = reps.coerceAtLeast(0)) }
    }

    fun setDraftWeight(line: Int, set: Int, weight: Double?) {
        editDraft(line, set) { it.copy(weightKg = weight) }
    }

    private fun editDraft(line: Int, set: Int, change: (DraftSet) -> DraftSet) {
        val draft = strength ?: return
        val lines = draft.lines.toMutableList()
        val row = lines.getOrNull(line) ?: return
        val sets = row.sets.toMutableList()
        val current = sets.getOrNull(set) ?: return
        sets[set] = change(current)
        lines[line] = row.copy(sets = sets)
        strength = draft.copy(lines = lines)
    }

    fun addDraftSet(line: Int) {
        val draft = strength ?: return
        val lines = draft.lines.toMutableList()
        val row = lines.getOrNull(line) ?: return
        lines[line] = row.copy(sets = row.sets + DraftSet(8, null))
        strength = draft.copy(lines = lines)
    }

    fun removeDraftSet(line: Int, set: Int) {
        val draft = strength ?: return
        val lines = draft.lines.toMutableList()
        val row = lines.getOrNull(line) ?: return
        lines[line] = row.copy(sets = row.sets.filterIndexed { index, _ -> index != set })
        strength = draft.copy(lines = lines)
    }

    fun removeDraftLine(line: Int) {
        val draft = strength ?: return
        strength = draft.copy(lines = draft.lines.filterIndexed { index, _ -> index != line })
        if (stack.lastOrNull() == Screen.Picker) back()
    }

    fun lineChoices(line: Int): List<com.vatsyahub.workout.data.Exercise> {
        val draft = strength ?: return emptyList()
        val row = draft.lines.getOrNull(line) ?: return snap.value.pool(draft.kind)
        val pool = snap.value.pool(draft.kind)
        return if (row.groupKind == "TYPE" && row.group.isNotBlank()) {
            pool.filter { exercise -> snap.value.typesOf(exercise.id).any { it.name.equals(row.group, true) } }
        } else {
            pool
        }
    }

    private fun rerollIds(kind: String, row: StrengthLine): List<Long> {
        val pool = snap.value.pool(kind)
        if (row.groupKind == "TYPE" && row.group.isNotBlank()) {
            return pool.filter { exercise ->
                snap.value.typesOf(exercise.id).any { it.name.equals(row.group, true) }
            }.map { it.id }
        }
        val part = row.group.ifBlank { snap.value.exercises.find { it.id == row.exerciseId }?.bodyPart.orEmpty() }
        if (part.isBlank()) return pool.map { it.id }
        return pool.filter { it.bodyPart.equals(part, true) }.map { it.id }
    }

    fun rerollDraftLine(line: Int) {
        val draft = strength ?: return
        val row = draft.lines.getOrNull(line) ?: return
        val ids = rerollIds(draft.kind, row)
        if (ids.isEmpty()) return
        val next = rerollOne(ids, row.exerciseId, Random)
        val lines = draft.lines.toMutableList()
        lines[line] = row.copy(exerciseId = next)
        strength = draft.copy(lines = lines)
    }

    fun rerollDraftAll() {
        val draft = strength ?: return
        val indexes = draft.lines.indices.groupBy { index ->
            val row = draft.lines[index]
            row.groupKind to row.group.ifBlank {
                snap.value.exercises.find { it.id == row.exerciseId }?.bodyPart
            }
        }
        val ids = draft.lines.map { it.exerciseId }.toMutableList()
        for ((key, group) in indexes) {
            val sample = draft.lines[group.first()]
            val pool = rerollIds(draft.kind, sample)
            if (pool.isEmpty() || key.second == null) continue
            val next = rerollAll(group.map { ids[it] }, pool, Random)
            group.zip(next).forEach { (index, id) -> ids[index] = id }
        }
        strength = draft.copy(lines = draft.lines.mapIndexed { index, line -> line.copy(exerciseId = ids[index]) })
    }

    fun saveStrength() {
        val draft = strength ?: return
        if (draft.lines.isEmpty()) return
        viewModelScope.launch {
            val id = repo.saveStrength(
                draft.kind,
                draft.day,
                draft.durationSec,
                draft.lines.map { line -> line.exerciseId to line.sets.map { it.reps to it.weightKg } },
            )
            strength = null
            showLog(id)
        }
    }

    fun newHiit(manual: Boolean) {
        if (!manual && busy()) return
        val loaded = snap.value
        val last = loaded.workouts.filter { it.kind == "HIIT" && it.finished }.maxByOrNull { it.performedAt }
        val pool = loaded.poolExercises("HIIT")
        val work = last?.workSec ?: 25
        val rest = last?.restSec ?: 5
        val breakSec = last?.breakSec ?: 300
        val setRows = last?.let { workout ->
            loaded.hiitSets.filter { it.workoutId == workout.id }.sortedBy { it.position }
        }.orEmpty()
        val template = setRows.firstOrNull()?.let { loaded.passesOf(it.patternId) }?.ifEmpty { null } ?: DefaultPasses
        val sets = if (setRows.isEmpty()) {
            List(3) { freshHiitSet(template, drawSet(pool, 3, Random), work, breakSec, true) }
        } else {
            setRows.map { row ->
                val count = loaded.blocks.count { it.workoutId == last!!.id && it.setIndex == row.position }.coerceAtLeast(1)
                val passes = loaded.passesOf(row.patternId).ifEmpty { template }
                freshHiitSet(
                    passes,
                    drawSet(pool, count, Random),
                    work,
                    breakSec,
                    fingerprint(passes) == fingerprint(template),
                )
            }
        }
        hiit = HiitDraft(LocalDate.now(), work, rest, breakSec, template, sets, manual)
        open(Screen.Hiit)
    }

    fun setFavourite(id: Long, favourite: Boolean) {
        val loaded = snap.value
        val workout = loaded.workouts.find { it.id == id } ?: return
        val next = withFavourite(loggedSession(loaded, workout), favourite)
        launch { repo.setFavourite(next.id, next.favourite) }
    }

    fun repeat(workoutId: Long) {
        if (busy()) return
        val loaded = snap.value
        val workout = loaded.workouts.find { it.id == workoutId } ?: return
        when (workout.kind) {
            "HEAVY", "BODYWEIGHT" -> {
                val plan = repeatStrength(loggedSession(loaded, workout))
                strength = StrengthDraft(
                    plan.kind,
                    LocalDate.now(),
                    plan.durationSec,
                    plan.lines.map { line ->
                        StrengthLine(line.exerciseId, line.sets.map { DraftSet(it.reps, it.weightKg) })
                    },
                )
                open(Screen.Strength)
            }
            "HIIT" -> {
                val setRows = loaded.hiitSets.filter { it.workoutId == workoutId }.sortedBy { it.position }
                val template = setRows.firstOrNull()?.let { loaded.passesOf(it.patternId) }?.ifEmpty { null } ?: DefaultPasses
                val sets = setRows.map { row ->
                    val ids = loaded.blocks.filter { it.workoutId == workoutId && it.setIndex == row.position }
                        .sortedBy { it.position }
                        .mapNotNull { it.exerciseId }
                    val passes = loaded.passesOf(row.patternId).ifEmpty { template }
                    freshHiitSet(
                        passes,
                        ids,
                        workout.workSec ?: 25,
                        workout.breakSec ?: 300,
                        fingerprint(passes) == fingerprint(template),
                    )
                }
                hiit = HiitDraft(
                    LocalDate.now(),
                    workout.workSec ?: 25,
                    workout.restSec ?: 5,
                    workout.breakSec ?: 300,
                    template,
                    sets,
                    false,
                )
                open(Screen.Hiit)
            }
            "OUTING" -> {
                val name = loaded.kinds.find { it.id == workout.activityKindId }?.name ?: return
                open(Screen.OutingReady(name))
            }
        }
    }

    private fun loggedSession(loaded: Snapshot, workout: Workout): LoggedSession {
        val lines = if (workout.kind == "HEAVY" || workout.kind == "BODYWEIGHT") {
            loaded.blocks.filter { it.workoutId == workout.id && it.exerciseId != null }
                .sortedBy { it.position }
                .map { block ->
                    val sets = loaded.sets.filter { it.blockId == block.id }
                        .sortedBy { it.position }
                        .map { SetLoad(it.reps, it.weightKg) }
                        .ifEmpty { List(3) { SetLoad(8, null) } }
                    LoggedLoad(block.exerciseId!!, sets)
                }
        } else {
            emptyList()
        }
        return LoggedSession(workout.id, workout.kind, workout.favourite, workout.durationSec, lines)
    }

    fun setHiitDay(day: LocalDate) {
        hiit = hiit?.copy(day = day)
    }

    fun setWork(seconds: Int) {
        val draft = hiit ?: return
        val next = seconds.coerceAtLeast(1)
        hiit = draft.copy(
            workSec = next,
            sets = draft.sets.map { set ->
                val count = expand(set.exerciseIds.size, set.passes).size
                set.copy(splitSeconds = retargetSplits(set.splitSeconds, count, draft.workSec, next))
            },
        )
    }

    fun setRest(seconds: Int) {
        hiit = hiit?.copy(restSec = seconds.coerceAtLeast(0))
    }

    fun setBreak(seconds: Int) {
        val draft = hiit ?: return
        val next = seconds.coerceAtLeast(0)
        hiit = draft.copy(
            breakSec = next,
            sets = draft.sets.map { if (it.breakSeconds == draft.breakSec) it.copy(breakSeconds = next) else it },
        )
    }

    fun setHiitCount(setIndex: Int, count: Int) {
        val draft = hiit ?: return
        val pool = snap.value.poolExercises("HIIT")
        val sets = draft.sets.toMutableList()
        val set = sets.getOrNull(setIndex) ?: return
        val ids = drawSet(pool, count.coerceAtLeast(1), Random)
        sets[setIndex] = freshHiitSet(set.passes, ids, draft.workSec, set.breakSeconds, set.shared)
        hiit = draft.copy(sets = sets)
    }

    fun addHiitSet() {
        val draft = hiit ?: return
        val pool = snap.value.poolExercises("HIIT")
        val count = draft.sets.lastOrNull()?.exerciseIds?.size ?: 3
        val set = freshHiitSet(draft.template, drawSet(pool, count, Random), draft.workSec, draft.breakSec, true)
        hiit = draft.copy(sets = draft.sets + set)
    }

    fun removeHiitSet(index: Int) {
        val draft = hiit ?: return
        if (draft.sets.size <= 1) return
        hiit = draft.copy(sets = draft.sets.filterIndexed { i, _ -> i != index })
        back()
    }

    fun removeHiitLine(setIndex: Int, line: Int) {
        val draft = hiit ?: return
        val sets = draft.sets.toMutableList()
        val set = sets.getOrNull(setIndex) ?: return
        val ids = set.exerciseIds.filterIndexed { index, _ -> index != line }
        sets[setIndex] = set.copy(exerciseIds = ids).aligned(draft.workSec, set.breakSeconds)
        hiit = draft.copy(sets = sets)
        back()
    }

    fun rerollHiit(setIndex: Int, line: Int) {
        val draft = hiit ?: return
        val pool = snap.value.pool("HIIT").map { it.id }
        val sets = draft.sets.toMutableList()
        val set = sets.getOrNull(setIndex) ?: return
        val ids = set.exerciseIds.toMutableList()
        if (line !in ids.indices) return
        ids[line] = rerollOne(pool, ids[line], Random)
        sets[setIndex] = set.copy(exerciseIds = ids).aligned(draft.workSec, set.breakSeconds)
        hiit = draft.copy(sets = sets)
    }

    fun rerollHiitAll() {
        val draft = hiit ?: return
        val pool = snap.value.poolExercises("HIIT")
        val ids = snap.value.pool("HIIT").map { it.id }
        hiit = draft.copy(
            sets = draft.sets.map { set ->
                val drawn = drawSet(pool, set.exerciseIds.size, Random).toMutableList()
                set.exerciseIds.forEachIndexed { index, current ->
                    if (drawn.getOrNull(index) == current && index < drawn.size) {
                        drawn[index] = rerollOne(ids, current, Random)
                    }
                }
                set.copy(exerciseIds = drawn).aligned(draft.workSec, set.breakSeconds)
            },
        )
    }

    fun swapHiit(setIndex: Int, line: Int, exerciseId: Long) {
        val draft = hiit ?: return
        val sets = draft.sets.toMutableList()
        val set = sets.getOrNull(setIndex) ?: return
        val ids = set.exerciseIds.toMutableList()
        if (line !in ids.indices) return
        ids[line] = exerciseId
        sets[setIndex] = set.copy(exerciseIds = ids)
        hiit = draft.copy(sets = sets)
    }

    fun setBoutSeconds(setIndex: Int, bout: Int, seconds: Int) {
        val draft = hiit ?: return
        val sets = draft.sets.toMutableList()
        val set = sets.getOrNull(setIndex) ?: return
        val splits = set.splitSeconds.toMutableList()
        if (bout !in splits.indices) return
        splits[bout] = seconds.coerceAtLeast(0)
        sets[setIndex] = set.copy(splitSeconds = splits)
        hiit = draft.copy(sets = sets)
    }

    fun openPatterns(setIndex: Int?) {
        patternSet = setIndex
        open(Screen.PatternPick)
    }

    fun currentPasses(): List<Pass> {
        val draft = hiit ?: return DefaultPasses
        val index = patternSet
        return if (index == null) draft.template else draft.sets.getOrNull(index)?.passes ?: draft.template
    }

    fun updatePasses(passes: List<Pass>) {
        val draft = hiit ?: return
        val cleaned = passes.filter { it.times > 0 }.ifEmpty { listOf(Pass(PASS, 1)) }
        if (patternSet == null) {
            hiit = draft.copy(
                template = cleaned,
                sets = draft.sets.map { set ->
                    if (set.shared) set.copy(passes = cleaned).aligned(draft.workSec, set.breakSeconds) else set
                },
            )
        } else {
            val index = patternSet ?: return
            val sets = draft.sets.toMutableList()
            val set = sets.getOrNull(index) ?: return
            sets[index] = set.copy(passes = cleaned, shared = false).aligned(draft.workSec, set.breakSeconds)
            hiit = draft.copy(sets = sets)
        }
    }

    fun applyPattern(patternId: Long) {
        val passes = snap.value.passesOf(patternId)
        if (passes.isNotEmpty()) updatePasses(passes)
        back()
    }

    fun changePass(index: Int, times: Int) {
        val passes = currentPasses().toMutableList()
        if (index !in passes.indices) return
        passes[index] = passes[index].copy(times = times.coerceAtLeast(1))
        updatePasses(passes)
    }

    fun removePass(index: Int) {
        val passes = currentPasses().toMutableList()
        if (passes.size <= 1) return
        passes.removeAt(index)
        updatePasses(passes)
    }

    fun addPass(kind: String) {
        updatePasses(currentPasses() + Pass(kind, if (kind == EACH) 2 else 1))
    }

    fun startClock() {
        val draft = hiit ?: return
        if (draft.sets.all { it.exerciseIds.isEmpty() }) return
        viewModelScope.launch {
            val id = repo.startHiit(
                draft.day,
                draft.workSec,
                draft.restSec,
                draft.breakSec,
                draft.sets.map { it.passes to it.exerciseIds },
            )
            gate.withLock {
                cues = cuesFromDraft(draft)
                cueIndex = 0
                clockWorkoutId = id
                startCue(play = true)
                startClockLoop()
            }
            open(Screen.Clock)
        }
    }

    fun saveManualHiit() {
        val draft = hiit ?: return
        viewModelScope.launch {
            val id = repo.saveManualHiit(
                draft.day,
                draft.workSec,
                draft.restSec,
                draft.breakSec,
                draft.sets.map {
                    ManualHiitSet(it.passes, it.exerciseIds, it.splitSeconds, it.breakSeconds)
                },
            )
            hiit = null
            showLog(id)
        }
    }

    fun pauseClock() {
        viewModelScope.launch {
            gate.withLock {
                if (clock == null) return@withLock
                if (runningClock) {
                    remainingMs = (cueEndElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0)
                    runningClock = false
                } else {
                    runningClock = true
                    cueEndElapsed = SystemClock.elapsedRealtime() + remainingMs
                }
                publishClock()
                persistClock()
            }
        }
    }

    fun forwardClock() {
        viewModelScope.launch {
            gate.withLock {
                val cue = cues.getOrNull(cueIndex) ?: return@withLock
                if (runningClock) remainingMs = (cueEndElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0)
                completeCue(elapsedSec(cue))
            }
        }
    }

    fun backClock() {
        viewModelScope.launch {
            gate.withLock {
                if (cueIndex == 0) return@withLock
                repo.deleteSplitAt(clockWorkoutId, cueIndex - 1)
                cueIndex -= 1
                startCue(play = true)
            }
        }
    }

    fun stopClock() {
        viewModelScope.launch {
            gate.withLock { finishClock(recordCurrent = false) }
        }
    }

    fun startOuting(kindId: Long) {
        if (busy()) return
        timerKind = kindId
        timerRunning = true
        timerAccumulated = 0
        timerResumedWall = System.currentTimeMillis()
        publishTimer()
        viewModelScope.launch { persistTimer() }
        startTimerLoop()
        open(Screen.OutingTimer)
    }

    fun pauseTimer() {
        if (timerRunning) {
            timerAccumulated = currentTimerMs()
            timerRunning = false
        } else {
            timerResumedWall = System.currentTimeMillis()
            timerRunning = true
        }
        publishTimer()
        viewModelScope.launch { persistTimer() }
    }

    fun stopTimer() {
        val kind = timerKind
        val seconds = (currentTimerMs() / 1000).toInt()
        timerJob?.cancel()
        timer = null
        timerRunning = false
        viewModelScope.launch {
            repo.clearActive()
            val id = repo.saveOuting(kind, LocalDate.now(), seconds, null, null, null, null, null, null, emptyMap())
            showLog(id)
        }
    }

    fun beginOutingLog(kindId: Long) {
        val kind = snap.value.kinds.find { it.id == kindId } ?: return
        outing = OutingDraft(
            kindId = kindId,
            day = LocalDate.now(),
            durationSec = null,
            distanceM = null,
            gradient = null,
            location = null,
            bottomM = null,
            topM = null,
            bothWays = if (kind.shape == "HIKE") false else null,
            fields = emptyMap(),
        )
        open(Screen.OutingForm)
    }

    fun saveOutingDraft() {
        val draft = outing ?: return
        viewModelScope.launch {
            val id = repo.saveOuting(
                draft.kindId,
                draft.day,
                draft.durationSec,
                draft.distanceM,
                draft.gradient,
                draft.location,
                draft.bottomM,
                draft.topM,
                draft.bothWays,
                draft.fields,
            )
            outing = null
            showLog(id)
        }
    }

    fun openPicker(request: Picker) {
        picker = request
        open(Screen.Picker)
    }

    fun pickExercise(id: Long) {
        when (val request = picker) {
            is Picker.StrengthSwap -> {
                val draft = strength ?: return
                val lines = draft.lines.toMutableList()
                val row = lines.getOrNull(request.line) ?: return
                val chosenPart = snap.value.exercises.find { it.id == id }?.bodyPart.orEmpty()
                val group = if (row.groupKind == "TYPE") row.group else chosenPart.ifBlank { row.group }
                lines[request.line] = row.copy(exerciseId = id, group = group)
                strength = draft.copy(lines = lines)
            }
            is Picker.HiitSwap -> swapHiit(request.set, request.line, id)
            is Picker.AddStrength -> {
                if (request.workoutId == null) {
                    val draft = strength
                    if (draft != null) {
                        strength = draft.copy(lines = draft.lines + StrengthLine(id, List(3) { DraftSet(8, null) }))
                    }
                } else {
                    launch { repo.addBlock(request.workoutId, id) }
                }
            }
            is Picker.AddHiit -> {
                if (request.workoutId == null) {
                    val draft = hiit
                    if (draft != null && request.set in draft.sets.indices) {
                        val sets = draft.sets.toMutableList()
                        val set = sets[request.set]
                        sets[request.set] = set.copy(exerciseIds = set.exerciseIds + id).aligned(draft.workSec, set.breakSeconds)
                        hiit = draft.copy(sets = sets)
                    }
                } else {
                    launch { repo.appendHiitExercise(request.workoutId, request.set, id) }
                }
            }
            else -> Unit
        }
        back()
    }

    fun pickPart(name: String) {
        val request = picker as? Picker.BodyPart ?: return
        choosePart(request.index, name)
        back()
    }

    fun addExercise(name: String, bodyPart: String, usage: String, types: List<String>) {
        launch { repo.addExercise(name, bodyPart, usage, types) }
    }

    fun saveExercise(id: Long?, name: String, bodyPart: String, usage: String, types: List<String>) {
        launch {
            if (id == null) {
                repo.addExercise(name, bodyPart, usage, types)
            } else {
                repo.updateExercise(id, name, bodyPart, usage)
                repo.setTypes(id, types)
            }
        }
    }

    fun updateExercise(id: Long, name: String, bodyPart: String, usage: String) {
        launch { repo.updateExercise(id, name, bodyPart, usage) }
    }

    fun addType(exerciseId: Long, name: String) {
        launch { repo.addType(exerciseId, name) }
    }

    fun removeType(exerciseId: Long, typeId: Long) {
        launch { repo.removeType(exerciseId, typeId) }
    }

    fun deleteExercise(id: Long) {
        launch {
            repo.deleteExercise(id)
            showHome()
            stack += Screen.Exercises
        }
    }

    fun addKind(name: String) {
        launch {
            repo.addKind(name)
            back()
        }
    }

    fun addField(kindId: Long, name: String) {
        launch { repo.addField(kindId, name) }
    }

    fun saveProfile(profile: Profile) {
        launch { repo.saveProfile(profile) }
    }

    fun editProfile(
        name: String? = null,
        age: Int? = null,
        setAge: Boolean = false,
        weightKg: Double? = null,
        setWeight: Boolean = false,
        heightCm: Double? = null,
        setHeight: Boolean = false,
        avatar: String? = null,
    ) {
        launch {
            repo.editProfile(
                name = name,
                age = age,
                setAge = setAge,
                weightKg = weightKg,
                setWeight = setWeight,
                heightCm = heightCm,
                setHeight = setHeight,
                avatar = avatar,
                setAvatar = avatar != null,
            )
        }
    }

    fun changeSet(id: Long, reps: Int, weight: Double?) = launch { repo.updateSet(id, reps, weight) }

    fun addSavedSet(blockId: Long) = launch { repo.addSet(blockId) }

    fun removeSavedSet(id: Long) = launch { repo.deleteSet(id) }

    fun removeSavedBlock(id: Long) = launch { repo.deleteBlock(id) }

    fun removeHiitBlock(workoutId: Long, blockId: Long, exerciseId: Long?, setIndex: Int) = launch {
        repo.deleteBlock(blockId)
        val doomed = snap.value.splits.filter {
            it.workoutId == workoutId && it.phase == PHASE_WORK && it.setIndex == setIndex && it.exerciseId == exerciseId
        }
        doomed.forEach { repo.deleteSplit(it.id) }
    }

    fun changeDuration(id: Long, seconds: Int?) = launch { repo.updateDuration(id, seconds) }

    fun changeDay(id: Long, day: LocalDate) = launch { repo.updateDay(id, day) }

    fun changeSplit(id: Long, seconds: Int) = launch { repo.updateSplit(id, seconds) }

    fun changeHiitTimes(id: Long, work: Int, rest: Int, breakSec: Int) = launch {
        repo.updateHiitTimes(id, work, rest, breakSec)
    }

    fun changeOuting(
        id: Long,
        distance: Double?,
        gradient: Double?,
        location: String?,
        bottom: Double?,
        top: Double?,
        bothWays: Boolean?,
        duration: Int?,
    ) = launch { repo.updateOuting(id, distance, gradient, location, bottom, top, bothWays, duration) }

    fun changeField(workoutId: Long, fieldId: Long, value: String) = launch {
        repo.setField(workoutId, fieldId, value)
    }

    fun deleteWorkout(id: Long) = launch {
        repo.deleteWorkout(id)
        showHome()
        stack += Screen.Logs
    }

    suspend fun exportText(): String = repo.exportText()

    suspend fun importText(text: String): Boolean = repo.importText(text)

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private fun cuesFromDraft(draft: HiitDraft): List<ResolvedCue> {
        val names = snap.value.exercises.associate { it.id to it.name }
        val raw = timeline(
            draft.sets.map { it.exerciseIds.size },
            draft.sets.map { it.passes },
            draft.workSec,
            draft.restSec,
            draft.breakSec,
        )
        return raw.map { cue ->
            val id = cue.slot?.let { draft.sets.getOrNull(cue.setIndex)?.exerciseIds?.getOrNull(it) }
            ResolvedCue(cue.phase, cue.setIndex, id, names[id] ?: "Exercise", cue.round, cue.seconds)
        }
    }

    private fun cuesFrom(loaded: Snapshot, workoutId: Long): List<ResolvedCue> {
        val workout = loaded.workouts.find { it.id == workoutId } ?: return emptyList()
        val setRows = loaded.hiitSets.filter { it.workoutId == workoutId }.sortedBy { it.position }
        val grouped = loaded.blocks.filter { it.workoutId == workoutId }
        val lists = setRows.map { row -> grouped.filter { it.setIndex == row.position }.sortedBy { it.position } }
        val patterns = setRows.map { loaded.passesOf(it.patternId).ifEmpty { DefaultPasses } }
        val raw = timeline(
            lists.map { it.size },
            patterns,
            workout.workSec ?: 25,
            workout.restSec ?: 5,
            workout.breakSec ?: 300,
        )
        return raw.map { cue ->
            val block = cue.slot?.let { lists.getOrNull(cue.setIndex)?.getOrNull(it) }
            ResolvedCue(
                cue.phase,
                cue.setIndex,
                block?.exerciseId,
                block?.let { loaded.blockName(it) } ?: "Exercise",
                cue.round,
                cue.seconds,
            )
        }
    }

    private suspend fun startCue(play: Boolean) {
        val cue = cues.getOrNull(cueIndex)
        if (cue == null) {
            finishClock(recordCurrent = false)
            return
        }
        remainingMs = cue.seconds * 1000L
        cueEndElapsed = SystemClock.elapsedRealtime() + remainingMs
        runningClock = true
        publishClock()
        persistClock()
        if (play) {
            if (cue.phase == PHASE_WORK) tones.high() else tones.low()
        }
    }

    private suspend fun completeCue(actualSec: Int) {
        val cue = cues.getOrNull(cueIndex) ?: return
        repo.recordSplit(
            clockWorkoutId,
            cueIndex,
            StoredCue(cue.phase, cue.setIndex, cue.exerciseId, cue.round, actualSec),
        )
        cueIndex += 1
        if (cueIndex >= cues.size) finishClock(recordCurrent = false) else startCue(true)
    }

    private fun elapsedSec(cue: ResolvedCue): Int {
        val spent = cue.seconds * 1000L - remainingMs
        return ((spent + 500) / 1000L).toInt().coerceIn(0, cue.seconds)
    }

    private suspend fun finishClock(recordCurrent: Boolean) {
        if (recordCurrent) {
            val cue = cues.getOrNull(cueIndex)
            if (cue != null) {
                repo.recordSplit(
                    clockWorkoutId,
                    cueIndex,
                    StoredCue(cue.phase, cue.setIndex, cue.exerciseId, cue.round, elapsedSec(cue)),
                )
            }
        }
        val id = clockWorkoutId
        runningClock = false
        clock = null
        cues = emptyList()
        if (id != 0L) {
            repo.finish(id)
            repo.clearActive()
            showLog(id)
        }
    }

    private fun startClockLoop() {
        clockJob?.cancel()
        clockJob = viewModelScope.launch {
            while (isActive && clock != null) {
                if (!runningClock) {
                    delay(120)
                    continue
                }
                val left = cueEndElapsed - SystemClock.elapsedRealtime()
                if (left <= 0) {
                    gate.withLock {
                        if (!runningClock || clock == null) return@withLock
                        val due = cueEndElapsed - SystemClock.elapsedRealtime()
                        if (due > 0) {
                            remainingMs = due
                            publishClock()
                            return@withLock
                        }
                        val cue = cues.getOrNull(cueIndex) ?: return@withLock
                        remainingMs = 0
                        completeCue(cue.seconds)
                    }
                } else {
                    remainingMs = left
                    publishClock()
                    delay(80)
                }
            }
        }
    }

    private suspend fun persistClock() {
        repo.saveActive(
            ActiveSession(
                mode = "HIIT",
                workoutId = clockWorkoutId,
                index = cueIndex,
                running = runningClock,
                remainingMs = remainingMs,
                resumedAt = System.currentTimeMillis(),
            ),
        )
    }

    private fun publishClock() {
        val cue = cues.getOrNull(cueIndex)
        if (cue == null) {
            clock = null
            return
        }
        val next = cues.drop(cueIndex + 1).firstOrNull { it.phase == PHASE_WORK }?.name.orEmpty()
        clock = ClockUi(
            workoutId = clockWorkoutId,
            remainingSec = ((remainingMs + 999) / 1000).toInt(),
            running = runningClock,
            now = if (cue.phase == PHASE_WORK) cue.name else "Rest",
            next = next,
        )
    }

    private fun currentTimerMs(): Long {
        val extra = if (timerRunning) (System.currentTimeMillis() - timerResumedWall).coerceAtLeast(0) else 0L
        return timerAccumulated + extra
    }

    private fun publishTimer() {
        timer = TimerUi(timerKind, (currentTimerMs() / 1000).toInt(), timerRunning)
    }

    private suspend fun persistTimer() {
        repo.saveActive(
            ActiveSession(
                mode = "OUTING",
                kindId = timerKind,
                running = timerRunning,
                resumedAt = timerResumedWall,
                accumulatedMs = timerAccumulated,
            ),
        )
    }

    private fun startTimerLoop() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive && timer != null) {
                publishTimer()
                delay(250)
            }
        }
    }

    private suspend fun restore(loaded: Snapshot) {
        val active = loaded.active ?: return
        if (active.mode == "HIIT" && active.workoutId != null) {
            val built = cuesFrom(loaded, active.workoutId)
            if (built.isEmpty()) {
                repo.finish(active.workoutId)
                repo.clearActive()
                return
            }
            val durations = built.map { it.seconds * 1000L }
            val elapsed = if (active.running) (System.currentTimeMillis() - active.resumedAt).coerceAtLeast(0) else 0L
            val pos = if (active.running) {
                catchUp(durations, active.index, active.remainingMs, elapsed)
            } else {
                com.vatsyahub.workout.domain.ClockPos(active.index, active.remainingMs, false)
            }
            val existing = loaded.splits.filter { it.workoutId == active.workoutId }.map { it.position }.toSet()
            for (index in active.index until pos.index) {
                val cue = built.getOrNull(index) ?: continue
                if (index !in existing) {
                    repo.recordSplit(
                        active.workoutId,
                        index,
                        StoredCue(cue.phase, cue.setIndex, cue.exerciseId, cue.round, cue.seconds),
                    )
                }
            }
            if (pos.finished) {
                repo.finish(active.workoutId)
                repo.clearActive()
                showLog(active.workoutId)
                return
            }
            cues = built
            cueIndex = pos.index
            remainingMs = pos.remainingMs
            clockWorkoutId = active.workoutId
            runningClock = active.running
            if (runningClock) {
                cueEndElapsed = SystemClock.elapsedRealtime() + remainingMs
                if (pos.index != active.index) {
                    val cue = built.getOrNull(pos.index)
                    if (cue?.phase == PHASE_WORK) tones.high() else tones.low()
                }
            }
            publishClock()
            persistClock()
            startClockLoop()
            open(Screen.Clock)
        } else if (active.mode == "OUTING" && active.kindId != null) {
            timerKind = active.kindId
            timerAccumulated = active.accumulatedMs
            timerResumedWall = active.resumedAt
            timerRunning = active.running
            publishTimer()
            startTimerLoop()
            open(Screen.OutingTimer)
        }
    }
}

fun exerciseKey(id: Long): String = "ex:$id"
