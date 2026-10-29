package com.vatsyahub.workout.domain

fun musclesOf(bodyPart: String): List<String> =
    bodyPart.split(',').map { it.trim() }.filter { it.isNotEmpty() }

fun matchesBodyPart(stored: String, asked: String): Boolean {
    val wants = musclesOf(asked)
    if (wants.isEmpty()) return false
    val has = musclesOf(stored)
    return wants.any { want -> has.any { it.equals(want, ignoreCase = true) } }
}
