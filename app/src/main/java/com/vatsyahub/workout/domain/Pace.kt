package com.vatsyahub.workout.domain

import java.util.Locale
import kotlin.math.roundToInt

fun swimPaceSeconds(distanceM: Double, durationSec: Int): Double? {
    if (distanceM <= 0.0 || durationSec <= 0) return null
    return durationSec / (distanceM / 100.0)
}

fun kmPaceSeconds(distanceM: Double, durationSec: Int): Double? {
    if (distanceM <= 0.0 || durationSec <= 0) return null
    return durationSec / (distanceM / 1000.0)
}

fun climbMetres(bottom: Double, top: Double): Double = (top - bottom).coerceAtLeast(0.0)

fun coveredMetres(climb: Double, bothWays: Boolean): Double = if (bothWays) climb * 2 else climb

fun shapeForKindName(name: String): String = when (name.trim().lowercase(Locale.US)) {
    "swim" -> "SWIM"
    "run" -> "RUN"
    "hike" -> "HIKE"
    else -> "CUSTOM"
}

fun formatPace(seconds: Double): String {
    val total = seconds.roundToInt().coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

fun formatKg(kg: Double): String {
    val text = String.format(Locale.US, "%.2f", kg).trimEnd('0').trimEnd('.')
    return "$text kg"
}

fun formatKm(meters: Double): String {
    val km = meters / 1000.0
    val text = String.format(Locale.US, "%.2f", km).trimEnd('0').trimEnd('.')
    return "$text km"
}

fun formatMetres(meters: Double): String {
    val text = String.format(Locale.US, "%.1f", meters).trimEnd('0').trimEnd('.')
    return "$text m"
}

fun formatDuration(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    val secs = safe % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, secs) else "%d:%02d".format(minutes, secs)
}

fun formatBreak(seconds: Int): String {
    if (seconds % 60 == 0) return "${seconds / 60} min"
    return formatDuration(seconds)
}

fun parseDecimal(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

fun parseDistanceToMeters(text: String): Double? {
    val raw = text.trim().lowercase(Locale.US).replace(',', '.')
    if (raw.isEmpty()) return null
    return when {
        raw.endsWith("km") -> raw.removeSuffix("km").trim().toDoubleOrNull()?.times(1000.0)
        raw.endsWith("m") -> raw.removeSuffix("m").trim().toDoubleOrNull()
        else -> raw.toDoubleOrNull()?.times(1000.0)
    }
}

fun parseDuration(text: String, defaultMinutes: Boolean): Int? {
    val raw = text.trim()
    if (raw.isEmpty()) return null
    if (raw.contains(':')) {
        val parts = raw.split(':')
        if (parts.size == 2) {
            val minutes = parts[0].toIntOrNull() ?: return null
            val seconds = parts[1].toIntOrNull() ?: return null
            if (seconds !in 0..59) return null
            return minutes * 60 + seconds
        }
        if (parts.size == 3) {
            val hours = parts[0].toIntOrNull() ?: return null
            val minutes = parts[1].toIntOrNull() ?: return null
            val seconds = parts[2].toIntOrNull() ?: return null
            if (minutes !in 0..59 || seconds !in 0..59) return null
            return hours * 3600 + minutes * 60 + seconds
        }
        return null
    }
    val number = raw.replace(',', '.').toDoubleOrNull() ?: return null
    val seconds = if (defaultMinutes) number * 60.0 else number
    return seconds.roundToInt().coerceAtLeast(0)
}
