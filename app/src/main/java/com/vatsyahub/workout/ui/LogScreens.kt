package com.vatsyahub.workout.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.vatsyahub.workout.data.Snapshot
import com.vatsyahub.workout.data.Workout
import com.vatsyahub.workout.data.blockName
import com.vatsyahub.workout.data.displayName
import com.vatsyahub.workout.data.localDay
import com.vatsyahub.workout.data.marks
import com.vatsyahub.workout.data.passesOf
import com.vatsyahub.workout.data.weekWorkouts
import com.vatsyahub.workout.domain.PHASE_BREAK
import com.vatsyahub.workout.domain.PHASE_WORK
import com.vatsyahub.workout.domain.bestsReport
import com.vatsyahub.workout.domain.dayText
import com.vatsyahub.workout.domain.exerciseReport
import com.vatsyahub.workout.domain.SetLoad
import com.vatsyahub.workout.domain.formatBreak
import com.vatsyahub.workout.domain.formatDuration
import com.vatsyahub.workout.domain.formatKm
import com.vatsyahub.workout.domain.formatSets
import com.vatsyahub.workout.domain.parseDuration
import com.vatsyahub.workout.domain.patternLabel
import com.vatsyahub.workout.domain.weekStart
import com.vatsyahub.workout.domain.weeklyReport
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun historyKind(workout: Workout, snap: Snapshot): String = when (workout.kind) {
    "HEAVY" -> "Heavy"
    "HIIT" -> "HIIT"
    "OUTING" -> snap.kinds.find { it.id == workout.activityKindId }?.name ?: "Outing"
    else -> "Bodyweight"
}

internal fun historyDetail(workout: Workout, snap: Snapshot): String {
    val day = dayText(localDay(workout.performedAt))
    return when (workout.kind) {
        "HIIT" -> {
            val count = snap.blocks.count { it.workoutId == workout.id }
            val exercises = if (count == 1) "1 exercise" else "$count exercises"
            val time = workout.durationSec?.let { plainTime(it) }
                ?: "${workout.workSec ?: 0} seconds on, ${workout.restSec ?: 0} seconds rest"
            "$day, $exercises, $time"
        }
        "OUTING" -> {
            val distance = workout.distanceM?.let { formatKm(it) }
            val time = workout.durationSec?.let { plainTime(it) }
            listOfNotNull(day, distance, time).joinToString(", ")
        }
        else -> {
            val blocks = snap.blocks.filter { it.workoutId == workout.id }
            val ids = blocks.map { it.id }.toSet()
            val reps = snap.sets.filter { it.blockId in ids }.sumOf { it.reps }
            val exercises = if (blocks.size == 1) "1 exercise" else "${blocks.size} exercises"
            val repText = if (reps == 1) "1 rep" else "$reps reps"
            "$day, $exercises, $repText"
        }
    }
}

private fun plainTime(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    val secs = safe % 60
    return when {
        hours > 0 && minutes == 0 && secs == 0 -> if (hours == 1) "1 hour" else "$hours hours"
        secs == 0 && hours == 0 -> if (minutes == 1) "1 minute" else "$minutes minutes"
        else -> formatDuration(safe)
    }
}

private fun weekCard(text: String): Pair<String, String> {
    val count = text.substringBefore(' ').toIntOrNull()
    return when {
        count != null && text == "$count this week" -> "This week" to (if (count == 1) "1 workout" else "$count workouts")
        count != null && text == "$count last week" -> "Last week" to (if (count == 1) "1 workout" else "$count workouts")
        else -> "This week" to text
    }
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
                Text(
                    "Finished sessions from this week. Earlier months are below.",
                    color = MaterialTheme.colorScheme.secondary,
                )
                SectionLabel("This week")
                if (week.isEmpty()) {
                    Panel {
                        Text("Nothing logged this week.")
                    }
                }
            }
            items(week, key = { it.id }) { workout ->
                WorkoutCard(historyKind(workout, snap), historyDetail(workout, snap), favourite = workout.favourite) {
                    model.open(Screen.LogDetail(workout.id))
                }
            }
            if (months.isNotEmpty()) {
                item { SectionLabel("Earlier") }
            }
            items(months, key = { "${it.year}-${it.monthValue}" }) { month ->
                WorkoutCard(
                    month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)),
                    "Earlier sessions",
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
    val title = selected.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
    ListPage {
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Panel {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Text("Earlier sessions", color = MaterialTheme.colorScheme.secondary)
                }
            }
            items(logs, key = { it.id }) { workout ->
                WorkoutCard(historyKind(workout, snap), historyDetail(workout, snap), favourite = workout.favourite) {
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
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (heavy) "Heavy" else "Bodyweight",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                FavouriteMark(workout.favourite) { model.setFavourite(workout.id, !workout.favourite) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                StatTile("Date", dayText(localDay(workout.performedAt)), Modifier.weight(1f), chooseDate)
                StatTile("Exercises", blocks.size.toString(), Modifier.weight(1f))
                StatTile("Reps", reps.toString(), Modifier.weight(1f))
            }
            Text(
                "Time",
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 8.dp),
            )
            Editable(
                workout.durationSec?.let { formatDuration(it) } ?: "Add a time",
                durationRaw(workout.durationSec),
                style = MaterialTheme.typography.titleLarge,
                number = true,
            ) { text ->
                model.changeDuration(workout.id, text.trim().ifEmpty { null }?.let { parseDuration(it, true) })
            }
        }
        if (blocks.isEmpty()) {
            Panel { Text("No exercises in this session.") }
        }
        blocks.forEach { block ->
            val exercise = snap.exercises.find { it.id == block.exerciseId }
            val part = exercise?.bodyPart?.takeIf { it.isNotBlank() } ?: "No body part"
            var removing by remember(block.id) { mutableStateOf(false) }
            Panel {
                Text("Exercise", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge)
                Text(snap.blockName(block), style = MaterialTheme.typography.titleLarge)
                Text(
                    "Body part",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(part, color = MaterialTheme.colorScheme.secondary)
                val sets = snap.sets.filter { it.blockId == block.id }.sortedBy { it.position }
                CompactSets(formatSets(sets.map { SetLoad(it.reps, it.weightKg) }, heavy), heavy) {
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
                }
                if (removing) {
                    QuietButton("Remove exercise", MaterialTheme.colorScheme.error) { model.removeSavedBlock(block.id) }
                } else {
                    QuietButton("Remove") { removing = true }
                }
                QuietButton("Add set") { model.addSavedSet(block.id) }
            }
        }
        QuietButton("Add exercise") { model.openPicker(Picker.AddStrength(workout.id)) }
        QuietButton("Delete", MaterialTheme.colorScheme.error) { model.deleteWorkout(workout.id) }
        QuietButton("Repeat") { model.repeat(workout.id) }
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
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("HIIT", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                FavouriteMark(workout.favourite) { model.setFavourite(workout.id, !workout.favourite) }
            }
            StatTile("Date", dayText(localDay(workout.performedAt)), Modifier.fillMaxWidth(), chooseDate)
            StatTile("Pattern", pattern, Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Work", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge)
                    Editable(
                        "${workout.workSec ?: 0} s",
                        (workout.workSec ?: 0).toString(),
                        style = MaterialTheme.typography.titleMedium,
                        number = true,
                    ) { text ->
                        val work = text.toIntOrNull() ?: return@Editable
                        model.changeHiitTimes(workout.id, work, workout.restSec ?: 0, workout.breakSec ?: 0)
                    }
                }
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text("Rest", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge)
                    Editable(
                        "${workout.restSec ?: 0} s",
                        (workout.restSec ?: 0).toString(),
                        style = MaterialTheme.typography.titleMedium,
                        number = true,
                    ) { text ->
                        val rest = text.toIntOrNull() ?: return@Editable
                        model.changeHiitTimes(workout.id, workout.workSec ?: 0, rest, workout.breakSec ?: 0)
                    }
                }
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text("Break", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge)
                    Editable(
                        formatBreak(workout.breakSec ?: 0),
                        durationRaw(workout.breakSec),
                        style = MaterialTheme.typography.titleMedium,
                        number = true,
                    ) { text ->
                        val value = parseDuration(text, true) ?: return@Editable
                        model.changeHiitTimes(workout.id, workout.workSec ?: 0, workout.restSec ?: 0, value)
                    }
                }
            }
        }
        val indexes = if (setRows.isEmpty()) {
            snap.blocks.filter { it.workoutId == workout.id }.map { it.setIndex }.distinct().sorted()
        } else {
            setRows.map { it.position }
        }
        indexes.forEach { setIndex ->
            Panel {
                Text(
                    "Set ${setIndex + 1}",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.labelLarge,
                )
                val inSet = snap.blocks.filter { it.workoutId == workout.id && it.setIndex == setIndex }
                    .sortedBy { it.position }
                if (inSet.isEmpty()) {
                    Text("No exercises in this set.", color = MaterialTheme.colorScheme.secondary)
                }
                inSet.forEach { block ->
                    val exercise = snap.exercises.find { it.id == block.exerciseId }
                    val part = exercise?.bodyPart?.takeIf { it.isNotBlank() } ?: "No body part"
                    var removing by remember(block.id) { mutableStateOf(false) }
                    Text(
                        "Exercise",
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(snap.blockName(block), style = MaterialTheme.typography.titleMedium)
                    Text(part, color = MaterialTheme.colorScheme.secondary)
                    if (removing) {
                        QuietButton("Remove exercise", MaterialTheme.colorScheme.error) {
                            model.removeHiitBlock(workout.id, block.id, block.exerciseId, setIndex)
                        }
                    } else {
                        QuietButton("Remove") { removing = true }
                    }
                }
                QuietButton("Add exercise") { model.openPicker(Picker.AddHiit(setIndex, workout.id)) }
                val splits = snap.splits.filter { it.workoutId == workout.id && it.setIndex == setIndex && it.phase != "REST" }
                    .sortedBy { it.position }
                if (splits.isNotEmpty()) {
                    Text(
                        "Times",
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                splits.forEach { split ->
                    val name = if (split.phase == PHASE_WORK) {
                        snap.displayName(split.exerciseId, split.nameSnapshot)
                    } else {
                        "Rest"
                    }
                    val shown = if (split.phase == PHASE_BREAK) formatDuration(split.seconds) else "${split.seconds} s"
                    Text(
                        name,
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Editable(shown, split.seconds.toString(), style = MaterialTheme.typography.titleMedium, number = true) { text ->
                        text.toIntOrNull()?.let { model.changeSplit(split.id, it) }
                    }
                }
            }
        }
        QuietButton("Delete", MaterialTheme.colorScheme.error) { model.deleteWorkout(workout.id) }
        QuietButton("Repeat") { model.repeat(workout.id) }
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
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(kind?.name ?: "Outing", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                FavouriteMark(workout.favourite) { model.setFavourite(workout.id, !workout.favourite) }
            }
            StatTile("Date", dayText(localDay(workout.performedAt)), Modifier.fillMaxWidth(), chooseDate)
        }
        Panel {
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
        }
        if (kind?.shape == "CUSTOM") QuietButton("Add field") { naming = true }
        QuietButton("Delete", MaterialTheme.colorScheme.error) { model.deleteWorkout(workout.id) }
        QuietButton("Repeat") { model.repeat(workout.id) }
    }
}

@Composable
fun WeeklyScreen(model: AppModel) {
    val snap by model.snap.collectAsState()
    val lines = weeklyReport(LocalDate.now(), snap.weekWorkouts(), snap.marks())
    Page {
        if (lines.isEmpty()) {
            Panel { Text("Nothing to compare yet.") }
        }
        lines.forEach { line ->
            val (label, value) = weekCard(line.text)
            ReportCard(label, value)
        }
    }
}

@Composable
fun ReportExercisesScreen(model: AppModel) {
    val snap by model.snap.collectAsState()
    ListPage {
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Text(
                    "Tap an exercise to see its recent sessions.",
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                )
            }
            if (snap.exercises.isEmpty()) {
                item {
                    Panel { Text("No exercises yet.") }
                }
            }
            items(snap.exercises, key = { it.id }) { exercise ->
                WorkoutCard(
                    exercise.name,
                    exercise.bodyPart.ifBlank { "No body part" },
                ) { model.open(Screen.ExerciseReport(exerciseKey(exercise.id), exercise.name)) }
            }
        }
    }
}

@Composable
fun ExerciseReportScreen(model: AppModel, key: String, title: String) {
    val snap by model.snap.collectAsState()
    val lines = exerciseReport(LocalDate.now(), key, snap.marks())
    Page {
        Panel {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text("Sessions in the last three months.", color = MaterialTheme.colorScheme.secondary)
        }
        if (lines.isEmpty()) {
            Panel { Text("Nothing logged for this exercise yet.") }
        }
        lines.forEach { line ->
            val id = line.workoutId
            if (id == null) {
                ReportCard("Best", line.text.removePrefix("Best · ").ifBlank { line.text })
            } else {
                val bits = line.text.split(" · ", limit = 2)
                ReportCard(bits.first(), bits.getOrElse(1) { line.text }) {
                    model.open(Screen.LogDetail(id))
                }
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
            item {
                Text(
                    "Best efforts from the last three months.",
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                )
            }
            if (lines.isEmpty()) {
                item { Panel { Text("No bests in the last three months.") } }
            }
            items(lines.size) { index ->
                val line = lines[index]
                val id = line.workoutId
                if (id == null) {
                    ReportCard("Best", line.text)
                } else {
                    ReportCard("Best", line.text) { model.open(Screen.LogDetail(id)) }
                }
            }
        }
    }
}

@Composable
private fun ReportCard(label: String, value: String, onClick: (() -> Unit)? = null) {
    val shape = Modifier
        .fillMaxWidth()
        .padding(vertical = 5.dp)
        .clip(RoundedCornerShape(16.dp))
        .background(MaterialTheme.colorScheme.surface)
    Column((if (onClick == null) shape else shape.clickable(onClick = onClick)).padding(16.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        Text(value, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
    }
}
