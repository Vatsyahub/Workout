package com.vatsyahub.workout.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
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

private val DarkCanvas = Color(0xFF121418)
private val DarkCard = Color(0xFF1C2028)
private val DarkInk = Color(0xFFF2F4F8)
private val DarkMuted = Color(0xFFA8B0BC)
private val DarkAccent = Color(0xFF4C8DFF)
private val DarkAccentSoft = Color(0xFF2A3548)
private val DarkLine = Color(0xFF3A4250)

private val DarkColors = darkColorScheme(
    background = DarkCanvas,
    surface = DarkCard,
    surfaceVariant = DarkAccentSoft,
    surfaceContainer = DarkCard,
    surfaceContainerLow = Color(0xFF171B22),
    surfaceContainerHigh = Color(0xFF262B36),
    surfaceContainerHighest = DarkAccentSoft,
    onBackground = DarkInk,
    onSurface = DarkInk,
    onSurfaceVariant = DarkMuted,
    primary = DarkAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1A3F7A),
    onPrimaryContainer = DarkInk,
    secondary = DarkMuted,
    onSecondary = DarkCanvas,
    secondaryContainer = Color(0xFF243044),
    onSecondaryContainer = DarkInk,
    outline = DarkLine,
    error = Color(0xFFFF8A80),
    onError = Color(0xFF2A0A0A),
)

@Composable
fun WorkoutTheme(dark: Boolean = false, content: @Composable () -> Unit) {
    val colors = if (dark) DarkColors else Colors
    MaterialTheme(colorScheme = colors) {
        CompositionLocalProvider(LocalContentColor provides colors.onBackground, content = content)
    }
}
