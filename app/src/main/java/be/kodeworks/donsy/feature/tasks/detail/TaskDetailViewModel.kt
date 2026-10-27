// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.feature.tasks.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import be.kodeworks.donsy.domain.model.Effort
import be.kodeworks.donsy.domain.model.Importance
import be.kodeworks.donsy.domain.model.Task
import be.kodeworks.donsy.domain.model.TaskStatus
import be.kodeworks.donsy.domain.repository.TaskRepository
import be.kodeworks.donsy.domain.routine.Routines
import be.kodeworks.donsy.domain.time.TimeSource
import be.kodeworks.donsy.feature.tasks.RoutineInfo
import be.kodeworks.donsy.ui.format.DueLabel
import be.kodeworks.donsy.ui.navigation.TaskDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TaskDetailUiState(
    val isLoading: Boolean = true,
    /** Null once loaded means the task doesn't exist (any more). */
    val task: TaskDetail? = null,
    val dialog: TaskDetailDialog? = null,
)

data class TaskDetail(
    val id: Long,
    val title: String,
    val notes: String,
    val status: TaskStatus,
    val isImportant: Boolean,
    val due: DueLabel?,
    val estimateMinutes: Int?,
    val effort: Effort?,
    val steps: List<StepItem>,
    /** Set for a routine. */
    val routine: RoutineInfo? = null,
    /** A routine marked done today. */
    val doneToday: Boolean = false,
) {
    val stepsDone: Int get() = steps.count { it.isDone }
}

data class StepItem(val id: Long, val title: String, val isDone: Boolean)

sealed interface TaskDetailDialog {
    data class CompleteWithOpenSteps(val openSteps: Int) : TaskDetailDialog
    data object CompleteAfterLastStep : TaskDetailDialog
}

sealed interface TaskDetailEvent {
    data class Deleted(val taskId: Long) : TaskDetailEvent
}

@HiltViewModel
class TaskDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: TaskRepository,
    private val timeSource: TimeSource,
) : ViewModel() {

    private val taskId = savedStateHandle.toRoute<TaskDetailRoute>().taskId
    private val dialog = MutableStateFlow<TaskDetailDialog?>(null)
    private var latest: Task? = null

    val uiState: StateFlow<TaskDetailUiState> =
        combine(repository.observeTask(taskId), dialog) { task, dialog ->
            latest = task
            TaskDetailUiState(isLoading = false, task = task?.toDetail(), dialog = dialog)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskDetailUiState())

    private val eventChannel = Channel<TaskDetailEvent>(Channel.BUFFERED)
    val events: Flow<TaskDetailEvent> = eventChannel.receiveAsFlow()

    fun setStepDone(stepId: Long, done: Boolean) {
        val task = latest ?: return
        val step = task.subtasks.firstOrNull { it.id == stepId } ?: return
        // A routine's steps are a checklist for each day; it is marked done for the day on its own.
        val isLastOpenStep = done && !step.isDone && task.openStepCount == 1 && task.status != TaskStatus.DONE && !task.isRoutine
        viewModelScope.launch {
            repository.setStepDone(stepId, done)
            if (isLastOpenStep) dialog.value = TaskDetailDialog.CompleteAfterLastStep
        }
    }

    /** "Mark done": asks first when steps are still open. */
    fun complete() {
        val task = latest ?: return
        if (task.openStepCount > 0) {
            dialog.value = TaskDetailDialog.CompleteWithOpenSteps(task.openStepCount)
        } else {
            viewModelScope.launch { repository.complete(taskId, alsoCompleteOpenSteps = false) }
        }
    }

    /** A routine's "Done for today", or (with false) taking that back. */
    fun setDoneToday(done: Boolean) = launch { repository.setRoutineDone(taskId, done) }

    fun confirmCompletion(alsoCompleteOpenSteps: Boolean) {
        dialog.value = null
        viewModelScope.launch { repository.complete(taskId, alsoCompleteOpenSteps) }
    }

    fun dismissDialog() {
        dialog.value = null
    }

    fun reopen() = launch { repository.reopen(taskId) }

    fun pause() = launch { repository.pause(taskId) }

    fun resume() = launch { repository.resume(taskId) }

    fun delete() = launch {
        repository.delete(taskId)
        eventChannel.send(TaskDetailEvent.Deleted(taskId))
    }

    fun addStep(title: String) = launch { repository.addStep(taskId, title) }

    /** Adds the steps picked from a breakdown template, in order. */
    fun addSteps(titles: List<String>) = launch { repository.addSteps(taskId, titles) }

    fun renameStep(stepId: Long, title: String) = launch { repository.renameStep(stepId, title) }

    fun deleteStep(stepId: Long) = launch { repository.deleteStep(stepId) }

    fun moveStep(fromIndex: Int, toIndex: Int) = launch { repository.moveStep(taskId, fromIndex, toIndex) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private fun Task.toDetail(): TaskDetail {
        val now = timeSource.now().atZone(timeSource.zone()).toLocalDateTime()
        val days = repeatDays
        val time = dueTime
        return TaskDetail(
            id = id,
            title = title,
            notes = notes,
            status = status,
            isImportant = importance == Importance.HIGH,
            due = dueDate?.let { DueLabel.of(it, dueTime, now) },
            estimateMinutes = estimateMinutes,
            effort = effort,
            steps = subtasks.map { StepItem(it.id, it.title, it.isDone) },
            routine = if (days != null && time != null) RoutineInfo(days, time, Routines.occursOn(this, now.toLocalDate())) else null,
            doneToday = Routines.isDoneOn(this, now.toLocalDate()),
        )
    }
}
