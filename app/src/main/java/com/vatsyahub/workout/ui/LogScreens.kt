package com.vatsyahub.workout.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vatsyahub.workout.data.Workout
import com.vatsyahub.workout.data.blockName
import com.vatsyahub.workout.data.displayName
import com.vatsyahub.workout.data.localDay
import com.vatsyahub.workout.data.marks
import com.vatsyahub.workout.data.passesOf
import com.vatsyahub.workout.data.summary
import com.vatsyahub.workout.data.weekWorkouts
import com.vatsyahub.workout.domain.PHASE_BREAK
import com.vatsyahub.workout.domain.PHASE_WORK
import com.vatsyahub.workout.domain.bestsReport
import com.vatsyahub.workout.domain.dayText
import com.vatsyahub.workout.domain.exerciseReport
import com.vatsyahub.workout.domain.formatBreak
import com.vatsyahub.workout.domain.formatDuration
import com.vatsyahub.workout.domain.formatKg
import com.vatsyahub.workout.domain.parseDecimal
import com.vatsyahub.workout.domain.parseDuration
import com.vatsyahub.workout.domain.patternLabel
import com.vatsyahub.workout.domain.weekStart
import com.vatsyahub.workout.domain.weeklyReport
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun historyTitle(summary: String): String {
    val parts = summary.split(" · ")
    return parts.getOrElse(1) { summary }
}

private fun historyDetail(summary: String): String {
    val parts = summary.split(" · ")
    if (parts.size < 3) return parts.firstOrNull().orEmpty()
    return parts.first() + " · " + parts.drop(2).joinToString(" · ")
}

@Composable
fun LogsScreen(model: AppModel) {
    val snap by model.snap.collectAsState()
    val today = LocalDate.now()
    val start = weekStart(today)
    val end = start.plusDays(6)
    val week = snap.workouts.filter { workout ->
        workout.finished && !localDay(workout.performedAt).let { it.isBefore(start) || it.isAfter(end) }
    }.sortedByDescending { it.performedAt }
    val months = snap.workouts.filter { it.finished }
        .map { localDay(it.performedAt) }
        .filter { it.isBefore(start) || it.isAfter(end) }
        .map { YearMonth.from(it) }
        .distinct()
        .sortedDescending()
    ListPage {
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Text(
                    "History",
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
                SectionLabel("This week")
                if (week.isEmpty()) {
                    Panel {
                        Text("Nothing logged this week.")
                    }
                }
            }
            items(week, key = { it.id }) { workout ->
                val summary = snap.summary(workout)
                WorkoutCard(historyTitle(summary), historyDetail(summary)) {
                    model.open(Screen.LogDetail(workout.id))
                }
            }
            if (months.isNotEmpty()) {
                item { SectionLabel("Earlier") }
            }
            items(months, key = { "${it.year}-${it.monthValue}" }) { month ->
                WorkoutCard(
                    month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)),
                    "Older sessions",
                ) {
                    model.open(Screen.Month(month.year, month.monthValue))
                }
            }
            item { QuietButton("Reports") { model.open(Screen.Reports) } }
        }
    }
}

@Composable
fun MonthScreen(model: AppModel, year: Int, month: Int) {
    val snap by model.snap.collectAsState()
    val selected = YearMonth.of(year, month)
    val logs = snap.workouts.filter { workout ->
        workout.finished && YearMonth.from(localDay(workout.performedAt)) == selected
    }.sortedByDescending { it.performedAt }
    ListPage {
        LazyColumn(Modifier.fillMaxSize()) {
            item { Line(selected.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))) }
            items(logs, key = { it.id }) { workout ->
                val summary = snap.summary(workout)
                WorkoutCard(historyTitle(summary), historyDetail(summary)) {
                    model.open(Screen.LogDetail(workout.id))
                }
            }
        }
    }
}

@Composable
fun LogDetailScreen(model: AppModel, id: Long) {
    val snap by model.snap.collectAsState()
    val workout = snap.workouts.find { it.id == id } ?: return
    when (workout.kind) {
        "HIIT" -> HiitLog(model, workout)
        "OUTING" -> OutingLog(model, workout)
        else -> StrengthLog(model, workout)
    }
}

@Composable
private fun StrengthLog(model: AppModel, workout: Workout) {
    val snap by model.snap.collectAsState()
    val heavy = workout.kind == "HEAVY"
    val blocks = snap.blocks.filter { it.workoutId == workout.id }.sortedBy { it.position }
    val chooseDate = pickDate(localDay(workout.performedAt)) { model.changeDay(workout.id, it) }
    val reps = blocks.sumOf { block -> snap.sets.filter { it.blockId == block.id }.sumOf { it.reps } }
    Page {
        Word(dayText(localDay(workout.performedAt))) { chooseDate() }
        Line(if (heavy) "Heavy" else "Bodyweight")
        blocks.forEach { block ->
            var removing by remember(block.id) { mutableStateOf(false) }
            Word(snap.blockName(block)) { removing = !removing }
            if (removing) {
                Word("Remove") { model.removeSavedBlock(block.id) }
            }
            val sets = snap.sets.filter { it.blockId == block.id }.sortedBy { it.position }
            sets.forEachIndexed { index, set ->
                SetRow(
                    label = "Set ${index + 1}",
                    reps = set.reps,
                    weight = set.weightKg,
                    heavy = heavy,
                    onRemove = { model.removeSavedSet(set.id) },
                    onReps = { model.changeSet(set.id, it, set.weightKg) },
                    onWeight = { model.changeSet(set.id, set.reps, it) },
                )
            }
            Word("Set ${sets.size + 1}") { model.addSavedSet(block.id) }
        }
        Word("Exercise") { model.openPicker(Picker.AddStrength(workout.id)) }
        Line("${blocks.size} exercises")
        Line("$reps reps")
        Editable(
            workout.durationSec?.let { formatDuration(it) } ?: "Time",
            durationRaw(workout.durationSec),
            number = true,
        ) { text ->
            model.changeDuration(workout.id, text.trim().ifEmpty { null }?.let { parseDuration(it, true) })
        }
        Word("Delete") { model.deleteWorkout(workout.id) }
        Word("Start again") { model.again(workout.id) }
    }
}

@Composable
private fun HiitLog(model: AppModel, workout: Workout) {
    val snap by model.snap.collectAsState()
    val chooseDate = pickDate(localDay(workout.performedAt)) { model.changeDay(workout.id, it) }
    val setRows = snap.hiitSets.filter { it.workoutId == workout.id }.sortedBy { it.position }
    val labels = setRows.map { patternLabel(snap.passesOf(it.patternId)) }.distinct()
    val pattern = if (labels.size == 1) labels.first() else "Mixed"
    Page {
        Word(dayText(localDay(workout.performedAt))) { chooseDate() }
        Line("HIIT")
        Editable("${workout.workSec ?: 0} s", (workout.workSec ?: 0).toString(), number = true) { text ->
            val work = text.toIntOrNull() ?: return@Editable
            model.changeHiitTimes(workout.id, work, workout.restSec ?: 0, workout.breakSec ?: 0)
        }
        Editable("${workout.restSec ?: 0} s", (workout.restSec ?: 0).toString(), number = true) { text ->
            val rest = text.toIntOrNull() ?: return@Editable
            model.changeHiitTimes(workout.id, workout.workSec ?: 0, rest, workout.breakSec ?: 0)
        }
        Editable(formatBreak(workout.breakSec ?: 0), durationRaw(workout.breakSec), number = true) { text ->
            val value = parseDuration(text, true) ?: return@Editable
            model.changeHiitTimes(workout.id, workout.workSec ?: 0, workout.restSec ?: 0, value)
        }
        Line(pattern)
        val indexes = if (setRows.isEmpty()) {
            snap.blocks.filter { it.workoutId == workout.id }.map { it.setIndex }.distinct().sorted()
        } else {
            setRows.map { it.position }
        }
        indexes.forEach { setIndex ->
            Line("Set ${setIndex + 1}")
            snap.blocks.filter { it.workoutId == workout.id && it.setIndex == setIndex }
                .sortedBy { it.position }
                .forEach { block ->
                    var removing by remember(block.id) { mutableStateOf(false) }
                    Word(snap.blockName(block)) { removing = !removing }
                    if (removing) {
                        Word("Remove") { model.removeHiitBlock(workout.id, block.id, block.exerciseId, setIndex) }
                    }
                }
            Word("Exercise") { model.openPicker(Picker.AddHiit(setIndex, workout.id)) }
            snap.splits.filter { it.workoutId == workout.id && it.setIndex == setIndex && it.phase != "REST" }
                .sortedBy { it.position }
                .forEach { split ->
                    val name = if (split.phase == PHASE_WORK) {
                        snap.displayName(split.exerciseId, split.nameSnapshot)
                    } else {
                        "Rest"
                    }
                    val shown = if (split.phase == PHASE_BREAK) "$name ${formatDuration(split.seconds)}" else "$name ${split.seconds} s"
                    Editable(shown, split.seconds.toString(), number = true) { text ->
                        text.toIntOrNull()?.let { model.changeSplit(split.id, it) }
                    }
                }
        }
        Word("Delete") { model.deleteWorkout(workout.id) }
        Word("Start again") { model.again(workout.id) }
    }
}

@Composable
private fun OutingLog(model: AppModel, workout: Workout) {
    val snap by model.snap.collectAsState()
    val kind = snap.kinds.find { it.id == workout.activityKindId }
    var naming by remember { mutableStateOf(false) }
    var fieldName by remember { mutableStateOf("") }
    BackHandler(enabled = naming) { naming = false }
    if (naming && kind != null) {
        FormPage(onSave = {
            val trimmed = fieldName.trim()
            if (trimmed.isEmpty()) return@FormPage
            model.addField(kind.id, trimmed)
            fieldName = ""
            naming = false
        }) {
            Field("Field", fieldName) { fieldName = it }
        }
        return
    }
    val chooseDate = pickDate(localDay(workout.performedAt)) { model.changeDay(workout.id, it) }
    Page {
        Word(dayText(localDay(workout.performedAt))) { chooseDate() }
        Line(kind?.name ?: "Outing")
        OutingFields(
            shape = kind?.shape ?: "CUSTOM",
            durationSec = workout.durationSec,
            distanceM = workout.distanceM,
            gradient = workout.gradient,
            location = workout.location,
            bottomM = workout.bottomM,
            topM = workout.topM,
            bothWays = workout.bothWays,
            extras = snap.fields.filter { it.kindId == kind?.id }.map { field ->
                Triple(field.id, field.name, snap.values.find { it.workoutId == workout.id && it.fieldId == field.id }?.value.orEmpty())
            },
            onDuration = { seconds ->
                model.changeOuting(workout.id, workout.distanceM, workout.gradient, workout.location, workout.bottomM, workout.topM, workout.bothWays, seconds)
            },
            onDistance = { distance ->
                model.changeOuting(workout.id, distance, workout.gradient, workout.location, workout.bottomM, workout.topM, workout.bothWays, workout.durationSec)
            },
            onGradient = { gradient ->
                model.changeOuting(workout.id, workout.distanceM, gradient, workout.location, workout.bottomM, workout.topM, workout.bothWays, workout.durationSec)
            },
            onLocation = { location ->
                model.changeOuting(workout.id, workout.distanceM, workout.gradient, location, workout.bottomM, workout.topM, workout.bothWays, workout.durationSec)
            },
            onBottom = { bottom ->
                model.changeOuting(workout.id, workout.distanceM, workout.gradient, workout.location, bottom, workout.topM, workout.bothWays, workout.durationSec)
            },
            onTop = { top ->
                model.changeOuting(workout.id, workout.distanceM, workout.gradient, workout.location, workout.bottomM, top, workout.bothWays, workout.durationSec)
            },
            onWays = { ways ->
                model.changeOuting(workout.id, workout.distanceM, workout.gradient, workout.location, workout.bottomM, workout.topM, ways, workout.durationSec)
            },
            onExtra = { fieldId, value -> model.changeField(workout.id, fieldId, value) },
        )
        if (kind?.shape == "CUSTOM") Word("Field") { naming = true }
        Word("Delete") { model.deleteWorkout(workout.id) }
        Word("Start again") { model.again(workout.id) }
    }
}

@Composable
fun WeeklyScreen(model: AppModel) {
    val snap by model.snap.collectAsState()
    val lines = weeklyReport(LocalDate.now(), snap.weekWorkouts(), snap.marks())
    Page {
        lines.forEach { line -> Line(line.text) }
    }
}

@Composable
fun ReportExercisesScreen(model: AppModel) {
    val snap by model.snap.collectAsState()
    ListPage {
        LazyColumn(Modifier.fillMaxSize()) {
            items(snap.exercises, key = { it.id }) { exercise ->
                Word(exercise.name) { model.open(Screen.ExerciseReport(exerciseKey(exercise.id), exercise.name)) }
            }
        }
    }
}

@Composable
fun ExerciseReportScreen(model: AppModel, key: String, title: String) {
    val snap by model.snap.collectAsState()
    val lines = exerciseReport(LocalDate.now(), key, snap.marks())
    Page {
        Line(title)
        lines.forEach { line ->
            if (line.workoutId == null) {
                Line(line.text)
            } else {
                Word(line.text) { model.open(Screen.LogDetail(line.workoutId)) }
            }
        }
    }
}

@Composable
fun BestsScreen(model: AppModel) {
    val snap by model.snap.collectAsState()
    val lines = bestsReport(LocalDate.now(), snap.marks())
    ListPage {
        LazyColumn(Modifier.fillMaxSize()) {
            items(lines.size) { index ->
                val line = lines[index]
                val id = line.workoutId
                if (id == null) {
                    Text(line.text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 8.dp))
                } else {
                    Word(line.text) { model.open(Screen.LogDetail(id)) }
                }
            }
        }
    }
}

