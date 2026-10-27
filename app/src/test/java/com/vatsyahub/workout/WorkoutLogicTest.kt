package com.vatsyahub.workout

import com.vatsyahub.workout.domain.EACH
import com.vatsyahub.workout.domain.PASS
import com.vatsyahub.workout.domain.Pass
import com.vatsyahub.workout.domain.PoolExercise
import com.vatsyahub.workout.domain.WeekWorkout
import com.vatsyahub.workout.domain.bestsReport
import com.vatsyahub.workout.domain.catchUp
import com.vatsyahub.workout.domain.climbMetres
import com.vatsyahub.workout.domain.coveredMetres
import com.vatsyahub.workout.domain.drawSet
import com.vatsyahub.workout.domain.expand
import com.vatsyahub.workout.domain.SetLoad
import com.vatsyahub.workout.domain.formatKm
import com.vatsyahub.workout.domain.formatPace
import com.vatsyahub.workout.domain.LoggedLoad
import com.vatsyahub.workout.domain.LoggedSession
import com.vatsyahub.workout.domain.formatSets
import com.vatsyahub.workout.domain.kmPaceSeconds
import com.vatsyahub.workout.domain.repeatStrength
import com.vatsyahub.workout.domain.withFavourite
import com.vatsyahub.workout.domain.parseDistanceToMeters
import com.vatsyahub.workout.domain.parseDuration
import com.vatsyahub.workout.domain.patternLabel
import com.vatsyahub.workout.domain.rerollOne
import com.vatsyahub.workout.domain.roundsOf
import com.vatsyahub.workout.domain.swimPaceSeconds
import com.vatsyahub.workout.domain.timeline
import com.vatsyahub.workout.domain.weeklyReport
import com.vatsyahub.workout.domain.Mark
import com.vatsyahub.workout.domain.inLastThreeMonths
import java.time.LocalDate
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutLogicTest {
    private val sample = listOf(Pass(PASS, 1), Pass(EACH, 2), Pass(PASS, 3))

    @Test
    fun samplePatternIsEighteenBouts() {
        val bouts = expand(3, sample)
        val names = bouts.map { listOf("A", "B", "C")[it] }
        assertEquals(
            listOf("A", "B", "C", "A", "A", "B", "B", "C", "C", "A", "B", "C", "A", "B", "C", "A", "B", "C"),
            names,
        )
        assertEquals(6, roundsOf(sample))
        assertEquals("One pass · Each twice · Three passes", patternLabel(sample))
    }

    @Test
    fun fourSetPatternIsFifteenThenNine() {
        val passes = listOf(Pass(PASS, 1), Pass(EACH, 2), Pass(EACH, 2))
        assertEquals(15, expand(3, passes).size)
        assertEquals(9, expand(9, listOf(Pass(PASS, 1))).size)
        val cues = timeline(listOf(3, 3, 3, 9), listOf(passes, passes, passes, listOf(Pass(PASS, 1))), 25, 5, 300)
        val work = cues.count { it.phase == "WORK" }
        val breaks = cues.count { it.phase == "BREAK" }
        assertEquals(54, work)
        assertEquals(3, breaks)
        assertEquals(27 * 60 + 15 * 60, cues.sumOf { it.seconds })
    }

    @Test
    fun threeSetsLastThirtySevenMinutes() {
        val cues = timeline(listOf(3, 3, 3), listOf(sample, sample, sample), 25, 5, 300)
        assertEquals(54, cues.count { it.phase == "WORK" })
        assertEquals(2, cues.count { it.phase == "BREAK" })
        assertEquals(37 * 60, cues.sumOf { it.seconds })
    }

    @Test
    fun catchUpWalksWholeCues() {
        val durations = listOf(1000L, 1000L, 1000L)
        assertEquals(0, catchUp(durations, 0, 1000, 400).index)
        assertEquals(600, catchUp(durations, 0, 1000, 400).remainingMs)
        val mid = catchUp(durations, 0, 1000, 2500)
        assertEquals(2, mid.index)
        assertEquals(500, mid.remainingMs)
        assertTrue(catchUp(durations, 0, 1000, 3000).finished)
    }

    @Test
    fun hiitSetMixesTypes() {
        val pool = listOf(
            PoolExercise(1, setOf("push")),
            PoolExercise(2, setOf("pull")),
            PoolExercise(3, setOf("legs")),
            PoolExercise(4, setOf("push")),
        )
        repeat(25) { seed ->
            val ids = drawSet(pool, 3, Random(seed))
            val types = ids.map { id -> pool.first { it.id == id }.types.first() }.toSet()
            assertEquals(setOf("push", "pull", "legs"), types)
        }
        val narrow = listOf(
            PoolExercise(1, setOf("push")),
            PoolExercise(2, setOf("push")),
            PoolExercise(3, setOf("push")),
            PoolExercise(4, setOf("pull")),
        )
        repeat(20) { seed ->
            val ids = drawSet(narrow, 3, Random(seed))
            assertTrue(ids.contains(4))
        }
    }

    @Test
    fun rerollDoesNotReturnTheSameExercise() {
        assertEquals(1L, rerollOne(listOf(1), 1, Random(1)))
        repeat(20) { seed ->
            assertTrue(rerollOne(listOf(1, 2, 3), 2, Random(seed)) != 2L)
        }
    }

    @Test
    fun paceClimbAndCarryDistance() {
        assertEquals(120.0, swimPaceSeconds(1500.0, 30 * 60)!!, 0.01)
        assertEquals(300.0, kmPaceSeconds(10_000.0, 50 * 60)!!, 0.01)
        assertNull(kmPaceSeconds(0.0, 60))
        assertEquals(400.0, climbMetres(200.0, 600.0), 0.01)
        assertEquals(800.0, coveredMetres(400.0, true), 0.01)
        assertEquals(400.0, coveredMetres(400.0, false), 0.01)
        assertEquals("8.4 km", formatKm(8400.0))
        assertEquals("1:45", formatPace(105.0))
        assertEquals(1500.0, parseDistanceToMeters("1.5")!!, 0.01)
        assertEquals(400.0, parseDistanceToMeters("400 m")!!, 0.01)
        assertEquals(45 * 60, parseDuration("45", true))
        assertEquals(45, parseDuration("0:45", true))
        assertEquals(25, parseDuration("25", false))
    }

    @Test
    fun weeklyComparesLoadRepsAndDistance() {
        val today = LocalDate.of(2026, 10, 26)
        val workouts = listOf(WeekWorkout(1, today), WeekWorkout(2, LocalDate.of(2026, 10, 20)))
        val marks = listOf(
            heavy(1, today, "Curl", 14.0, 10),
            heavy(2, LocalDate.of(2026, 10, 20), "Curl", 12.0, 8),
            body(1, today, "Push-up", 30),
            body(2, LocalDate.of(2026, 10, 20), "Push-up", 24),
            hiit(1, today, "Burpee", 25),
            hiit(2, LocalDate.of(2026, 10, 20), "Burpee", 25),
            hike(3, today, 8400.0),
            hike(4, LocalDate.of(2026, 10, 21), 6200.0),
        )
        val lines = weeklyReport(today, workouts + WeekWorkout(3, today) + WeekWorkout(4, LocalDate.of(2026, 10, 21)), marks)
            .map { it.text }
        assertEquals("2 this week", lines[0])
        assertEquals("2 last week", lines[1])
        assertTrue(lines.any { it == "Curl · 14 kg · up from 12 kg" })
        assertTrue(lines.any { it == "Push-up · 30 reps · up from 24" })
        assertTrue(lines.any { it == "Burpee · 25 s · same as last week" })
        assertTrue(lines.any { it == "Hike · 8.4 km · up from 6.2 km" })
    }

    @Test
    fun sameWeightShowsReps() {
        val today = LocalDate.of(2026, 10, 26)
        val marks = listOf(
            heavy(1, today, "Curl", 14.0, 10),
            heavy(2, LocalDate.of(2026, 10, 20), "Curl", 14.0, 8),
        )
        val line = weeklyReport(today, emptyList(), marks).map { it.text }.single { it.startsWith("Curl") }
        assertEquals("Curl · 14 kg · 10 reps · up from 8", line)
    }

    @Test
    fun bestsUseThreeMonthsAndHikeTieBreak() {
        val today = LocalDate.of(2026, 10, 26)
        assertTrue(inLastThreeMonths(today.minusMonths(3), today))
        assertTrue(!inLastThreeMonths(today.minusMonths(3).minusDays(1), today))
        val old = today.minusMonths(4)
        val marks = listOf(
            heavy(1, LocalDate.of(2026, 10, 20), "Curl", 14.0, 22),
            heavy(2, LocalDate.of(2026, 10, 16), "Curl", 12.0, 24),
            heavy(9, old, "Curl", 40.0, 100),
            hike(3, LocalDate.of(2026, 10, 10), 5000.0, climb = 400.0, duration = 3600),
            hike(4, LocalDate.of(2026, 10, 11), 5000.0, climb = 400.0, duration = 3000),
            hike(5, old, 9000.0, climb = 900.0, duration = 1000),
        )
        val lines = bestsReport(today, marks).map { it.text }
        assertTrue(lines.any { it.startsWith("Curl · 14 kg") })
        assertTrue(lines.any { it.startsWith("Curl · 24 reps") })
        assertTrue(lines.none { it.contains("40 kg") })
        assertTrue(lines.none { it.contains("900") })
        val hike = lines.single { it.startsWith("Hike") }
        assertTrue(hike.contains("400 m"))
        assertEquals(4L, bestsReport(today, marks).single { it.text.startsWith("Hike") }.workoutId)
    }

    @Test
    fun setsCollapseIntoOneLine() {
        assertEquals(
            "3 × 8 @ 20 kg",
            formatSets(List(3) { SetLoad(8, 20.0) }, showWeight = true),
        )
        assertEquals(
            "1 × 4, 2 × 8",
            formatSets(listOf(SetLoad(4), SetLoad(8), SetLoad(8)), showWeight = false),
        )
        assertEquals(
            "1 × 8 @ 20 kg, 2 × 8 @ 22.5 kg",
            formatSets(listOf(SetLoad(8, 20.0), SetLoad(8, 22.5), SetLoad(8, 22.5)), showWeight = true),
        )
        assertEquals(
            "1 × 8 @ 20 kg, 1 × 10 @ 20 kg, 1 × 8 @ 20 kg",
            formatSets(listOf(SetLoad(8, 20.0), SetLoad(10, 20.0), SetLoad(8, 20.0)), showWeight = true),
        )
        assertEquals("2 × 8", formatSets(listOf(SetLoad(8, null), SetLoad(8, null)), showWeight = true))
        assertEquals("1 × 8", formatSets(listOf(SetLoad(8, 20.0)), showWeight = false))
        assertEquals("", formatSets(emptyList(), showWeight = true))
    }

    @Test
    fun starringDoesNotAlterSets() {
        val session = LoggedSession(
            id = 4,
            kind = "HEAVY",
            favourite = false,
            durationSec = 45 * 60,
            lines = listOf(
                LoggedLoad(7, listOf(SetLoad(8, 20.0), SetLoad(6, 22.5))),
                LoggedLoad(8, listOf(SetLoad(10, null))),
            ),
        )
        val starred = withFavourite(session, true)
        assertEquals(true, starred.favourite)
        assertEquals(4L, starred.id)
        assertEquals(45 * 60, starred.durationSec)
        assertEquals(8, starred.lines[0].sets[0].reps)
        assertEquals(20.0, starred.lines[0].sets[0].weightKg!!, 0.001)
        assertEquals(6, starred.lines[0].sets[1].reps)
        assertEquals(22.5, starred.lines[0].sets[1].weightKg!!, 0.001)
        assertEquals(10, starred.lines[1].sets[0].reps)
        assertNull(starred.lines[1].sets[0].weightKg)
        assertEquals(false, session.favourite)
        assertEquals(8, session.lines[0].sets[0].reps)
        assertEquals(20.0, session.lines[0].sets[0].weightKg!!, 0.001)
        assertEquals(10, session.lines[1].sets[0].reps)
        assertNull(session.lines[1].sets[0].weightKg)
    }

    @Test
    fun repeatCopiesLoadsIntoANewDraft() {
        val session = LoggedSession(
            id = 11,
            kind = "HEAVY",
            favourite = true,
            durationSec = 30 * 60,
            lines = listOf(
                LoggedLoad(3, listOf(SetLoad(8, 20.0), SetLoad(8, 20.0), SetLoad(6, 22.5))),
                LoggedLoad(5, listOf(SetLoad(12, 16.0))),
            ),
        )
        val draft = repeatStrength(session)
        assertEquals("HEAVY", draft.kind)
        assertNull(draft.durationSec)
        assertEquals(3L, draft.lines[0].exerciseId)
        assertEquals(listOf(8, 8, 6), draft.lines[0].sets.map { it.reps })
        assertEquals(20.0, draft.lines[0].sets[0].weightKg!!, 0.001)
        assertEquals(20.0, draft.lines[0].sets[1].weightKg!!, 0.001)
        assertEquals(22.5, draft.lines[0].sets[2].weightKg!!, 0.001)
        assertEquals(5L, draft.lines[1].exerciseId)
        assertEquals(12, draft.lines[1].sets.single().reps)
        assertEquals(16.0, draft.lines[1].sets.single().weightKg!!, 0.001)
        assertEquals(11L, session.id)
        assertEquals(true, session.favourite)
        assertEquals(30 * 60, session.durationSec)
        assertEquals(listOf(8, 8, 6), session.lines[0].sets.map { it.reps })
        assertEquals(20.0, session.lines[0].sets[0].weightKg!!, 0.001)
        assertEquals(16.0, session.lines[1].sets[0].weightKg!!, 0.001)
    }

    private fun heavy(id: Long, day: LocalDate, name: String, weight: Double, reps: Int) = Mark(
        workoutId = id,
        day = day,
        kind = "HEAVY",
        exerciseKey = name,
        exerciseName = name,
        topWeight = weight,
        totalReps = reps,
        repsAtTop = reps,
    )

    private fun body(id: Long, day: LocalDate, name: String, reps: Int) = Mark(
        workoutId = id,
        day = day,
        kind = "BODYWEIGHT",
        exerciseKey = name,
        exerciseName = name,
        totalReps = reps,
        repsAtTop = reps,
    )

    private fun hiit(id: Long, day: LocalDate, name: String, work: Int) = Mark(
        workoutId = id,
        day = day,
        kind = "HIIT",
        exerciseKey = name,
        exerciseName = name,
        workSec = work,
    )

    private fun hike(
        id: Long,
        day: LocalDate,
        distance: Double,
        climb: Double = 100.0,
        duration: Int = 3600,
    ) = Mark(
        workoutId = id,
        day = day,
        kind = "OUTING",
        exerciseKey = "hike",
        exerciseName = "Hike",
        distanceM = distance,
        durationSec = duration,
        climbM = climb,
        outingName = "Hike",
        outingShape = "HIKE",
    )
}
