package com.vatsyahub.workout.ui

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import com.vatsyahub.workout.data.strengthLibrary
import com.vatsyahub.workout.data.typesOf
import com.vatsyahub.workout.domain.ACTIVE_BLOCK
import com.vatsyahub.workout.domain.BuilderDraft
import com.vatsyahub.workout.domain.HeldSession
import com.vatsyahub.workout.domain.LogRecord
import com.vatsyahub.workout.domain.PlanBody
import com.vatsyahub.workout.domain.PlanCount
import com.vatsyahub.workout.domain.PlanHiit
import com.vatsyahub.workout.domain.PlanHiitSet
import com.vatsyahub.workout.domain.PlanLine
import com.vatsyahub.workout.domain.PlanPass
import com.vatsyahub.workout.domain.PlanSet
import com.vatsyahub.workout.domain.StrengthHold
import com.vatsyahub.workout.domain.StartResult
import com.vatsyahub.workout.domain.WorkoutBook
import com.vatsyahub.workout.domain.classifyPickedKind
import com.vatsyahub.workout.domain.decodePlan
import com.vatsyahub.workout.domain.decodeStrengthHold
import com.vatsyahub.workout.domain.encodePlan
import com.vatsyahub.workout.domain.encodeStrengthHold
import com.vatsyahub.workout.domain.openPlan
import com.vatsyahub.workout.domain.planDetail
import com.vatsyahub.workout.domain.planTitle
import com.vatsyahub.workout.domain.saveBuilderPlan
import com.vatsyahub.workout.domain.startWorkout
import com.vatsyahub.workout.domain.DefaultPasses
import com.vatsyahub.workout.domain.EACH
import com.vatsyahub.workout.domain.PASS
import com.vatsyahub.workout.domain.PHASE_BREAK
import com.vatsyahub.workout.domain.PHASE_COOLDOWN
import com.vatsyahub.workout.domain.PHASE_REST
import com.vatsyahub.workout.domain.PHASE_WARMUP
import com.vatsyahub.workout.domain.PHASE_WORK
import com.vatsyahub.workout.domain.Pass
import com.vatsyahub.workout.domain.catchUp
import com.vatsyahub.workout.domain.DrawCard
import com.vatsyahub.workout.domain.drawnOrder
import com.vatsyahub.workout.domain.lineUse
import com.vatsyahub.workout.domain.repsFor
import com.vatsyahub.workout.domain.spreadType
import com.vatsyahub.workout.domain.takeExact
import com.vatsyahub.workout.domain.drawSet
import com.vatsyahub.workout.domain.expand
import com.vatsyahub.workout.domain.fingerprint
import com.vatsyahub.workout.domain.matchesBodyPart
import com.vatsyahub.workout.domain.moveItem
import com.vatsyahub.workout.domain.musclesOf
import com.vatsyahub.workout.domain.LoggedLoad
import com.vatsyahub.workout.domain.LoggedSession
import com.vatsyahub.workout.domain.SetLoad
import com.vatsyahub.workout.domain.savedExerciseSets
import com.vatsyahub.workout.domain.repeatStrength
import com.vatsyahub.workout.domain.rerollAll
import com.vatsyahub.workout.domain.rerollInSlot
import com.vatsyahub.workout.domain.rerollOne
import com.vatsyahub.workout.domain.shapeForKindName
import com.vatsyahub.workout.domain.strengthLabel
import com.vatsyahub.workout.domain.stepExercise
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
    data class PickSwap(val line: Int) : Picker
}

data class BuildState(
    val kind: String,
    val parts: List<PartAsk>,
    val mode: String = "PART",
    val picks: List<StrengthLine> = emptyList(),
    val picksDragged: Boolean = false,
)

data class ClockUi(
    val workoutId: Long,
    val remainingSec: Int,
    val totalSec: Int,
    val fraction: Float,
    val work: Boolean,
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

data class ActiveCard(val title: String, val detail: String)

class AppModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as WorkoutApp).repository
    private val tones = Tones(app)
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
    var notice by mutableStateOf<String?>(null)
    var planNote by mutableStateOf<String?>(null)
    var sessionReady by mutableStateOf(false)

    private var activeMode: String? = null
    private val sessionGate = Mutex()

    private var cues: List<ResolvedCue> = emptyList()
    private var cueIndex = 0
    private var remainingMs = 0L
    private var runningClock = false
    private var awaitingStart = false
    private var cueEndElapsed = 0L
    private var clockWorkoutId = 0L
    private var clockJob: kotlinx.coroutines.Job? = null

    private var timerKind = 0L
    private var timerRunning = false
    private var timerAccumulated = 0L
    private var timerResumedWall = 0L
    private var timerJob: kotlinx.coroutines.Job? = null
    private var pickDrag: Int? = null
    private var draftDrag: Int? = null

    init {
        viewModelScope.launch {
            try {
                val loaded = repo.data().first()
                if (activeMode == null) restore(loaded)
            } finally {
                sessionReady = true
            }
            snapshotFlow { strength }.collect { persistLiveStrength() }
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
        val draft = strength
        if (stack.lastOrNull() == Screen.Strength && draft != null && draft.started && !draft.logging) {
            val under = stack.getOrNull(stack.lastIndex - 1)
            val fromPick = under == Screen.Build && build.mode == "PICK"
            if (fromPick) {
                build = build.copy(picks = draft.lines)
                releaseStrength()
            } else {
                strength = draft.copy(started = false)
                releaseStrength()
                return
            }
        }
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
        notice = null
        when {
            clock != null || activeMode == "HIIT" -> open(Screen.Clock)
            timer != null || activeMode == "OUTING" -> open(Screen.OutingTimer)
            strengthLive() || activeMode == "STRENGTH" -> open(Screen.Strength)
            else -> open(Screen.Workout)
        }
    }

    fun activeCard(loaded: Snapshot): ActiveCard? {
        if (clock != null || activeMode == "HIIT") {
            val now = clock?.now?.takeIf { it.isNotBlank() } ?: "In progress"
            return ActiveCard("HIIT", now)
        }
        if (timer != null || activeMode == "OUTING") {
            val kindId = timer?.kindId ?: loaded.active?.kindId
            val name = loaded.kinds.find { it.id == kindId }?.name ?: "Outing"
            return ActiveCard(name, "In progress")
        }
        if (strengthLive() || activeMode == "STRENGTH") {
            val draft = strength
            val title = strengthLabel(draft?.kind ?: storedStrengthKind(loaded) ?: "HEAVY")
            val detail = draft?.lines?.getOrNull(draft.focus)?.let { line ->
                loaded.exercises.find { it.id == line.exerciseId }?.name
            } ?: "In progress"
            return ActiveCard(title, detail)
        }
        if (!sessionReady) {
            val active = loaded.active ?: return null
            return when (active.mode) {
                "HIIT" -> ActiveCard("HIIT", "In progress")
                "OUTING" -> ActiveCard(loaded.kinds.find { it.id == active.kindId }?.name ?: "Outing", "In progress")
                "STRENGTH" -> ActiveCard(strengthLabel(storedStrengthKind(loaded) ?: "HEAVY"), "In progress")
                else -> null
            }
        }
        return null
    }

    private fun storedStrengthKind(loaded: Snapshot): String? =
        loaded.active?.payload?.let { decodeStrengthHold(it) }?.kind

    private fun strengthLive(): Boolean {
        val draft = strength
        return draft != null && draft.started && !draft.logging && draft.lines.isNotEmpty()
    }

    private fun blockNew(): Boolean {
        if (!sessionReady) return true
        val result = WorkoutBook(active = activeMode?.let { HeldSession(it, it) }).startWorkout("NEXT", "Next")
        if (result is StartResult.Rejected) {
            notice = result.message
            return true
        }
        return false
    }

    private fun busy(): Boolean = blockNew()

    private fun releaseStrength() {
        if (activeMode != "STRENGTH") return
        activeMode = null
        viewModelScope.launch {
            sessionGate.withLock {
                if (activeMode == null) repo.clearActive()
            }
        }
    }

    fun beginEmptyStrength(kind: String) {
        if (busy()) return
        strength = StrengthDraft(kind, LocalDate.now(), null, emptyList(), logging = true)
        open(Screen.Strength)
    }

    fun beginBuild(kind: String) {
        if (busy()) return
        build = buildFor(kind, "PART")
        open(Screen.Build)
    }

    fun setBuildMode(mode: String) {
        val picks = build.picks
        build = buildFor(build.kind, mode).copy(picks = picks, picksDragged = build.picksDragged)
    }

    private fun buildFor(kind: String, mode: String): BuildState {
        if (mode == "PICK") return BuildState(kind, emptyList(), mode)
        val pool = snap.value.pool(kind)
        val names = if (mode == "TYPE") {
            pool.flatMap { exercise -> snap.value.typesOf(exercise.id).map { it.name } }
        } else {
            pool.flatMap { musclesOf(it.bodyPart) }
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

    private fun drawCard(exercise: com.vatsyahub.workout.data.Exercise, key: Long = exercise.id): DrawCard =
        DrawCard(
            id = exercise.id,
            name = exercise.name,
            muscles = musclesOf(exercise.bodyPart),
            types = snap.value.typesOf(exercise.id).map { it.name }.toSet(),
            usage = exercise.usage,
            key = key,
        )

    private fun lineCard(line: StrengthLine): DrawCard? {
        val exercise = snap.value.exercises.find { it.id == line.exerciseId } ?: return null
        return drawCard(exercise, line.key)
    }

    private fun prescribedSets(card: DrawCard, ordered: List<DrawCard>, kind: String): List<DraftSet> {
        val heavy = lineUse(kind, card.usage) == "HEAVY"
        return repsFor(card, ordered, kind, loggedReps(card.id, heavy)).map { DraftSet(it, null) }
    }

    private fun loggedReps(exerciseId: Long, asHeavy: Boolean): List<Int>? {
        val loaded = snap.value
        val usage = loaded.exercises.find { it.id == exerciseId }?.usage
        val kinds = if (asHeavy) setOf("HEAVY", "MIXED") else setOf("BODYWEIGHT", "MIXED")
        val sessions = loaded.workouts.filter { it.finished && it.kind in kinds }.sortedByDescending { it.performedAt }
        for (workout in sessions) {
            val block = loaded.blocks.filter { it.workoutId == workout.id && it.exerciseId == exerciseId }
                .maxByOrNull { it.position } ?: continue
            val sets = loaded.sets.filter { it.blockId == block.id }.sortedBy { it.position }
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
            return sets.map { it.reps }
        }
        return null
    }

    private fun arrangePicks(lines: List<StrengthLine>, reorder: Boolean): List<StrengthLine> {
        val cards = lines.mapNotNull { lineCard(it) }
        if (cards.size != lines.size) return lines
        val kind = classifyPickedKind(cards.map { it.usage }, build.kind)
        if (!reorder) {
            return lines.map { line ->
                val card = cards.first { it.key == line.key }
                if (line.setsEdited) line else line.copy(sets = prescribedSets(card, cards, kind))
            }
        }
        val ordered = drawnOrder(cards, kind, dragged = false)
        return ordered.map { card ->
            val line = lines.first { it.key == card.key }
            if (line.setsEdited) line else line.copy(sets = prescribedSets(card, ordered, kind))
        }
    }

    private fun orderDrawn(cards: List<DrawCard>, kind: String, dragged: Boolean): List<DrawCard> =
        drawnOrder(cards, kind, dragged)

    fun drawBuild() {
        if (activeMode != null) {
            notice = ACTIVE_BLOCK
            return
        }
        val pool = snap.value.pool(build.kind)
        val used = HashSet<Long>()
        val chosen = ArrayList<Pair<DrawCard, String>>()
        for (ask in build.parts) {
            if (ask.bodyPart.isBlank() || ask.count < 1) continue
            val matching = pool.map { drawCard(it) }.filter { card ->
                if (build.mode == "TYPE") {
                    card.types.any { it.equals(ask.bodyPart, true) }
                } else {
                    matchesBodyPart(card.muscles.joinToString(", "), ask.bodyPart)
                }
            }
            val picked = if (build.mode == "TYPE") {
                spreadType(matching.filter { it.id !in used }, ask.count, Random)
            } else {
                takeExact(matching, ask.count, Random, used)
            }
            for (card in picked) {
                if (!used.add(card.id)) continue
                chosen += card to ask.bodyPart
            }
        }
        if (chosen.isEmpty()) return
        val ordered = orderDrawn(chosen.map { it.first }, build.kind, dragged = false)
        val groups = chosen.toMap()
        val lines = ordered.map { card ->
            StrengthLine(
                card.id,
                prescribedSets(card, ordered, build.kind),
                groups[card].orEmpty(),
                build.mode,
                key = lineKey(),
            )
        }
        strength = StrengthDraft(build.kind, LocalDate.now(), null, lines)
        open(Screen.Strength)
    }

    fun addPicked(exerciseId: Long) {
        if (snap.value.exercises.none { it.id == exerciseId }) return
        val added = build.picks + StrengthLine(exerciseId, openingDraftSets(), groupKind = "PICK")
        build = build.copy(picks = arrangePicks(added, reorder = !build.picksDragged))
    }

    fun removePicked(index: Int) {
        val next = build.picks.filterIndexed { i, _ -> i != index }
        build = build.copy(
            picks = arrangePicks(next, reorder = !build.picksDragged),
            picksDragged = build.picksDragged && next.isNotEmpty(),
        )
        pickDrag = null
    }

    fun beginPickDrag(index: Int) {
        if (index in build.picks.indices) pickDrag = index
    }

    fun dragPicked(step: Int): Boolean {
        val from = pickDrag ?: return false
        if (step == 0 || build.picks.isEmpty()) return false
        val to = (from + step).coerceIn(0, build.picks.lastIndex)
        if (to == from) return false
        val next = moveItem(build.picks, from, to)
        build = build.copy(picks = arrangePicks(next, reorder = false), picksDragged = true)
        pickDrag = to
        return true
    }

    fun endPickDrag() {
        pickDrag = null
    }

    fun movePicked(from: Int, to: Int) {
        val next = moveItem(build.picks, from, to)
        val dragged = build.picksDragged || next !== build.picks
        build = build.copy(
            picks = if (next === build.picks) next else arrangePicks(next, reorder = false),
            picksDragged = dragged,
        )
        pickDrag = null
    }

    fun setPickReps(line: Int, set: Int, reps: Int) {
        editPick(line, set) { it.copy(reps = reps.coerceAtLeast(0)) }
    }

    fun setPickWeight(line: Int, set: Int, weight: Double?) {
        editPick(line, set) { it.copy(weightKg = weight) }
    }

    fun addPickSet(line: Int) {
        val picks = build.picks.toMutableList()
        val row = picks.getOrNull(line) ?: return
        picks[line] = row.copy(sets = row.sets + DraftSet(8, null), setsEdited = true)
        build = build.copy(picks = picks)
    }

    fun replacePickedSets(index: Int, sets: List<DraftSet>) {
        val picks = build.picks.toMutableList()
        val line = picks.getOrNull(index) ?: return
        picks[index] = line.copy(sets = exactSets(line.sets, sets), setsEdited = true)
        build = build.copy(picks = picks)
    }

    fun removePickSet(line: Int, set: Int) {
        val picks = build.picks.toMutableList()
        val row = picks.getOrNull(line) ?: return
        picks[line] = row.copy(sets = row.sets.filterIndexed { index, _ -> index != set }, setsEdited = true)
        build = build.copy(picks = picks)
    }

    private fun editPick(line: Int, set: Int, change: (DraftSet) -> DraftSet) {
        val picks = build.picks.toMutableList()
        val row = picks.getOrNull(line) ?: return
        val sets = row.sets.toMutableList()
        val current = sets.getOrNull(set) ?: return
        sets[set] = change(current)
        picks[line] = row.copy(sets = sets, setsEdited = true)
        build = build.copy(picks = picks)
    }

    fun openPicked() {
        if (build.picks.isEmpty()) return
        if (busy()) return
        val draft = StrengthDraft(
            build.kind,
            LocalDate.now(),
            null,
            build.picks.map { line -> line.copy(sets = line.sets.map { it.copy() }, groupKind = "PICK") },
        )
        activeMode = "STRENGTH"
        strength = withKind(draft.copy(started = true, focus = 0))
        open(Screen.Strength)
    }

    fun startFocusedWorkout() {
        val draft = strength ?: return
        if (draft.lines.isEmpty() || draft.logging) return
        if (draft.started) return
        if (busy()) return
        activeMode = "STRENGTH"
        strength = draft.copy(started = true, focus = 0)
    }

    fun rememberFocus(index: Int) {
        val draft = strength ?: return
        if (!draft.started || index !in draft.lines.indices || draft.focus == index) return
        strength = draft.copy(focus = index)
    }

    fun stepStrength(delta: Int) {
        val draft = strength ?: return
        val count = draft.lines.size
        if (count == 0 || delta == 0) return
        var index = draft.focus
        repeat(kotlin.math.abs(delta)) {
            index = stepExercise(index, count, delta > 0)
        }
        if (index != draft.focus) strength = draft.copy(focus = index)
    }

    fun beginDraftDrag(index: Int) {
        val draft = strength ?: return
        if (index in draft.lines.indices) draftDrag = index
    }

    fun dragDraft(step: Int): Boolean {
        val from = draftDrag ?: return false
        val draft = strength ?: return false
        if (step == 0 || draft.lines.isEmpty()) return false
        val to = (from + step).coerceIn(0, draft.lines.lastIndex)
        if (to == from) return false
        val lines = moveItem(draft.lines, from, to)
        val focus = when (draft.focus) {
            from -> to
            in minOf(from, to)..maxOf(from, to) -> draft.focus + if (from < to) -1 else 1
            else -> draft.focus
        }
        strength = draft.copy(lines = lines, focus = focus.coerceIn(0, lines.lastIndex))
        draftDrag = to
        return true
    }

    fun endDraftDrag() {
        draftDrag = null
    }

    fun moveDraft(from: Int, to: Int) {
        val draft = strength ?: return
        val lines = moveItem(draft.lines, from, to)
        if (lines === draft.lines) return
        val focus = when (draft.focus) {
            from -> to
            in minOf(from, to)..maxOf(from, to) -> draft.focus + if (from < to) -1 else 1
            else -> draft.focus
        }
        strength = draft.copy(lines = lines, focus = focus.coerceIn(0, lines.lastIndex))
        draftDrag = null
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
        lines[line] = row.copy(sets = sets, setsEdited = true)
        strength = draft.copy(lines = lines)
    }

    fun addDraftSet(line: Int) {
        val draft = strength ?: return
        val lines = draft.lines.toMutableList()
        val row = lines.getOrNull(line) ?: return
        lines[line] = row.copy(sets = row.sets + DraftSet(8, null), setsEdited = true)
        strength = draft.copy(lines = lines)
    }

    fun replaceDraftSets(line: Int, sets: List<DraftSet>) {
        val draft = strength ?: return
        val lines = draft.lines.toMutableList()
        val row = lines.getOrNull(line) ?: return
        lines[line] = row.copy(sets = exactSets(row.sets, sets), setsEdited = true)
        strength = draft.copy(lines = lines)
    }

    fun removeDraftSet(line: Int, set: Int) {
        val draft = strength ?: return
        val lines = draft.lines.toMutableList()
        val row = lines.getOrNull(line) ?: return
        lines[line] = row.copy(sets = row.sets.filterIndexed { index, _ -> index != set }, setsEdited = true)
        strength = draft.copy(lines = lines)
    }

    fun removeDraftLine(line: Int) {
        val draft = strength ?: return
        val lines = draft.lines.filterIndexed { index, _ -> index != line }
        val focus = when {
            lines.isEmpty() -> 0
            draft.focus > line -> draft.focus - 1
            draft.focus == line -> draft.focus.coerceAtMost(lines.lastIndex)
            else -> draft.focus
        }
        strength = withKind(
            draft.copy(
                lines = lines,
                focus = focus,
                started = draft.started && lines.isNotEmpty(),
            ),
        )
        if (activeMode == "STRENGTH" && !strengthLive()) releaseStrength()
        draftDrag = null
        if (stack.lastOrNull() == Screen.Picker) back()
    }

    fun lineChoices(line: Int): List<com.vatsyahub.workout.data.Exercise> {
        val draft = strength ?: return emptyList()
        val row = draft.lines.getOrNull(line) ?: return snap.value.pool(draft.kind)
        if (row.groupKind == "PICK") return snap.value.strengthLibrary()
        val pool = snap.value.pool(draft.kind)
        return when {
            row.groupKind == "TYPE" && row.group.isNotBlank() ->
                pool.filter { exercise -> snap.value.typesOf(exercise.id).any { it.name.equals(row.group, true) } }
            row.group.isNotBlank() ->
                pool.filter { matchesBodyPart(it.bodyPart, row.group) }
            else -> pool
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
        return pool.filter { matchesBodyPart(it.bodyPart, part) }.map { it.id }
    }

    fun rerollDraftLine(line: Int) {
        val draft = strength ?: return
        val row = draft.lines.getOrNull(line) ?: return
        if (row.groupKind == "PICK") return
        val ids = rerollIds(draft.kind, row)
        if (ids.isEmpty()) return
        val pool = ids.filter { id -> draft.lines.none { it.exerciseId == id && it.key != row.key } }
        val placed = rerollInSlot(draft.lines.map { it.exerciseId }, line, pool, row.shownIds, Random) ?: return
        val lines = draft.lines.toMutableList()
        lines[line] = row.copy(exerciseId = placed.ids[line], setsEdited = false, shownIds = placed.shown)
        val cards = lines.mapNotNull { lineCard(it) }
        val refreshed = lines.map { line ->
            val card = cards.find { it.key == line.key } ?: return@map line
            if (line.key != row.key || line.setsEdited) line else line.copy(sets = prescribedSets(card, cards, draft.kind))
        }
        strength = draft.copy(lines = refreshed)
    }

    fun rerollDraftAll() {
        val draft = strength ?: return
        val picks = draft.lines.filter { it.groupKind == "PICK" }
        val used = picks.map { it.exerciseId }.toMutableSet()
        val redrawn = ArrayList<StrengthLine>()
        val groups = draft.lines.filter { it.groupKind != "PICK" }.groupBy { it.groupKind to it.group }
        for ((_, rows) in groups) {
            val sample = rows.first()
            val pool = rerollIds(draft.kind, sample).mapNotNull { id ->
                snap.value.exercises.find { it.id == id }?.let { drawCard(it) }
            }.filter { it.id !in used }
            val picked = if (sample.groupKind == "TYPE") {
                spreadType(pool, rows.size, Random)
            } else {
                takeExact(pool, rows.size, Random, used)
            }
            for (card in picked) {
                if (!used.add(card.id)) continue
                redrawn += StrengthLine(card.id, emptyList(), sample.group, sample.groupKind)
            }
        }
        val combined = redrawn + picks
        val cards = combined.mapNotNull { lineCard(it) }
        val orderedCards = drawnOrder(cards, draft.kind, dragged = false)
        val lines = orderedCards.map { card ->
            val line = combined.first { it.key == card.key }
            if (line.groupKind == "PICK" && line.setsEdited) line
            else line.copy(sets = prescribedSets(card, orderedCards, draft.kind), setsEdited = line.setsEdited && line.groupKind == "PICK")
        }
        strength = draft.copy(lines = lines, focus = draft.focus.coerceIn(0, (lines.size - 1).coerceAtLeast(0)))
    }

    fun saveStrength() {
        val draft = withKind(strength ?: return)
        if (draft.lines.isEmpty()) return
        strength = draft
        viewModelScope.launch {
            val id = repo.saveStrength(
                draft.kind,
                draft.day,
                draft.durationSec,
                draft.lines.map { line -> line.exerciseId to line.sets.map { it.reps to it.weightKg } },
            )
            activeMode = null
            strength = null
            sessionGate.withLock {
                if (activeMode == null) repo.clearActive()
            }
            showLog(id)
        }
    }

    fun saveBuildAsPlan() {
        val current = build
        val loaded = snap.value
        val draft = if (current.mode == "PICK") {
            if (current.picks.isEmpty()) return
            val usages = current.picks.map { line ->
                loaded.exercises.find { it.id == line.exerciseId }?.usage.orEmpty()
            }
            BuilderDraft(
                kind = classifyPickedKind(usages, current.kind),
                mode = "PICK",
                lines = current.picks.map { line -> planLine(loaded, line) },
            )
        } else {
            val counts = current.parts.map { PlanCount(it.bodyPart, it.count, current.mode) }
            if (counts.none { it.count > 0 }) return
            BuilderDraft(kind = current.kind, mode = current.mode, counts = counts)
        }
        storePlan(draft)
    }

    fun saveStrengthAsPlan() {
        val draft = strength ?: return
        if (draft.logging || draft.lines.isEmpty()) return
        storePlan(
            BuilderDraft(
                kind = draft.kind,
                mode = "LINES",
                lines = draft.lines.map { line -> planLine(snap.value, line) },
            ),
        )
    }

    fun saveHiitAsPlan() {
        val draft = hiit ?: return
        if (draft.manual) return
        val loaded = snap.value
        storePlan(
            BuilderDraft(
                kind = "HIIT",
                mode = "HIIT",
                hiit = PlanHiit(
                    workSec = draft.workSec,
                    restSec = draft.restSec,
                    breakSec = draft.breakSec,
                    warmupSec = draft.warmupSec,
                    cooldownSec = draft.cooldownSec,
                    template = draft.template.map { PlanPass(it.kind, it.times) },
                    sets = draft.sets.map { set ->
                        PlanHiitSet(
                            exerciseIds = set.exerciseIds,
                            names = set.exerciseIds.map { id -> loaded.exercises.find { it.id == id }?.name.orEmpty() },
                            passes = set.passes.map { PlanPass(it.kind, it.times) },
                            shared = set.shared,
                            splitSeconds = set.splitSeconds,
                            breakSeconds = set.breakSeconds,
                        )
                    },
                ),
            ),
        )
    }

    fun startPlan(id: Long) {
        if (busy()) return
        val row = snap.value.plans.find { it.id == id } ?: return
        val body = decodePlan(row.body) ?: return
        val kept = WorkoutBook(plans = listOf(com.vatsyahub.workout.domain.PlanRecord(row.id, row.kind, body)))
            .openPlan(row.id)
        if (kept is com.vatsyahub.workout.domain.StartResult.Rejected) {
            notice = kept.message
            return
        }
        when {
            body.hiit != null -> {
                hiit = hiitFromPlan(body.hiit)
                startClock()
            }
            body.lines.isNotEmpty() -> {
                activeMode = "STRENGTH"
                strength = StrengthDraft(
                    kind = row.kind,
                    day = LocalDate.now(),
                    durationSec = null,
                    lines = body.lines.map { it.toStrengthLine() },
                    started = true,
                    focus = 0,
                )
                open(Screen.Strength)
            }
            body.counts.any { it.count > 0 } -> {
                val mode = body.counts.firstOrNull { it.mode.isNotBlank() }?.mode ?: body.mode
                strength = null
                build = BuildState(row.kind, body.counts.map { PartAsk(it.label, it.count) }, mode)
                drawBuild()
                val drafted = strength
                if (drafted != null && drafted.lines.isNotEmpty()) {
                    activeMode = "STRENGTH"
                    strength = drafted.copy(started = true, focus = 0)
                } else {
                    open(Screen.Build)
                }
            }
        }
    }

    private fun withKind(draft: StrengthDraft): StrengthDraft {
        if (draft.lines.none { it.groupKind == "PICK" } && draft.kind != "MIXED") return draft
        val usages = draft.lines.map { line ->
            snap.value.exercises.find { it.id == line.exerciseId }?.usage.orEmpty()
        }
        return draft.copy(kind = classifyPickedKind(usages, draft.kind))
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
        val warmup = last?.warmupSec ?: 120
        val cooldown = last?.cooldownSec ?: 300
        hiit = HiitDraft(LocalDate.now(), work, rest, breakSec, template, sets, manual, warmup, cooldown)
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
            "HEAVY", "BODYWEIGHT", "MIXED" -> {
                val plan = repeatStrength(loggedSession(loaded, workout))
                val lines = plan.lines.map { line ->
                    StrengthLine(line.exerciseId, line.sets.map { DraftSet(it.reps, it.weightKg) })
                }
                if (lines.isNotEmpty()) activeMode = "STRENGTH"
                strength = StrengthDraft(
                    plan.kind,
                    LocalDate.now(),
                    plan.durationSec,
                    lines,
                    started = lines.isNotEmpty(),
                    focus = 0,
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
                    workout.warmupSec ?: 120,
                    workout.cooldownSec ?: 300,
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
        val lines = if (workout.kind == "HEAVY" || workout.kind == "BODYWEIGHT" || workout.kind == "MIXED") {
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

    fun setWarmup(seconds: Int) {
        hiit = hiit?.copy(warmupSec = seconds.coerceAtLeast(0))
    }

    fun setCooldown(seconds: Int) {
        hiit = hiit?.copy(cooldownSec = seconds.coerceAtLeast(0))
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
        val kept = set.exerciseIds.mapIndexedNotNull { index, id ->
            if (index == line) null else id to set.shownIds.getOrElse(index) { emptySet() }
        }
        sets[setIndex] = set.copy(
            exerciseIds = kept.map { it.first },
            shownIds = kept.map { it.second },
        ).aligned(draft.workSec, set.breakSeconds)
        hiit = draft.copy(sets = sets)
        back()
    }

    fun rerollHiit(setIndex: Int, line: Int) {
        val draft = hiit ?: return
        val pool = snap.value.pool("HIIT").map { it.id }
        val sets = draft.sets.toMutableList()
        val set = sets.getOrNull(setIndex) ?: return
        if (line !in set.exerciseIds.indices) return
        val placed = rerollInSlot(
            set.exerciseIds,
            line,
            pool,
            set.shownIds.getOrElse(line) { emptySet() },
            Random,
        ) ?: return
        val shown = List(placed.ids.size) { index ->
            if (index == line) placed.shown else set.shownIds.getOrElse(index) { emptySet() }
        }
        sets[setIndex] = set.copy(exerciseIds = placed.ids, shownIds = shown).aligned(draft.workSec, set.breakSeconds)
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
                set.copy(exerciseIds = drawn, shownIds = drawn.map { setOf(it) }).aligned(draft.workSec, set.breakSeconds)
            },
        )
    }

    fun swapHiit(setIndex: Int, line: Int, exerciseId: Long) {
        val draft = hiit ?: return
        val sets = draft.sets.toMutableList()
        val set = sets.getOrNull(setIndex) ?: return
        if (line !in set.exerciseIds.indices) return
        val ids = set.exerciseIds.toMutableList()
        val previous = ids[line]
        ids[line] = exerciseId
        val shown = List(ids.size) { index ->
            val prior = set.shownIds.getOrElse(index) { emptySet() }
            if (index == line) prior + previous + exerciseId else prior
        }
        sets[setIndex] = set.copy(exerciseIds = ids, shownIds = shown)
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
        if (clock != null) {
            open(Screen.Clock)
            return
        }
        val draft = hiit ?: return
        if (draft.sets.all { it.exerciseIds.isEmpty() }) return
        if (busy()) return
        activeMode = "HIIT"
        viewModelScope.launch {
            val id = repo.startHiit(
                draft.day,
                draft.workSec,
                draft.restSec,
                draft.breakSec,
                draft.sets.map { it.passes to it.exerciseIds },
                draft.warmupSec,
                draft.cooldownSec,
            )
            gate.withLock {
                cues = cuesFromDraft(draft)
                cueIndex = 0
                clockWorkoutId = id
                awaitingStart = true
                startCue(play = false)
                runningClock = false
                publishClock()
                persistClock()
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
                draft.warmupSec,
                draft.cooldownSec,
            )
            hiit = null
            showLog(id)
        }
    }

    fun pauseClock() {
        viewModelScope.launch {
            gate.withLock {
                if (clock == null) return@withLock
                if (awaitingStart) {
                    awaitingStart = false
                    runningClock = true
                    cueEndElapsed = SystemClock.elapsedRealtime() + remainingMs
                    val cue = cues.getOrNull(cueIndex)
                    if (cue != null) playCueStart(cue.phase)
                    publishClock()
                    persistClock()
                    return@withLock
                }
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
        if (timer != null) {
            open(Screen.OutingTimer)
            return
        }
        if (busy()) return
        activeMode = "OUTING"
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
        activeMode = null
        timer = null
        timerRunning = false
        viewModelScope.launch {
            sessionGate.withLock {
                if (activeMode == null) repo.clearActive()
            }
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
                val group = when {
                    row.groupKind == "TYPE" -> row.group
                    row.group.isNotBlank() -> row.group
                    else -> chosenPart
                }
                lines[request.line] = row.copy(
                    exerciseId = id,
                    group = group,
                    shownIds = row.shownIds + row.exerciseId + id,
                )
                strength = withKind(draft.copy(lines = lines))
            }
            is Picker.PickSwap -> {
                val picks = build.picks.toMutableList()
                val row = picks.getOrNull(request.line) ?: return
                picks[request.line] = row.copy(exerciseId = id)
                build = build.copy(picks = picks)
            }
            is Picker.HiitSwap -> swapHiit(request.set, request.line, id)
            is Picker.AddStrength -> {
                if (request.workoutId == null) {
                    val draft = strength
                    if (draft != null) {
                        val handPicked = draft.kind == "MIXED" || draft.lines.any { it.groupKind == "PICK" }
                        val line = StrengthLine(
                            id,
                            openingDraftSets(),
                            groupKind = if (handPicked) "PICK" else "PART",
                        )
                        strength = withKind(draft.copy(lines = draft.lines + line))
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

    private fun exactSets(previous: List<DraftSet>, edited: List<DraftSet>): List<DraftSet> =
        savedExerciseSets(
            previous.map { SetLoad(it.reps, it.weightKg) },
            edited.map { SetLoad(it.reps, it.weightKg) },
        ).map { DraftSet(it.reps, it.weightKg) }

    fun replaceSavedSets(blockId: Long, sets: List<DraftSet>) = launch {
        val previous = snap.value.sets.filter { it.blockId == blockId }.sortedBy { it.position }
            .map { DraftSet(it.reps, it.weightKg) }
        val saved = exactSets(previous, sets)
        repo.replaceBlockSets(blockId, saved.map { it.reps to it.weightKg })
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
            draft.warmupSec,
            draft.cooldownSec,
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
            workout.warmupSec ?: 0,
            workout.cooldownSec ?: 0,
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
        if (play) playCueStart(cue.phase)
    }

    private fun playCueStart(phase: String) {
        when (phase) {
            PHASE_WORK -> tones.ring()
            PHASE_WARMUP -> tones.drum()
            PHASE_REST -> tones.whistle()
            PHASE_BREAK, PHASE_COOLDOWN -> tones.bell()
        }
    }

    private fun playCueBoundary(finished: String, next: String?) {
        val whistle = finished == PHASE_WARMUP || (finished == PHASE_WORK && next != PHASE_REST)
        if (whistle) tones.whistle()
        if (next == null) tones.horn()
    }

    private suspend fun completeCue(actualSec: Int) {
        val cue = cues.getOrNull(cueIndex) ?: return
        repo.recordSplit(
            clockWorkoutId,
            cueIndex,
            StoredCue(cue.phase, cue.setIndex, cue.exerciseId, cue.round, actualSec),
        )
        cueIndex += 1
        val next = cues.getOrNull(cueIndex)
        playCueBoundary(cue.phase, next?.phase)
        if (next == null) finishClock(recordCurrent = false) else startCue(true)
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
        activeMode = null
        if (id != 0L) {
            repo.finish(id)
            sessionGate.withLock {
                if (activeMode == null) repo.clearActive()
            }
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
        sessionGate.withLock {
            if (activeMode != "HIIT") return@withLock
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
            totalSec = cue.seconds.coerceAtLeast(1),
            fraction = if (cue.seconds <= 0) 0f else (remainingMs.toFloat() / (cue.seconds * 1000f)).coerceIn(0f, 1f),
            work = cue.phase == PHASE_WORK,
            running = runningClock,
            now = when (cue.phase) {
                PHASE_WORK -> cue.name
                PHASE_WARMUP -> "Warmup"
                PHASE_COOLDOWN -> "Cooldown"
                else -> "Rest"
            },
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
        sessionGate.withLock {
            if (activeMode != "OUTING") return@withLock
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
                tones.horn()
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
                    if (cue != null) playCueStart(cue.phase)
                }
            }
            activeMode = "HIIT"
            publishClock()
            persistClock()
            startClockLoop()
            open(Screen.Clock)
        } else if (active.mode == "OUTING" && active.kindId != null) {
            activeMode = "OUTING"
            timerKind = active.kindId
            timerAccumulated = active.accumulatedMs
            timerResumedWall = active.resumedAt
            timerRunning = active.running
            publishTimer()
            startTimerLoop()
            open(Screen.OutingTimer)
        } else if (active.mode == "STRENGTH") {
            val hold = active.payload?.let { decodeStrengthHold(it) }
            if (hold == null || hold.lines.isEmpty()) {
                repo.clearActive()
                return
            }
            strength = hold.toDraft()
            activeMode = "STRENGTH"
            open(Screen.Strength)
        }
    }

    private suspend fun persistLiveStrength() {
        val draft = strength
        sessionGate.withLock {
            if (activeMode != "STRENGTH") return@withLock
            if (draft == null || !draft.started || draft.logging || draft.lines.isEmpty()) return@withLock
            repo.saveActive(
                ActiveSession(
                    mode = "STRENGTH",
                    running = true,
                    index = draft.focus,
                    payload = encodeStrengthHold(holdOf(draft)),
                ),
            )
        }
    }

    private fun holdOf(draft: StrengthDraft): StrengthHold {
        val names = snap.value.exercises.associate { it.id to it.name }
        return StrengthHold(
            kind = draft.kind,
            day = draft.day.toString(),
            durationSec = draft.durationSec,
            started = true,
            focus = draft.focus,
            lines = draft.lines.map { line ->
                PlanLine(
                    exerciseId = line.exerciseId,
                    name = names[line.exerciseId].orEmpty(),
                    sets = line.sets.map { PlanSet(it.reps, it.weightKg) },
                    group = line.group,
                    groupKind = line.groupKind,
                )
            },
            shown = draft.lines.map { it.shownIds.toList() },
        )
    }

    private fun planLine(loaded: Snapshot, line: StrengthLine): PlanLine {
        val name = loaded.exercises.find { it.id == line.exerciseId }?.name.orEmpty()
        return PlanLine(
            exerciseId = line.exerciseId,
            name = name,
            sets = line.sets.map { PlanSet(it.reps, it.weightKg) },
            group = line.group,
            groupKind = line.groupKind,
        )
    }

    private fun storePlan(draft: BuilderDraft) {
        val loaded = snap.value
        val logs = loaded.workouts.map { LogRecord(it.id, it.finished, it.kind) }
        val saved = WorkoutBook(logs = logs).saveBuilderPlan(draft)
        if (saved.logs != logs) return
        val plan = saved.plans.last()
        viewModelScope.launch {
            repo.savePlan(plan.kind, planTitle(plan.kind, plan.body), encodePlan(plan.body))
        }
        planNote = draft.mode
    }

    private fun hiitFromPlan(plan: PlanHiit): HiitDraft {
        val template = plan.template.map { Pass(it.kind, it.times) }.ifEmpty { DefaultPasses }
        val sets = plan.sets.map { set ->
            val passes = set.passes.map { Pass(it.kind, it.times) }.ifEmpty { template }
            HiitSetDraft(
                passes = passes,
                shared = set.shared,
                exerciseIds = set.exerciseIds,
                splitSeconds = set.splitSeconds.ifEmpty {
                    List(expand(set.exerciseIds.size, passes).size) { plan.workSec }
                },
                breakSeconds = set.breakSeconds,
            )
        }
        return HiitDraft(
            day = LocalDate.now(),
            workSec = plan.workSec,
            restSec = plan.restSec,
            breakSec = plan.breakSec,
            template = template,
            sets = sets.ifEmpty { listOf(freshHiitSet(template, emptyList(), plan.workSec, plan.breakSec, true)) },
            manual = false,
            warmupSec = plan.warmupSec,
            cooldownSec = plan.cooldownSec,
        )
    }
}

private fun PlanLine.toStrengthLine(): StrengthLine =
    StrengthLine(
        exerciseId = exerciseId,
        sets = sets.map { DraftSet(it.reps, it.weightKg) }.ifEmpty { openingDraftSets() },
        group = group,
        groupKind = groupKind,
        setsEdited = true,
    )

private fun StrengthHold.toDraft(): StrengthDraft =
    StrengthDraft(
        kind = kind,
        day = runCatching { LocalDate.parse(day) }.getOrElse { LocalDate.now() },
        durationSec = durationSec,
        lines = lines.mapIndexed { index, line ->
            line.toStrengthLine().copy(shownIds = shown.getOrElse(index) { emptyList() }.toSet())
        },
        started = true,
        focus = focus.coerceAtLeast(0),
    )

fun exerciseKey(id: Long): String = "ex:$id"
