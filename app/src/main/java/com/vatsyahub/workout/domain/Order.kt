package com.vatsyahub.workout.domain

data class DragSteps(val steps: Int, val offset: Float)

fun <T> moveItem(items: List<T>, from: Int, to: Int): List<T> {
    if (from == to || from !in items.indices || to !in items.indices) return items
    val next = items.toMutableList()
    val item = next.removeAt(from)
    next.add(to, item)
    return next
}

fun stepExercise(index: Int, count: Int, forward: Boolean): Int {
    if (count <= 0) return 0
    val current = index.coerceIn(0, count - 1)
    return if (forward) (current + 1).coerceAtMost(count - 1) else (current - 1).coerceAtLeast(0)
}

fun dragSteps(offset: Float, itemSpan: Float): DragSteps {
    if (itemSpan <= 0f) return DragSteps(0, 0f)
    var y = offset
    var steps = 0
    val threshold = itemSpan / 2f
    while (y > threshold) {
        y -= itemSpan
        steps += 1
    }
    while (y < -threshold) {
        y += itemSpan
        steps -= 1
    }
    return DragSteps(steps, y)
}
