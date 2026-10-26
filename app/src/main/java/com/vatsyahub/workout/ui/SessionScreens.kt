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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
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
    Page {
        Word(dayText(draft.day)) { chooseDate() }
        Line(if (heavy) "Heavy" else "Bodyweight")
        draft.lines.forEachIndexed { lineIndex, line ->
            val exercise = snap.exercises.find { it.id == line.exerciseId }
            Word(exercise?.name ?: "Exercise") { model.openPicker(Picker.StrengthSwap(lineIndex)) }
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
            Word("Set ${line.sets.size + 1}") { model.addDraftSet(lineIndex) }
        }
        Word("Exercise") { model.openPicker(Picker.AddStrength(null)) }
        Line("${draft.lines.size} exercises")
        Line("${draft.lines.sumOf { line -> line.sets.sumOf { it.reps } }} reps")
        Editable(
            draft.durationSec?.let { formatDuration(it) } ?: "Time",
            durationRaw(draft.durationSec),
            number = true,
        ) { text ->
            model.setDraftDuration(parseDuration(text, true))
        }
        Word("Reroll") { model.rerollDraftAll() }
        Word("Save") { model.saveStrength() }
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
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .clickable { removing = !removing }
                .padding(vertical = 8.dp)
                .padding(end = 16.dp),
        )
        Editable(reps.toString(), reps.toString(), number = true) { text ->
            text.toIntOrNull()?.let(onReps)
        }
        if (heavy) {
            Text("  ", modifier = Modifier.padding(horizontal = 8.dp))
            Editable(weight?.let { formatKg(it) } ?: "kg", weight?.let { trimNumber(it) }.orEmpty(), number = true) { text ->
                onWeight(text.trim().ifEmpty { null }?.let { parseDecimal(it) })
            }
        }
    }
    if (removing) Word("Remove") { onRemove() }
}

@Composable
fun HiitScreen(model: AppModel) {
    val snap by model.snap.collectAsState()
    val draft = model.hiit ?: return
    val shared = draft.sets.all { it.shared }
    val chooseDate = pickDate(draft.day) { model.setHiitDay(it) }
    Page {
        Word(dayText(draft.day)) { chooseDate() }
        val rounds = if (shared) "${roundsOf(draft.template)} rounds" else null
        val label = if (shared) patternLabel(draft.template) else "Mixed"
        Word(if (rounds == null) label else "$label\n$rounds") { model.openPatterns(null) }
        Editable("${draft.workSec} s", draft.workSec.toString(), number = true) { text ->
            text.toIntOrNull()?.let { model.setWork(it) }
        }
        Editable("${draft.restSec} s", draft.restSec.toString(), number = true) { text ->
            text.toIntOrNull()?.let { model.setRest(it) }
        }
        Editable(formatBreak(draft.breakSec), durationRaw(draft.breakSec), number = true) { text ->
            parseDuration(text, true)?.let { model.setBreak(it) }
        }
        draft.sets.forEachIndexed { setIndex, set ->
            Word("Set ${setIndex + 1}") { model.open(Screen.SetMenu(setIndex)) }
            Editable(set.exerciseIds.size.toString(), set.exerciseIds.size.toString(), number = true) { text ->
                text.toIntOrNull()?.let { model.setHiitCount(setIndex, it) }
            }
            set.exerciseIds.forEachIndexed { line, id ->
                val name = snap.exercises.find { it.id == id }?.name ?: "Exercise"
                Word(name) { model.openPicker(Picker.HiitSwap(setIndex, line)) }
            }
            if (draft.manual) {
                val slots = expand(set.exerciseIds.size, set.passes)
                slots.forEachIndexed { bout, slot ->
                    val id = set.exerciseIds.getOrNull(slot)
                    val name = snap.exercises.find { it.id == id }?.name ?: "Exercise"
                    val seconds = set.splitSeconds.getOrElse(bout) { draft.workSec }
                    Editable("$name $seconds s", seconds.toString(), number = true) { text ->
                        text.toIntOrNull()?.let { model.setBoutSeconds(setIndex, bout, it) }
                    }
                }
            }
        }
        Word("Set") { model.addHiitSet() }
        Word("Reroll") { model.rerollHiitAll() }
        if (draft.manual) Word("Save") { model.saveManualHiit() } else Word("Start") { model.startClock() }
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
    val passes = model.currentPasses()
    Page {
        passes.forEachIndexed { index, pass ->
            Editable(patternLabel(listOf(pass)), pass.times.toString(), number = true) { text ->
                val times = text.toIntOrNull() ?: return@Editable
                if (times <= 0) model.removePass(index) else model.changePass(index, times)
            }
        }
        Word("Pass") { model.addPass("PASS") }
        Word("Each") { model.addPass("EACH") }
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
                val part = snap.exercises.find { it.id == line?.exerciseId }?.bodyPart
                val kind = model.strength?.kind ?: "HEAVY"
                Word("Reroll") {
                    model.rerollDraftLine(request.line)
                    model.back()
                }
                Word("Remove") { model.removeDraftLine(request.line) }
                snap.pool(kind).filter { part == null || it.bodyPart.equals(part, true) }.forEach { exercise ->
                    Word(exercise.name) { model.pickExercise(exercise.id) }
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
    Box(modifier.size(72.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(visual))
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
    var naming by remember { mutableStateOf(false) }
    var fieldName by remember { mutableStateOf("") }
    val chooseDate = pickDate(draft.day) { model.outing = draft.copy(day = it) }
    if (naming) {
        Page {
            Editable(fieldName.ifBlank { "Field" }, fieldName) { fieldName = it }
            Word("Save") {
                if (fieldName.isNotBlank()) model.addField(kind.id, fieldName)
                fieldName = ""
                naming = false
            }
        }
        return
    }
    Page {
        Word(dayText(draft.day)) { chooseDate() }
        Line(kind.name)
        OutingFields(
            shape = kind.shape,
            durationSec = draft.durationSec,
            distanceM = draft.distanceM,
            gradient = draft.gradient,
            location = draft.location,
            bottomM = draft.bottomM,
            topM = draft.topM,
            bothWays = draft.bothWays,
            extras = snap.fields.filter { it.kindId == kind.id }.map { field ->
                Triple(field.id, field.name, draft.fields[field.id].orEmpty())
            },
            onDuration = { model.outing = model.outing?.copy(durationSec = it) },
            onDistance = { model.outing = model.outing?.copy(distanceM = it) },
            onGradient = { model.outing = model.outing?.copy(gradient = it) },
            onLocation = { model.outing = model.outing?.copy(location = it) },
            onBottom = { model.outing = model.outing?.copy(bottomM = it) },
            onTop = { model.outing = model.outing?.copy(topM = it) },
            onWays = { model.outing = model.outing?.copy(bothWays = it) },
            onExtra = { id, value ->
                val current = model.outing ?: return@OutingFields
                model.outing = current.copy(fields = current.fields + (id to value))
            },
        )
        if (kind.shape == "CUSTOM") {
            Word("Field") { naming = true }
        }
        Word("Save") { model.saveOutingDraft() }
    }
}

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
    Editable(distanceM?.let { formatKm(it) } ?: "Distance", distanceM?.let { trimNumber(it / 1000.0) }.orEmpty(), number = true) { text ->
        onDistance(text.trim().ifEmpty { null }?.let { parseDistanceToMeters(it) })
    }
    Editable(durationSec?.let { formatDuration(it) } ?: "Time", durationRaw(durationSec), number = true) { text ->
        onDuration(text.trim().ifEmpty { null }?.let { parseDuration(it, true) })
    }
    val pace = if (distanceM != null && durationSec != null) {
        if (shape == "SWIM") swimPaceSeconds(distanceM, durationSec) else kmPaceSeconds(distanceM, durationSec)
    } else {
        null
    }
    if (pace != null) {
        Line(formatPace(pace) + if (shape == "SWIM") " /100 m" else " /km")
    }
    if (shape == "RUN") {
        Editable(gradient?.let { "Gradient ${trimNumber(it)}" } ?: "Gradient", gradient?.let { trimNumber(it) }.orEmpty(), number = true) { text ->
            onGradient(text.trim().ifEmpty { null }?.let { parseDecimal(it) })
        }
    }
    if (shape == "HIKE") {
        Editable(bottomM?.let { "Bottom ${formatMetres(it)}" } ?: "Bottom", bottomM?.let { trimNumber(it) }.orEmpty(), number = true) { text ->
            onBottom(text.trim().ifEmpty { null }?.let { parseDecimal(it) })
        }
        Editable(topM?.let { "Top ${formatMetres(it)}" } ?: "Top", topM?.let { trimNumber(it) }.orEmpty(), number = true) { text ->
            onTop(text.trim().ifEmpty { null }?.let { parseDecimal(it) })
        }
        if (bottomM != null && topM != null) {
            val climb = climbMetres(bottomM, topM)
            Line("Climb ${formatMetres(climb)}")
            if (bothWays == true) Line("Covered ${formatMetres(coveredMetres(climb, true))}")
        }
        Word(if (bothWays == true) "Both ways" else "One way") { onWays(bothWays != true) }
    }
    if (shape == "RUN" || shape == "HIKE") {
        Editable(location?.takeIf { it.isNotBlank() } ?: "Location", location.orEmpty()) { text ->
            onLocation(text.trim().ifEmpty { null })
        }
    }
    extras.forEach { (id, name, value) ->
        Editable(value.ifBlank { name }, value) { onExtra(id, it) }
    }
}

internal fun durationRaw(seconds: Int?): String {
    if (seconds == null) return ""
    return if (seconds % 60 == 0) (seconds / 60).toString() else formatDuration(seconds)
}
