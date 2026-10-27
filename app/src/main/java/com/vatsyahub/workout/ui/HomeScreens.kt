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
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vatsyahub.workout.data.localDay
import com.vatsyahub.workout.data.pool
import com.vatsyahub.workout.data.summary
import com.vatsyahub.workout.data.typesOf
import com.vatsyahub.workout.domain.parseDecimal
import com.vatsyahub.workout.domain.weekStart
import java.time.LocalDate
import kotlinx.coroutines.launch

private val BuiltInOutings = listOf("Swim", "Run", "Hike")

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
    val extraKinds = snap.kinds.filter { kind -> BuiltInOutings.none { it.equals(kind.name, true) } }
    Page {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
            ProfilePhoto(snap.profile.avatar, 52.dp) { model.tab(Screen.Settings) }
            Text(
                if (name.isBlank()) "Workout" else name,
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(start = 14.dp),
            )
        }
        Text("Start a session, or look back at the week.", color = MaterialTheme.colorScheme.secondary)
        if (model.clock != null || model.timer != null) {
            PrimaryButton(if (model.clock != null) "Resume HIIT" else "Resume outing") { model.openWorkout() }
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
            StartCard(Icons.Filled.Pool, "Swim", "In the water", Modifier.weight(1f)) { model.startNamedOuting("Swim") }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) {
            StartCard(Icons.AutoMirrored.Filled.DirectionsRun, "Run", "On the road", Modifier.weight(1f)) {
                model.startNamedOuting("Run")
            }
            StartCard(Icons.Filled.Terrain, "Hike", "On a trail", Modifier.weight(1f)) { model.startNamedOuting("Hike") }
        }
        extraKinds.forEach { kind ->
            Door(kindIcon(kind.shape), kind.name) { model.startOuting(kind.id) }
        }
        Door(Icons.Filled.Edit, "Other outing") { model.open(Screen.AddKind) }
        SectionLabel("Recent")
        if (recent.isEmpty()) {
            Text("Finished sessions show up here.", color = MaterialTheme.colorScheme.secondary)
        } else {
            recent.forEach { workout ->
                val summary = snap.summary(workout)
                WorkoutCard(cardTitle(summary), cardDetail(summary)) { model.open(Screen.LogDetail(workout.id)) }
            }
        }
        QuietButton("Log a past workout") { model.open(Screen.LogType) }
    }
}

@Composable
fun WorkoutScreen(model: AppModel) {
    val snap by model.snap.collectAsState()
    Page {
        Door(Icons.Filled.FitnessCenter, "Heavy") { model.beginBuild("HEAVY") }
        Door(Icons.Filled.AccessibilityNew, "Bodyweight") { model.beginBuild("BODYWEIGHT") }
        Door(Icons.Filled.Timer, "HIIT") { model.newHiit(manual = false) }
        snap.kinds.forEach { kind ->
            Door(kindIcon(kind.shape), kind.name) { model.startOuting(kind.id) }
        }
        Door(Icons.Filled.Edit, "Kind") { model.open(Screen.AddKind) }
        Door(Icons.Filled.History, "Log") { model.open(Screen.LogType) }
    }
}

@Composable
fun AddKindScreen(model: AppModel) {
    var name by remember { mutableStateOf("") }
    FormPage(onSave = {
        if (name.isNotBlank()) model.addKind(name.trim())
    }) {
        Field("Name", name) { name = it }
        Text(
            "Swim, Run, and Hike are already on Home. Use this for another outing.",
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
fun LogTypeScreen(model: AppModel) {
    val snap by model.snap.collectAsState()
    val extra = snap.kinds.filter { kind -> BuiltInOutings.none { it.equals(kind.name, true) } }
    Page {
        Text(
            "For a session that is already finished.",
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
        )
        Door(Icons.Filled.FitnessCenter, "Heavy") { model.beginEmptyStrength("HEAVY") }
        Door(Icons.Filled.AccessibilityNew, "Bodyweight") { model.beginEmptyStrength("BODYWEIGHT") }
        Door(Icons.Filled.Timer, "HIIT") { model.newHiit(manual = true) }
        Door(Icons.Filled.Pool, "Swim") { model.logNamedOuting("Swim") }
        Door(Icons.AutoMirrored.Filled.DirectionsRun, "Run") { model.logNamedOuting("Run") }
        Door(Icons.Filled.Terrain, "Hike") { model.logNamedOuting("Hike") }
        extra.forEach { kind ->
            Door(kindIcon(kind.shape), kind.name) { model.beginOutingLog(kind.id) }
        }
    }
}

@Composable
fun BuildScreen(model: AppModel) {
    val snap by model.snap.collectAsState()
    val build = model.build
    val pool = snap.pool(build.kind)
    if (pool.isEmpty()) {
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
    ActionPage("Build", { model.drawBuild() }) {
        val byType = build.mode == "TYPE"
        Text(
            if (byType) {
                "Choose how many exercises per workout type. 0 skips that type."
            } else {
                "Choose how many exercises per body part. 0 skips that part."
            },
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
        )
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            ChoiceChip("Body part", !byType) { model.setBuildMode("PART") }
            ChoiceChip("Workout type", byType) { model.setBuildMode("TYPE") }
        }
        if (build.parts.isEmpty()) {
            Panel {
                Text(
                    if (byType) {
                        "No workout types yet. Add a type on an exercise, then come back."
                    } else {
                        "These exercises have no body part yet."
                    },
                )
            }
        }
        build.parts.forEachIndexed { index, part ->
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (byType) "Workout type" else "Body part",
                            color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.labelLarge,
                        )
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
    }
}

@Composable
fun ExercisesScreen(model: AppModel) {
    val snap by model.snap.collectAsState()
    var filter by remember { mutableStateOf("All") }
    var byType by remember { mutableStateOf(false) }
    val parts = snap.exercises.map { it.bodyPart }.filter { it.isNotBlank() }.distinct().sortedBy { it.lowercase() }
    val typeNames = snap.exercises.flatMap { exercise -> snap.typesOf(exercise.id).map { it.name } }
        .distinct()
        .sortedBy { it.lowercase() }
    val chips = if (byType) typeNames else parts
    val filtered = snap.exercises.filter { exercise ->
        if (filter == "All") return@filter true
        if (byType) snap.typesOf(exercise.id).any { it.name.equals(filter, true) }
        else exercise.bodyPart.equals(filter, true)
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
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 14.dp, bottom = 8.dp),
                ) {
                    ChoiceChip("All", filter == "All") { filter = "All" }
                    chips.forEach { name ->
                        ChoiceChip(name, filter.equals(name, true)) { filter = name }
                    }
                    ChoiceChip(if (byType) "Body parts" else "Types", false) {
                        byType = !byType
                        filter = "All"
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
                WorkoutCard(exercise.name, listOf(exercise.bodyPart, usage).filter { it.isNotBlank() }.joinToString(" · ")) {
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
        Field("Name", name) { name = it }
        Field("Body part", body) { body = it }
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
        Door(Icons.Filled.Person, "Edit profile") { model.open(Screen.Profile) }
        Door(Icons.Filled.History, "Reports") { model.open(Screen.Reports) }
        Door(Icons.Filled.Folder, "Backup") { model.open(Screen.Carry) }
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
        if (bitmap == null) {
            Word("Photo") { pick.launch("image/*") }
        } else {
            Image(
                bitmap,
                contentDescription = "Photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(96.dp)
                    .clip(CircleShape)
                    .clickable { pick.launch("image/*") },
            )
        }
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
        if (waiting != null) {
            Word("Replace") {
                scope.launch {
                    val ok = model.importText(waiting)
                    pending = null
                    model.carryNote = if (ok) "Saved" else "Unreadable"
                }
            }
        } else {
            Word("Export") { export.launch("workout.json") }
            Word("Import") { open.launch(arrayOf("application/json", "text/*", "*/*")) }
            model.carryNote?.let { Text(it, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 12.dp)) }
        }
    }
}

@Composable
fun ReportsScreen(model: AppModel) {
    Page {
        Word("Weekly") { model.open(Screen.Weekly) }
        Word("Exercise") { model.open(Screen.ReportExercises) }
        Word("Bests") { model.open(Screen.Bests) }
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
