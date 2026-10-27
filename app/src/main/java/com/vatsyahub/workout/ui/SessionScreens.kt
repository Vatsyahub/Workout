package com.vatsyahub.workout.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vatsyahub.workout.data.pool
import com.vatsyahub.workout.domain.EACH
import com.vatsyahub.workout.domain.PASS
import com.vatsyahub.workout.domain.Pass
import com.vatsyahub.workout.domain.coveredMetres
import com.vatsyahub.workout.domain.climbMetres
import com.vatsyahub.workout.domain.dayText
import com.vatsyahub.workout.domain.expand
import com.vatsyahub.workout.domain.formatBreak
import com.vatsyahub.workout.domain.formatDuration
import com.vatsyahub.workout.domain.formatKg
import com.vatsyahub.workout.domain.formatKm
import com.vatsyahub.workout.domain.formatMetres
import com.vatsyahub.workout.domain.formatPace
import com.vatsyahub.workout.domain.kmPaceSeconds
import com.vatsyahub.workout.domain.parseDecimal
import com.vatsyahub.workout.domain.parseDistanceToMeters
import com.vatsyahub.workout.domain.parseDuration
import com.vatsyahub.workout.domain.patternLabel
import com.vatsyahub.workout.domain.roundsOf
import com.vatsyahub.workout.domain.swimPaceSeconds

@Composable
fun StrengthScreen(model: AppModel) {
    val snap by model.snap.collectAsState()
    val draft = model.strength ?: return
    val heavy = draft.kind == "HEAVY"
    val chooseDate = pickDate(draft.day) { model.setDraftDay(it) }
    val exerciseCount = draft.lines.size
    val totalReps = draft.lines.sumOf { line -> line.sets.sumOf { it.reps } }
    ActionPage("Finish", { model.saveStrength() }) {
        Panel {
            Text(if (heavy) "Heavy" else "Bodyweight", style = MaterialTheme.typography.titleLarge)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                StatTile("Date", dayText(draft.day), Modifier.weight(1f), chooseDate)
                StatTile("Exercises", exerciseCount.toString(), Modifier.weight(1f))
                StatTile("Reps", totalReps.toString(), Modifier.weight(1f))
            }
            Text(
                "Time",
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 8.dp),
            )
            Editable(
                draft.durationSec?.let { formatDuration(it) } ?: "Add a time",
                durationRaw(draft.durationSec),
                style = MaterialTheme.typography.titleLarge,
                number = true,
            ) { text ->
                model.setDraftDuration(parseDuration(text, true))
            }
        }
        if (draft.lines.isEmpty()) {
            Panel {
                Text("Add the exercises in this session.")
            }
        }
        draft.lines.forEachIndexed { lineIndex, line ->
            val exercise = snap.exercises.find { it.id == line.exerciseId }
            Panel {
                Text("Exercise", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        Modifier
                            .weight(1f)
                            .clickable { model.openPicker(Picker.StrengthSwap(lineIndex)) },
                    ) {
                        Text(exercise?.name ?: "Choose an exercise", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Body part",
                            color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        Text(
                            exercise?.bodyPart?.takeIf { it.isNotBlank() } ?: "None",
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        if (line.groupKind == "TYPE" && line.group.isNotBlank()) {
                            Text(
                                "Workout type",
                                color = MaterialTheme.colorScheme.secondary,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                            Text(line.group, color = MaterialTheme.colorScheme.secondary)
                        }
                        Text("Tap the name to choose a replacement", color = MaterialTheme.colorScheme.secondary)
                    }
                    IconButton(onClick = { model.rerollDraftLine(lineIndex) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Reroll")
                    }
                    IconButton(onClick = { model.removeDraftLine(lineIndex) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                    }
                }
                SetTableHeader(heavy)
                line.sets.forEachIndexed { setIndex, set ->
                    SetRow(
                        label = "Set ${setIndex + 1}",
                        reps = set.reps,
                        weight = set.weightKg,
                        heavy = heavy,
                        onRemove = { model.removeDraftSet(lineIndex, setIndex) },
                        onReps = { model.setDraftReps(lineIndex, setIndex, it) },
                        onWeight = { model.setDraftWeight(lineIndex, setIndex, it) },
                    )
                }
                QuietButton("Add set") { model.addDraftSet(lineIndex) }
            }
        }
        QuietButton("Add exercise") { model.openPicker(Picker.AddStrength(null)) }
        QuietButton("Reroll all") { model.rerollDraftAll() }
    }
}

@Composable
fun SetRow(
    label: String,
    reps: Int,
    weight: Double?,
    heavy: Boolean,
    onRemove: () -> Unit,
    onReps: (Int) -> Unit,
    onWeight: (Double?) -> Unit,
) {
    var removing by remember { mutableStateOf(false) }
    val number = label.substringAfterLast(' ').ifBlank { label }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            number,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .weight(0.7f)
                .clickable { removing = !removing }
                .padding(vertical = 8.dp),
        )
        Box(Modifier.weight(1f)) {
            Editable(
                reps.toString(),
                reps.toString(),
                style = MaterialTheme.typography.titleMedium,
                number = true,
            ) { text ->
                text.toIntOrNull()?.let(onReps)
            }
        }
        if (heavy) {
            Box(Modifier.weight(1.1f)) {
                Editable(
                    weight?.let { formatKg(it) } ?: "kg",
                    weight?.let { trimNumber(it) }.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    number = true,
                ) { text ->
                    onWeight(text.trim().ifEmpty { null }?.let { parseDecimal(it) })
                }
            }
        }
    }
    if (removing) {
        QuietButton("Remove set", MaterialTheme.colorScheme.error, onRemove)
    }
}

@Composable
fun HiitScreen(model: AppModel) {
    val snap by model.snap.collectAsState()
    val draft = model.hiit ?: return
    val shared = draft.sets.all { it.shared }
    val chooseDate = pickDate(draft.day) { model.setHiitDay(it) }
    val action = if (draft.manual) "Save" else "Start"
    val patternName = if (shared) patternLabel(draft.template) else "Mixed"
    val roundCount = if (shared) roundsOf(draft.template).toString() else "Mixed"
    ActionPage(action, { if (draft.manual) model.saveManualHiit() else model.startClock() }) {
        Panel {
            Text("HIIT", style = MaterialTheme.typography.titleLarge)
            StatTile("Date", dayText(draft.day), Modifier.fillMaxWidth(), chooseDate)
            StatTile("Pattern", patternName, Modifier.fillMaxWidth()) { model.openPatterns(null) }
            StatTile("Rounds", roundCount, Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Work", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge)
                    Editable("${draft.workSec} s", draft.workSec.toString(), style = MaterialTheme.typography.titleMedium, number = true) { text ->
                        text.toIntOrNull()?.let { model.setWork(it) }
                    }
                }
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text("Rest", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge)
                    Editable("${draft.restSec} s", draft.restSec.toString(), style = MaterialTheme.typography.titleMedium, number = true) { text ->
                        text.toIntOrNull()?.let { model.setRest(it) }
                    }
                }
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text("Break", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge)
                    Editable(formatBreak(draft.breakSec), durationRaw(draft.breakSec), style = MaterialTheme.typography.titleMedium, number = true) { text ->
                        parseDuration(text, true)?.let { model.setBreak(it) }
                    }
                }
            }
        }
        draft.sets.forEachIndexed { setIndex, set ->
            Panel {
                Text(
                    "Set ${setIndex + 1}",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clickable { model.open(Screen.SetMenu(setIndex)) },
                )
                Text(
                    "Exercises",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Editable(
                    set.exerciseIds.size.toString(),
                    set.exerciseIds.size.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    number = true,
                ) { text ->
                    text.toIntOrNull()?.let { model.setHiitCount(setIndex, it) }
                }
                if (set.exerciseIds.isEmpty()) {
                    Text("No exercises in this set.", color = MaterialTheme.colorScheme.secondary)
                }
                set.exerciseIds.forEachIndexed { line, id ->
                    val exercise = snap.exercises.find { it.id == id }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { model.openPicker(Picker.HiitSwap(setIndex, line)) }
                            .padding(vertical = 6.dp),
                    ) {
                        Text(exercise?.name ?: "Exercise", style = MaterialTheme.typography.titleMedium)
                        val part = exercise?.bodyPart.orEmpty()
                        if (part.isNotBlank()) {
                            Text(part, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
                if (draft.manual) {
                    val slots = expand(set.exerciseIds.size, set.passes)
                    slots.forEachIndexed { bout, slot ->
                        val id = set.exerciseIds.getOrNull(slot)
                        val name = snap.exercises.find { it.id == id }?.name ?: "Exercise"
                        val seconds = set.splitSeconds.getOrElse(bout) { draft.workSec }
                        Text(
                            name,
                            color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        Editable("$seconds s", seconds.toString(), style = MaterialTheme.typography.titleMedium, number = true) { text ->
                            text.toIntOrNull()?.let { model.setBoutSeconds(setIndex, bout, it) }
                        }
                    }
                }
            }
        }
        QuietButton("Add set") { model.addHiitSet() }
        QuietButton("Reroll all") { model.rerollHiitAll() }
    }
}

@Composable
fun PatternPickScreen(model: AppModel) {
    val snap by model.snap.collectAsState()
    Page {
        snap.patterns.sortedBy { it.label }.forEach { pattern ->
            Word(pattern.label) { model.applyPattern(pattern.id) }
        }
        Word("Edit") { model.open(Screen.PatternEdit) }
    }
}

@Composable
fun PatternEditScreen(model: AppModel) {
    var rows by remember { mutableStateOf(model.currentPasses().map { PassRow(it.kind, it.times.toString()) }) }
    FormPage(onSave = {
        val passes = rows.mapNotNull { row ->
            val times = row.times.trim().toIntOrNull() ?: return@mapNotNull null
            if (times <= 0) null else Pass(row.kind, times)
        }
        model.updatePasses(passes.ifEmpty { listOf(Pass(PASS, 1)) })
        model.back()
    }) {
        rows.forEachIndexed { index, row ->
            Field(if (row.kind == EACH) "Each" else "Pass", row.times, number = true) { text ->
                rows = rows.toMutableList().also { it[index] = row.copy(times = text) }
            }
            if (rows.size > 1) {
                Word("Remove") {
                    rows = rows.filterIndexed { rowIndex, _ -> rowIndex != index }
                }
            }
        }
        Word("Pass") { rows = rows + PassRow(PASS, "1") }
        Word("Each") { rows = rows + PassRow(EACH, "2") }
    }
}

@Composable
fun SetMenuScreen(model: AppModel, index: Int) {
    val set = model.hiit?.sets?.getOrNull(index) ?: return
    Page {
        Word(patternLabel(set.passes)) { model.openPatterns(index) }
        if ((model.hiit?.sets?.size ?: 0) > 1) {
            Word("Remove") { model.removeHiitSet(index) }
        }
    }
}

@Composable
fun PickerScreen(model: AppModel) {
    val snap by model.snap.collectAsState()
    val request = model.picker
    Page {
        when (request) {
            is Picker.StrengthSwap -> {
                val line = model.strength?.lines?.getOrNull(request.line)
                val fromType = line?.groupKind == "TYPE" && line.group.isNotBlank()
                Text(
                    if (fromType) {
                        "Exercises in ${line?.group}. Tap one to use it here."
                    } else {
                        "Any exercise in this workout. Tap one to use it here."
                    },
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                )
                model.lineChoices(request.line)
                    .sortedWith(compareBy({ it.name.lowercase() }, { it.bodyPart.lowercase() }))
                    .forEach { exercise ->
                        WorkoutCard(
                            exercise.name,
                            if (exercise.bodyPart.isBlank()) "No body part" else exercise.bodyPart,
                        ) { model.pickExercise(exercise.id) }
                    }
            }
            is Picker.HiitSwap -> {
                Word("Reroll") {
                    model.rerollHiit(request.set, request.line)
                    model.back()
                }
                Word("Remove") { model.removeHiitLine(request.set, request.line) }
                snap.pool("HIIT").forEach { exercise ->
                    Word(exercise.name) { model.pickExercise(exercise.id) }
                }
            }
            is Picker.AddStrength -> {
                val kind = if (request.workoutId == null) {
                    model.strength?.kind ?: "HEAVY"
                } else {
                    snap.workouts.find { it.id == request.workoutId }?.kind ?: "HEAVY"
                }
                snap.pool(kind).forEach { exercise ->
                    Word("${exercise.name} · ${exercise.bodyPart}") { model.pickExercise(exercise.id) }
                }
            }
            is Picker.AddHiit -> {
                snap.pool("HIIT").forEach { exercise ->
                    Word(exercise.name) { model.pickExercise(exercise.id) }
                }
            }
            is Picker.BodyPart -> {
                snap.pool(model.build.kind).map { it.bodyPart }.distinct().sortedBy { it.lowercase() }.forEach { part ->
                    Word(part) { model.pickPart(part) }
                }
            }
            null -> Unit
        }
    }
}

@Composable
fun ClockScreen(model: AppModel) {
    val clock = model.clock ?: return
    KeepScreenOn()
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp),
    ) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(formatDuration(clock.remainingSec), style = MaterialTheme.typography.displayLarge)
            Text(clock.now, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 18.dp))
            if (clock.next.isNotBlank()) {
                Text(
                    clock.next,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 12.dp)) {
            Box(Modifier.fillMaxWidth()) {
                Control(Icons.Filled.SkipPrevious, "Back", 32.dp, Modifier.align(Alignment.CenterStart)) { model.backClock() }
                Control(Icons.Filled.SkipNext, "Forward", 32.dp, Modifier.align(Alignment.CenterEnd)) { model.forwardClock() }
                val playIcon = if (clock.running) Icons.Filled.Pause else Icons.Filled.PlayArrow
                val playLabel = if (clock.running) "Pause" else "Play"
                Control(playIcon, playLabel, 52.dp, Modifier.align(Alignment.Center)) { model.pauseClock() }
            }
            Control(Icons.Filled.Stop, "Stop", 32.dp, Modifier.align(Alignment.CenterHorizontally)) { model.stopClock() }
        }
    }
}

@Composable
fun OutingTimerScreen(model: AppModel) {
    val snap by model.snap.collectAsState()
    val timer = model.timer ?: return
    val name = snap.kinds.find { it.id == timer.kindId }?.name.orEmpty()
    KeepScreenOn()
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp),
    ) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            if (name.isNotBlank()) {
                Text(name, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.secondary)
            }
            Text(formatDuration(timer.elapsedSec), style = MaterialTheme.typography.displayLarge)
        }
        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val icon = if (timer.running) Icons.Filled.Pause else Icons.Filled.PlayArrow
            Control(icon, if (timer.running) "Pause" else "Play", 52.dp, Modifier) { model.pauseTimer() }
            Control(Icons.Filled.Stop, "Stop", 32.dp, Modifier) { model.stopTimer() }
        }
    }
}

@Composable
private fun Control(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    visual: Dp,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val filled = visual >= 48.dp
    Box(
        modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            modifier = Modifier.size(visual),
            tint = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

@Composable
fun OutingFormScreen(model: AppModel) {
    val snap by model.snap.collectAsState()
    val draft = model.outing ?: return
    val kind = snap.kinds.find { it.id == draft.kindId } ?: return
    var distance by remember { mutableStateOf(draft.distanceM?.let { trimNumber(it / 1000.0) }.orEmpty()) }
    var time by remember { mutableStateOf(durationRaw(draft.durationSec)) }
    var gradient by remember { mutableStateOf(draft.gradient?.let { trimNumber(it) }.orEmpty()) }
    var location by remember { mutableStateOf(draft.location.orEmpty()) }
    var bottom by remember { mutableStateOf(draft.bottomM?.let { trimNumber(it) }.orEmpty()) }
    var top by remember { mutableStateOf(draft.topM?.let { trimNumber(it) }.orEmpty()) }
    var bothWays by remember { mutableStateOf(draft.bothWays == true) }
    var extras by remember { mutableStateOf(draft.fields) }
    var fieldName by remember { mutableStateOf("") }
    val chooseDate = pickDate(draft.day) { picked -> model.outing = model.outing?.copy(day = picked) }
    FormPage(onSave = {
        val current = model.outing ?: return@FormPage
        val pendingField = fieldName.trim()
        if (kind.shape == "CUSTOM" && pendingField.isNotEmpty()) model.addField(kind.id, pendingField)
        model.outing = current.copy(
            durationSec = time.trim().ifEmpty { null }?.let { parseDuration(it, true) },
            distanceM = distance.trim().ifEmpty { null }?.let { parseDistanceToMeters(it) },
            gradient = gradient.trim().ifEmpty { null }?.let { parseDecimal(it) },
            location = location.trim().ifEmpty { null },
            bottomM = bottom.trim().ifEmpty { null }?.let { parseDecimal(it) },
            topM = top.trim().ifEmpty { null }?.let { parseDecimal(it) },
            bothWays = if (kind.shape == "HIKE") bothWays else current.bothWays,
            fields = extras,
        )
        model.saveOutingDraft()
    }) {
        Word(dayText(model.outing?.day ?: draft.day)) { chooseDate() }
        Line(kind.name)
        Field("Distance", distance, number = true) { distance = it }
        Field("Time", time, number = true) { time = it }
        val metres = distance.trim().ifEmpty { null }?.let { parseDistanceToMeters(it) }
        val seconds = time.trim().ifEmpty { null }?.let { parseDuration(it, true) }
        if (metres != null && seconds != null) {
            val pace = if (kind.shape == "SWIM") swimPaceSeconds(metres, seconds) else kmPaceSeconds(metres, seconds)
            if (pace != null) Line(formatPace(pace) + if (kind.shape == "SWIM") " /100 m" else " /km")
        }
        if (kind.shape == "RUN") {
            Field("Gradient", gradient, number = true) { gradient = it }
        }
        if (kind.shape == "HIKE") {
            Field("Bottom", bottom, number = true) { bottom = it }
            Field("Top", top, number = true) { top = it }
            val low = bottom.trim().ifEmpty { null }?.let { parseDecimal(it) }
            val high = top.trim().ifEmpty { null }?.let { parseDecimal(it) }
            if (low != null && high != null) {
                val climb = climbMetres(low, high)
                Line("Climb ${formatMetres(climb)}")
                if (bothWays) Line("Covered ${formatMetres(coveredMetres(climb, true))}")
            }
            Label("Way")
            Word(if (bothWays) "Both ways" else "One way") { bothWays = !bothWays }
        }
        if (kind.shape == "RUN" || kind.shape == "HIKE") {
            Field("Location", location) { location = it }
        }
        snap.fields.filter { it.kindId == kind.id }.forEach { field ->
            Field(field.name, extras[field.id].orEmpty()) { text ->
                extras = extras + (field.id to text)
            }
        }
        if (kind.shape == "CUSTOM") {
            Field("Field", fieldName) { fieldName = it }
            Word("Add") {
                val trimmed = fieldName.trim()
                if (trimmed.isNotEmpty()) {
                    model.addField(kind.id, trimmed)
                    fieldName = ""
                }
            }
        }
    }
}

private data class PassRow(val kind: String, val times: String)

@Composable
fun OutingFields(
    shape: String,
    durationSec: Int?,
    distanceM: Double?,
    gradient: Double?,
    location: String?,
    bottomM: Double?,
    topM: Double?,
    bothWays: Boolean?,
    extras: List<Triple<Long, String, String>>,
    onDuration: (Int?) -> Unit,
    onDistance: (Double?) -> Unit,
    onGradient: (Double?) -> Unit,
    onLocation: (String?) -> Unit,
    onBottom: (Double?) -> Unit,
    onTop: (Double?) -> Unit,
    onWays: (Boolean) -> Unit,
    onExtra: (Long, String) -> Unit,
) {
    LabeledEdit(
        "Distance",
        distanceM?.let { formatKm(it) } ?: "Add distance",
        distanceM?.let { trimNumber(it / 1000.0) }.orEmpty(),
        number = true,
    ) { text ->
        onDistance(text.trim().ifEmpty { null }?.let { parseDistanceToMeters(it) })
    }
    LabeledEdit(
        "Time",
        durationSec?.let { formatDuration(it) } ?: "Add a time",
        durationRaw(durationSec),
        number = true,
    ) { text ->
        onDuration(text.trim().ifEmpty { null }?.let { parseDuration(it, true) })
    }
    val pace = if (distanceM != null && durationSec != null) {
        if (shape == "SWIM") swimPaceSeconds(distanceM, durationSec) else kmPaceSeconds(distanceM, durationSec)
    } else {
        null
    }
    if (pace != null) {
        StatTile("Pace", formatPace(pace) + if (shape == "SWIM") " /100 m" else " /km")
    }
    if (shape == "RUN") {
        LabeledEdit(
            "Gradient",
            gradient?.let { trimNumber(it) } ?: "Add gradient",
            gradient?.let { trimNumber(it) }.orEmpty(),
            number = true,
        ) { text ->
            onGradient(text.trim().ifEmpty { null }?.let { parseDecimal(it) })
        }
    }
    if (shape == "HIKE") {
        LabeledEdit(
            "Bottom",
            bottomM?.let { formatMetres(it) } ?: "Add the bottom",
            bottomM?.let { trimNumber(it) }.orEmpty(),
            number = true,
        ) { text ->
            onBottom(text.trim().ifEmpty { null }?.let { parseDecimal(it) })
        }
        LabeledEdit(
            "Top",
            topM?.let { formatMetres(it) } ?: "Add the top",
            topM?.let { trimNumber(it) }.orEmpty(),
            number = true,
        ) { text ->
            onTop(text.trim().ifEmpty { null }?.let { parseDecimal(it) })
        }
        if (bottomM != null && topM != null) {
            val climb = climbMetres(bottomM, topM)
            StatTile("Climb", formatMetres(climb))
            if (bothWays == true) StatTile("Covered", formatMetres(coveredMetres(climb, true)))
        }
        Text(
            "Way",
            color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 10.dp),
        )
        Row(Modifier.padding(top = 6.dp)) {
            ChoiceChip("One way", bothWays != true) { if (bothWays == true) onWays(false) }
            ChoiceChip("Both ways", bothWays == true) { if (bothWays != true) onWays(true) }
        }
    }
    if (shape == "RUN" || shape == "HIKE") {
        LabeledEdit(
            "Location",
            location?.takeIf { it.isNotBlank() } ?: "Add a place",
            location.orEmpty(),
        ) { text ->
            onLocation(text.trim().ifEmpty { null })
        }
    }
    extras.forEach { (id, name, value) ->
        LabeledEdit(name, value.ifBlank { "Add $name" }, value) { onExtra(id, it) }
    }
}

@Composable
private fun LabeledEdit(
    label: String,
    shown: String,
    raw: String,
    number: Boolean = false,
    onCommit: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        Editable(shown, raw, style = MaterialTheme.typography.titleLarge, number = number, onCommit = onCommit)
    }
}

internal fun durationRaw(seconds: Int?): String {
    if (seconds == null) return ""
    return if (seconds % 60 == 0) (seconds / 60).toString() else formatDuration(seconds)
}
