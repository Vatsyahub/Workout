package com.vatsyahub.workout.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vatsyahub.workout.data.pool
import com.vatsyahub.workout.data.typesOf
import com.vatsyahub.workout.domain.parseDecimal
import kotlinx.coroutines.launch

private fun kindIcon(shape: String): ImageVector = when (shape) {
    "SWIM" -> Icons.Filled.Pool
    "RUN" -> Icons.AutoMirrored.Filled.DirectionsRun
    "HIKE" -> Icons.Filled.Terrain
    else -> Icons.AutoMirrored.Filled.DirectionsWalk
}

@Composable
fun HomeScreen(model: AppModel) {
    val snap by model.snap.collectAsState()
    val title = snap.profile.name.trim().ifBlank { "Home" }
    Page {
        Text(
            title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
        )
        Door(Icons.Filled.FitnessCenter, "Workout") { model.openWorkout() }
        Door(Icons.Filled.History, "Logs") { model.open(Screen.Logs) }
        Door(Icons.AutoMirrored.Filled.ViewList, "Exercises") { model.open(Screen.Exercises) }
        Door(Icons.Filled.Settings, "Settings") { model.open(Screen.Settings) }
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
    }
}

@Composable
fun LogTypeScreen(model: AppModel) {
    val snap by model.snap.collectAsState()
    Page {
        Door(Icons.Filled.FitnessCenter, "Heavy") { model.beginEmptyStrength("HEAVY") }
        Door(Icons.Filled.AccessibilityNew, "Bodyweight") { model.beginEmptyStrength("BODYWEIGHT") }
        Door(Icons.Filled.Timer, "HIIT") { model.newHiit(manual = true) }
        snap.kinds.forEach { kind ->
            Door(kindIcon(kind.shape), kind.name) { model.beginOutingLog(kind.id) }
        }
    }
}

@Composable
fun BuildScreen(model: AppModel) {
    val snap by model.snap.collectAsState()
    val build = model.build
    Page {
        build.parts.forEachIndexed { index, part ->
            Word(part.bodyPart.ifBlank { "Part" }) { model.openPicker(Picker.BodyPart(index)) }
            Editable(part.count.toString(), part.count.toString(), number = true) { text ->
                text.toIntOrNull()?.let { model.setPartCount(index, it) }
            }
        }
        if (build.parts.any { it.bodyPart.isNotBlank() }) {
            Word("Part") { model.addPart() }
        }
        Word("Draw") { model.drawBuild() }
        if (snap.pool(build.kind).isEmpty()) {
            Word("Exercises") { model.open(Screen.Exercises) }
        }
    }
}

@Composable
fun ExercisesScreen(model: AppModel) {
    val snap by model.snap.collectAsState()
    var step by remember { mutableStateOf("list") }
    var filter by remember { mutableStateOf("All") }
    var dimension by remember { mutableStateOf("part") }
    BackHandler(enabled = step != "list") { step = "list" }
    val parts = snap.exercises.map { it.bodyPart }.distinct().sortedBy { it.lowercase() }
    val typeNames = snap.exercises.flatMap { exercise -> snap.typesOf(exercise.id).map { it.name } }
        .distinct()
        .sortedBy { it.lowercase() }
    val filtered = snap.exercises.filter { exercise ->
        when (dimension) {
            "part" -> filter == "All" || exercise.bodyPart.equals(filter, true)
            else -> filter == "All" || snap.typesOf(exercise.id).any { it.name.equals(filter, true) }
        }
    }
    if (step == "by") {
        Page {
            Word("All") {
                filter = "All"
                step = "list"
            }
            Word("Body part") { step = "part" }
            Word("Workout type") { step = "type" }
        }
        return
    }
    if (step == "part" || step == "type") {
        val choices = if (step == "part") parts else typeNames
        Page {
            choices.forEach { name ->
                Word(name) {
                    dimension = if (step == "part") "part" else "type"
                    filter = name
                    step = "list"
                }
            }
        }
        return
    }
    ListPage {
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Word(filter) { step = "by" }
            }
            items(filtered, key = { it.id }) { exercise ->
                Word("${exercise.name} · ${exercise.bodyPart}") { model.open(Screen.Exercise(exercise.id)) }
            }
            item {
                Word("Add") { model.open(Screen.AddExercise) }
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
        Label("Usage")
        listOf("HEAVY" to "Heavy", "BODYWEIGHT" to "Bodyweight", "BOTH" to "Both").forEach { (value, label) ->
            ToggleRow(label, usage == value) { usage = value }
        }
        Label("Types")
        known.forEach { typeName ->
            ToggleRow(typeName, chosen.any { it.equals(typeName, true) }) { toggleType(typeName) }
        }
        Field("New type", draftType, onDone = {
            includeType(draftType)
            draftType = ""
        }) { draftType = it }
        Word("Add") {
            includeType(draftType)
            draftType = ""
        }
        if (exerciseId != null) {
            Word("Delete") { model.deleteExercise(exerciseId) }
        }
    }
}

@Composable
fun SettingsScreen(model: AppModel) {
    Page {
        Door(Icons.Filled.Person, "Profile") { model.open(Screen.Profile) }
        Door(Icons.Filled.Folder, "Carry") { model.open(Screen.Carry) }
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

internal fun trimNumber(value: Double): String =
    String.format(java.util.Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
