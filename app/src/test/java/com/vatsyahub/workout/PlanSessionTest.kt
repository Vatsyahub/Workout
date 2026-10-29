package com.vatsyahub.workout

import com.vatsyahub.workout.domain.ACTIVE_BLOCK
import com.vatsyahub.workout.domain.BuilderDraft
import com.vatsyahub.workout.domain.HeldSession
import com.vatsyahub.workout.domain.LogRecord
import com.vatsyahub.workout.domain.PlanCount
import com.vatsyahub.workout.domain.PlanHiit
import com.vatsyahub.workout.domain.PlanHiitSet
import com.vatsyahub.workout.domain.PlanLine
import com.vatsyahub.workout.domain.PlanPass
import com.vatsyahub.workout.domain.PlanSet
import com.vatsyahub.workout.domain.StartResult
import com.vatsyahub.workout.domain.StrengthHold
import com.vatsyahub.workout.domain.WorkoutBook
import com.vatsyahub.workout.domain.decodeStrengthHold
import com.vatsyahub.workout.domain.deletePlan
import com.vatsyahub.workout.domain.editPlan
import com.vatsyahub.workout.domain.encodeStrengthHold
import com.vatsyahub.workout.domain.finishActive
import com.vatsyahub.workout.domain.openPlan
import com.vatsyahub.workout.domain.restoreSession
import com.vatsyahub.workout.domain.restored
import com.vatsyahub.workout.domain.saveBuilderPlan
import com.vatsyahub.workout.domain.startWorkout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanSessionTest {
    private val bench = PlanLine(
        exerciseId = 4,
        name = "Bench Press",
        sets = listOf(PlanSet(8, 60.0), PlanSet(6, 70.0)),
        group = "Chest",
        groupKind = "PART",
    )
    private val row = PlanLine(
        exerciseId = 9,
        name = "Bent Over Row",
        sets = listOf(PlanSet(10, 40.0)),
    )

    @Test
    fun savingABuilderListAsAPlanDoesNotCreateAFinishedLog() {
        val existing = listOf(LogRecord(3, finished = true, kind = "HEAVY"))
        val saved = WorkoutBook(logs = existing).saveBuilderPlan(
            BuilderDraft(kind = "HEAVY", mode = "PICK", lines = listOf(bench, row)),
        )
        assertEquals(existing, saved.logs)
        assertTrue(saved.logs.all { it.finished })
        assertEquals(listOf(bench, row), saved.plans.single().body.lines)
        assertEquals("PICK", saved.plans.single().body.mode)

        val counts = WorkoutBook(logs = existing).saveBuilderPlan(
            BuilderDraft(
                kind = "BODYWEIGHT",
                mode = "PART",
                counts = listOf(PlanCount("Chest", 2, "PART"), PlanCount("Back", 1, "PART")),
            ),
        )
        assertEquals(existing, counts.logs)
        assertEquals(2, counts.plans.single().body.counts.first().count)
        assertEquals("Chest", counts.plans.single().body.counts.first().label)

        val hiit = PlanHiit(
            workSec = 25,
            restSec = 5,
            breakSec = 300,
            warmupSec = 120,
            cooldownSec = 300,
            template = listOf(PlanPass("PASS", 1), PlanPass("EACH", 2)),
            sets = listOf(
                PlanHiitSet(
                    exerciseIds = listOf(1, 2),
                    names = listOf("Burpee", "Squat"),
                    passes = listOf(PlanPass("PASS", 1)),
                    shared = true,
                    splitSeconds = listOf(25, 25),
                    breakSeconds = 300,
                ),
            ),
        )
        val hiitSaved = WorkoutBook(logs = existing).saveBuilderPlan(
            BuilderDraft(kind = "HIIT", mode = "HIIT", hiit = hiit),
        )
        assertEquals(existing, hiitSaved.logs)
        assertEquals(hiit, hiitSaved.plans.single().body.hiit)
        assertTrue(hiitSaved.logs.none { it.kind == "HIIT" && it.id != 3L })
    }

    @Test
    fun openingAPlanLeavesItUntilThatSessionIsFinished() {
        val saved = WorkoutBook().saveBuilderPlan(
            BuilderDraft(kind = "HEAVY", mode = "LINES", lines = listOf(bench, row)),
        )
        val id = saved.plans.single().id
        val first = saved.openPlan(id)
        assertTrue(first is StartResult.Started)
        val started = (first as StartResult.Started).book
        assertEquals(saved.plans, started.plans)
        assertEquals(saved.logs, started.logs)
        assertEquals(id, started.active?.sourcePlanId)
        val finished = started.finishActive()
        assertTrue(finished.plans.isEmpty())
        assertNull(finished.active)
        assertTrue(finished.logs.single().finished)
        assertTrue(finished.openPlan(id) is StartResult.Rejected)
    }

    @Test
    fun finishingTheSessionStartedFromAPlanRemovesThatPlanAndKeepsAFavourite() {
        val logs = listOf(LogRecord(3, finished = true, kind = "HEAVY"))
        val favourite = 3L
        val saved = WorkoutBook(logs = logs, favourites = listOf(favourite))
            .saveBuilderPlan(BuilderDraft(kind = "HEAVY", mode = "LINES", lines = listOf(bench, row)))
            .saveBuilderPlan(BuilderDraft(kind = "BODYWEIGHT", mode = "LINES", lines = listOf(row)))
        val planId = saved.plans[0].id
        val otherId = saved.plans[1].id
        val started = (saved.openPlan(planId) as StartResult.Started).book
        assertEquals(saved.plans, started.plans)
        assertEquals(listOf(favourite), started.favourites)
        assertEquals(logs, started.logs)
        assertEquals(planId, started.active?.sourcePlanId)
        val finished = started.finishActive()
        assertTrue(finished.plans.none { it.id == planId })
        assertEquals(otherId, finished.plans.single().id)
        assertEquals(listOf(row), finished.plans.single().body.lines)
        assertEquals(listOf(favourite), finished.favourites)
        assertEquals(logs.size + 1, finished.logs.size)
        assertTrue(finished.logs.all { it.finished })
        assertNull(finished.active)
        assertTrue(finished.openPlan(planId) is StartResult.Rejected)
        val unrelated = (finished.startWorkout("STRENGTH", "Heavy") as StartResult.Started).book
        val afterOther = unrelated.finishActive()
        assertEquals(listOf(otherId), afterOther.plans.map { it.id })
        assertEquals(listOf(favourite), afterOther.favourites)
    }

    @Test
    fun deleteRemovesOnlyThatPlan() {
        val logs = listOf(LogRecord(3, finished = true, kind = "HEAVY"))
        val favourite = 3L
        val book = WorkoutBook(logs = logs, favourites = listOf(favourite))
            .saveBuilderPlan(BuilderDraft(kind = "HEAVY", mode = "LINES", lines = listOf(bench, row)))
            .saveBuilderPlan(BuilderDraft(kind = "BODYWEIGHT", mode = "LINES", lines = listOf(row)))
        val first = book.plans[0].id
        val second = book.plans[1]
        val deleted = book.deletePlan(first)
        assertEquals(listOf(second.id), deleted.plans.map { it.id })
        assertEquals(second.body.lines, deleted.plans.single().body.lines)
        assertEquals(logs, deleted.logs)
        assertEquals(listOf(favourite), deleted.favourites)
        assertNull(deleted.active)
    }

    @Test
    fun editChangesThePlansExerciseList() {
        val logs = listOf(LogRecord(3, finished = true, kind = "HEAVY"))
        val favourite = 3L
        val book = WorkoutBook(logs = logs, favourites = listOf(favourite))
            .saveBuilderPlan(BuilderDraft(kind = "HEAVY", mode = "LINES", lines = listOf(bench, row)))
            .saveBuilderPlan(BuilderDraft(kind = "BODYWEIGHT", mode = "PICK", lines = listOf(row)))
        val id = book.plans[0].id
        val other = book.plans[1]
        val editedLines = listOf(
            row.copy(sets = listOf(PlanSet(12, 50.0))),
            bench.copy(sets = listOf(PlanSet(5, 80.0), PlanSet(5, 85.0))),
        )
        val edited = book.editPlan(id, editedLines)
        assertEquals(editedLines, edited.plans.first { it.id == id }.body.lines)
        assertEquals(other, edited.plans.first { it.id == other.id })
        assertEquals(logs, edited.logs)
        assertEquals(listOf(favourite), edited.favourites)
        assertNull(edited.active)
    }

    @Test
    fun editedPlanSetsDifferFromTheOriginalAndAnAddedExerciseIsOnThePlan() {
        val logs = listOf(LogRecord(3, finished = true, kind = "HEAVY", favourite = true))
        val book = WorkoutBook(logs = logs, favourites = listOf(3L))
            .saveBuilderPlan(BuilderDraft(kind = "HEAVY", mode = "LINES", lines = listOf(bench, row)))
        val id = book.plans.single().id
        val originalSets = book.plans.single().body.lines.first { it.exerciseId == bench.exerciseId }.sets
        val curl = PlanLine(
            exerciseId = 21,
            name = "Curl",
            sets = listOf(PlanSet(12, 14.0)),
            groupKind = "PICK",
        )
        val editedSets = listOf(PlanSet(10, 65.0), PlanSet(4, 80.0), PlanSet(12, 20.0))
        val edited = book.editPlan(
            id,
            listOf(bench.copy(sets = editedSets), curl),
        )
        val saved = edited.plans.single()
        val savedBench = saved.body.lines.first { it.exerciseId == bench.exerciseId }
        assertNotEquals(originalSets, savedBench.sets)
        assertEquals(editedSets, savedBench.sets)
        assertEquals(curl.sets, saved.body.lines.first { it.exerciseId == curl.exerciseId && it.name == "Curl" }.sets)
        assertEquals(logs, edited.logs)
        assertEquals(listOf(3L), edited.favourites)
        assertNull(edited.active)
    }

    @Test
    fun anActiveSessionIsRestored() {
        val hold = StrengthHold(
            kind = "HEAVY",
            day = "2026-10-27",
            durationSec = null,
            started = true,
            focus = 1,
            lines = listOf(bench, row),
        )
        val session = HeldSession("STRENGTH", "Heavy", encodeStrengthHold(hold))
        val book = WorkoutBook(active = session)
        assertEquals(session, book.restored())
        val restored = restoreSession(book.restored())
        assertEquals("Heavy", restored?.label)
        assertEquals(hold, decodeStrengthHold(restored!!.payload!!))
        assertEquals(1, decodeStrengthHold(restored.payload)!!.focus)
        assertEquals("Bent Over Row", decodeStrengthHold(restored.payload)!!.lines[1].name)

        val hiit = HeldSession("HIIT", "HIIT")
        assertEquals(hiit, restoreSession(WorkoutBook(active = hiit).restored()))
    }

    @Test
    fun startingASecondWorkoutWhileOneIsActiveIsRejected() {
        val saved = WorkoutBook().saveBuilderPlan(
            BuilderDraft(kind = "HEAVY", mode = "PICK", lines = listOf(bench)),
        )
        val live = saved.startWorkout("HIIT", "HIIT")
        assertTrue(live is StartResult.Started)
        val busy = (live as StartResult.Started).book
        val cards = listOf("Heavy", "Bodyweight", "HIIT", "Swim", "Run", "Hike")
        for (card in cards) {
            val again = busy.startWorkout(card, card)
            assertTrue(again is StartResult.Rejected)
            val rejected = again as StartResult.Rejected
            assertEquals(ACTIVE_BLOCK, rejected.message)
            assertEquals(busy.active, rejected.book.active)
            assertEquals(busy.plans, rejected.book.plans)
        }
        val planAgain = busy.openPlan(saved.plans.single().id)
        assertTrue(planAgain is StartResult.Rejected)
        assertEquals(ACTIVE_BLOCK, (planAgain as StartResult.Rejected).message)
        assertEquals(saved.plans, planAgain.book.plans)
        val favourite = busy.startWorkout("STRENGTH", "Heavy")
        assertTrue(favourite is StartResult.Rejected)
        assertEquals(ACTIVE_BLOCK, (favourite as StartResult.Rejected).message)
    }
}
