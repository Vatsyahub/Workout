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
import com.vatsyahub.workout.domain.DrawCard
import com.vatsyahub.workout.domain.defaultReps
import com.vatsyahub.workout.domain.drawnOrder
import com.vatsyahub.workout.domain.dragSteps
import com.vatsyahub.workout.domain.largestMuscle
import com.vatsyahub.workout.domain.repsFor
import com.vatsyahub.workout.domain.spreadType
import com.vatsyahub.workout.domain.takeExact
import com.vatsyahub.workout.domain.dropIndex
import com.vatsyahub.workout.domain.orderNumber
import com.vatsyahub.workout.domain.shiftForDrag
import com.vatsyahub.workout.domain.drawSet
import com.vatsyahub.workout.domain.expand
import com.vatsyahub.workout.domain.SetLoad
import com.vatsyahub.workout.domain.formatKm
import com.vatsyahub.workout.domain.formatPace
import com.vatsyahub.workout.domain.moveItem
import com.vatsyahub.workout.domain.LoggedLoad
import com.vatsyahub.workout.domain.LoggedSession
import com.vatsyahub.workout.domain.formatSets
import com.vatsyahub.workout.domain.kmPaceSeconds
import com.vatsyahub.workout.domain.PickCandidate
import com.vatsyahub.workout.domain.classifyPickedKind
import com.vatsyahub.workout.data.StarterLibrary
import com.vatsyahub.workout.domain.matchesBodyPart
import com.vatsyahub.workout.domain.matchExercises
import com.vatsyahub.workout.domain.musclesOf
import com.vatsyahub.workout.domain.repeatStrength
import com.vatsyahub.workout.domain.strengthLabel
import com.vatsyahub.workout.domain.withFavourite
import com.vatsyahub.workout.domain.parseDistanceToMeters
import com.vatsyahub.workout.domain.parseDuration
import com.vatsyahub.workout.domain.patternLabel
import com.vatsyahub.workout.domain.isArmsMuscle
import com.vatsyahub.workout.domain.musclesWithoutMovingHandleArms
import com.vatsyahub.workout.domain.repairAssignment
import com.vatsyahub.workout.domain.rerollInSlot
import com.vatsyahub.workout.domain.rerollOne
import com.vatsyahub.workout.domain.usesMovingHandles
import com.vatsyahub.workout.domain.roundsOf
import com.vatsyahub.workout.domain.stepExercise
import com.vatsyahub.workout.domain.swimPaceSeconds
import com.vatsyahub.workout.domain.timeline
import com.vatsyahub.workout.domain.weeklyReport
import com.vatsyahub.workout.domain.Mark
import com.vatsyahub.workout.domain.inLastThreeMonths
import java.time.LocalDate
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
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
        assertEquals(
            "In order, once · Each exercise twice in a row · In order, 3 times",
            patternLabel(sample),
        )
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
    fun oneLineRerollShowsEachMatchOnceThenMayRepeatInTheSameSlot() {
        val pool = listOf(1L, 2L, 3L, 4L)
        var ids = listOf(10L, 1L, 30L)
        var shown = emptySet<Long>()
        val first = mutableListOf(1L)
        repeat(pool.size - 1) { step ->
            val placed = rerollInSlot(ids, 1, pool, shown, Random(step))!!
            assertEquals(listOf(10L, placed.ids[1], 30L), placed.ids)
            assertTrue(placed.ids[1] != ids[1])
            assertFalse(first.contains(placed.ids[1]))
            first += placed.ids[1]
            ids = placed.ids
            shown = placed.shown
        }
        assertEquals(pool.toSet(), first.toSet())
        val repeat = rerollInSlot(ids, 1, pool, shown, Random(9))!!
        assertEquals(10L, repeat.ids[0])
        assertEquals(30L, repeat.ids[2])
        assertTrue(repeat.ids[1] != ids[1])
        assertTrue(first.contains(repeat.ids[1]))
        ids = repeat.ids
        shown = repeat.shown
        val second = mutableListOf(ids[1])
        repeat(pool.size - 1) { step ->
            val placed = rerollInSlot(ids, 1, pool, shown, Random(20 + step))!!
            assertEquals(10L, placed.ids[0])
            assertEquals(30L, placed.ids[2])
            assertTrue(placed.ids[1] != ids[1])
            assertFalse(second.contains(placed.ids[1]))
            second += placed.ids[1]
            ids = placed.ids
            shown = placed.shown
        }
        assertEquals(pool.toSet(), second.toSet())
        assertEquals(7L, rerollInSlot(listOf(7L), 0, listOf(7L), setOf(7L), Random(1))!!.ids.single())
    }

    @Test
    fun movingHandlesDropArmsAndLeaveEveryOtherMuscle() {
        val note = "Legs, Glutes, Arms (if moving handles are used)"
        assertEquals("Legs, Glutes", musclesWithoutMovingHandleArms(note, "Elliptical Trainer"))
        assertEquals(
            "Legs, Forearms, Glutes, Core",
            musclesWithoutMovingHandleArms(
                "Legs, Forearms, Arms (if moving handles are used), Core",
                "Elliptical Trainer",
            ),
        )
        assertEquals(
            "Legs, Glutes",
            musclesWithoutMovingHandleArms("Legs, Glutes, Arms", name = "Bike with moving handles"),
        )
        val boxing = "Shoulders, Arms, Core, Legs"
        assertEquals(boxing, musclesWithoutMovingHandleArms(boxing, "Shadow Boxing"))
        val rowing = "Back, Legs, Arms, Core"
        assertEquals(rowing, musclesWithoutMovingHandleArms(rowing, "Rowing Machine"))
        val farmer = "Traps, Forearms, Core, Legs"
        assertEquals(farmer, musclesWithoutMovingHandleArms(farmer, "Farmer's Walk"))
        val cross = "Legs, Glutes, Arms"
        assertEquals(cross, musclesWithoutMovingHandleArms(cross, "Cross-Trainer Intervals"))
        val library = listOf(
            "Elliptical Trainer" to note,
            "Elliptical Trainer" to "Legs, Glutes, Arms (if moving handles are used), Core",
            "Shadow Boxing" to boxing,
            "Rowing Machine" to rowing,
            "Farmer's Walk" to farmer,
            "Cross-Trainer Intervals" to cross,
        )
        val fixed = library.map { (name, muscles) -> musclesWithoutMovingHandleArms(muscles, name) }
        assertEquals("Legs, Glutes", fixed[0])
        assertEquals("Legs, Glutes, Core", fixed[1])
        assertEquals(library.drop(2).map { it.second }, fixed.drop(2))
        val seeded = StarterLibrary.rows.filter { it.name == "Elliptical Trainer" }
        assertEquals(listOf("Legs", "Glutes"), seeded.map { musclesOf(it.muscles) }.distinct().single())
        assertTrue(seeded.none { musclesOf(it.muscles).any(::isArmsMuscle) })
        assertTrue(StarterLibrary.rows.any { row -> musclesOf(row.muscles).any(::isArmsMuscle) })
        assertTrue(
            StarterLibrary.rows.none { row ->
                usesMovingHandles(row.name, row.muscles, row.types, row.usage) &&
                    musclesOf(row.muscles).any(::isArmsMuscle)
            },
        )
    }

    @Test
    fun fullRerollDoesNotHandASlotAnExerciseItAlreadyShowed() {
        val matches = listOf(1L, 2L, 3L, 4L)
        repeat(25) { seed ->
            val fixed = repairAssignment(
                assigned = listOf(1L, 2L),
                histories = listOf(listOf(1L), listOf(2L)),
                matches = matches,
                random = Random(seed),
            )
            assertTrue(fixed[0].id != 1L)
            assertTrue(fixed[1].id != 2L)
            assertEquals(2, fixed.map { it.id }.distinct().size)
            assertTrue(fixed.all { it.id in matches })
        }
        val only = repairAssignment(listOf(1L), listOf(listOf(1L)), listOf(1L), Random(1))
        assertEquals(1L, only.single().id)
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

    @Test
    fun searchReturnsAtMostFiveAndRanksPrefixBeforeContains() {
        val exercises = listOf(
            PickCandidate(1, "Curl", "HEAVY"),
            PickCandidate(2, "Hammer curl", "HEAVY"),
            PickCandidate(3, "Preacher curl", "HEAVY"),
            PickCandidate(4, "Incline curl", "BODYWEIGHT"),
            PickCandidate(5, "Cable curl", "HEAVY"),
            PickCandidate(6, "Spider curl", "HEAVY"),
            PickCandidate(7, "Reverse curl", "BODYWEIGHT"),
            PickCandidate(8, "Squat", "HEAVY"),
            PickCandidate(9, "Row", "HEAVY"),
            PickCandidate(10, "Romanian deadlift", "HEAVY"),
            PickCandidate(11, "Reverse fly", "BODYWEIGHT"),
            PickCandidate(12, "Rear delt raise", "HEAVY"),
            PickCandidate(13, "Renegade row", "HEAVY"),
            PickCandidate(14, "Rest pause curl", "HEAVY"),
            PickCandidate(15, "Upright row", "BODYWEIGHT"),
        )
        val curls = matchExercises(exercises, "curl")
        assertEquals(5, curls.size)
        assertEquals(
            listOf("Curl", "Cable curl", "Hammer curl", "Incline curl", "Preacher curl"),
            curls.map { it.name },
        )
        val rows = matchExercises(exercises, "r")
        assertEquals(5, rows.size)
        assertTrue(rows.all { it.name.lowercase().startsWith("r") })
        assertTrue(rows.none { it.name == "Upright row" })
        assertEquals(
            listOf("Press", "Bench press", "Leg press"),
            matchExercises(
                listOf(
                    PickCandidate(1, "Bench press", "HEAVY"),
                    PickCandidate(2, "Press", "HEAVY"),
                    PickCandidate(3, "Leg press", "BODYWEIGHT"),
                ),
                "PrEsS",
            ).map { it.name },
        )
        assertTrue(matchExercises(exercises, "").isEmpty())
        assertTrue(matchExercises(exercises, "   ").isEmpty())
    }

    @Test
    fun pickedMixOfHeavyAndBodyweightIsMixed() {
        assertEquals("MIXED", classifyPickedKind(listOf("HEAVY", "BODYWEIGHT"), "HEAVY"))
        assertEquals("MIXED", classifyPickedKind(listOf("BODYWEIGHT", "HEAVY"), "BODYWEIGHT"))
        assertEquals("MIXED", classifyPickedKind(listOf("HEAVY", "BOTH", "BODYWEIGHT"), "HEAVY"))
        assertEquals("Mixed", strengthLabel("MIXED"))
    }

    @Test
    fun allHeavyPickStaysHeavy() {
        assertEquals("HEAVY", classifyPickedKind(listOf("HEAVY", "HEAVY"), "BODYWEIGHT"))
        assertEquals("HEAVY", classifyPickedKind(listOf("HEAVY", "BOTH"), "BODYWEIGHT"))
        assertEquals("HEAVY", classifyPickedKind(listOf("BOTH"), "HEAVY"))
        assertEquals("BODYWEIGHT", classifyPickedKind(listOf("BODYWEIGHT", "BOTH"), "HEAVY"))
        assertEquals("Heavy", strengthLabel("HEAVY"))
    }

    @Test
    fun repeatOfMixedLogCopiesLinesWithoutMutatingOriginal() {
        val session = LoggedSession(
            id = 9,
            kind = "MIXED",
            favourite = true,
            durationSec = 40 * 60,
            lines = listOf(
                LoggedLoad(1, listOf(SetLoad(8, 20.0), SetLoad(8, 20.0))),
                LoggedLoad(2, listOf(SetLoad(10, null))),
            ),
        )
        val draft = repeatStrength(session)
        assertEquals("MIXED", draft.kind)
        assertNull(draft.durationSec)
        assertEquals(1L, draft.lines[0].exerciseId)
        assertEquals(listOf(8, 8), draft.lines[0].sets.map { it.reps })
        assertEquals(20.0, draft.lines[0].sets[0].weightKg!!, 0.001)
        assertEquals(20.0, draft.lines[0].sets[1].weightKg!!, 0.001)
        assertEquals(2L, draft.lines[1].exerciseId)
        assertEquals(10, draft.lines[1].sets.single().reps)
        assertNull(draft.lines[1].sets.single().weightKg)
        assertNotSame(session.lines, draft.lines)
        assertNotSame(session.lines[0].sets, draft.lines[0].sets)
        assertNotSame(session.lines[1].sets, draft.lines[1].sets)
        assertEquals(9L, session.id)
        assertEquals("MIXED", session.kind)
        assertEquals(true, session.favourite)
        assertEquals(40 * 60, session.durationSec)
        assertEquals(listOf(8, 8), session.lines[0].sets.map { it.reps })
        assertEquals(20.0, session.lines[0].sets[0].weightKg!!, 0.001)
        assertEquals(20.0, session.lines[0].sets[1].weightKg!!, 0.001)
        assertEquals(10, session.lines[1].sets[0].reps)
        assertNull(session.lines[1].sets[0].weightKg)
    }

    @Test
    fun dragReorderFollowsTheFingerAndRenumbers() {
        val shift = dragSteps(250f, 400f)
        assertEquals(1, shift.steps)
        assertEquals(-150f, shift.offset, 0.01f)
        assertEquals(0, dragSteps(40f, 100f).steps)
        assertEquals(40f, dragSteps(40f, 100f).offset, 0.01f)
        val back = dragSteps(-180f, 100f)
        assertEquals(-2, back.steps)
        assertEquals(20f, back.offset, 0.01f)
        assertEquals(0, dragSteps(10f, 0f).steps)
        val moved = moveItem(listOf("Squat", "Press", "Row"), 0, 2)
        assertEquals(listOf("Press", "Row", "Squat"), moved)
        assertEquals(listOf(1, 2, 3), moved.indices.map { it + 1 })
        assertEquals("Squat", moved[2])
        assertEquals(listOf("Row", "Squat", "Press"), moveItem(listOf("Squat", "Press", "Row"), 2, 0))
        val same = listOf("A", "B")
        assertEquals(same, moveItem(same, 0, 0))
        assertEquals(same, moveItem(same, -1, 0))
        assertEquals(same, moveItem(same, 0, 4))
        val spans = listOf(200f, 200f, 200f, 200f)
        assertEquals(1, dropIndex(0, 4, 250f, spans))
        assertEquals(2, dropIndex(0, 4, 350f, spans))
        assertEquals(0, dropIndex(0, 4, -40f, spans))
        assertEquals(1, dropIndex(2, 4, -150f, spans))
        assertEquals(-200f, shiftForDrag(1, 0, 2, 200f), 0.01f)
        assertEquals(0f, shiftForDrag(3, 0, 2, 200f), 0.01f)
        assertEquals(200f, shiftForDrag(1, 2, 0, 200f), 0.01f)
        assertEquals(3, orderNumber(0, 0, 2))
        assertEquals(1, orderNumber(1, 0, 2))
        assertEquals(2, orderNumber(2, 0, 2))
    }

    @Test
    fun drawFollowsHisCountsThenOrdersTheList() {
        val lat = card(1, "Lat Pulldown", listOf("Lats"), setOf("Pull"), "HEAVY")
        val row = card(2, "Seated Row", listOf("Upper Back"), setOf("Pull"), "HEAVY")
        val curl = card(3, "Barbell Curl", listOf("Biceps"), setOf("Pull"), "HEAVY")
        val rear = card(4, "Reverse Fly", listOf("Rear Deltoids"), setOf("Pull"), "HEAVY")
        val pool = listOf(lat, row, curl, rear)
        val three = spreadType(pool, 3, Random(1)).map { it.name }
        assertEquals(listOf("Lat Pulldown", "Seated Row", "Barbell Curl"), three)
        assertFalse(three.contains("Reverse Fly"))
        val two = spreadType(pool, 2, Random(1)).map { it.name }
        assertEquals(listOf("Lat Pulldown", "Seated Row"), two)
        val bench = card(5, "Barbell Bench Press", listOf("Chest", "Triceps"), setOf("Push"), "HEAVY")
        val fly = card(6, "Chest Fly", listOf("Chest"), setOf("Push"), "HEAVY")
        val pushUp = card(7, "Push-Ups", listOf("Chest", "Triceps"), setOf("Push"), "BODYWEIGHT")
        val squat = card(8, "Back Squat", listOf("Quadriceps"), setOf("Legs"), "HEAVY")
        val extension = card(9, "Leg Extension", listOf("Quadriceps"), setOf("Legs"), "HEAVY")
        val raise = card(10, "Lateral Raise", listOf("Side Deltoids"), setOf("Push"), "HEAVY")
        val overhead = card(11, "Overhead Press", listOf("Shoulders", "Triceps"), setOf("Push"), "HEAVY")
        val spine = card(12, "Back Extension", listOf("Lower Back"), setOf("Pull"), "HEAVY")
        val hinge = card(13, "Romanian Deadlift", listOf("Hamstrings"), setOf("Pull"), "HEAVY")
        val ordered = orderNames(listOf(curl, row, lat, pushUp, bench, fly, extension, squat, raise, overhead, hinge, spine))
        assertTrue(ordered.indexOf("Lat Pulldown") < ordered.indexOf("Barbell Curl"))
        assertTrue(ordered.indexOf("Seated Row") < ordered.indexOf("Barbell Curl"))
        assertTrue(ordered.indexOf("Barbell Bench Press") < ordered.indexOf("Push-Ups"))
        assertTrue(ordered.indexOf("Barbell Bench Press") < ordered.indexOf("Chest Fly"))
        assertTrue(ordered.indexOf("Back Squat") < ordered.indexOf("Leg Extension"))
        assertTrue(ordered.indexOf("Overhead Press") < ordered.indexOf("Lateral Raise"))
        assertTrue(ordered.indexOf("Back Extension") < ordered.indexOf("Back Squat"))
        assertTrue(ordered.indexOf("Back Extension") < ordered.indexOf("Romanian Deadlift"))
        val spineAt = ordered.indexOf("Back Extension")
        val squatAt = ordered.indexOf("Back Squat")
        val hingeAt = ordered.indexOf("Romanian Deadlift")
        assertTrue(kotlin.math.abs(spineAt - squatAt) > 1)
        assertTrue(kotlin.math.abs(spineAt - hingeAt) > 1)
        val dragged = listOf(curl, lat, row)
        assertEquals(listOf("Barbell Curl", "Lat Pulldown", "Seated Row"), drawnOrder(dragged, "HEAVY", dragged = true).map { it.name })
        assertEquals(setOf(5L, 6L), takeExact(listOf(bench, fly), 2, Random(2), emptySet()).map { it.id }.toSet())
        assertEquals(listOf(6L), takeExact(listOf(bench, fly, lat), 1, Random(2), setOf(5L, 1L)).map { it.id })
        assertEquals(listOf(1L), takeExact(listOf(lat), 3, Random(1), emptySet()).map { it.id })
        val session = listOf(bench, fly, pushUp, raise)
        assertEquals(6, defaultReps(bench, orderOf(session), "HEAVY"))
        assertEquals(10, defaultReps(fly, orderOf(session), "HEAVY"))
        assertEquals(8, defaultReps(pushUp, orderOf(listOf(pushUp, bench)), "MIXED"))
        assertEquals(10, defaultReps(raise, orderOf(session), "HEAVY"))
        assertEquals(12, defaultReps(pushUp, orderOf(listOf(bench, pushUp)), "BODYWEIGHT"))
        assertEquals(listOf(4, 4, 2), repsFor(bench, orderOf(session), "HEAVY", listOf(4, 4, 2)))
        assertEquals(listOf(6, 6, 6), repsFor(bench, orderOf(session), "HEAVY", null))
        assertEquals("pecs", largestMuscle(bench.muscles))
        assertEquals("lats", largestMuscle(listOf("Lats", "Biceps")))
        assertEquals("spinal", largestMuscle(listOf("Lower Back", "Glutes")))
        val secondLat = card(14, "Pull-Ups", listOf("Lats"), setOf("Pull"), "BOTH")
        val four = spreadType(listOf(lat, secondLat, row, curl, rear), 4, Random(2)).map { it.name }
        assertEquals(4, four.size)
        assertTrue(four.contains("Seated Row"))
        assertTrue(four.contains("Barbell Curl"))
        assertTrue(four.contains("Reverse Fly"))
        assertEquals(1, four.count { it == "Lat Pulldown" || it == "Pull-Ups" })
        val five = spreadType(listOf(lat, secondLat, row, curl, rear), 5, Random(2)).map { it.name }
        assertEquals(setOf("Lat Pulldown", "Pull-Ups", "Seated Row", "Barbell Curl", "Reverse Fly"), five.toSet())
        val picked = listOf(curl, lat, bench)
        assertEquals(picked.map { it.id }.toSet(), drawnOrder(picked, "HEAVY", dragged = false).map { it.id }.toSet())
        val loadedPress = card(32, "Zercher Press", listOf("Chest"), setOf("Push"), "HEAVY")
        val archer = card(33, "Archer Push-Ups", listOf("Chest"), setOf("Push"), "BODYWEIGHT")
        assertEquals(
            listOf("Zercher Press", "Archer Push-Ups"),
            drawnOrder(listOf(archer, loadedPress), "MIXED", dragged = false).map { it.name },
        )
        assertTrue(ordered.indexOf("Back Squat") < ordered.indexOf("Barbell Curl"))
        assertTrue(ordered.indexOf("Lateral Raise") < ordered.indexOf("Barbell Curl"))
    }

    private fun card(id: Long, name: String, muscles: List<String>, types: Set<String>, usage: String) =
        DrawCard(id, name, muscles, types, usage)

    private fun orderOf(cards: List<DrawCard>) = drawnOrder(cards, "HEAVY", dragged = false)

    private fun orderNames(cards: List<DrawCard>) = orderOf(cards).map { it.name }

    @Test
    fun starterLibraryKeepsEveryListedExercise() {
        val rows = StarterLibrary.rows
        assertEquals(257, rows.size)
        assertEquals(122, rows.count { it.usage == "HEAVY" })
        assertEquals(101, rows.count { it.usage == "BODYWEIGHT" })
        assertEquals(34, rows.count { it.usage == "BOTH" })
        assertEquals(11, rows.groupingBy { it.name }.eachCount().count { it.value > 1 })
        val push = rows.first { it.name == "Push-Ups" }
        assertEquals("Chest, Triceps, Shoulders, Core", push.muscles)
        assertEquals(listOf("Chest", "Triceps", "Shoulders", "Core"), musclesOf(push.muscles))
        assertTrue(matchesBodyPart(push.muscles, "Chest"))
        assertTrue(matchesBodyPart(push.muscles, "core"))
        assertFalse(matchesBodyPart(push.muscles, "Hamstrings"))
        assertFalse(matchesBodyPart(push.muscles, ""))
        assertEquals("Plank Jack", rows.last().name)
        val elliptical = rows.filter { it.name == "Elliptical Trainer" }
        assertEquals(1, elliptical.size)
        assertEquals("Legs, Glutes", elliptical.single().muscles)
        assertEquals(listOf("Legs", "Glutes"), musclesOf(elliptical.single().muscles))
        val keptArms = listOf(
            "Shadow Boxing" to "Shoulders, Arms, Core, Legs",
            "Kickboxing Drills" to "Shoulders, Arms, Core, Legs",
            "Battle Ropes" to "Shoulders, Arms, Core",
            "Air Bike" to "Quadriceps, Glutes, Arms, Core",
            "Rowing Machine" to "Back, Legs, Arms, Core",
            "Ski Ergometer" to "Back, Shoulders, Arms, Core",
            "Swimming" to "Shoulders, Back, Arms, Legs, Core",
            "Freestyle Swimming" to "Shoulders, Back, Arms, Core",
            "Cross-Trainer Intervals" to "Legs, Glutes, Arms",
            "Bear Complex" to "Legs, Back, Shoulders, Arms",
        )
        for ((name, muscles) in keptArms) {
            val matches = rows.filter { it.name == name }
            assertTrue(name, matches.isNotEmpty())
            assertTrue(name, matches.all { it.muscles == muscles && musclesOf(it.muscles).contains("Arms") })
        }
        val sled = rows.filter { it.name == "Sled Pull" }
        assertEquals(2, sled.size)
        assertTrue(sled.all { it.muscles == "Back, Arms, Legs, Core" && musclesOf(it.muscles).contains("Arms") })
        assertTrue(rows.none { it.muscles.contains("if moving handles") })
        assertTrue(rows.all { it.name.isNotBlank() && it.muscles.isNotBlank() && it.types.isNotBlank() })
        assertTrue(rows.none { it.name.equals("Exercise Name", ignoreCase = true) })
        assertTrue(rows.none { it.usage !in setOf("HEAVY", "BODYWEIGHT", "BOTH") })
    }

    @Test
    fun exerciseSwipeStopsAtTheEnds() {
        assertEquals(1, stepExercise(0, 4, forward = true))
        assertEquals(3, stepExercise(3, 4, forward = true))
        assertEquals(2, stepExercise(3, 4, forward = false))
        assertEquals(0, stepExercise(0, 4, forward = false))
        assertEquals(0, stepExercise(0, 1, forward = true))
        assertEquals(0, stepExercise(0, 1, forward = false))
        assertEquals(0, stepExercise(3, 0, forward = true))
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
