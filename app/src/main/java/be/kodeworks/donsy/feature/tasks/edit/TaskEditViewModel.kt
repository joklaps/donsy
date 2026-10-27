// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.feature.tasks.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import be.kodeworks.donsy.core.routine.MissingReminderPermission
import be.kodeworks.donsy.core.routine.ReminderPermissions
import be.kodeworks.donsy.data.preferences.UserPreferencesRepository
import be.kodeworks.donsy.domain.model.Effort
import be.kodeworks.donsy.domain.model.Importance
import be.kodeworks.donsy.domain.model.RepeatDays
import be.kodeworks.donsy.domain.model.TaskDraft
import be.kodeworks.donsy.domain.repository.BrainDumpRepository
import be.kodeworks.donsy.domain.repository.TaskRepository
import be.kodeworks.donsy.domain.task.TaskDrafts
import be.kodeworks.donsy.ui.navigation.TaskEditRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

enum class TaskEditMode { NEW, EDIT, FROM_BRAIN_DUMP }

/** "Never" is an ordinary task; the others make it a routine. */
enum class RepeatChoice { NEVER, EVERY_DAY, WEEKDAYS, PICK_DAYS }

data class TaskEditUiState(
    val isLoading: Boolean = true,
    val mode: TaskEditMode = TaskEditMode.NEW,
    val title: String = "",
    val notes: String = "",
    val isImportant: Boolean = false,
    val dueDate: LocalDate? = null,
    /** Null means the end of [dueDate]; for a routine, the reminder time (required). */
    val dueTime: LocalTime? = null,
    val estimateMinutes: Int? = null,
    val effort: Effort? = null,
    val repeat: RepeatChoice = RepeatChoice.NEVER,
    /** The days chosen under [RepeatChoice.PICK_DAYS]. */
    val pickedDays: Set<DayOfWeek> = emptySet(),
    /** What Android still has to allow; a routine can't be saved until it is null. */
    val missingPermission: MissingReminderPermission? = null,
    val isSaving: Boolean = false,
) {
    val isRoutine: Boolean get() = repeat != RepeatChoice.NEVER

    val repeatDays: RepeatDays? get() = when (repeat) {
        RepeatChoice.NEVER -> null
        RepeatChoice.EVERY_DAY -> RepeatDays.EVERY_DAY
        RepeatChoice.WEEKDAYS -> RepeatDays.WEEKDAYS
        RepeatChoice.PICK_DAYS -> RepeatDays.of(pickedDays)
    }

    /** A routine needs days, a time and Android's permission to remind on time. */
    val canSave: Boolean get() = !isLoading && !isSaving && title.isNotBlank() &&
        (!isRoutine || (repeatDays != null && dueTime != null && missingPermission == null))
}

sealed interface TaskEditResult {
    data class Created(val taskId: Long) : TaskEditResult
    data class Converted(val taskId: Long) : TaskEditResult
    data object Updated : TaskEditResult
}

@HiltViewModel
class TaskEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tasks: TaskRepository,
    private val brainDump: BrainDumpRepository,
    private val permissions: ReminderPermissions,
    private val preferences: UserPreferencesRepository,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<TaskEditRoute>()
    private val state = MutableStateFlow(TaskEditUiState())
    val uiState: StateFlow<TaskEditUiState> = state.asStateFlow()

    private val resultChannel = Channel<TaskEditResult>(Channel.BUFFERED)
    val results: Flow<TaskEditResult> = resultChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            state.value = when {
                route.taskId != TaskEditRoute.NO_ID -> {
                    val task = tasks.observeTask(route.taskId).first()
                    if (task == null) {
                        TaskEditUiState(isLoading = false, mode = TaskEditMode.EDIT)
                    } else {
                        TaskEditUiState(
                            isLoading = false,
                            mode = TaskEditMode.EDIT,
                            title = task.title,
                            notes = task.notes,
                            isImportant = task.importance == Importance.HIGH,
                            dueDate = task.dueDate,
                            dueTime = task.dueTime,
                            estimateMinutes = task.estimateMinutes,
                            effort = task.effort,
                            repeat = task.repeatDays.toChoice(),
                            pickedDays = task.repeatDays?.days.orEmpty().toSet(),
                        )
                    }
                }
                route.brainDumpItemId != TaskEditRoute.NO_ID -> {
                    val draft = brainDump.get(route.brainDumpItemId)?.let { TaskDrafts.fromBrainDumpText(it.text) }
                    TaskEditUiState(
                        isLoading = false,
                        mode = TaskEditMode.FROM_BRAIN_DUMP,
                        title = draft?.title.orEmpty(),
                        notes = draft?.notes.orEmpty(),
                    )
                }
                else -> TaskEditUiState(isLoading = false)
            }.copy(missingPermission = permissions.missing())
        }
    }

    val notificationPermissionAsked: Flow<Boolean> = preferences.notificationPermissionAsked

    fun setTitle(value: String) = state.update { it.copy(title = value) }

    fun setNotes(value: String) = state.update { it.copy(notes = value) }

    fun setImportant(value: Boolean) = state.update { it.copy(isImportant = value) }

    /** Removing the date also removes the time. */
    fun setDueDate(value: LocalDate?) = state.update {
        it.copy(dueDate = value, dueTime = if (value == null) null else it.dueTime)
    }

    /** Null resets to the default: the end of the due date. A routine has a time but no date. */
    fun setDueTime(value: LocalTime?) = state.update {
        if (it.dueDate == null && !it.isRoutine) it else it.copy(dueTime = value)
    }

    /** Back to "Never" drops a routine's time unless a due date gives it meaning again. */
    fun setRepeat(choice: RepeatChoice) = state.update {
        it.copy(repeat = choice, dueTime = if (choice == RepeatChoice.NEVER && it.dueDate == null) null else it.dueTime)
    }

    fun toggleDay(day: DayOfWeek) = state.update {
        it.copy(pickedDays = if (day in it.pickedDays) it.pickedDays - day else it.pickedDays + day)
    }

    /** Permissions are given in Android's settings, outside the app: check again on return. */
    fun refreshPermissions() = state.update { it.copy(missingPermission = permissions.missing()) }

    fun markNotificationPermissionAsked() {
        viewModelScope.launch { preferences.markNotificationPermissionAsked() }
    }

    /** Choosing the selected estimate again clears it. */
    fun toggleEstimate(minutes: Int) = state.update {
        it.copy(estimateMinutes = if (it.estimateMinutes == minutes) null else minutes)
    }

    /** Choosing the selected effort again clears it. */
    fun toggleEffort(effort: Effort) = state.update { it.copy(effort = if (it.effort == effort) null else effort) }

    fun save() {
        val current = state.value
        if (!current.canSave) return
        state.update { it.copy(isSaving = true) }
        val draft = TaskDraft(
            title = current.title,
            notes = current.notes,
            importance = if (current.isImportant) Importance.HIGH else Importance.NORMAL,
            dueDate = current.dueDate.takeUnless { current.isRoutine },
            dueTime = current.dueTime,
            repeatDays = current.repeatDays,
            estimateMinutes = current.estimateMinutes,
            effort = current.effort,
        )
        viewModelScope.launch {
            val result = when (current.mode) {
                TaskEditMode.NEW -> tasks.create(draft)?.let(TaskEditResult::Created)
                TaskEditMode.FROM_BRAIN_DUMP ->
                    tasks.createFromBrainDump(route.brainDumpItemId, draft)?.let(TaskEditResult::Converted)
                TaskEditMode.EDIT -> {
                    tasks.update(route.taskId, draft)
                    TaskEditResult.Updated
                }
            }
            if (result != null) resultChannel.send(result) else state.update { it.copy(isSaving = false) }
        }
    }
}

private fun RepeatDays?.toChoice(): RepeatChoice = when {
    this == null -> RepeatChoice.NEVER
    isEveryDay -> RepeatChoice.EVERY_DAY
    isWeekdays -> RepeatChoice.WEEKDAYS
    else -> RepeatChoice.PICK_DAYS
}
