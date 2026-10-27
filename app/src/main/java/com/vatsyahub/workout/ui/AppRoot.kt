package com.vatsyahub.workout.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vatsyahub.workout.domain.strengthLabel

@Composable
fun AppRoot(model: AppModel = viewModel()) {
    val screen = model.stack.lastOrNull() ?: Screen.Home
    val immersive = screen == Screen.Clock || screen == Screen.OutingTimer
    BackHandler(enabled = model.stack.size > 1 && !immersive) { model.back() }
    if (immersive) {
        ScreenBody(model, screen)
        return
    }
    val tab = tabOf(screen)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (screen !in RootTabs) {
                Bar(titleOf(screen, model)) { model.back() }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = tab == "home",
                    onClick = { model.tab(Screen.Home) },
                    icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                    label = { Text("Home") },
                )
                NavigationBarItem(
                    selected = tab == "history",
                    onClick = { model.tab(Screen.Logs) },
                    icon = { Icon(Icons.Filled.History, contentDescription = "History") },
                    label = { Text("History") },
                )
                NavigationBarItem(
                    selected = tab == "exercises",
                    onClick = { model.tab(Screen.Exercises) },
                    icon = { Icon(Icons.Filled.FitnessCenter, contentDescription = "Exercises") },
                    label = { Text("Exercises") },
                )
                NavigationBarItem(
                    selected = tab == "profile",
                    onClick = { model.tab(Screen.Settings) },
                    icon = { Icon(Icons.Filled.Person, contentDescription = "Profile") },
                    label = { Text("Profile") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            ScreenBody(model, screen)
        }
    }
}

private val RootTabs = setOf(Screen.Home, Screen.Logs, Screen.Exercises, Screen.Settings)

private fun tabOf(screen: Screen): String = when (screen) {
    Screen.Logs, is Screen.Month, is Screen.LogDetail, Screen.Reports, Screen.Weekly,
    Screen.ReportExercises, is Screen.ExerciseReport, Screen.Bests,
    -> "history"
    Screen.Exercises, Screen.AddExercise, is Screen.Exercise -> "exercises"
    Screen.Settings, Screen.Profile, Screen.Carry -> "profile"
    else -> "home"
}

private fun titleOf(screen: Screen, model: AppModel): String = when (screen) {
    Screen.Workout -> "Workout"
    Screen.AddKind -> "New outing"
    Screen.LogType -> "Log a workout"
    Screen.Build -> strengthLabel(model.build.kind)
    Screen.Strength -> strengthLabel(model.strength?.kind ?: "HEAVY")
    Screen.StrengthTime -> "Total time"
    Screen.Hiit -> "HIIT"
    Screen.PatternPick -> "Order"
    Screen.PatternEdit -> "Edit order"
    is Screen.SetMenu -> "Set"
    Screen.Picker -> if (model.picker is Picker.StrengthSwap || model.picker is Picker.PickSwap) {
        "Choose a replacement"
    } else {
        "Choose exercise"
    }
    Screen.OutingForm -> "Outing"
    is Screen.Month -> "History"
    is Screen.LogDetail -> "Workout"
    Screen.AddExercise -> "New exercise"
    is Screen.Exercise -> "Exercise"
    Screen.Profile -> "Profile"
    Screen.Carry -> "Backup"
    Screen.Reports -> "Reports"
    Screen.Weekly -> "This week"
    Screen.ReportExercises -> "Exercises"
    is Screen.ExerciseReport -> screen.title
    Screen.Bests -> "Bests"
    is Screen.OutingReady -> screen.name
    else -> ""
}

@Composable
private fun Bar(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun ScreenBody(model: AppModel, screen: Screen) {
    when (screen) {
        Screen.Home -> HomeScreen(model)
        Screen.Workout -> WorkoutScreen(model)
        Screen.AddKind -> AddKindScreen(model)
        Screen.LogType -> LogTypeScreen(model)
        Screen.Build -> BuildScreen(model)
        Screen.Strength -> StrengthScreen(model)
        Screen.StrengthTime -> StrengthTimeScreen(model)
        Screen.Hiit -> HiitScreen(model)
        Screen.PatternPick -> PatternPickScreen(model)
        Screen.PatternEdit -> PatternEditScreen(model)
        is Screen.SetMenu -> SetMenuScreen(model, screen.index)
        Screen.Clock -> ClockScreen(model)
        Screen.Picker -> PickerScreen(model)
        Screen.OutingTimer -> OutingTimerScreen(model)
        is Screen.OutingReady -> OutingReadyScreen(model, screen.name)
        Screen.OutingForm -> OutingFormScreen(model)
        Screen.Logs -> LogsScreen(model)
        is Screen.Month -> MonthScreen(model, screen.year, screen.month)
        is Screen.LogDetail -> LogDetailScreen(model, screen.id)
        Screen.Exercises -> ExercisesScreen(model)
        Screen.AddExercise -> ExerciseFormScreen(model, null)
        is Screen.Exercise -> ExerciseFormScreen(model, screen.id)
        Screen.Settings -> SettingsScreen(model)
        Screen.Profile -> ProfileScreen(model)
        Screen.Carry -> CarryScreen(model)
        Screen.Reports -> ReportsScreen(model)
        Screen.Weekly -> WeeklyScreen(model)
        Screen.ReportExercises -> ReportExercisesScreen(model)
        is Screen.ExerciseReport -> ExerciseReportScreen(model, screen.key, screen.title)
        Screen.Bests -> BestsScreen(model)
    }
}
