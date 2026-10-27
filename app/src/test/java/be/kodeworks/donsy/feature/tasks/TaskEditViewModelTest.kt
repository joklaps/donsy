// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.feature.tasks

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import be.kodeworks.donsy.core.routine.MissingReminderPermission
import be.kodeworks.donsy.data.repository.RoomBrainDumpRepository
import be.kodeworks.donsy.data.repository.RoomTaskRepository
import be.kodeworks.donsy.domain.model.Effort
import be.kodeworks.donsy.domain.model.Importance
import be.kodeworks.donsy.domain.model.RepeatDays
import be.kodeworks.donsy.domain.model.TaskDraft
import be.kodeworks.donsy.domain.time.FakeTimeSource
import be.kodeworks.donsy.feature.tasks.edit.RepeatChoice
import be.kodeworks.donsy.feature.tasks.edit.TaskEditMode
import be.kodeworks.donsy.feature.tasks.edit.TaskEditResult
import be.kodeworks.donsy.feature.tasks.edit.TaskEditViewModel
import be.kodeworks.donsy.testing.FakeReminderPermissions
import be.kodeworks.donsy.testing.MainDispatcherRule
import be.kodeworks.donsy.testing.inMemoryDatabase
import be.kodeworks.donsy.testing.testPreferences
import be.kodeworks.donsy.ui.navigation.TaskEditRoute
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
class TaskEditViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val database = inMemoryDatabase()
    private val time = FakeTimeSource()
    private val tasks = RoomTaskRepository(database, time)
    private val brainDump = RoomBrainDumpRepository(database.brainDumpDao(), time)
    private val permissions = FakeReminderPermissions()

    @After
    fun tearDown() = database.close()

    private fun viewModel(taskId: Long = TaskEditRoute.NO_ID, brainDumpItemId: Long = TaskEditRoute.NO_ID) =
        TaskEditViewModel(
            SavedStateHandle(mapOf("taskId" to taskId, "brainDumpItemId" to brainDumpItemId)),
            tasks,
            brainDump,
            permissions,
            testPreferences(),
        )

    private suspend fun TaskEditViewModel.loaded() = uiState.first { !it.isLoading }

    @Test
    fun newTaskNeedsATitleAndIsCreatedWithAllFields() = runTest {
        val viewModel = viewModel()
        assertEquals(TaskEditMode.NEW, viewModel.loaded().mode)
        assertFalse(viewModel.uiState.value.canSave)

        viewModel.setTitle("Biology test")
        viewModel.setNotes("Chapters 3-5")
        viewModel.setImportant(true)
        viewModel.setDueDate(LocalDate.of(2026, 10, 24))
        viewModel.setDueTime(LocalTime.of(8, 30))
        viewModel.toggleEstimate(30)
        viewModel.toggleEffort(Effort.HIGH)

        viewModel.results.test {
            viewModel.save()
            val created = awaitItem() as TaskEditResult.Created
            with(tasks.observeTask(created.taskId).first()!!) {
                assertEquals("Biology test", title)
                assertEquals("Chapters 3-5", notes)
                assertEquals(Importance.HIGH, importance)
                assertEquals(LocalDate.of(2026, 10, 24), dueDate)
                assertEquals(LocalTime.of(8, 30), dueTime)
                assertEquals(30, estimateMinutes)
                assertEquals(Effort.HIGH, effort)
            }
        }
    }

    @Test
    fun choosingTheSelectedEstimateOrEffortAgainClearsIt() = runTest {
        val viewModel = viewModel()
        viewModel.loaded()
        viewModel.toggleEstimate(15)
        viewModel.toggleEstimate(15)
        viewModel.toggleEffort(Effort.LOW)
        viewModel.toggleEffort(Effort.LOW)
        assertNull(viewModel.uiState.value.estimateMinutes)
        assertNull(viewModel.uiState.value.effort)
    }

    @Test
    fun aDueTimeNeedsADateAndGoesWithIt() = runTest {
        val viewModel = viewModel()
        viewModel.loaded()
        viewModel.setDueTime(LocalTime.of(9, 0))
        assertNull(viewModel.uiState.value.dueTime)

        viewModel.setDueDate(LocalDate.of(2026, 10, 24))
        viewModel.setDueTime(LocalTime.of(9, 0))
        viewModel.setDueDate(LocalDate.of(2026, 10, 25))
        assertEquals(LocalTime.of(9, 0), viewModel.uiState.value.dueTime)

        viewModel.setDueDate(null)
        assertNull(viewModel.uiState.value.dueTime)
    }

    @Test
    fun editingKeepsOrResetsTheDueTime() = runTest {
        val id = tasks.create(TaskDraft("Hand in essay", dueDate = LocalDate.of(2026, 10, 24), dueTime = LocalTime.of(8, 30)))!!
        val viewModel = viewModel(taskId = id)
        assertEquals(LocalTime.of(8, 30), viewModel.loaded().dueTime)

        viewModel.setDueTime(null)
        viewModel.results.test {
            viewModel.save()
            assertEquals(TaskEditResult.Updated, awaitItem())
        }
        with(tasks.observeTask(id).first()!!) {
            assertEquals(LocalDate.of(2026, 10, 24), dueDate)
            assertNull(dueTime)
        }
    }

    @Test
    fun aRoutineNeedsDaysATimeAndPermissionBeforeItCanBeSaved() = runTest {
        permissions.missing = MissingReminderPermission.ALARMS
        val viewModel = viewModel()
        viewModel.loaded()
        viewModel.setTitle("Pack my schoolbag")
        viewModel.setRepeat(RepeatChoice.PICK_DAYS)
        assertFalse(viewModel.uiState.value.canSave)

        viewModel.toggleDay(DayOfWeek.SUNDAY)
        viewModel.toggleDay(DayOfWeek.WEDNESDAY)
        assertFalse(viewModel.uiState.value.canSave)

        viewModel.setDueTime(LocalTime.of(19, 0))
        assertFalse(viewModel.uiState.value.canSave)
        assertEquals(MissingReminderPermission.ALARMS, viewModel.uiState.value.missingPermission)

        // Allowed in Android's settings; noticed when the screen comes back.
        permissions.missing = null
        viewModel.refreshPermissions()
        assertTrue(viewModel.uiState.value.canSave)

        viewModel.results.test {
            viewModel.save()
            val created = awaitItem() as TaskEditResult.Created
            with(tasks.observeTask(created.taskId).first()!!) {
                assertEquals(RepeatDays.of(setOf(DayOfWeek.SUNDAY, DayOfWeek.WEDNESDAY)), repeatDays)
                assertEquals(LocalTime.of(19, 0), dueTime)
                assertNull(dueDate)
            }
        }
    }

    @Test
    fun aRoutineOpensWithItsRepeatChoiceAndCanBecomeAnOrdinaryTaskAgain() = runTest {
        val id = tasks.create(TaskDraft("Take my pill", dueTime = LocalTime.of(8, 0), repeatDays = RepeatDays.WEEKDAYS))!!
        val viewModel = viewModel(taskId = id)
        with(viewModel.loaded()) {
            assertEquals(RepeatChoice.WEEKDAYS, repeat)
            assertTrue(isRoutine)
        }

        viewModel.setRepeat(RepeatChoice.NEVER)
        assertNull(viewModel.uiState.value.dueTime)
        viewModel.results.test {
            viewModel.save()
            assertEquals(TaskEditResult.Updated, awaitItem())
        }
        assertFalse(tasks.observeTask(id).first()!!.isRoutine)
    }

    @Test
    fun editingLoadsTheTaskAndSavesChanges() = runTest {
        val id = tasks.create(TaskDraft("Old title", estimateMinutes = 5))!!
        val viewModel = viewModel(taskId = id)
        with(viewModel.loaded()) {
            assertEquals(TaskEditMode.EDIT, mode)
            assertEquals("Old title", title)
            assertEquals(5, estimateMinutes)
        }

        viewModel.setTitle("New title")
        viewModel.results.test {
            viewModel.save()
            assertEquals(TaskEditResult.Updated, awaitItem())
        }
        assertEquals("New title", tasks.observeTask(id).first()!!.title)
    }

    @Test
    fun aThoughtIsPrefilledAndConverted() = runTest {
        val itemId = brainDump.capture("Plan Sam's party\nCake and music")!!
        val viewModel = viewModel(brainDumpItemId = itemId)
        with(viewModel.loaded()) {
            assertEquals(TaskEditMode.FROM_BRAIN_DUMP, mode)
            assertEquals("Plan Sam's party", title)
            assertEquals("Cake and music", notes)
        }

        viewModel.results.test {
            viewModel.save()
            val converted = awaitItem() as TaskEditResult.Converted
            assertEquals(itemId, tasks.observeTask(converted.taskId).first()!!.sourceBrainDumpId)
        }
        assertTrue(brainDump.observeInbox().first().isEmpty())
    }
}
