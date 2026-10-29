package com.vatsyahub.workout.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

data class Mark(
    val workoutId: Long,
    val day: LocalDate,
    val kind: String,
    val exerciseKey: String,
    val exerciseName: String,
    val topWeight: Double? = null,
    val totalReps: Int = 0,
    val repsAtTop: Int = 0,
    val workSec: Int? = null,
    val distanceM: Double? = null,
    val durationSec: Int? = null,
    val gradient: Double? = null,
    val climbM: Double? = null,
    val paceSec: Double? = null,
    val outingName: String? = null,
    val outingShape: String? = null,
)

data class WeekWorkout(val id: Long, val day: LocalDate)

data class ReportLine(val text: String, val workoutId: Long?)

private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

fun dayText(day: LocalDate): String = day.format(dayFormat)

fun weekStart(day: LocalDate): LocalDate = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

fun inLastThreeMonths(day: LocalDate, today: LocalDate): Boolean {
    val start = today.minusMonths(3)
    return !day.isBefore(start) && !day.isAfter(today)
}

fun weeklyReport(today: LocalDate, workouts: List<WeekWorkout>, marks: List<Mark>): List<ReportLine> {
    val thisStart = weekStart(today)
    val lastStart = thisStart.minusWeeks(1)
    val lastEnd = thisStart.minusDays(1)
    val thisCount = workouts.count { !it.day.isBefore(thisStart) && !it.day.isAfter(today) }
    val lastCount = workouts.count { !it.day.isBefore(lastStart) && !it.day.isAfter(lastEnd) }
    val lines = ArrayList<ReportLine>()
    lines += ReportLine("$thisCount this week", null)
    lines += ReportLine("$lastCount last week", null)
    val window = marks.filter { inLastThreeMonths(it.day, today) }
    val thisMarks = window.filter { !it.day.isBefore(thisStart) && !it.day.isAfter(today) }
    val lastMarks = window.filter { !it.day.isBefore(lastStart) && !it.day.isAfter(lastEnd) }
    val keys = LinkedHashSet<Pair<String, String>>()
    thisMarks.forEach { keys += it.exerciseKey to modeOf(it) }
    for ((key, mode) in keys) {
        val current = thisMarks.filter { it.exerciseKey == key && modeOf(it) == mode }
        val previous = lastMarks.filter { it.exerciseKey == key && modeOf(it) == mode }
        val line = when (mode) {
            "HEAVY" -> heavyWeekLine(current, previous)
            "BODY" -> bodyWeekLine(current, previous)
            "HIIT" -> hiitWeekLine(current, previous)
            "OUTING" -> outingWeekLine(current, previous)
            else -> null
        }
        if (line != null) lines += ReportLine(line, null)
    }
    return lines
}

private fun modeOf(mark: Mark): String = when {
    mark.kind == "OUTING" -> "OUTING"
    mark.kind == "HIIT" -> "HIIT"
    mark.topWeight != null -> "HEAVY"
    gymKind(mark.kind) -> "BODY"
    else -> mark.kind
}

private fun bestHeavy(marks: List<Mark>): Mark? =
    marks.filter { it.topWeight != null }
        .maxWithOrNull(compareBy<Mark> { it.topWeight ?: 0.0 }.thenBy { it.repsAtTop }.thenBy { it.day })

private fun bestReps(marks: List<Mark>): Mark? =
    marks.maxWithOrNull(compareBy<Mark> { it.totalReps }.thenBy { it.day })

private fun bestWork(marks: List<Mark>): Mark? =
    marks.filter { it.workSec != null }
        .maxWithOrNull(compareBy<Mark> { it.workSec ?: 0 }.thenBy { it.day })

private fun bestDistance(marks: List<Mark>): Mark? =
    marks.filter { it.distanceM != null }
        .maxWithOrNull(compareBy<Mark> { it.distanceM ?: 0.0 }.thenBy { it.day })

private fun heavyWeekLine(current: List<Mark>, previous: List<Mark>): String? {
    val now = bestHeavy(current) ?: return null
    val name = now.exerciseName
    val weight = now.topWeight ?: return null
    val then = bestHeavy(previous)
    if (then?.topWeight == null) return "$name · ${formatKg(weight)}"
    val before = then.topWeight
    return when {
        weight > before + 0.001 -> "$name · ${formatKg(weight)} · up from ${formatKg(before)}"
        weight < before - 0.001 -> "$name · ${formatKg(weight)} · down from ${formatKg(before)}"
        now.repsAtTop > then.repsAtTop ->
            "$name · ${formatKg(weight)} · ${now.repsAtTop} reps · up from ${then.repsAtTop}"
        now.repsAtTop < then.repsAtTop ->
            "$name · ${formatKg(weight)} · ${now.repsAtTop} reps · down from ${then.repsAtTop}"
        else -> "$name · ${formatKg(weight)} · same as last week"
    }
}

private fun bodyWeekLine(current: List<Mark>, previous: List<Mark>): String? {
    val now = bestReps(current) ?: return null
    val then = bestReps(previous)
    val name = now.exerciseName
    if (then == null) return "$name · ${now.totalReps} reps"
    return change("$name · ${now.totalReps} reps", now.totalReps, then.totalReps, "reps")
}

private fun hiitWeekLine(current: List<Mark>, previous: List<Mark>): String? {
    val now = bestWork(current) ?: return null
    val work = now.workSec ?: return null
    val then = bestWork(previous)?.workSec
    val name = now.exerciseName
    if (then == null) return "$name · $work s"
    return change("$name · $work s", work, then, "s")
}

private fun outingWeekLine(current: List<Mark>, previous: List<Mark>): String? {
    val now = bestDistance(current) ?: return null
    val distance = now.distanceM ?: return null
    val name = now.outingName ?: now.exerciseName
    val then = bestDistance(previous)?.distanceM
    val head = "$name · ${formatKm(distance)}"
    if (then == null) return head
    return when {
        distance > then + 0.5 -> "$head · up from ${formatKm(then)}"
        distance < then - 0.5 -> "$head · down from ${formatKm(then)}"
        else -> "$head · same as last week"
    }
}

private fun change(head: String, now: Int, then: Int, unit: String): String {
    val from = if (unit == "reps") then.toString() else "$then $unit"
    return when {
        now > then -> "$head · up from $from"
        now < then -> "$head · down from $from"
        else -> "$head · same as last week"
    }
}

fun exerciseReport(today: LocalDate, exerciseKey: String, marks: List<Mark>): List<ReportLine> {
    val rows = marks.filter { it.exerciseKey == exerciseKey && inLastThreeMonths(it.day, today) }
        .sortedWith(compareByDescending<Mark> { it.day }.thenByDescending { it.workoutId })
    if (rows.isEmpty()) return emptyList()
    val lines = ArrayList<ReportLine>()
    val best = bestLine(rows)
    if (best != null) lines += ReportLine(best, null)
    for (mark in rows) {
        val text = when {
            mark.kind == "HIIT" -> "${dayText(mark.day)} · ${mark.workSec ?: 0} s"
            mark.topWeight != null -> "${dayText(mark.day)} · ${formatKg(mark.topWeight)} · ${mark.totalReps} reps"
            else -> "${dayText(mark.day)} · ${mark.totalReps} reps"
        }
        lines += ReportLine(text, mark.workoutId)
    }
    return lines
}

private fun bestLine(rows: List<Mark>): String? {
    val heavy = bestHeavy(rows)
    if (heavy?.topWeight != null) return "Best · ${formatKg(heavy.topWeight)}"
    val body = rows.filter { gymKind(it.kind) && it.topWeight == null }
    val reps = bestReps(body)
    if (reps != null && reps.totalReps > 0) return "Best · ${reps.totalReps} reps"
    val work = bestWork(rows.filter { it.kind == "HIIT" })
    if (work?.workSec != null) return "Best · ${work.workSec} s"
    return null
}

fun bestsReport(today: LocalDate, marks: List<Mark>): List<ReportLine> {
    val window = marks.filter { inLastThreeMonths(it.day, today) }
    val lines = ArrayList<ReportLine>()
    val byExercise = window.filter { it.kind != "OUTING" }.groupBy { it.exerciseKey }
    val ordered = byExercise.entries.sortedBy { it.value.first().exerciseName.lowercase(Locale.US) }
    for ((_, group) in ordered) {
        val name = group.first().exerciseName
        val heavy = bestHeavy(group)
        if (heavy?.topWeight != null) {
            lines += ReportLine("$name · ${formatKg(heavy.topWeight)} · ${dayText(heavy.day)}", heavy.workoutId)
            val reps = bestReps(group.filter { gymKind(it.kind) })
            if (reps != null) {
                lines += ReportLine("$name · ${reps.totalReps} reps · ${dayText(reps.day)}", reps.workoutId)
            }
        } else {
            val body = bestReps(group.filter { gymKind(it.kind) })
            if (body != null && body.totalReps > 0) {
                lines += ReportLine("$name · ${body.totalReps} reps · ${dayText(body.day)}", body.workoutId)
            }
        }
        val work = bestWork(group.filter { it.kind == "HIIT" })
        if (work?.workSec != null) {
            lines += ReportLine("$name · ${work.workSec} s · ${dayText(work.day)}", work.workoutId)
        }
    }
    val outings = window.filter { it.kind == "OUTING" }.groupBy { it.outingName ?: it.exerciseName }
    for ((name, group) in outings.entries.sortedBy { it.key.lowercase(Locale.US) }) {
        val shape = group.first().outingShape
        val distance = bestDistance(group)
        if (shape == "HIKE") {
            val climb = bestClimb(group)
            if (climb?.climbM != null) {
                lines += ReportLine("$name · ${formatMetres(climb.climbM)} · ${dayText(climb.day)}", climb.workoutId)
            }
            continue
        }
        if (distance?.distanceM != null && shape != "HIKE") {
            lines += ReportLine("$name · ${formatKm(distance.distanceM)} · ${dayText(distance.day)}", distance.workoutId)
        }
        val pace = bestPace(group)
        if (pace?.paceSec != null) {
            val unit = if (shape == "SWIM") "/100 m" else "/km"
            lines += ReportLine(
                "$name · ${formatPace(pace.paceSec)} $unit · ${dayText(pace.day)}",
                pace.workoutId,
            )
        }
        if (shape == "RUN") {
            val grade = group.filter { it.gradient != null }
                .maxWithOrNull(compareBy<Mark> { it.gradient ?: 0.0 }.thenBy { it.day })
            if (grade?.gradient != null) {
                val text = String.format(Locale.US, "%.2f", grade.gradient).trimEnd('0').trimEnd('.')
                lines += ReportLine("$name · gradient $text · ${dayText(grade.day)}", grade.workoutId)
            }
        }
    }
    return lines
}

private fun gymKind(kind: String): Boolean =
    kind == "HEAVY" || kind == "BODYWEIGHT" || kind == "MIXED"

private fun bestPace(marks: List<Mark>): Mark? =
    marks.filter { it.paceSec != null }
        .minWithOrNull(compareBy<Mark> { it.paceSec ?: Double.MAX_VALUE }.thenByDescending { it.day })

private fun bestClimb(marks: List<Mark>): Mark? =
    marks.filter { it.climbM != null }
        .maxWithOrNull(
            compareBy<Mark> { it.climbM ?: 0.0 }
                .thenByDescending { it.durationSec ?: Int.MAX_VALUE }
                .thenBy { it.day },
        )
