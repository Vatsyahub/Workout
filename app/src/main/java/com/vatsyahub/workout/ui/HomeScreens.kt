package com.vatsyahub.workout.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vatsyahub.workout.data.Exercise
import com.vatsyahub.workout.data.localDay
import com.vatsyahub.workout.data.pool
import com.vatsyahub.workout.data.strengthLibrary
import com.vatsyahub.workout.data.summary
import com.vatsyahub.workout.data.typesOf
import com.vatsyahub.workout.domain.PickCandidate
import com.vatsyahub.workout.domain.PlanBody
import com.vatsyahub.workout.domain.SetLoad
import com.vatsyahub.workout.domain.classifyPickedKind
import com.vatsyahub.workout.domain.decodePlan
import com.vatsyahub.workout.domain.planDetail
import com.vatsyahub.workout.domain.planTitle
import com.vatsyahub.workout.domain.formatSets
import com.vatsyahub.workout.domain.matchesBodyPart
import com.vatsyahub.workout.domain.matchExercises
import com.vatsyahub.workout.domain.musclesOf
import com.vatsyahub.workout.domain.parseDecimal
import com.vatsyahub.workout.domain.showsWeight
import com.vatsyahub.workout.domain.weekStart
import java.time.LocalDate
import kotlinx.coroutines.launch

private fun usageLabel(usage: String): String = when (usage) {
    "HEAVY" -> "Heavy"
    "BODYWEIGHT" -> "Bodyweight"
    "BOTH" -> "Both"
    else -> usage
}

private fun kindIcon(shape: String): ImageVector = when (shape) {
    "SWIM" -> Icons.Filled.Pool
    "RUN" -> Icons.AutoMirrored.Filled.DirectionsRun
    "HIKE" -> Icons.Filled.Terrain
    else -> Icons.AutoMirrored.Filled.DirectionsWalk
}

@Composable
fun HomeScreen(model: AppModel) {
    val snap by model.snap.collectAsState()
    val name = snap.profile.name.trim()
    val today = LocalDate.now()
    val start = weekStart(today)
    val end = start.plusDays(6)
    val week = snap.workouts.count { workout ->
        workout.finished && !localDay(workout.performedAt).let { it.isBefore(start) || it.isAfter(end) }
    }
    val recent = snap.workouts.filter { it.finished }.sortedByDescending { it.performedAt }.take(3)
    Page {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
            ProfilePhoto(snap.profile.avatar, 52.dp) { model.tab(Screen.Settings) }
            Column(Modifier.padding(start = 14.dp)) {
                Text("Barbook", style = MaterialTheme.typography.headlineLarge)
                if (name.isNotBlank()) {
                    Text(name, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
        Text("Start a session, or look back at the week.", color = MaterialTheme.colorScheme.secondary)
        val active = model.activeCard(snap)
        if (active != null) {
            Panel {
                Text("Active workout", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge)
                Text(active.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 4.dp))
                if (active.detail.isNotBlank()) {
                    Text(active.detail, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 2.dp))
                }
                model.notice?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
                PrimaryButton("Open") { model.openWorkout() }
            }
        } else {
            model.notice?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }
        }
        SectionLabel("This week")
        Panel {
            Text(
                week.toString(),
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                if (week == 1) "workout" else "workouts",
                color = MaterialTheme.colorScheme.secondary,
            )
            QuietButton("Reports") { model.open(Screen.Reports) }
        }
        SectionLabel("Start")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            StartCard(Icons.Filled.FitnessCenter, "Heavy", "Weights", Modifier.weight(1f)) { model.beginBuild("HEAVY") }
            StartCard(Icons.Filled.AccessibilityNew, "Bodyweight", "No weights", Modifier.weight(1f)) { model.beginBuild("BODYWEIGHT") }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) {
            StartCard(Icons.Filled.Timer, "HIIT", "Intervals", Modifier.weight(1f)) { model.newHiit(manual = false) }
            StartCard(Icons.Filled.Pool, "Swim", "In the water", Modifier.weight(1f)) { model.openNamedOuting("Swim") }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) {
            StartCard(Icons.AutoMirrored.Filled.DirectionsRun, "Run", "On the road", Modifier.weight(1f)) {
                model.openNamedOuting("Run")
            }
            StartCard(Icons.Filled.Terrain, "Hike", "On a trail", Modifier.weight(1f)) { model.openNamedOuting("Hike") }
        }
        if (snap.plans.isNotEmpty()) {
            SectionLabel("Plans")
            snap.plans.forEach { plan ->
                val body = decodePlan(plan.body) ?: PlanBody()
                WorkoutCard(
                    plan.title.ifBlank { planTitle(plan.kind, body) },
                    planDetail(body),
                    icon = planIcon(plan.kind),
                    onEdit = { model.editPlan(plan.id) },
                    onDelete = { model.deletePlan(plan.id) },
                ) { model.startPlan(plan.id) }
            }
        }
        val favourites = snap.workouts.filter { it.finished && it.favourite }.sortedByDescending { it.performedAt }
        if (favourites.isNotEmpty()) {
            SectionLabel("Favourites")
            favourites.forEach { workout ->
                WorkoutCard(
                    historyKind(workout, snap),
                    historyDetail(workout, snap),
                    onUnfavourite = { model.setFavourite(workout.id, false) },
                    icon = historyIcon(workout, snap),
                ) { model.repeat(workout.id) }
            }
        }
        SectionLabel("Recent")
        if (recent.isEmpty()) {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Filled.History)
                    Text(
                        "Finished sessions show up here.",
                        modifier = Modifier.padding(start = 12.dp),
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        } else {
            recent.forEach { workout ->
                val summary = snap.summary(workout)
                WorkoutCard(
                    cardTitle(summary),
                    cardDetail(summary),
                    favourite = workout.favourite,
                    icon = historyIcon(workout, snap),
                ) {
                    model.open(Screen.LogDetail(workout.id))
                }
            }
        }
        QuietButton("Log a past workout") { model.open(Screen.LogType) }
    }
}

@Composable
fun WorkoutScreen(model: AppModel) {
    val snap by model.snap.collectAsState()
    Page {
        Door(Icons.Filled.FitnessCenter, "Heavy", "Weights") { model.beginBuild("HEAVY") }
        Door(Icons.Filled.AccessibilityNew, "Bodyweight", "No weights") { model.beginBuild("BODYWEIGHT") }
        Door(Icons.Filled.Timer, "HIIT", "Intervals") { model.newHiit(manual = false) }
        snap.kinds.forEach { kind ->
            val detail = when (kind.shape) {
                "SWIM" -> "In the water"
                "RUN" -> "On the road"
                "HIKE" -> "On a trail"
                else -> "An outing"
            }
            Door(kindIcon(kind.shape), kind.name, detail) { model.startOuting(kind.id) }
        }
        Door(Icons.Filled.Edit, "Kind", "Another outing") { model.open(Screen.AddKind) }
        Door(Icons.Filled.History, "Log", "A session that is already finished") { model.open(Screen.LogType) }
    }
}

@Composable
fun AddKindScreen(model: AppModel) {
    var name by remember { mutableStateOf("") }
    FormPage(onSave = {
        if (name.isNotBlank()) model.addKind(name.trim())
    }) {
        Panel {
            Text("New outing", style = MaterialTheme.typography.titleLarge)
            Text(
                "Swim, Run, and Hike are already on Home. Use this for another outing.",
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 4.dp),
            )
            Field("Name", name) { name = it }
        }
    }
}

@Composable
fun LogTypeScreen(model: AppModel) {
    Page {
        Panel {
            Text("Log a workout", style = MaterialTheme.typography.titleLarge)
            Text(
                "For a session that is already finished.",
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Door(Icons.Filled.FitnessCenter, "Heavy", "Weights you already finished") { model.beginEmptyStrength("HEAVY") }
        Door(Icons.Filled.AccessibilityNew, "Bodyweight", "A session without weights") { model.beginEmptyStrength("BODYWEIGHT") }
        Door(Icons.Filled.Timer, "HIIT", "Intervals you already finished") { model.newHiit(manual = true) }
        Door(Icons.Filled.Pool, "Swim", "Distance and time in the water") { model.logNamedOuting("Swim") }
        Door(Icons.AutoMirrored.Filled.DirectionsRun, "Run", "Distance and time on the road") { model.logNamedOuting("Run") }
        Door(Icons.Filled.Terrain, "Hike", "Distance, time, and the climb") { model.logNamedOuting("Hike") }
    }
}

@Composable
fun BuildScreen(model: AppModel) {
    val snap by model.snap.collectAsState()
    val build = model.build
    val library = snap.strengthLibrary()
    if (library.isEmpty()) {
        ActionPage("Add exercise", { model.tab(Screen.Exercises) }) {
            Panel {
                Text("Add exercises before a workout can be drawn.")
                Text(
                    "Heavy only uses heavy exercises. Bodyweight uses bodyweight exercises.",
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        return
    }
    val picking = build.mode == "PICK"
    val byType = build.mode == "TYPE"
    val pool = snap.pool(build.kind)
    var query by remember { mutableStateOf("") }
    val matches = if (!picking) {
        emptyList()
    } else {
        matchExercises(library.map { PickCandidate(it.id, it.name, it.usage) }, query)
    }
    val pickedKind = classifyPickedKind(
        build.picks.map { line -> snap.exercises.find { it.id == line.exerciseId }?.usage.orEmpty() },
        build.kind,
    )
    ActionPage(
        if (picking) "Save and start workout" else "Build",
        {
            if (picking) {
                if (build.picks.isEmpty()) return@ActionPage
                model.openPicked()
            } else {
                val total = build.parts.sumOf { it.count }
                if (total < 1) return@ActionPage
                model.drawBuild()
            }
        },
        showAction = !picking || build.picks.isNotEmpty(),
    ) {
        if (!picking && pool.isNotEmpty()) {
            Panel {
                if (byType) {
                    Text("Workout type", style = MaterialTheme.typography.titleLarge)
                }
                Text(
                    if (byType) {
                        "Tap Build to draw exercises of each workout type you set above zero."
                    } else {
                        "Tap Build to draw exercises for each muscle you set above zero."
                    },
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = if (byType) 4.dp else 0.dp),
                )
            }
        }
        if (byType) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                ChoiceChip("Workout type", true) { model.setBuildMode("TYPE") }
                ChoiceChip("Pick exercises", false) { model.setBuildMode("PICK") }
            }
        } else {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                ChoiceChip("Body part", build.mode == "PART") { model.setBuildMode("PART") }
                ChoiceChip("Pick exercises", picking) { model.setBuildMode("PICK") }
            }
        }
        if (picking) {
            Text(
                "A workout can mix heavy and bodyweight exercises.",
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 8.dp),
            )
            Field("Exercise name", query) { query = it }
            if (query.isNotBlank() && matches.isEmpty()) {
                Text(
                    "No exercises match.",
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            matches.forEach { candidate ->
                val exercise = library.find { it.id == candidate.id } ?: return@forEach
                val detail = listOf(exercise.bodyPart, usageLabel(exercise.usage))
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                WorkoutCard(exercise.name, detail, icon = Icons.Filled.FitnessCenter) {
                    model.addPicked(candidate.id)
                    query = ""
                }
            }
            if (build.picks.isEmpty()) {
                Panel {
                    Text("Add at least one exercise", style = MaterialTheme.typography.titleMedium)
                }
            }
            ReorderColumn(
                items = build.picks,
                keyOf = { it.key },
                onMove = { from, to -> model.movePicked(from, to) },
            ) { index, line, number, _, handle ->
                val exercise = snap.exercises.find { it.id == line.exerciseId }
                PickedExerciseCard(
                    number = number,
                    line = line,
                    exercise = exercise,
                    showWeight = showsWeight(pickedKind, exercise?.usage),
                    onName = { model.openPicker(Picker.PickSwap(index)) },
                    onRemove = { model.removePicked(index) },
                    onSave = { model.replacePickedSets(index, it) },
                    handle = handle,
                )
            }
        } else if (pool.isEmpty()) {
            Panel {
                Text("Add exercises before a workout can be drawn.")
                Text(
                    "Heavy only uses heavy exercises. Bodyweight uses bodyweight exercises.",
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        } else {
            val chosen = build.parts.filter { it.count > 0 }
            val total = chosen.sumOf { it.count }
            if (build.parts.isEmpty()) {
                Panel {
                    Text(
                        if (byType) {
                            "No workout types yet. Add a type on an exercise, then come back."
                        } else {
                            "These exercises have no muscles yet."
                        },
                    )
                }
            }
            build.parts.forEachIndexed { index, part ->
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(if (byType) Icons.Filled.Timer else Icons.Filled.AccessibilityNew)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            if (byType) {
                                Text(
                                    "Workout type",
                                    color = MaterialTheme.colorScheme.secondary,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                            Text(part.bodyPart, style = MaterialTheme.typography.titleLarge)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Exercises",
                                color = MaterialTheme.colorScheme.secondary,
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { model.setPartCount(index, part.count - 1) }) {
                                    Icon(Icons.Filled.Remove, contentDescription = "Fewer exercises")
                                }
                                Text(part.count.toString(), style = MaterialTheme.typography.headlineSmall)
                                IconButton(onClick = { model.setPartCount(index, part.count + 1) }) {
                                    Icon(Icons.Filled.Add, contentDescription = "More exercises")
                                }
                            }
                        }
                    }
                }
            }
            Panel {
                if (total < 1) {
                    Text("Set at least one count", style = MaterialTheme.typography.titleMedium)
                } else {
                    val noun = if (total == 1) "exercise" else "exercises"
                    Text("You are building $total $noun", style = MaterialTheme.typography.titleMedium)
                    Text(
                        chosen.joinToString(", ") { it.bodyPart },
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            if (byType) {
                ChoiceChip("Body part", false) { model.setBuildMode("PART") }
            } else {
                ChoiceChip("Workout type", false) { model.setBuildMode("TYPE") }
            }
        }
        val canSavePlan = if (picking) build.picks.isNotEmpty() else build.parts.any { it.count > 0 }
        if (canSavePlan) {
            QuietButton("Save as Plan") { model.saveBuildAsPlan() }
            if (model.planNote == "PICK" || model.planNote == "PART" || model.planNote == "TYPE") {
                Text("Saved as a Plan", color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

private fun planIcon(kind: String): ImageVector = when (kind) {
    "HIIT" -> Icons.Filled.Timer
    "BODYWEIGHT" -> Icons.Filled.AccessibilityNew
    else -> Icons.Filled.FitnessCenter
}

@Composable
private fun PickedExerciseCard(
    number: Int,
    line: StrengthLine,
    exercise: Exercise?,
    showWeight: Boolean,
    onName: () -> Unit,
    onRemove: () -> Unit,
    onSave: (List<DraftSet>) -> Unit,
    handle: @Composable () -> Unit,
) {
    var editing by remember(line.key) { mutableStateOf(false) }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OrderBadge(number)
            Column(Modifier.weight(1f).padding(start = 12.dp).clickable(onClick = onName)) {
                Text(exercise?.name ?: "Choose an exercise", style = MaterialTheme.typography.titleLarge)
                Text(
                    exercise?.bodyPart?.takeIf { it.isNotBlank() } ?: "None",
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            handle()
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
            }
        }
        val summary = formatSets(line.sets.map { SetLoad(it.reps, it.weightKg) }, showWeight)
        if (summary.isNotBlank()) {
            Text(summary, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 10.dp))
        }
        SoftButton("Sets and reps") { editing = !editing }
        if (editing) {
            EditableSets(line.sets, showWeight) { saved ->
                onSave(saved)
                editing = false
            }
        }
    }
}

@Composable
fun ExercisesScreen(model: AppModel) {
    val snap by model.snap.collectAsState()
    var filter by remember { mutableStateOf("All") }
    var byType by remember { mutableStateOf(false) }
    val parts = snap.exercises.flatMap { musclesOf(it.bodyPart) }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() }
    val typeNames = snap.exercises.flatMap { exercise -> snap.typesOf(exercise.id).map { it.name } }
        .distinct()
        .sortedBy { it.lowercase() }
    val chips = if (byType) typeNames else parts
    val filtered = snap.exercises.filter { exercise ->
        if (filter == "All") return@filter true
        if (byType) snap.typesOf(exercise.id).any { it.name.equals(filter, true) }
        else matchesBodyPart(exercise.bodyPart, filter)
    }
    ListPage {
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Text(
                    "Exercises",
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
                Text(
                    "${snap.exercises.size} saved",
                    color = MaterialTheme.colorScheme.secondary,
                )
                Column(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp)) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        ChoiceChip("All", filter == "All") { filter = "All" }
                        chips.forEach { name ->
                            ChoiceChip(name, filter.equals(name, true)) { filter = name }
                        }
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        ChoiceChip(if (byType) "Muscles" else "Types", false) {
                            byType = !byType
                            filter = "All"
                        }
                    }
                }
            }
            if (filtered.isEmpty()) {
                item {
                    Panel {
                        Text(if (snap.exercises.isEmpty()) "No exercises yet." else "Nothing matches this filter.")
                    }
                }
            }
            items(filtered, key = { it.id }) { exercise ->
                val usage = usageLabel(exercise.usage)
                WorkoutCard(
                    exercise.name,
                    listOf(exercise.bodyPart, usage).filter { it.isNotBlank() }.joinToString(" · "),
                    icon = Icons.Filled.FitnessCenter,
                ) {
                    model.open(Screen.Exercise(exercise.id))
                }
            }
            item {
                PrimaryButton("Add exercise") { model.open(Screen.AddExercise) }
            }
        }
    }
}

@Composable
fun ExerciseFormScreen(model: AppModel, exerciseId: Long?) {
    val snap by model.snap.collectAsState()
    val existing = snap.exercises.find { it.id == exerciseId }
    var name by remember(exerciseId) { mutableStateOf("") }
    var body by remember(exerciseId) { mutableStateOf("") }
    var usage by remember(exerciseId) { mutableStateOf("HEAVY") }
    var chosen by remember(exerciseId) { mutableStateOf(listOf<String>()) }
    var added by remember(exerciseId) { mutableStateOf(listOf<String>()) }
    var draftType by remember(exerciseId) { mutableStateOf("") }
    var seeded by remember(exerciseId) { mutableStateOf(exerciseId == null) }
    if (!seeded && existing != null) {
        name = existing.name
        body = existing.bodyPart
        usage = existing.usage
        chosen = snap.typesOf(existing.id).map { it.name }
        seeded = true
    }
    if (existing == null && exerciseId != null) {
        Page { }
        return
    }
    val known = (snap.types.map { it.name } + added + chosen)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }
        .sortedBy { it.lowercase() }
    fun includeType(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return
        if (known.none { it.equals(trimmed, true) }) added = added + trimmed
        if (chosen.none { it.equals(trimmed, true) }) chosen = chosen + trimmed
    }
    fun toggleType(typeName: String) {
        chosen = if (chosen.any { it.equals(typeName, true) }) {
            chosen.filterNot { it.equals(typeName, true) }
        } else {
            chosen + typeName
        }
    }
    FormPage(onSave = {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@FormPage
        val pending = draftType.trim()
        val types = (if (pending.isEmpty()) chosen else chosen + pending)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
        model.saveExercise(exerciseId, trimmed, body.trim(), usage, types)
        model.back()
    }) {
        Panel {
            Text(
                if (exerciseId == null) "New exercise" else existing?.name ?: "Exercise",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                "Name, muscles, and how you use it.",
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 4.dp),
            )
        Field("Name", name) { name = it }
        Field("", body) { body = it }
        Label("Used for")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            listOf("HEAVY" to "Heavy", "BODYWEIGHT" to "Bodyweight", "BOTH" to "Both").forEach { (value, label) ->
                ChoiceChip(label, usage == value) { usage = value }
            }
        }
        Label("Types")
        Text(
            "Tap a type to include it. HIIT draws from these.",
            color = MaterialTheme.colorScheme.secondary,
        )
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 8.dp),
        ) {
            known.forEach { typeName ->
                ChoiceChip(typeName, chosen.any { it.equals(typeName, true) }) { toggleType(typeName) }
            }
        }
        Field("New type", draftType, onDone = {
            includeType(draftType)
            draftType = ""
        }) { draftType = it }
        QuietButton("Add type") {
            includeType(draftType)
            draftType = ""
        }
        }
        if (exerciseId != null) {
            QuietButton("Delete exercise", MaterialTheme.colorScheme.error) { model.deleteExercise(exerciseId) }
        }
    }
}

@Composable
fun SettingsScreen(model: AppModel) {
    val snap by model.snap.collectAsState()
    val profile = snap.profile
    val facts = listOfNotNull(
        profile.age?.let { "$it years" },
        profile.weightKg?.let { "${trimNumber(it)} kg" },
        profile.heightCm?.let { "${trimNumber(it)} cm" },
    ).joinToString(" · ")
    Page {
        Text(
            "Profile",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
        )
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProfilePhoto(profile.avatar, 64.dp) { model.open(Screen.Profile) }
                Column(Modifier.padding(start = 14.dp)) {
                    Text(
                        profile.name.trim().ifBlank { "Add your name" },
                        style = MaterialTheme.typography.titleLarge,
                    )
                    if (facts.isNotBlank()) {
                        Text(facts, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }
        SectionLabel("Appearance")
        Row {
            ChoiceChip("Light", profile.appearance != "DARK") {
                model.saveProfile(profile.copy(appearance = "LIGHT"))
            }
            ChoiceChip("Dark", profile.appearance == "DARK") {
                model.saveProfile(profile.copy(appearance = "DARK"))
            }
        }
        Door(Icons.Filled.Person, "Edit profile", "Name, age, weight, height, and photo") { model.open(Screen.Profile) }
        Door(Icons.Filled.History, "Reports", "This week, one exercise, and bests") { model.open(Screen.Reports) }
        Door(Icons.Filled.Folder, "Backup", "Export or import a file") { model.open(Screen.Carry) }
    }
}

@Composable
fun ProfileScreen(model: AppModel) {
    val snap by model.snap.collectAsState()
    val profile = snap.profile
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var avatar by remember { mutableStateOf<String?>(null) }
    var dirty by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val encoded = shrinkAvatar(context, uri) ?: return@launch
            dirty = true
            avatar = encoded
        }
    }
    if (!dirty) {
        name = profile.name
        age = profile.age?.toString().orEmpty()
        weight = profile.weightKg?.let { trimNumber(it) }.orEmpty()
        height = profile.heightCm?.let { trimNumber(it) }.orEmpty()
        avatar = profile.avatar
    }
    val bitmap = remember(avatar) {
        avatar?.let { encoded ->
            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }
    FormPage(onSave = {
        model.saveProfile(
            profile.copy(
                name = name.trim(),
                age = age.trim().toIntOrNull(),
                weightKg = weight.trim().ifEmpty { null }?.let { parseDecimal(it) },
                heightCm = height.trim().ifEmpty { null }?.let { parseDecimal(it) },
                avatar = avatar,
            ),
        )
        model.back()
    }) {
        Panel {
            Text("Photo", style = MaterialTheme.typography.titleLarge)
            Text(
                "Shown on Home. Tap to choose a picture.",
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            if (bitmap == null) {
                InsetCard("Photo", "Choose a picture", Icons.Filled.Person) { pick.launch("image/*") }
            } else {
                Image(
                    bitmap,
                    contentDescription = "Photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .size(96.dp)
                        .clip(CircleShape)
                        .clickable { pick.launch("image/*") },
                )
            }
        }
        Panel {
            Text("Details", style = MaterialTheme.typography.titleLarge)
            Text(
                "Name, age, weight, and height.",
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 4.dp),
            )
        Field("Name", name) {
            dirty = true
            name = it
        }
        Field("Age", age, number = true) {
            dirty = true
            age = it
        }
        Field("Weight", weight, number = true) {
            dirty = true
            weight = it
        }
        Field("Height", height, number = true) {
            dirty = true
            height = it
        }
        }
    }
}

@Composable
fun CarryScreen(model: AppModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<String?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = model.exportText()
            context.contentResolver.openOutputStream(uri)?.use { stream -> stream.write(text.toByteArray()) }
            model.carryNote = "Saved"
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
            if (text.isNullOrBlank()) model.carryNote = "Unreadable" else pending = text
        }
    }
    Page {
        val waiting = pending
        Panel {
            Text("Backup", style = MaterialTheme.typography.titleLarge)
            Text(
                "One file of your profile, exercises, and logs.",
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (waiting != null) {
            Door(Icons.Filled.Folder, "Replace everything with this file", "Overwrites the workouts on this phone") {
                scope.launch {
                    val ok = model.importText(waiting)
                    pending = null
                    model.carryNote = if (ok) "Saved" else "Unreadable"
                }
            }
        } else {
            Door(Icons.Filled.Folder, "Export", "Save a workout.json file") { export.launch("workout.json") }
            Door(Icons.Filled.Folder, "Import", "Read a saved workout file") { open.launch(arrayOf("application/json", "text/*", "*/*")) }
            model.carryNote?.let {
                Panel { Text(it, style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}

@Composable
fun ReportsScreen(model: AppModel) {
    Page {
        Panel {
            Text("Reports", style = MaterialTheme.typography.titleLarge)
            Text(
                "Pick a report.",
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        WorkoutCard("This week", "Workouts this week, and what changed since last week.", icon = Icons.Filled.History) {
            model.open(Screen.Weekly)
        }
        WorkoutCard("One exercise", "Every recent session for a single exercise.", icon = Icons.Filled.FitnessCenter) {
            model.open(Screen.ReportExercises)
        }
        WorkoutCard("Bests", "Best weight, reps, time, distance, and pace.", icon = Icons.Filled.Timer) {
            model.open(Screen.Bests)
        }
    }
}

@Composable
private fun ProfilePhoto(avatar: String?, size: Dp, onClick: () -> Unit) {
    val bitmap = remember(avatar) {
        avatar?.let { encoded ->
            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }
    if (bitmap == null) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Person, contentDescription = "Profile", tint = MaterialTheme.colorScheme.primary)
        }
    } else {
        Image(
            bitmap,
            contentDescription = "Profile",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .clickable(onClick = onClick),
        )
    }
}

private fun cardTitle(summary: String): String {
    val parts = summary.split(" · ")
    return parts.getOrElse(1) { summary }
}

private fun cardDetail(summary: String): String {
    val parts = summary.split(" · ")
    if (parts.size < 3) return parts.firstOrNull().orEmpty()
    return parts.first() + " · " + parts.drop(2).joinToString(" · ")
}

internal fun trimNumber(value: Double): String =
    String.format(java.util.Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
