package com.vatsyahub.workout.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Ink = Color(0xFF0E0E0E)
private val Paper = Color(0xFFF4F4F4)
private val Mist = Color(0xFFA9A9A9)

private val Colors = darkColorScheme(
    background = Ink,
    surface = Ink,
    onBackground = Paper,
    onSurface = Paper,
    primary = Paper,
    onPrimary = Ink,
    secondary = Mist,
    onSecondary = Ink,
)

@Composable
fun WorkoutTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
