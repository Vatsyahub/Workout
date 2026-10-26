package com.vatsyahub.workout.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun AppRoot(model: AppModel = viewModel()) {
    val screen = model.stack.lastOrNull() ?: Screen.Home
    BackHandler(enabled = model.stack.size > 1) { model.back() }
    when (screen) {
        Screen.Home -> HomeScreen(model)
        Screen.Workout -> WorkoutScreen(model)
        Screen.AddKind -> AddKindScreen(model)
        Screen.LogType -> LogTypeScreen(model)
        Screen.Build -> BuildScreen(model)
        Screen.Strength -> StrengthScreen(model)
        Screen.Hiit -> HiitScreen(model)
        Screen.PatternPick -> PatternPickScreen(model)
        Screen.PatternEdit -> PatternEditScreen(model)
        is Screen.SetMenu -> SetMenuScreen(model, screen.index)
        Screen.Clock -> ClockScreen(model)
        Screen.Picker -> PickerScreen(model)
        Screen.OutingTimer -> OutingTimerScreen(model)
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
