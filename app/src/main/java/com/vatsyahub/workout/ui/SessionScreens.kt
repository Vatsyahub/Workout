package com.vatsyahub.workout.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Timer
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vatsyahub.workout.data.passesOf
import com.vatsyahub.workout.data.pool
import com.vatsyahub.workout.data.strengthLibrary
import com.vatsyahub.workout.domain.EACH
import com.vatsyahub.workout.domain.PASS
import com.vatsyahub.workout.domain.Pass
import com.vatsyahub.workout.domain.coveredMetres
import com.vatsyahub.workout.domain.climbMetres
import com.vatsyahub.workout.domain.dayText
import com.vatsyahub.workout.domain.expand
import com.vatsyahub.workout.domain.formatBreak
import com.vatsyahub.workout.domain.musclesOf
import com.vatsyahub.workout.domain.formatDuration
import com.vatsyahub.workout.domain.SetLoad
import com.vatsyahub.workout.domain.formatKm
import com.vatsyahub.workout.domain.formatSets
import com.vatsyahub.workout.domain.formatMetres
import com.vatsyahub.workout.domain.formatPace
import com.vatsyahub.workout.domain.kmPaceSeconds
import com.vatsyahub.workout.domain.parseDecimal
import com.vatsyahub.workout.domain.parseDistanceToMeters
import com.vatsyahub.workout.domain.parseDuration
import com.vatsyahub.workout.domain.orderLabel
import com.vatsyahub.workout.domain.patternLabel
import com.vatsyahub.workout.domain.roundsOf
import com.vatsyahub.workout.domain.showsWeight
import com.vatsyahub.workout.domain.strengthLabel
import com.vatsyahub.workout.domain.swimPaceSeconds

@Composable
fun StrengthScreen(model: AppModel) {
    val snap by model.snap.collectAsState()
    val draft = model.strength ?: return
    if (draft.started && !draft.logging && draft.lines.isNotEmpty()) {
        StrengthFocus(model, draft)
        return
    }
    val chooseDate = pickDate(draft.day) { model.setDraftDay(it) }
    val exerciseCount = draft.lines.size
    val totalReps = draft.lines.sumOf { line -> line.sets.sumOf { it.reps } }
    val handPicked = draft.lines.isNotEmpty() && draft.lines.all { it.groupKind == "PICK" }
    val editing = draft.editingPlanId != null
    val action = when {
        editing -> "Save"
        draft.logging -> "Mark as finished"
        else -> "Save and start workout"
    }
    ActionPage(
        action,
        {
            when {
                editing -> model.savePlanEdit()
                draft.logging -> model.open(Screen.StrengthTime)
                else -> model.startFocusedWorkout()
            }
        },
        showAction = draft.logging || draft.lines.isNotEmpty(),
    ) {
        Panel {
            Text(strengthLabel(draft.kind), style = MaterialTheme.typography.titleLarge)
            Text(
                if (editing) {
                    "Change the reps or weight, add or remove a set, add an exercise, then Save."
                } else {
                    "Tap a set line to change sets and reps. Drag the handle to reorder."
                },
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                StatTile("Date", dayText(draft.day), Modifier.weight(1f), chooseDate)
                StatTile("Exercises", exerciseCount.toString(), Modifier.weight(1f))
                StatTile("Reps", totalReps.toString(), Modifier.weight(1f))
            }
        }
        if (draft.lines.isEmpty()) {
            Panel {
                Text("Add the exercises in this session.")
            }
        }
        ReorderColumn(
            items = draft.lines,
            keyOf = { it.key },
            onMove = { from, to -> model.moveDraft(from, to) },
        ) { lineIndex, line, number, _, handle ->
            val exercise = snap.exercises.find { it.id == line.exerciseId }
            val showWeight = showsWeight(draft.kind, exercise?.usage)
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OrderBadge(number)
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(start = 12.dp)
                            .clickable { model.openPicker(Picker.StrengthSwap(lineIndex)) },
                    ) {
                        Text(exercise?.name ?: "Choose an exercise", style = MaterialTheme.typography.titleLarge)
                        Text(
                            exercise?.bodyPart?.takeIf { it.isNotBlank() } ?: "None",
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        if (line.groupKind == "TYPE" && line.group.isNotBlank()) {
                            Text(line.group, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                    handle()
                    if (!editing && line.groupKind != "PICK") {
                        IconButton(onClick = { model.rerollDraftLine(lineIndex) }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Reroll")
                        }
                    }
                    IconButton(onClick = { model.removeDraftLine(lineIndex) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                    }
                }
                if (editing) {
                    EditableSets(line.sets, showWeight, continuous = true) {
                        model.replaceDraftSets(lineIndex, it)
                    }
                } else {
                    CompactSets(
                        formatSets(line.sets.map { SetLoad(it.reps, it.weightKg) }, showWeight),
                        showWeight,
                        line.sets,
                    ) { model.replaceDraftSets(lineIndex, it) }
                }
            }
        }
        QuietButton("Add exercise") { model.openPicker(Picker.AddStrength(null)) }
        if (!editing && !handPicked) QuietButton("Reroll all") { model.rerollDraftAll() }
        if (!draft.logging && draft.editingPlanId == null && draft.lines.isNotEmpty()) {
            QuietButton("Save as Plan") { model.saveStrengthAsPlan() }
            if (model.planNote == "LINES") {
                Text("Saved as a Plan", color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
private fun StrengthFocus(model: AppModel, draft: StrengthDraft) {
    val snap by model.snap.collectAsState()
    val pagerState = rememberPagerState(initialPage = draft.focus.coerceIn(0, draft.lines.lastIndex)) {
        draft.lines.size
    }
    LaunchedEffect(pagerState.currentPage) {
        model.rememberFocus(pagerState.currentPage)
    }
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) { page ->
        val line = draft.lines[page]
        val exercise = snap.exercises.find { it.id == line.exerciseId }
        val showWeight = showsWeight(draft.kind, exercise?.usage)
        val last = page == draft.lines.lastIndex
        BoxWithConstraints(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OrderBadge(page + 1)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(exercise?.name ?: "Choose an exercise", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            exercise?.bodyPart?.takeIf { it.isNotBlank() } ?: "None",
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        if (line.groupKind == "TYPE" && line.group.isNotBlank()) {
                            Text(line.group, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                    IconBadge(Icons.Filled.FitnessCenter)
                }
                Text(
                    "${page + 1} of ${draft.lines.size}",
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 12.dp),
                )
                line.sets.forEachIndexed { setIndex, set ->
                    Text(
                        "Set ${setIndex + 1}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text("${set.reps} reps", style = MaterialTheme.typography.titleLarge)
                    if (showWeight) {
                        Text(
                            set.weightKg?.let { "${trimNumber(it)} kg" } ?: "No weight",
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
                CompactSets(
                    formatSets(line.sets.map { SetLoad(it.reps, it.weightKg) }, showWeight),
                    showWeight,
                    line.sets,
                ) { model.replaceDraftSets(page, it) }
            }
            if (last) {
                PrimaryButton("Mark workout complete") { model.open(Screen.StrengthTime) }
            }
        }
        }
    }
}

@Composable
fun StrengthTimeScreen(model: AppModel) {
    val draft = model.strength ?: return
    var time by remember { mutableStateOf(durationRaw(draft.durationSec)) }
    ActionPage("Mark as finished", {
        model.setDraftDuration(time.trim().ifEmpty { null }?.let { parseDuration(it, true) })
        model.saveStrength()
    }) {
        Panel {
            Text("Total time", style = MaterialTheme.typography.titleLarge)
            Text(
                "Leave this empty to save with no duration.",
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 4.dp),
            )
            Field("Total time", time, number = true) { time = it }
        }
    }
}

@Composable
internal fun CompactSets(
    summary: String,
    heavy: Boolean,
    sets: List<DraftSet>,
    onSave: (List<DraftSet>) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    if (summary.isNotBlank()) {
        Text(
            "Sets",
            color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            summary,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { open = !open }
                .padding(top = 2.dp, bottom = 2.dp),
        )
    }
    if (open && summary.isNotBlank()) {
        EditableSets(sets, heavy) { saved ->
            onSave(saved)
            open = false
        }
    }
}

@Composable
internal fun EditableSets(
    initial: List<DraftSet>,
    heavy: Boolean,
    continuous: Boolean = false,
    onSave: (List<DraftSet>) -> Unit,
) {
    var rows by remember { mutableStateOf(editorSets(initial)) }
    fun publish(next: List<EditorSet>) {
        rows = next
        if (continuous) onSave(next.savedDraft(initial))
    }
    SetTableHeader(heavy)
    rows.forEachIndexed { index, row ->
        key(row.key) {
            SetRow(
                label = "Set ${index + 1}",
                reps = row.reps,
                weight = row.weightKg,
                heavy = heavy,
                onRemove = { publish(rows.withoutSet(row.key)) },
                onReps = { reps -> publish(rows.withReps(row.key, reps)) },
                onWeight = { weight -> publish(rows.withWeight(row.key, weight)) },
            )
        }
    }
    QuietButton("Add set") { publish(rows.withAddedSet()) }
    if (!continuous) {
        SoftButton("Save") { onSave(rows.savedDraft(initial)) }
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
        Box(Modifier.weight(1f).padding(end = 6.dp, top = 4.dp, bottom = 4.dp)) {
            BoxField(reps.toString()) { text ->
                text.toIntOrNull()?.let(onReps)
            }
        }
        if (heavy) {
            Box(Modifier.weight(1.1f).padding(top = 4.dp, bottom = 4.dp)) {
                BoxField(weight?.let { trimNumber(it) }.orEmpty(), placeholder = "kg") { text ->
                    val trimmed = text.trim()
                    if (trimmed.isEmpty()) onWeight(null)
                    else parseDecimal(trimmed)?.let(onWeight)
                }
            }
        }
    }
    if (removing) {
        QuietButton("Remove set", MaterialTheme.colorScheme.error, onRemove)
    }
}

@Composable
private fun GapTime(label: String, seconds: Int, fallback: Int, onChange: (Int) -> Unit) {
    Text(label, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
    if (seconds <= 0) {
        Text("Removed", style = MaterialTheme.typography.titleMedium)
        QuietButton("Add") { onChange(fallback) }
    } else {
        val shown = if (seconds < 60) "$seconds s" else formatBreak(seconds)
        Editable(shown, durationRaw(seconds), style = MaterialTheme.typography.titleMedium, number = true) { text ->
            parseDuration(text, true)?.let { onChange(it.coerceAtLeast(0)) }
        }
        QuietButton("Remove") { onChange(0) }
    }
}

@Composable
fun HiitScreen(model: AppModel) {
    val snap by model.snap.collectAsState()
    val draft = model.hiit ?: return
    val shared = draft.sets.all { it.shared }
    val chooseDate = pickDate(draft.day) { model.setHiitDay(it) }
    val editing = draft.editingPlanId != null
    val action = when {
        editing -> "Save"
        draft.manual -> "Save"
        else -> "Open clock"
    }
    val patternName = if (shared) patternLabel(draft.template) else "Mixed"
    val roundCount = if (shared) roundsOf(draft.template).toString() else "Mixed"
    ActionPage(action, {
        when {
            editing -> model.saveHiitPlanEdit()
            draft.manual -> model.saveManualHiit()
            else -> model.startClock()
        }
    }) {
        CompactRow("HIIT", dayText(draft.day), onClick = chooseDate)
        StatTile("Order", patternName, Modifier.fillMaxWidth()) { model.openPatterns(null) }
        StatTile("Times around", roundCount, Modifier.fillMaxWidth())
        Text("Work", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
        Editable("${draft.workSec} s", draft.workSec.toString(), style = MaterialTheme.typography.titleMedium, number = true) { text ->
            text.toIntOrNull()?.let { model.setWork(it) }
        }
        GapTime("Rest", draft.restSec, 5) { model.setRest(it) }
        GapTime("Break", draft.breakSec, 300) { model.setBreak(it) }
        GapTime("Warmup", draft.warmupSec, 120) { model.setWarmup(it) }
        GapTime("Cooldown", draft.cooldownSec, 300) { model.setCooldown(it) }
        draft.sets.forEachIndexed { setIndex, set ->
            Column(Modifier.padding(top = 8.dp)) {
                CompactRow(
                    "Set ${setIndex + 1}",
                    patternLabel(set.passes),
                    if (set.exerciseIds.size == 1) "1 exercise" else "${set.exerciseIds.size} exercises",
                ) { model.open(Screen.SetMenu(setIndex)) }
                Text(
                    "Exercises",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Editable(
                    set.exerciseIds.size.toString(),
                    set.exerciseIds.size.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    number = true,
                ) { text ->
                    text.toIntOrNull()?.let { model.setHiitCount(setIndex, it) }
                }
                if (set.exerciseIds.isEmpty()) {
                    Text("No exercises in this set.", color = MaterialTheme.colorScheme.secondary)
                }
                set.exerciseIds.forEachIndexed { line, id ->
                    val exercise = snap.exercises.find { it.id == id }
                    CompactRow(
                        exercise?.name ?: "Exercise",
                        exercise?.bodyPart?.takeIf { it.isNotBlank() } ?: "None",
                    ) { model.openPicker(Picker.HiitSwap(setIndex, line)) }
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
        if (!draft.manual && draft.editingPlanId == null) {
            QuietButton("Save as Plan") { model.saveHiitAsPlan() }
            if (model.planNote == "HIIT") {
                Text("Saved as a Plan", color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
fun PatternPickScreen(model: AppModel) {
    val snap by model.snap.collectAsState()
    Page {
        snap.patterns.sortedBy { it.label }.forEach { pattern ->
            val label = patternLabel(snap.passesOf(pattern.id))
            CompactRow(label, "Use this order") { model.applyPattern(pattern.id) }
        }
        CompactRow("Edit order", "Change how the exercises go around") { model.open(Screen.PatternEdit) }
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
            val backToBack = row.kind == EACH
            val count = row.times.toIntOrNull() ?: 1
            Panel {
                Text(orderLabel(row.kind, count), style = MaterialTheme.typography.titleLarge)
                Text(
                    if (backToBack) {
                        "One exercise, back to back, before the next."
                    } else {
                        "A, then B, then C."
                    },
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Field("Times", row.times, number = true) { text ->
                    rows = rows.toMutableList().also { it[index] = row.copy(times = text) }
                }
                if (rows.size > 1) {
                    QuietButton("Remove", MaterialTheme.colorScheme.error) {
                        rows = rows.filterIndexed { rowIndex, _ -> rowIndex != index }
                    }
                }
            }
        }
        Door(Icons.Filled.Add, "In order", "A, then B, then C") { rows = rows + PassRow(PASS, "1") }
        Door(Icons.Filled.Add, "Back to back", "A, A, then B, B, then C, C") { rows = rows + PassRow(EACH, "2") }
    }
}

@Composable
fun SetMenuScreen(model: AppModel, index: Int) {
    val set = model.hiit?.sets?.getOrNull(index) ?: return
    Page {
        CompactRow(patternLabel(set.passes), "Change the order for this set") { model.openPatterns(index) }
        if ((model.hiit?.sets?.size ?: 0) > 1) {
            CompactRow("Remove set", "Take this set out of the workout") { model.removeHiitSet(index) }
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
                val fromPick = line?.groupKind == "PICK"
                Text(
                    when {
                        fromType -> "Exercises in ${line?.group}. Tap one to use it here."
                        fromPick -> "Any exercise you have added. Tap one to use it here."
                        line?.group?.isNotBlank() == true -> "Exercises that use ${line.group}. Tap one to use it here."
                        else -> "Any exercise in this workout. Tap one to use it here."
                    },
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
                val kind = model.strength?.kind ?: "HEAVY"
                model.lineChoices(request.line)
                    .sortedWith(compareBy({ it.name.lowercase() }, { it.bodyPart.lowercase() }))
                    .forEach { exercise ->
                        CompactRow(
                            exercise.name,
                            exercise.bodyPart.ifBlank { "None" },
                            previewSetLine(snap, exercise, previewKind(exercise.usage, kind)),
                        ) { model.pickExercise(exercise.id) }
                    }
            }
            is Picker.HiitSwap -> {
                CompactRow("Reroll", "Draw a different exercise") {
                    model.rerollHiit(request.set, request.line)
                    model.back()
                }
                CompactRow("Remove", "Take this exercise out of the set") { model.removeHiitLine(request.set, request.line) }
                snap.pool("HIIT").forEach { exercise ->
                    CompactRow(
                        exercise.name,
                        exercise.bodyPart.ifBlank { "None" },
                        previewSetLine(snap, exercise, previewKind(exercise.usage, "BODYWEIGHT")),
                    ) { model.pickExercise(exercise.id) }
                }
            }
            is Picker.AddStrength -> {
                val draft = model.strength
                val kind = if (request.workoutId == null) {
                    draft?.kind ?: "HEAVY"
                } else {
                    snap.workouts.find { it.id == request.workoutId }?.kind ?: "HEAVY"
                }
                val handPicked = request.workoutId == null &&
                    (kind == "MIXED" || draft?.lines?.any { it.groupKind == "PICK" } == true)
                val exercises = if (handPicked || kind == "MIXED") snap.strengthLibrary() else snap.pool(kind)
                exercises.forEach { exercise ->
                    CompactRow(
                        exercise.name,
                        exercise.bodyPart.ifBlank { "None" },
                        previewSetLine(snap, exercise, previewKind(exercise.usage, kind)),
                    ) { model.pickExercise(exercise.id) }
                }
            }
            is Picker.PickSwap -> {
                Text(
                    "Any exercise you have added. Tap one to use it here.",
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
                snap.strengthLibrary()
                    .sortedWith(compareBy({ it.name.lowercase() }, { it.bodyPart.lowercase() }))
                    .forEach { exercise ->
                        CompactRow(
                            exercise.name,
                            exercise.bodyPart.ifBlank { "None" },
                            previewSetLine(snap, exercise, previewKind(exercise.usage, model.build.kind)),
                        ) { model.pickExercise(exercise.id) }
                    }
            }
            is Picker.AddHiit -> {
                snap.pool("HIIT").forEach { exercise ->
                    CompactRow(
                        exercise.name,
                        exercise.bodyPart.ifBlank { "None" },
                        previewSetLine(snap, exercise, previewKind(exercise.usage, "BODYWEIGHT")),
                    ) { model.pickExercise(exercise.id) }
                }
            }
            is Picker.BodyPart -> {
                snap.pool(model.build.kind)
                    .flatMap { musclesOf(it.bodyPart) }
                    .distinctBy { it.lowercase() }
                    .sortedBy { it.lowercase() }
                    .forEach { part ->
                        CompactRow(part, "Draw exercises that use this muscle") { model.pickPart(part) }
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
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CountdownRing(clock.fraction, clock.work, formatDuration(clock.remainingSec))
            Text(
                clock.now.ifBlank { "HIIT" },
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 20.dp),
            )
            if (clock.next.isNotBlank()) {
                Text(
                    clock.next,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        Box(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Control(Icons.Filled.SkipPrevious, "Back", 32.dp, Modifier.align(Alignment.CenterStart)) { model.backClock() }
            Control(Icons.Filled.SkipNext, "Forward", 32.dp, Modifier.align(Alignment.CenterEnd)) { model.forwardClock() }
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                val playIcon = if (clock.running) Icons.Filled.Pause else Icons.Filled.PlayArrow
                Control(playIcon, if (clock.running) "Pause" else "Play", 52.dp, Modifier) { model.pauseClock() }
                Control(Icons.Filled.Stop, "Stop", 32.dp, Modifier.padding(top = 8.dp)) { model.stopClock() }
            }
        }
    }
}

@Composable
private fun CountdownRing(fraction: Float, work: Boolean, label: String) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val arc = if (work) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
    Box(Modifier.size(280.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 18.dp.toPx()
            val inset = stroke / 2f
            val diameter = size.minDimension - stroke
            val topLeft = Offset(inset, inset)
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = Size(diameter, diameter),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (fraction > 0f) {
                drawArc(
                    color = arc,
                    startAngle = -90f,
                    sweepAngle = 360f * fraction.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(diameter, diameter),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Text(label, style = MaterialTheme.typography.displayLarge)
    }
}

@Composable
fun OutingReadyScreen(model: AppModel, name: String) {
    val icon = when (name) {
        "Swim" -> Icons.Filled.Pool
        "Run" -> Icons.AutoMirrored.Filled.DirectionsRun
        "Hike" -> Icons.Filled.Terrain
        else -> Icons.Filled.Timer
    }
    val detail = when (name) {
        "Swim" -> "Start the clock when you are in the water."
        "Run" -> "Start the clock when you are ready to run."
        "Hike" -> "Start the clock when you are on the trail."
        else -> "Start the clock when you are ready."
    }
    ActionPage("Start", { model.startNamedOuting(name) }) {
        CompactRow(name, detail, leading = { IconBadge(icon) })
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
        Column(Modifier.align(Alignment.Center).fillMaxWidth()) {
                Panel {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    IconBadge(
                        when (name) {
                            "Swim" -> Icons.Filled.Pool
                            "Run" -> Icons.AutoMirrored.Filled.DirectionsRun
                            "Hike" -> Icons.Filled.Terrain
                            else -> Icons.Filled.Timer
                        },
                    )
                    Text(name.ifBlank { "Outing" }, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
                    Text(
                        if (timer.running) "Clock running" else "Paused",
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        formatDuration(timer.elapsedSec),
                        style = MaterialTheme.typography.displayLarge,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
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
        CompactRow(
            kind.name,
            when (kind.shape) {
                "SWIM" -> "Distance and time for this swim."
                "RUN" -> "Distance, time, and where you ran."
                "HIKE" -> "Distance, time, and the climb."
                else -> "What you want to remember from this outing."
            },
            leading = {
                IconBadge(
                    when (kind.shape) {
                        "SWIM" -> Icons.Filled.Pool
                        "RUN" -> Icons.AutoMirrored.Filled.DirectionsRun
                        "HIKE" -> Icons.Filled.Terrain
                        else -> Icons.Filled.Timer
                    },
                )
            },
            onClick = chooseDate,
        )
            StatTile("Date", dayText(model.outing?.day ?: draft.day), Modifier.fillMaxWidth(), chooseDate)
            Field("Distance", distance, number = true) { distance = it }
            Field("Time", time, number = true) { time = it }
            val metres = distance.trim().ifEmpty { null }?.let { parseDistanceToMeters(it) }
            val seconds = time.trim().ifEmpty { null }?.let { parseDuration(it, true) }
            if (metres != null && seconds != null) {
                val pace = if (kind.shape == "SWIM") swimPaceSeconds(metres, seconds) else kmPaceSeconds(metres, seconds)
                if (pace != null) {
                    StatTile("Pace", formatPace(pace) + if (kind.shape == "SWIM") " /100 m" else " /km")
                }
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
                    StatTile("Climb", formatMetres(climb))
                    if (bothWays) StatTile("Covered", formatMetres(coveredMetres(climb, true)))
                }
                Text(
                    "Way",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Row(Modifier.padding(top = 6.dp)) {
                    ChoiceChip("One way", !bothWays) { bothWays = false }
                    ChoiceChip("Both ways", bothWays) { bothWays = true }
                }
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
                SoftButton("Add") {
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
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 10.dp),
        ) {
            Editable(shown, raw, style = MaterialTheme.typography.titleLarge, number = number, onCommit = onCommit)
        }
    }
}

internal fun durationRaw(seconds: Int?): String {
    if (seconds == null) return ""
    return if (seconds % 60 == 0) (seconds / 60).toString() else formatDuration(seconds)
}
