package com.vatsyahub.workout.domain

data class LoggedLoad(
    val exerciseId: Long,
    val sets: List<SetLoad>,
)

data class LoggedSession(
    val id: Long,
    val kind: String,
    val favourite: Boolean,
    val durationSec: Int?,
    val lines: List<LoggedLoad>,
)

data class StrengthPlan(
    val kind: String,
    val durationSec: Int?,
    val lines: List<LoggedLoad>,
)

fun withFavourite(session: LoggedSession, favourite: Boolean): LoggedSession =
    session.copy(
        favourite = favourite,
        lines = session.lines.map { line ->
            LoggedLoad(line.exerciseId, line.sets.map { SetLoad(it.reps, it.weightKg) })
        },
    )

fun repeatStrength(session: LoggedSession): StrengthPlan =
    StrengthPlan(
        kind = session.kind,
        durationSec = null,
        lines = session.lines.map { line ->
            LoggedLoad(line.exerciseId, line.sets.map { SetLoad(it.reps, it.weightKg) })
        },
    )
