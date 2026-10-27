// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.feature.tasks

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import be.kodeworks.donsy.data.repository.RoomTaskRepository
import be.kodeworks.donsy.domain.model.TaskDraft
import be.kodeworks.donsy.domain.model.TaskStatus
import be.kodeworks.donsy.domain.time.FakeTimeSource
import be.kodeworks.donsy.feature.tasks.detail.TaskDetailDialog
import be.kodeworks.donsy.feature.tasks.detail.TaskDetailEvent
import be.kodeworks.donsy.feature.tasks.detail.TaskDetailUiState
import be.kodeworks.donsy.feature.tasks.detail.TaskDetailViewModel
import be.kodeworks.donsy.testing.MainDispatcherRule
import be.kodeworks.donsy.testing.inMemoryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TaskDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val database = inMemoryDatabase()
    private val time = FakeTimeSource()
    private val repository = RoomTaskRepository(database, time)

    @After
    fun tearDown() = database.close()

    private fun viewModelFor(taskId: Long) =
        TaskDetailViewModel(SavedStateHandle(mapOf("taskId" to taskId)), repository, time)

    private suspend fun TaskDetailViewModel.state(predicate: (TaskDetailUiState) -> Boolean) =
        uiState.first { !it.isLoading && predicate(it) }

    @Test
    fun checkingOffTheLastOpenStepOffersToFinishTheTask() = runTest {
        val id = repository.create(TaskDraft("Essay"))!!
        val outline = repository.addStep(id, "Outline")!!
        val draft = repository.addStep(id, "Draft")!!
        val viewModel = viewModelFor(id)
        viewModel.state { it.task?.steps?.size == 2 }

        viewModel.setStepDone(outline, true)
        viewModel.state { it.task?.stepsDone == 1 }
        assertNull(viewModel.uiState.value.dialog)

        viewModel.setStepDone(draft, true)
        viewModel.state { it.dialog == TaskDetailDialog.CompleteAfterLastStep }

        // "Not yet" leaves the task open: nothing completes by itself.
        viewModel.dismissDialog()
        assertEquals(TaskStatus.ACTIVE, viewModel.state { it.dialog == null }.task?.status)

        // The ViewModel decides on the last state it has seen, and Room answers asynchronously:
        // wait for each change to arrive before the next one.
        viewModel.state { it.task?.stepsDone == 2 }
        viewModel.setStepDone(draft, false)
        viewModel.state { it.task?.stepsDone == 1 }
        viewModel.setStepDone(draft, true)
        viewModel.state { it.dialog == TaskDetailDialog.CompleteAfterLastStep }
        viewModel.confirmCompletion(alsoCompleteOpenSteps = false)
        viewModel.state { it.task?.status == TaskStatus.DONE }
    }

    @Test
    fun markDoneWithOpenStepsAsksWhetherToCheckThemOff() = runTest {
        val id = repository.create(TaskDraft("Room"))!!
        repository.addStep(id, "Desk")
        val viewModel = viewModelFor(id)
        viewModel.state { it.task?.steps?.size == 1 }

        viewModel.complete()
        assertEquals(TaskDetailDialog.CompleteWithOpenSteps(1), viewModel.state { it.dialog != null }.dialog)

        viewModel.confirmCompletion(alsoCompleteOpenSteps = false)
        val task = viewModel.state { it.task?.status == TaskStatus.DONE }.task!!
        assertEquals(0, task.stepsDone)
    }

    @Test
    fun stepsCanBeAddedRenamedMovedAndDeleted() = runTest {
        val id = repository.create(TaskDraft("Party"))!!
        val viewModel = viewModelFor(id)
        viewModel.state { it.task != null }

        viewModel.addStep("Invite")
        viewModel.addStep("Cake")
        viewModel.state { it.task?.steps?.size == 2 }
        viewModel.moveStep(1, 0)
        val steps = viewModel.state { it.task?.steps?.firstOrNull()?.title == "Cake" }.task!!.steps
        viewModel.renameStep(steps[0].id, "Chocolate cake")
        viewModel.state { it.task?.steps?.firstOrNull()?.title == "Chocolate cake" }
        viewModel.deleteStep(steps[1].id)
        assertEquals(listOf("Chocolate cake"), viewModel.state { it.task?.steps?.size == 1 }.task!!.steps.map { it.title })
    }

    @Test
    fun templateStepsAreAddedAfterExistingOnesInOrder() = runTest {
        val id = repository.create(TaskDraft("Biology test"))!!
        val viewModel = viewModelFor(id)
        viewModel.addStep("Ask Sam for the notes")
        viewModel.state { it.task?.steps?.size == 1 }

        viewModel.addSteps(listOf("Find the topics", "Make flashcards"))

        val steps = viewModel.state { it.task?.steps?.size == 3 }.task!!.steps
        assertEquals(listOf("Ask Sam for the notes", "Find the topics", "Make flashcards"), steps.map { it.title })
    }

    @Test
    fun pauseResumeAndDelete() = runTest {
        val id = repository.create(TaskDraft("Guitar"))!!
        val viewModel = viewModelFor(id)
        viewModel.state { it.task != null }

        viewModel.pause()
        viewModel.state { it.task?.status == TaskStatus.PAUSED }
        viewModel.resume()
        viewModel.state { it.task?.status == TaskStatus.ACTIVE }

        viewModel.events.test {
            viewModel.delete()
            assertEquals(TaskDetailEvent.Deleted(id), awaitItem())
        }
    }
}
