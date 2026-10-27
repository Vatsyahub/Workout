package com.vatsyahub.workout.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val Canvas = Color(0xFFF2F3F5)
private val Card = Color(0xFFFFFFFF)
private val Ink = Color(0xFF16181D)
private val Muted = Color(0xFF5C6570)
private val Accent = Color(0xFF1F6FEB)
private val AccentSoft = Color(0xFFE7F0FF)
private val Line = Color(0xFFE3E6EB)

private val Colors = lightColorScheme(
    background = Canvas,
    surface = Card,
    surfaceVariant = AccentSoft,
    onBackground = Ink,
    onSurface = Ink,
    primary = Accent,
    onPrimary = Color.White,
    secondary = Muted,
    onSecondary = Color.White,
    outline = Line,
    error = Color(0xFFB42318),
)

@Composable
fun WorkoutTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors) {
        CompositionLocalProvider(LocalContentColor provides Colors.onBackground, content = content)
    }
}
