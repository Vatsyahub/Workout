package com.vatsyahub.workout.domain

const val PHASE_WORK = "WORK"
const val PHASE_REST = "REST"
const val PHASE_BREAK = "BREAK"
const val PHASE_WARMUP = "WARMUP"
const val PHASE_COOLDOWN = "COOLDOWN"

data class Cue(
    val phase: String,
    val setIndex: Int,
    val slot: Int?,
    val round: Int?,
    val seconds: Int,
)

fun timeline(
    exerciseCounts: List<Int>,
    patterns: List<List<Pass>>,
    workSec: Int,
    restSec: Int,
    breakSec: Int,
    warmupSec: Int = 0,
    cooldownSec: Int = 0,
): List<Cue> {
    val cues = ArrayList<Cue>()
    if (warmupSec > 0) cues += Cue(PHASE_WARMUP, 0, null, null, warmupSec)
    val last = exerciseCounts.lastIndex
    exerciseCounts.forEachIndexed { setIndex, count ->
        val pattern = patterns.getOrElse(setIndex) { DefaultPasses }
        val sequence = expand(count, pattern)
        val seen = IntArray(count.coerceAtLeast(0))
        for (slot in sequence) {
            if (slot !in seen.indices) continue
            seen[slot] += 1
            cues += Cue(PHASE_WORK, setIndex, slot, seen[slot], workSec)
            if (restSec > 0) cues += Cue(PHASE_REST, setIndex, slot, seen[slot], restSec)
        }
        if (setIndex != last && sequence.isNotEmpty() && breakSec > 0) {
            cues += Cue(PHASE_BREAK, setIndex, null, null, breakSec)
        }
    }
    if (cooldownSec > 0 && cues.any { it.phase == PHASE_WORK }) {
        cues += Cue(PHASE_COOLDOWN, last.coerceAtLeast(0), null, null, cooldownSec)
    }
    return cues
}

data class ClockPos(val index: Int, val remainingMs: Long, val finished: Boolean)

fun catchUp(durationsMs: List<Long>, index: Int, remainingMs: Long, elapsedMs: Long): ClockPos {
    if (index >= durationsMs.size) return ClockPos(durationsMs.size, 0, true)
    var cursor = index
    var left = elapsedMs.coerceAtLeast(0)
    var remaining = remainingMs.coerceAtLeast(0)
    if (remaining > left) return ClockPos(cursor, remaining - left, false)
    left -= remaining
    cursor += 1
    while (cursor < durationsMs.size) {
        val duration = durationsMs[cursor]
        if (left < duration) return ClockPos(cursor, duration - left, false)
        left -= duration
        cursor += 1
    }
    return ClockPos(durationsMs.size, 0, true)
}
