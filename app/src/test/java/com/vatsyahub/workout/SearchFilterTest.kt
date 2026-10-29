package com.vatsyahub.workout

import com.vatsyahub.workout.domain.SearchExercise
import com.vatsyahub.workout.domain.SearchFilters
import com.vatsyahub.workout.domain.filterExercises
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchFilterTest {
    private val exercises = listOf(
        SearchExercise(1, "Bench Press", "Chest, Triceps", listOf("Push")),
        SearchExercise(2, "Incline Press", "Chest, Shoulders", listOf("Push")),
        SearchExercise(3, "Chest Fly", "Chest", listOf("Isolation")),
        SearchExercise(4, "Row", "Back, Biceps", listOf("Pull")),
        SearchExercise(5, "Press Up", "Chest, Arms", listOf("Push")),
        SearchExercise(6, "Lat Pulldown", "Back, Biceps", listOf("Pull")),
    )

    @Test
    fun nameBodyPartAndWorkoutTypeNarrowTogether() {
        assertEquals(
            listOf("Press Up", "Bench Press", "Incline Press"),
            filterExercises(exercises, SearchFilters("press", "Chest", "Push")).map { it.name },
        )
        assertEquals(
            listOf("Bench Press", "Chest Fly", "Incline Press", "Press Up"),
            filterExercises(exercises, SearchFilters("", "chest", "")).map { it.name },
        )
        assertEquals(
            listOf("Lat Pulldown", "Row"),
            filterExercises(exercises, SearchFilters("  ", "", "pull")).map { it.name },
        )
        assertTrue(filterExercises(exercises, SearchFilters("row", "Chest", "Pull")).isEmpty())
        assertTrue(filterExercises(exercises, SearchFilters("squat", "Chest", "Push")).isEmpty())
        assertEquals(
            listOf("Press Up"),
            filterExercises(exercises, SearchFilters("press", "Arms", "")).map { it.name },
        )
    }
}
