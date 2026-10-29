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

fun dropIndex(from: Int, count: Int, offsetY: Float, spans: List<Float>): Int {
    if (count <= 0) return 0
    val start = from.coerceIn(0, count - 1)
    val span = spans.getOrElse(start) { 0f }.takeIf { it > 1f } ?: 160f
    return (start + dragSteps(offsetY, span).steps).coerceIn(0, count - 1)
}

fun shiftForDrag(index: Int, from: Int, target: Int, draggedSpan: Float): Float {
    if (from < 0 || index == from || draggedSpan <= 0f || target == from) return 0f
    if (target > from && index in (from + 1)..target) return -draggedSpan
    if (target < from && index in target until from) return draggedSpan
    return 0f
}

fun orderNumber(index: Int, from: Int, target: Int): Int {
    val place = when {
        from < 0 || target < 0 || from == target -> index
        index == from -> target
        target > from && index in (from + 1)..target -> index - 1
        target < from && index in target until from -> index + 1
        else -> index
    }
    return place + 1
}
