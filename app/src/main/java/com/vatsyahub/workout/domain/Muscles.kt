package com.vatsyahub.workout.domain

fun musclesOf(bodyPart: String): List<String> =
    bodyPart.split(',').map { it.trim() }.filter { it.isNotEmpty() }

fun matchesBodyPart(stored: String, asked: String): Boolean {
    val wants = musclesOf(asked)
    if (wants.isEmpty()) return false
    val has = musclesOf(stored)
    return wants.any { want -> has.any { it.equals(want, ignoreCase = true) } }
}

fun usesMovingHandles(name: String = "", muscles: String = "", types: String = "", use: String = ""): Boolean =
    listOf(name, muscles, types, use).any { it.contains("moving handles", ignoreCase = true) }

/** Arms, including a note such as "Arms (if moving handles are used)". Forearms stays. */
fun isArmsMuscle(token: String): Boolean {
    val trimmed = token.trim()
    if (trimmed.length < 4 || !trimmed.regionMatches(0, "arms", 0, 4, ignoreCase = true)) return false
    return trimmed.length == 4 || !trimmed[4].isLetter()
}

/**
 * Exercises that use moving handles do not list Arms. Every other muscle stays.
 * Exercises that do not use moving handles are left unchanged.
 */
fun musclesWithoutMovingHandleArms(
    muscles: String,
    name: String = "",
    types: String = "",
    use: String = "",
): String {
    if (!usesMovingHandles(name, muscles, types, use)) return muscles
    return musclesOf(muscles).filterNot(::isArmsMuscle).joinToString(", ")
}
