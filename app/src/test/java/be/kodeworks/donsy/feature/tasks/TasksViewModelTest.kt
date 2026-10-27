// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.feature.tasks

import be.kodeworks.donsy.core.routine.MissingReminderPermission
import be.kodeworks.donsy.data.repository.RoomTaskRepository
import be.kodeworks.donsy.domain.model.Importance
import be.kodeworks.donsy.domain.model.RepeatDays
import be.kodeworks.donsy.domain.model.TaskDraft
import be.kodeworks.donsy.domain.model.TaskStatus
import be.kodeworks.donsy.domain.time.FakeTimeSource
import be.kodeworks.donsy.testing.FakeReminderPermissions
import be.kodeworks.donsy.testing.MainDispatcherRule
import be.kodeworks.donsy.testing.inMemoryDatabase
import be.kodeworks.donsy.testing.testPreferences
import be.kodeworks.donsy.ui.format.DueLabel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
class TasksViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val database = inMemoryDatabase()
    private val time = FakeTimeSource() // 2026-10-21
    private val repository = RoomTaskRepository(database, time)
    // Lazy: viewModelScope must be created after MainDispatcherRule has installed the test dispatcher.
    private val permissions = FakeReminderPermissions()
    private val viewModel by lazy { TasksViewModel(repository, time, permissions, testPreferences()) }

    @After
    fun tearDown() = database.close()

    private suspend fun state(predicate: (TasksUiState) -> Boolean) = viewModel.uiState.first { !it.isLoading && predicate(it) }

    @Test
    fun activeTasksAreOrderedByDueDateThenImportanceThenAge() = runTest {
        val undated = repository.create(TaskDraft("Undated"))!!
        time.advanceBy(Duration.ofMinutes(1))
        val importantUndated = repository.create(TaskDraft("Important undated", importance = Importance.HIGH))!!
        val friday = repository.create(TaskDraft("Friday", dueDate = LocalDate.of(2026, 10, 23)))!!
        val yesterday = repository.create(TaskDraft("Yesterday", dueDate = LocalDate.of(2026, 10, 20)))!!

        val state = state { it.active.size == 4 }

        assertEquals(listOf(yesterday, friday, importantUndated, undated), state.active.map { it.id })
        assertTrue(state.active.first().due!!.isPast)
        assertEquals(DueLabel.ThisWeek(LocalDate.of(2026, 10, 23)), state.active[1].due)
    }

    @Test
    fun onTheSameDayEarlierTimesComeFirstAndNoTimeMeansTheEndOfTheDay() = runTest {
        val today = LocalDate.of(2026, 10, 21) // it is 12:00 in Brussels
        val endOfDay = repository.create(TaskDraft("End of day", dueDate = today))!!
        val afternoon = repository.create(TaskDraft("Afternoon", dueDate = today, dueTime = LocalTime.of(16, 0)))!!
        val morning = repository.create(TaskDraft("Morning", dueDate = today, dueTime = LocalTime.of(9, 0)))!!

        val state = state { it.active.size == 3 }

        assertEquals(listOf(morning, afternoon, endOfDay), state.active.map { it.id })
        assertEquals(
            listOf(DueLabel.EarlierToday(LocalTime.of(9, 0)), DueLabel.Today(LocalTime.of(16, 0)), DueLabel.Today()),
            state.active.map { it.due },
        )
    }

    @Test
    fun routinesHaveTheirOwnSectionWithTodaysFirst() = runTest {
        // Wednesday 21 October 2026.
        val weekend = repository.create(
            TaskDraft("Water the plants", dueTime = LocalTime.of(9, 0), repeatDays = RepeatDays.of(setOf(DayOfWeek.SATURDAY))),
        )!!
        val bag = repository.create(TaskDraft("Pack my schoolbag", dueTime = LocalTime.of(19, 0), repeatDays = RepeatDays.WEEKDAYS))!!
        val pill = repository.create(TaskDraft("Take my pill", dueTime = LocalTime.of(8, 0), repeatDays = RepeatDays.EVERY_DAY))!!
        val plain = repository.create(TaskDraft("Essay"))!!

        val state = state { it.routines.size == 3 }

        assertEquals(listOf(pill, bag, weekend), state.routines.map { it.id })
        assertEquals(listOf(plain), state.active.map { it.id })
        assertEquals(listOf(true, true, false), state.routines.map { it.routine!!.isToday })
        assertNull(state.remindersMissing)
    }

    @Test
    fun tickingARoutineMarksItDoneForTodayOnly() = runTest {
        val pill = repository.create(TaskDraft("Take my pill", dueTime = LocalTime.of(8, 0), repeatDays = RepeatDays.EVERY_DAY))!!
        state { it.routines.size == 1 }

        viewModel.toggleDone(pill)
        assertTrue(state { it.routines.single().isDone }.routines.single().routine!!.isToday)
        assertEquals(TaskStatus.ACTIVE, repository.observeTask(pill).first()!!.status)

        viewModel.toggleDone(pill)
        state { !it.routines.single().isDone }
    }

    @Test
    fun aRoutineNotDueTodayCannotBeTickedOff() = runTest {
        val weekend = repository.create(
            TaskDraft("Water the plants", dueTime = LocalTime.of(9, 0), repeatDays = RepeatDays.of(setOf(DayOfWeek.SATURDAY))),
        )!!
        state { it.routines.size == 1 }
        viewModel.toggleDone(weekend)
        assertNull(repository.observeTask(weekend).first()!!.routineDoneOn)
    }

    @Test
    fun missingPermissionsAreOnlyMentionedWhenThereAreRoutines() = runTest {
        permissions.missing = MissingReminderPermission.NOTIFICATIONS
        repository.create(TaskDraft("Essay"))
        assertNull(state { it.active.size == 1 }.remindersMissing)

        repository.create(TaskDraft("Take my pill", dueTime = LocalTime.of(8, 0), repeatDays = RepeatDays.EVERY_DAY))
        assertEquals(MissingReminderPermission.NOTIFICATIONS, state { it.routines.size == 1 }.remindersMissing)

        permissions.missing = null
        viewModel.refreshPermissions()
        state { it.remindersMissing == null }
    }

    @Test
    fun tasksAreGroupedByStatus() = runTest {
        repository.create(TaskDraft("Active"))
        repository.pause(repository.create(TaskDraft("Paused"))!!)
        repository.complete(repository.create(TaskDraft("Done"))!!, alsoCompleteOpenSteps = false)

        val state = state { it.done.isNotEmpty() && it.paused.isNotEmpty() }

        assertEquals(listOf("Active"), state.active.map { it.title })
        assertEquals(listOf("Paused"), state.paused.map { it.title })
        assertEquals(listOf("Done"), state.done.map { it.title })
    }

    @Test
    fun completingATaskWithOpenStepsAsksFirst() = runTest {
        val id = repository.create(TaskDraft("Trip"))!!
        repository.addStep(id, "Pack")
        repository.addStep(id, "Tickets")
        state { it.active.firstOrNull()?.stepsTotal == 2 }

        viewModel.toggleDone(id)
        assertEquals(PendingCompletion(id, openSteps = 2), state { it.pendingCompletion != null }.pendingCompletion)

        viewModel.confirmCompletion(alsoCompleteOpenSteps = true)
        val done = state { it.done.isNotEmpty() }.done.single()
        assertEquals(2, done.stepsDone)
        assertNull(viewModel.uiState.value.pendingCompletion)
    }

    @Test
    fun taskWithoutOpenStepsCompletesDirectly_andTogglingAgainReopens() = runTest {
        val id = repository.create(TaskDraft("Quick one"))!!
        state { it.active.isNotEmpty() }

        viewModel.toggleDone(id)
        state { it.done.isNotEmpty() }

        viewModel.toggleDone(id)
        assertEquals(listOf(id), state { it.active.isNotEmpty() }.active.map { it.id })
    }

    @Test
    fun restoreUndoesADeletion() = runTest {
        val id = repository.create(TaskDraft("Oops"))!!
        repository.delete(id)
        state { it.isEmpty }

        viewModel.restore(id)
        assertEquals(listOf(id), state { it.active.isNotEmpty() }.active.map { it.id })
    }
}
