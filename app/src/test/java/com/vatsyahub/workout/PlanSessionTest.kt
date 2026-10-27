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
import com.vatsyahub.workout.domain.encodeStrengthHold
import com.vatsyahub.workout.domain.finishActive
import com.vatsyahub.workout.domain.openPlan
import com.vatsyahub.workout.domain.restoreSession
import com.vatsyahub.workout.domain.restored
import com.vatsyahub.workout.domain.saveBuilderPlan
import com.vatsyahub.workout.domain.startWorkout
import org.junit.Assert.assertEquals
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
    fun openingAPlanCanBeStartedAgainBecauseThePlanRemains() {
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
        assertEquals(saved.plans, finished.plans)
        assertNull(finished.active)
        val second = finished.openPlan(id)
        assertTrue(second is StartResult.Started)
        val again = (second as StartResult.Started).book
        assertEquals(id, again.plans.single().id)
        assertEquals(listOf(bench, row), again.plans.single().body.lines)
        assertEquals(id, again.active?.sourcePlanId)
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
