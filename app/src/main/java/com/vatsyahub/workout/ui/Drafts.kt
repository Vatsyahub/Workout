package com.vatsyahub.workout.ui

import com.vatsyahub.workout.domain.DefaultPasses
import com.vatsyahub.workout.domain.Pass
import com.vatsyahub.workout.domain.expand
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong

private val lineKeys = AtomicLong(1)

fun lineKey(): Long = lineKeys.getAndIncrement()

data class DraftSet(val reps: Int, val weightKg: Double?)

data class StrengthLine(
    val exerciseId: Long,
    val sets: List<DraftSet>,
    val group: String = "",
    val groupKind: String = "PART",
    val key: Long = lineKey(),
    val setsEdited: Boolean = false,
    val shownIds: Set<Long> = emptySet(),
)

data class StrengthDraft(
    val kind: String,
    val day: LocalDate,
    val durationSec: Int?,
    val lines: List<StrengthLine>,
    val started: Boolean = false,
    val focus: Int = 0,
    val logging: Boolean = false,
)

data class PartAsk(val bodyPart: String, val count: Int)

fun openingDraftSets(): List<DraftSet> = List(3) { DraftSet(8, null) }

data class HiitSetDraft(
    val passes: List<Pass>,
    val shared: Boolean,
    val exerciseIds: List<Long>,
    val splitSeconds: List<Int>,
    val breakSeconds: Int,
    val shownIds: List<Set<Long>> = emptyList(),
)

data class HiitDraft(
    val day: LocalDate,
    val workSec: Int,
    val restSec: Int,
    val breakSec: Int,
    val template: List<Pass>,
    val sets: List<HiitSetDraft>,
    val manual: Boolean,
    val warmupSec: Int = 120,
    val cooldownSec: Int = 300,
)

data class OutingDraft(
    val kindId: Long,
    val day: LocalDate,
    val durationSec: Int?,
    val distanceM: Double?,
    val gradient: Double?,
    val location: String?,
    val bottomM: Double?,
    val topM: Double?,
    val bothWays: Boolean?,
    val fields: Map<Long, String>,
)

fun HiitSetDraft.aligned(workSec: Int, breakSec: Int): HiitSetDraft {
    val count = expand(exerciseIds.size, passes).size
    val splits = List(count) { index -> splitSeconds.getOrElse(index) { workSec } }
    return copy(splitSeconds = splits, breakSeconds = breakSec)
}

fun retargetSplits(splits: List<Int>, count: Int, oldWork: Int, newWork: Int): List<Int> =
    List(count) { index ->
        val current = splits.getOrNull(index)
        if (current == null || current == oldWork) newWork else current
    }

fun freshHiitSet(passes: List<Pass>, ids: List<Long>, workSec: Int, breakSec: Int, shared: Boolean): HiitSetDraft =
    HiitSetDraft(
        passes = passes,
        shared = shared,
        exerciseIds = ids,
        splitSeconds = List(expand(ids.size, passes).size) { workSec },
        breakSeconds = breakSec,
    )

fun defaultTemplate(): List<Pass> = DefaultPasses
