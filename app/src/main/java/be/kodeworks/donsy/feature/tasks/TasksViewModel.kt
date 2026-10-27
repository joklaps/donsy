// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import be.kodeworks.donsy.core.routine.MissingReminderPermission
import be.kodeworks.donsy.core.routine.ReminderPermissions
import be.kodeworks.donsy.data.preferences.UserPreferencesRepository
import be.kodeworks.donsy.domain.model.Importance
import be.kodeworks.donsy.domain.model.RepeatDays
import be.kodeworks.donsy.domain.model.Task
import be.kodeworks.donsy.domain.model.TaskStatus
import be.kodeworks.donsy.domain.repository.TaskRepository
import be.kodeworks.donsy.domain.routine.Routines
import be.kodeworks.donsy.domain.time.TimeSource
import be.kodeworks.donsy.ui.format.DueLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject

data class TasksUiState(
    val isLoading: Boolean = true,
    /** Active routines: today's first, by time. */
    val routines: List<TaskListItem> = emptyList(),
    /** Set when routines exist but Android doesn't allow their reminders (any more). */
    val remindersMissing: MissingReminderPermission? = null,
    val active: List<TaskListItem> = emptyList(),
    val paused: List<TaskListItem> = emptyList(),
    val done: List<TaskListItem> = emptyList(),
    val showDone: Boolean = false,
    /** Set while asking whether open steps should be checked off too. */
    val pendingCompletion: PendingCompletion? = null,
) {
    val isEmpty: Boolean get() = routines.isEmpty() && active.isEmpty() && paused.isEmpty() && done.isEmpty()

    /** The topmost section, so the screen can tell when a new one appears above the others. */
    val firstSection: String? get() = when {
        isLoading || isEmpty -> null
        routines.isNotEmpty() -> "routines"
        active.isNotEmpty() -> "active"
        paused.isNotEmpty() -> "paused"
        else -> "done"
    }
}

data class TaskListItem(
    val id: Long,
    val title: String,
    val isDone: Boolean,
    val isImportant: Boolean,
    val due: DueLabel?,
    val stepsDone: Int,
    val stepsTotal: Int,
    /** Set for a routine; [isDone] then means "done for today". */
    val routine: RoutineInfo? = null,
)

data class RoutineInfo(
    val days: RepeatDays,
    val time: LocalTime,
    /** Today is one of its days, so it can be ticked off. */
    val isToday: Boolean,
)

data class PendingCompletion(val taskId: Long, val openSteps: Int)

@HiltViewModel
class TasksViewModel @Inject constructor(
    private val repository: TaskRepository,
    private val timeSource: TimeSource,
    private val permissions: ReminderPermissions,
    private val preferences: UserPreferencesRepository,
) : ViewModel() {

    private val showDone = MutableStateFlow(false)

    /** Re-read on resume: permissions are given or withdrawn in Android's settings. */
    private val missingPermission = MutableStateFlow(permissions.missing())
    private val pendingCompletion = MutableStateFlow<PendingCompletion?>(null)
    private var latestTasks: List<Task> = emptyList()

    val uiState: StateFlow<TasksUiState> =
        combine(repository.observeTasks(), showDone, pendingCompletion, missingPermission) { tasks, showDone, pending, missing ->
            latestTasks = tasks
            val now = timeSource.now().atZone(timeSource.zone()).toLocalDateTime()
            val today = now.toLocalDate()
            val routines = tasks.filter { it.status == TaskStatus.ACTIVE && it.isRoutine }
                .sortedWith(compareBy<Task> { !Routines.occursOn(it, today) }.thenBy { it.dueTime }.thenBy { it.createdAt })
            TasksUiState(
                isLoading = false,
                routines = routines.map { it.toItem(now) },
                remindersMissing = missing.takeIf { routines.isNotEmpty() },
                active = tasks.filter { it.status == TaskStatus.ACTIVE && !it.isRoutine }.sortedWith(activeOrder).map { it.toItem(now) },
                paused = tasks.filter { it.status == TaskStatus.PAUSED }.map { it.toItem(now) },
                done = tasks.filter { it.status == TaskStatus.DONE }
                    .sortedByDescending { it.completedAt }
                    .map { it.toItem(now) },
                showDone = showDone,
                pendingCompletion = pending,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksUiState())

    /**
     * Checkbox on a row: completes (asking about open steps first) or reopens. A routine is done
     * for today only; open steps don't hold it up, since they start fresh on its next day.
     */
    fun toggleDone(id: Long) {
        val task = latestTasks.firstOrNull { it.id == id } ?: return
        val today = timeSource.now().atZone(timeSource.zone()).toLocalDate()
        when {
            task.isRoutine -> if (Routines.occursOn(task, today)) {
                viewModelScope.launch { repository.setRoutineDone(id, !Routines.isDoneOn(task, today)) }
            }
            task.status == TaskStatus.DONE -> viewModelScope.launch { repository.reopen(id) }
            task.openStepCount > 0 -> pendingCompletion.value = PendingCompletion(id, task.openStepCount)
            else -> viewModelScope.launch { repository.complete(id, alsoCompleteOpenSteps = false) }
        }
    }

    fun confirmCompletion(alsoCompleteOpenSteps: Boolean) {
        val pending = pendingCompletion.value ?: return
        pendingCompletion.value = null
        viewModelScope.launch { repository.complete(pending.taskId, alsoCompleteOpenSteps) }
    }

    fun dismissCompletion() {
        pendingCompletion.value = null
    }

    fun toggleShowDone() = showDone.update { !it }

    fun refreshPermissions() {
        missingPermission.value = permissions.missing()
    }

    val notificationPermissionAsked: Flow<Boolean> = preferences.notificationPermissionAsked

    fun markNotificationPermissionAsked() {
        viewModelScope.launch { preferences.markNotificationPermissionAsked() }
    }

    /** Undo for a deletion made on the detail screen. */
    fun restore(id: Long) {
        viewModelScope.launch { repository.restore(id) }
    }

    private val archivedChannel = Channel<Long>(Channel.BUFFERED)

    /** A done task was archived; the screen offers Undo. */
    val archived: Flow<Long> = archivedChannel.receiveAsFlow()

    fun archive(id: Long) {
        viewModelScope.launch {
            repository.archive(id)
            archivedChannel.send(id)
        }
    }

    fun unarchive(id: Long) {
        viewModelScope.launch { repository.unarchive(id) }
    }

    private companion object {
        /** Soonest due first (no time = end of that day), undated last; important before normal; then oldest first. */
        val activeOrder = compareBy<Task, LocalDate?>(nullsLast()) { it.dueDate }
            .thenBy { it.dueTime ?: LocalTime.MAX }
            .thenBy { if (it.importance == Importance.HIGH) 0 else 1 }
            .thenBy { it.createdAt }
    }
}

fun Task.toItem(now: LocalDateTime): TaskListItem {
    val today = now.toLocalDate()
    val days = repeatDays
    val time = dueTime
    return TaskListItem(
        id = id,
        title = title,
        isDone = status == TaskStatus.DONE || Routines.isDoneOn(this, today),
        isImportant = importance == Importance.HIGH,
        due = dueDate?.let { DueLabel.of(it, dueTime, now) },
        stepsDone = doneStepCount,
        stepsTotal = subtasks.size,
        routine = if (days != null && time != null) RoutineInfo(days, time, Routines.occursOn(this, today)) else null,
    )
}
