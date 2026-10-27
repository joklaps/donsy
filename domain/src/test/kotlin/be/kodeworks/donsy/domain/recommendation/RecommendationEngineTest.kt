// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.domain.recommendation

import be.kodeworks.donsy.domain.model.Effort
import be.kodeworks.donsy.domain.model.EnergyLevel
import be.kodeworks.donsy.domain.model.Importance
import be.kodeworks.donsy.domain.model.RepeatDays
import be.kodeworks.donsy.domain.model.Subtask
import be.kodeworks.donsy.domain.model.Task
import be.kodeworks.donsy.domain.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class RecommendationEngineTest {

    private val zone = ZoneId.of("Europe/Brussels")
    private val now = Instant.parse("2026-10-21T10:00:00Z") // Wednesday 12:00 in Brussels
    private val today = LocalDate.of(2026, 10, 21)
    private var nextId = 1L

    private fun task(
        title: String = "Task ${nextId}",
        status: TaskStatus = TaskStatus.ACTIVE,
        importance: Importance = Importance.NORMAL,
        due: LocalDate? = null,
        dueTime: LocalTime? = null,
        repeatDays: RepeatDays? = null,
        estimate: Int? = null,
        effort: Effort? = null,
        notNowUntil: Instant? = null,
        lastWorkedAt: Instant? = null,
        createdAt: Instant = now.minus(Duration.ofDays(1)),
        steps: List<Boolean> = emptyList(), // true = done
    ): Task {
        val id = nextId++
        return Task(
            id = id, title = title, notes = "", status = status, importance = importance, dueDate = due, dueTime = dueTime,
            repeatDays = repeatDays, routineDoneOn = null,
            estimateMinutes = estimate, effort = effort, notNowUntil = notNowUntil, lastWorkedAt = lastWorkedAt,
            sourceBrainDumpId = null, createdAt = createdAt, updatedAt = createdAt, completedAt = null,
            subtasks = steps.mapIndexed { i, done -> Subtask(id * 100 + i, id, "Step ${i + 1}", i, done, null) },
        )
    }

    private fun recommend(vararg tasks: Task, context: RecommendationContext = RecommendationContext()) =
        RecommendationEngine.recommend(tasks.toList(), context, now, zone)

    private fun suggested(vararg tasks: Task, context: RecommendationContext = RecommendationContext()) =
        recommend(*tasks, context = context) as Recommendation.Suggest

    // --- Eligibility -------------------------------------------------------------------------

    @Test
    fun onlyActiveTasksThatAreNotSetAsideAreSuggested() {
        val result = recommend(
            task(status = TaskStatus.DONE),
            task(status = TaskStatus.PAUSED),
            task(status = TaskStatus.ARCHIVED),
            task(notNowUntil = now.plusSeconds(60)),
        )
        assertTrue(result is Recommendation.Nothing)
        assertEquals(false, (result as Recommendation.Nothing).allSkipped)
    }

    @Test
    fun routinesAreNeverSuggested() {
        val pill = task(repeatDays = RepeatDays.EVERY_DAY, dueTime = LocalTime.of(8, 0), importance = Importance.HIGH)
        assertTrue(recommend(pill) is Recommendation.Nothing)
        val plain = task()
        assertEquals(plain.id, suggested(pill, plain).task.id)
    }

    @Test
    fun notNowExpires() {
        val task = task(notNowUntil = now)
        assertEquals(task.id, suggested(task).task.id)
    }

    @Test
    fun notNowLastsUntilTheStartOfTomorrowLocally() {
        assertEquals(Instant.parse("2026-10-21T22:00:00Z"), RecommendationEngine.notNowUntil(now, zone))
    }

    @Test
    fun skippedTasksAreLeftOut_andSkippingEverythingSaysSo() {
        val a = task(importance = Importance.HIGH)
        val b = task()
        assertEquals(b.id, suggested(a, b, context = RecommendationContext(skippedTaskIds = setOf(a.id))).task.id)

        val result = recommend(a, b, context = RecommendationContext(skippedTaskIds = setOf(a.id, b.id)))
        assertTrue((result as Recommendation.Nothing).allSkipped)
    }

    // --- Dimensions --------------------------------------------------------------------------

    @Test
    fun urgencyFollowsTheDueDate() {
        val cases = mapOf(
            null to 0, today to 100, today.plusDays(1) to 100, today.plusDays(3) to 80,
            today.plusDays(7) to 50, today.plusDays(8) to 20,
        )
        cases.forEach { (due, expected) -> assertEquals("due $due", expected, RecommendationEngine.urgency(due, today)) }
    }

    @Test
    fun overdueUrgencyFadesToAFloorSoItCannotDominateForever() {
        val byDaysLate = (1..12).map { RecommendationEngine.urgency(today.minusDays(it.toLong()), today) }
        assertEquals(listOf(100, 100, 100, 90, 80, 70, 60, 50, 40, 40, 40, 40), byDaysLate)
    }

    @Test
    fun anImportantUndatedTaskBeatsAPlainOneDueNextMonth() {
        val important = task(importance = Importance.HIGH)
        val later = task(due = today.plusDays(30))
        assertEquals(important.id, suggested(later, important).task.id)
    }

    @Test
    fun longOverdueTasksDoNotCrowdOutWhatIsDueToday() {
        val longOverdue = task(due = today.minusDays(20))
        val dueToday = task(due = today)
        assertEquals(dueToday.id, suggested(longOverdue, dueToday).task.id)
    }

    @Test
    fun timeFit() {
        assertEquals(50, RecommendationEngine.timeFit(estimate = 30, available = null))
        assertEquals(60, RecommendationEngine.timeFit(estimate = null, available = 15))
        assertEquals(100, RecommendationEngine.timeFit(estimate = 15, available = 15))
        assertEquals(20, RecommendationEngine.timeFit(estimate = 30, available = 15))
    }

    @Test
    fun aBigTaskBrokenIntoStepsFitsASmallTimeWindow() {
        val big = task(estimate = 60, steps = listOf(false, false, false, false)) // 15 min per step
        assertEquals(15, RecommendationEngine.effectiveEstimate(big))
        val reasons = suggested(big, context = RecommendationContext(availableMinutes = 15)).reasons
        assertTrue(Reason.FitsAvailableTime(15) in reasons)
    }

    @Test
    fun availableTimePrefersTasksThatFit() {
        val long = task(estimate = 60)
        val short = task(estimate = 5)
        assertEquals(short.id, suggested(long, short, context = RecommendationContext(availableMinutes = 15)).task.id)
    }

    @Test
    fun energyFitMatrix() {
        val expected = mapOf(
            EnergyLevel.LOW to listOf(100, 50, 10),
            EnergyLevel.MEDIUM to listOf(80, 100, 50),
            EnergyLevel.HIGH to listOf(60, 80, 100),
        )
        expected.forEach { (energy, row) ->
            assertEquals(row, Effort.entries.map { RecommendationEngine.energyFit(energy, it) })
            assertEquals(60, RecommendationEngine.energyFit(energy, null))
        }
        assertEquals(50, RecommendationEngine.energyFit(null, Effort.HIGH))
    }

    @Test
    fun lowEnergyFavoursLightTasks() {
        val heavy = task(effort = Effort.HIGH)
        val light = task(effort = Effort.LOW)
        val result = suggested(heavy, light, context = RecommendationContext(energy = EnergyLevel.LOW))
        assertEquals(light.id, result.task.id)
        assertTrue(Reason.MatchesEnergy(EnergyLevel.LOW) in result.reasons)
    }

    @Test
    fun continuityPrefersRecentWorkThenStartedTasks() {
        assertEquals(100, RecommendationEngine.continuity(task(lastWorkedAt = now.minus(Duration.ofHours(23))), now))
        assertEquals(70, RecommendationEngine.continuity(task(lastWorkedAt = now.minus(Duration.ofDays(3)), steps = listOf(true, false)), now))
        assertEquals(0, RecommendationEngine.continuity(task(lastWorkedAt = now.minus(Duration.ofDays(3))), now))
    }

    // --- Ties and determinism ----------------------------------------------------------------

    @Test
    fun tiesBreakOnUrgencyThenDueDateAndTimeThenAgeThenId() {
        // Identical scores: the older task wins, then the lower id, then the earlier due date.
        val older = task(createdAt = now.minus(Duration.ofDays(5)))
        val newer = task(createdAt = now.minus(Duration.ofDays(1)))
        assertEquals(older.id, suggested(newer, older).task.id)

        val sameAgeA = task(createdAt = now)
        val sameAgeB = task(createdAt = now)
        assertEquals(sameAgeA.id, suggested(sameAgeB, sameAgeA).task.id)

        val dueSooner = task(due = today.plusDays(4))
        val dueLater = task(due = today.plusDays(6))
        assertEquals(dueSooner.id, suggested(dueLater, dueSooner).task.id)

        // Same day: an earlier time wins; no time counts as the end of the day.
        val atNine = task(due = today.plusDays(4), dueTime = LocalTime.of(9, 0))
        val atFour = task(due = today.plusDays(4), dueTime = LocalTime.of(16, 0))
        val endOfDay = task(due = today.plusDays(4))
        assertEquals(atNine.id, suggested(endOfDay, atFour, atNine).task.id)
        assertEquals(atFour.id, suggested(endOfDay, atFour).task.id)
    }

    @Test
    fun theSameInputAlwaysGivesTheSameSuggestion() {
        val tasks = listOf(
            task(due = today.plusDays(2), estimate = 30),
            task(importance = Importance.HIGH, effort = Effort.LOW),
            task(due = today.minusDays(5), steps = listOf(true, false)),
            task(estimate = 5),
            task(createdAt = now.minus(Duration.ofDays(9))),
        )
        val context = RecommendationContext(availableMinutes = 15, energy = EnergyLevel.MEDIUM)
        val expected = RecommendationEngine.recommend(tasks, context, now, zone)
        repeat(20) { seed ->
            assertEquals(expected, RecommendationEngine.recommend(tasks.shuffled(java.util.Random(seed.toLong())), context, now, zone))
        }
    }

    // --- What is suggested and why -----------------------------------------------------------

    @Test
    fun theFirstOpenStepIsSuggested() {
        val task = task(steps = listOf(true, false, false))
        assertEquals(task.subtasks[1], suggested(task).step)
    }

    @Test
    fun aTaskWithAllStepsDoneIsSuggestedAsAWrapUp() {
        val result = suggested(task(steps = listOf(true, true)))
        assertNull(result.step)
        assertEquals(listOf(Reason.AllStepsDone), result.reasons)
    }

    @Test
    fun reasonsAreTheStrongestSignals() {
        val result = suggested(task(due = today.plusDays(1), importance = Importance.HIGH, estimate = 5))
        assertEquals(listOf(Reason.DueTomorrow, Reason.Important), result.reasons)

        assertEquals(listOf(Reason.WasDue), suggested(task(due = today.minusDays(2))).reasons)
        assertEquals(listOf(Reason.DueSoon(4)), suggested(task(due = today.plusDays(4))).reasons)
        assertEquals(listOf(Reason.Quick(5)), suggested(task(estimate = 5)).reasons)
        assertEquals(
            listOf(Reason.StepsInProgress(done = 1, total = 3)),
            suggested(task(steps = listOf(true, false, false))).reasons,
        )
    }

    @Test
    fun withoutAnySignalItIsSimplyAGoodPlaceToStart() {
        assertEquals(listOf(Reason.GoodPlaceToStart), suggested(task()).reasons)
    }

    // --- The example in docs/recommendation-engine.md -------------------------------------------

    @Test
    fun documentedWorkedExample() {
        val biology = task(title = "Prepare for my biology test", due = today.plusDays(2), estimate = 60, steps = List(4) { false })
        val grandma = task(title = "Call grandma", importance = Importance.HIGH, estimate = 5)
        val essay = task(
            title = "Finish the essay",
            due = today.minusDays(10),
            lastWorkedAt = now.minus(Duration.ofDays(1)),
            steps = listOf(true, false),
        )
        val withTime = RecommendationContext(availableMinutes = 15)
        fun total(task: Task, context: RecommendationContext) = RecommendationEngine.scores(task, context, now, today).total

        assertEquals(listOf(6150, 5250, 5150), listOf(biology, grandma, essay).map { total(it, withTime) })
        val suggestion = suggested(biology, grandma, essay, context = withTime)
        assertEquals(biology.id, suggestion.task.id)
        assertEquals(biology.subtasks.first(), suggestion.step)
        assertEquals(listOf(Reason.DueSoon(2), Reason.FitsAvailableTime(15)), suggestion.reasons)

        // As one undivided block it would not fit the 15 minutes and drop to last place.
        assertEquals(4550, total(biology.copy(subtasks = emptyList()), withTime))

        // Without a time choice.
        val noTime = RecommendationContext()
        assertEquals(listOf(5150, 4250, 4950), listOf(biology, grandma, essay).map { total(it, noTime) })
        assertEquals(listOf(Reason.DueSoon(2), Reason.Quick(15)), suggested(biology, grandma, essay).reasons)
    }

    // --- Starters ----------------------------------------------------------------------------

    @Test
    fun startersDependOnWhatExists() {
        assertEquals(listOf(Starter.ADD_SMALL_TASK), (recommend() as Recommendation.Nothing).starters)

        val withInbox = recommend(context = RecommendationContext(brainDumpCount = 3)) as Recommendation.Nothing
        assertEquals(listOf(Starter.SORT_BRAIN_DUMP, Starter.ADD_SMALL_TASK), withInbox.starters)

        val onlyPaused = recommend(task(status = TaskStatus.PAUSED)) as Recommendation.Nothing
        assertEquals(listOf(Starter.ADD_SMALL_TASK, Starter.PICK_ANY_TASK), onlyPaused.starters)
    }
}
