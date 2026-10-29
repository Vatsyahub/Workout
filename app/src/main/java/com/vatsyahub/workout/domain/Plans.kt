package com.vatsyahub.workout.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val ACTIVE_BLOCK = "A workout is already active."

@Serializable
data class PlanSet(val reps: Int, val weightKg: Double? = null)

@Serializable
data class PlanLine(
    val exerciseId: Long,
    val name: String,
    val sets: List<PlanSet> = emptyList(),
    val group: String = "",
    val groupKind: String = "PICK",
)

@Serializable
data class PlanCount(val label: String, val count: Int, val mode: String = "PART")

@Serializable
data class PlanPass(val kind: String, val times: Int)

@Serializable
data class PlanHiitSet(
    val exerciseIds: List<Long> = emptyList(),
    val names: List<String> = emptyList(),
    val passes: List<PlanPass> = emptyList(),
    val shared: Boolean = true,
    val splitSeconds: List<Int> = emptyList(),
    val breakSeconds: Int = 0,
)

@Serializable
data class PlanHiit(
    val workSec: Int,
    val restSec: Int,
    val breakSec: Int,
    val warmupSec: Int,
    val cooldownSec: Int,
    val template: List<PlanPass> = emptyList(),
    val sets: List<PlanHiitSet> = emptyList(),
)

@Serializable
data class PlanBody(
    val mode: String = "PICK",
    val counts: List<PlanCount> = emptyList(),
    val lines: List<PlanLine> = emptyList(),
    val hiit: PlanHiit? = null,
)

@Serializable
data class StrengthHold(
    val kind: String,
    val day: String,
    val durationSec: Int? = null,
    val started: Boolean = true,
    val focus: Int = 0,
    val lines: List<PlanLine> = emptyList(),
    val shown: List<List<Long>> = emptyList(),
    val sourcePlanId: Long? = null,
)

data class BuilderDraft(
    val kind: String,
    val mode: String,
    val counts: List<PlanCount> = emptyList(),
    val lines: List<PlanLine> = emptyList(),
    val hiit: PlanHiit? = null,
)

data class PlanRecord(
    val id: Long,
    val kind: String,
    val body: PlanBody,
)

data class LogRecord(
    val id: Long,
    val finished: Boolean,
    val kind: String = "",
    val favourite: Boolean = false,
)

data class HeldSession(
    val mode: String,
    val label: String,
    val payload: String? = null,
    val sourcePlanId: Long? = null,
)

data class WorkoutBook(
    val plans: List<PlanRecord> = emptyList(),
    val logs: List<LogRecord> = emptyList(),
    val favourites: List<Long> = emptyList(),
    val active: HeldSession? = null,
    val nextPlanId: Long = 1,
    val nextLogId: Long = 1,
)

@Serializable
data class SessionOrigin(val sourcePlanId: Long? = null)

sealed interface StartResult {
    data class Started(val book: WorkoutBook) : StartResult
    data class Rejected(val book: WorkoutBook, val message: String) : StartResult
}

private val planJson = Json { encodeDefaults = true; ignoreUnknownKeys = true }

fun encodePlan(body: PlanBody): String = planJson.encodeToString(body)

fun decodePlan(text: String): PlanBody? = try {
    planJson.decodeFromString<PlanBody>(text)
} catch (_: Exception) {
    null
}

fun encodeOrigin(id: Long): String = planJson.encodeToString(SessionOrigin(id))

fun decodeOrigin(text: String): Long? = try {
    planJson.decodeFromString<SessionOrigin>(text).sourcePlanId
} catch (_: Exception) {
    null
}

fun encodeStrengthHold(hold: StrengthHold): String = planJson.encodeToString(hold)

fun decodeStrengthHold(text: String): StrengthHold? = try {
    planJson.decodeFromString<StrengthHold>(text)
} catch (_: Exception) {
    null
}

fun planTitle(kind: String, body: PlanBody): String = when {
    kind == "HIIT" || body.hiit != null -> "HIIT"
    else -> strengthLabel(kind)
}

fun planDetail(body: PlanBody): String {
    body.hiit?.let { hiit ->
        val names = hiit.sets.flatMap { it.names }.filter { it.isNotBlank() }
        val listed = if (names.isEmpty()) {
            val sets = hiit.sets.size
            if (sets == 1) "1 set" else "$sets sets"
        } else {
            names.joinToString(", ")
        }
        return "$listed · ${hiit.warmupSec}s warmup · ${hiit.restSec}s rest · ${hiit.breakSec}s break · ${hiit.cooldownSec}s cooldown"
    }
    if (body.lines.isNotEmpty()) {
        return body.lines.joinToString(", ") { line -> line.name.ifBlank { "Exercise" } }
    }
    if (body.mode == "FILTER") {
        val count = body.counts.maxOfOrNull { it.count } ?: 0
        if (count < 1) return ""
        val noun = if (count == 1) "exercise" else "exercises"
        val labels = body.counts.map { it.label.trim() }.filter { it.isNotEmpty() }.distinct()
        return if (labels.isEmpty()) "$count $noun" else labels.joinToString(" · ") + " · $count $noun"
    }
    return body.counts.filter { it.count > 0 }.joinToString(", ") { "${it.label} ${it.count}" }
}

fun applyEditedSets(edited: List<PlanSet>): List<PlanSet> = edited.map { PlanSet(it.reps, it.weightKg) }

fun WorkoutBook.savePlanEdits(id: Long, body: PlanBody): WorkoutBook {
    if (plans.none { it.id == id }) return this
    val written = body.copy(lines = body.lines.map { line -> line.copy(sets = applyEditedSets(line.sets)) })
    return copy(plans = plans.map { plan -> if (plan.id == id) plan.copy(body = written) else plan })
}

fun WorkoutBook.deletePlan(id: Long): WorkoutBook =
    copy(plans = plans.filterNot { it.id == id })

fun WorkoutBook.saveBuilderPlan(draft: BuilderDraft): WorkoutBook {
    val plan = PlanRecord(
        id = nextPlanId,
        kind = draft.kind,
        body = PlanBody(
            mode = draft.mode,
            counts = draft.counts,
            lines = draft.lines,
            hiit = draft.hiit,
        ),
    )
    return copy(plans = plans + plan, nextPlanId = nextPlanId + 1)
}

fun WorkoutBook.openPlan(id: Long): StartResult {
    val plan = plans.find { it.id == id } ?: return StartResult.Rejected(this, "Missing")
    if (active != null) return StartResult.Rejected(this, ACTIVE_BLOCK)
    val mode = if (plan.kind == "HIIT" || plan.body.hiit != null) "HIIT" else "STRENGTH"
    return StartResult.Started(
        copy(active = HeldSession(mode, planTitle(plan.kind, plan.body), sourcePlanId = plan.id)),
    )
}

fun WorkoutBook.startWorkout(mode: String, label: String): StartResult {
    if (active != null) return StartResult.Rejected(this, ACTIVE_BLOCK)
    return StartResult.Started(copy(active = HeldSession(mode, label)))
}

fun WorkoutBook.finishActive(): WorkoutBook {
    val session = active ?: return this
    val origin = session.sourcePlanId
    return copy(
        active = null,
        plans = if (origin == null) plans else plans.filterNot { it.id == origin },
        logs = logs + LogRecord(nextLogId, finished = true, kind = session.mode),
        nextLogId = nextLogId + 1,
    )
}

fun WorkoutBook.editPlanBody(id: Long, body: PlanBody): WorkoutBook = savePlanEdits(id, body)

fun WorkoutBook.editPlan(id: Long, lines: List<PlanLine>): WorkoutBook {
    val plan = plans.find { it.id == id } ?: return this
    return savePlanEdits(id, plan.body.copy(lines = lines))
}

fun WorkoutBook.restored(): HeldSession? = active

fun restoreSession(session: HeldSession?): HeldSession? {
    if (session == null) return null
    val payload = session.payload ?: return session
    if (session.mode != "STRENGTH") return session
    val hold = decodeStrengthHold(payload) ?: return null
    return session.copy(label = strengthLabel(hold.kind), payload = encodeStrengthHold(hold))
}
