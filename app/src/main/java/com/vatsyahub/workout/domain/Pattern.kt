package com.vatsyahub.workout.domain

data class Pass(val kind: String, val times: Int)

const val PASS = "PASS"
const val EACH = "EACH"

val DefaultPasses = listOf(Pass(PASS, 1), Pass(EACH, 2), Pass(PASS, 3))

fun expand(exerciseCount: Int, passes: List<Pass>): List<Int> {
    if (exerciseCount <= 0) return emptyList()
    val out = ArrayList<Int>()
    for (pass in passes) {
        val times = pass.times.coerceAtLeast(1)
        if (pass.kind == EACH) {
            for (slot in 0 until exerciseCount) {
                repeat(times) { out += slot }
            }
        } else {
            repeat(times) {
                for (slot in 0 until exerciseCount) out += slot
            }
        }
    }
    return out
}

fun roundsOf(passes: List<Pass>): Int = passes.sumOf { it.times.coerceAtLeast(0) }

private val numberWords = listOf(
    "Zero", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
)

fun numberWord(n: Int): String = numberWords.getOrNull(n) ?: n.toString()

fun patternLabel(passes: List<Pass>): String {
    if (passes.isEmpty()) return "Pattern"
    return passes.joinToString(" · ") { pass ->
        if (pass.kind == EACH) {
            if (pass.times == 2) "Each twice" else "Each ${pass.times}"
        } else if (pass.times == 1) {
            "One pass"
        } else {
            "${numberWord(pass.times)} passes"
        }
    }
}

fun fingerprint(passes: List<Pass>): String = passes.joinToString("|") { "${it.kind}:${it.times}" }
