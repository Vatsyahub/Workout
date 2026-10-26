package com.vatsyahub.workout.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val Ink = Color(0xFF111111)
private val Paper = Color(0xFFFFFFFF)
private val Quiet = Color(0xFF3A3A3A)

private val Colors = lightColorScheme(
    background = Paper,
    surface = Paper,
    onBackground = Ink,
    onSurface = Ink,
    primary = Ink,
    onPrimary = Paper,
    secondary = Quiet,
    onSecondary = Paper,
)

@Composable
fun WorkoutTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors) {
        CompositionLocalProvider(LocalContentColor provides Colors.onBackground, content = content)
    }
}
