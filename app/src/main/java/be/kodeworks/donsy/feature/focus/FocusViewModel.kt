// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.feature.focus

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import be.kodeworks.donsy.core.focus.FocusAlarmScheduler
import be.kodeworks.donsy.core.focus.FocusSessionManager
import be.kodeworks.donsy.core.focus.FocusSummary
import be.kodeworks.donsy.core.focus.FocusTarget
import be.kodeworks.donsy.data.preferences.UserPreferencesRepository
import be.kodeworks.donsy.domain.focus.FocusDurations
import be.kodeworks.donsy.domain.focus.FocusSession
import be.kodeworks.donsy.domain.focus.FocusSessionMachine
import be.kodeworks.donsy.domain.model.FocusSessionStatus
import be.kodeworks.donsy.domain.model.Subtask
import be.kodeworks.donsy.domain.model.Task
import be.kodeworks.donsy.domain.model.TaskStatus
import be.kodeworks.donsy.domain.recommendation.RecommendationEngine
import be.kodeworks.donsy.domain.repository.TaskRepository
import be.kodeworks.donsy.domain.time.TimeSource
import be.kodeworks.donsy.feature.tasks.components.StepPosition
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import javax.inject.Inject

/** A task or step as shown on the Focus screen. */
data class TargetInfo(
    val target: FocusTarget,
    /** The step's title, or the task's when there is no step. */
    val title: String,
    /** The task's title when [title] is a step; null otherwise. */
    val partOf: String?,
    /** Where the step sits in its task; null for a whole task. */
    val step: StepPosition?,
    val isDone: Boolean,
)

sealed interface FocusUiState {
    data object Loading : FocusUiState

    data class Setup(
        val target: TargetInfo?,
        val minutes: Int,
        /** The session lengths offered, from Settings. [minutes] may be another one, e.g. after Settings changed. */
        val durations: List<Int>,
        /** Tasks to choose from: each active task's first open step, or the task itself. */
        val options: List<TargetInfo>,
        /** Offer on-time end alerts: they need permission and the user hasn't answered yet. */
        val offerPreciseAlerts: Boolean = false,
    ) : FocusUiState

    data class Active(
        val isPaused: Boolean,
        val remaining: Duration,
        val planned: Duration,
        val target: TargetInfo?,
    ) : FocusUiState {
        val progress: Float get() = 1f - remaining.toMillis().toFloat() / planned.toMillis().coerceAtLeast(1)
    }

    data class Ended(
        val summary: FocusSummary,
        val target: TargetInfo?,
    ) : FocusUiState
}

private data class SetupChoices(val target: FocusTarget?, val minutes: Int, val durations: List<Int>, val offerPreciseAlerts: Boolean)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FocusViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val manager: FocusSessionManager,
    private val tasks: TaskRepository,
    private val preferences: UserPreferencesRepository,
    private val time: TimeSource,
    private val alarms: FocusAlarmScheduler,
) : ViewModel() {

    /** The user's pick on this screen, or the default length from Settings. */
    private val minutes: Flow<Int> =
        combine(savedStateHandle.getStateFlow<Int?>(KEY_MINUTES, null), preferences.focusDefaultMinutes) { chosen, default ->
            chosen ?: default
        }
    private val selectedTarget = MutableStateFlow<FocusTarget?>(null)
    private var completingSessionId: Long? = null
    private var lengthForTarget: Job? = null
    private var justMarkedDone: FocusTarget? = null

    /** The task whose last step was just checked off in the summary; the screen asks to finish it too. */
    private val completeTaskOffer = MutableStateFlow<Long?>(null)
    val offerToCompleteTask: StateFlow<Boolean> =
        completeTaskOffer.map { it != null }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val notificationPermissionAsked: Flow<Boolean> = preferences.notificationPermissionAsked

    /** Re-read on resume: the permission is granted in Android's settings, outside the app. */
    private val canScheduleExact = MutableStateFlow(alarms.canScheduleExact())

    private val offerPreciseAlerts: Flow<Boolean> =
        combine(preferences.preciseEndAlerts, preferences.preciseAlertsOfferAnswered, canScheduleExact) { chosen, answered, allowed ->
            alarms.exactNeedsPermission && !answered && !(chosen && allowed)
        }

    init {
        // A target handed over from Home's "Start".
        viewModelScope.launch {
            manager.pendingTarget.filterNotNull().collect {
                // The previous summary was closed by prepare(); forget its follow-up question too.
                completeTaskOffer.value = null
                justMarkedDone = null
                chooseTarget(it)
                manager.consumePendingTarget()
            }
        }
    }

    /** The active session with its remaining time, ticking every second while it runs. */
    private val ticking: Flow<Pair<FocusSession, Duration>?> = manager.activeSession.flatMapLatest { session ->
        when (session?.status) {
            null -> flowOf(null)
            FocusSessionStatus.RUNNING -> flow {
                while (true) {
                    emit(session to FocusSessionMachine.remaining(session, time))
                    delay(TICK_MILLIS)
                }
            }
            else -> flowOf(session to FocusSessionMachine.remaining(session, time))
        }
    }

    private val setupChoices = combine(selectedTarget, minutes, preferences.focusDurations, offerPreciseAlerts) { target, minutes, durations, offer ->
        SetupChoices(target, minutes, durations, offer)
    }

    val uiState: StateFlow<FocusUiState> =
        combine(ticking, manager.summary, tasks.observeTasks(), setupChoices) { active, summary, allTasks, (target, minutes, durations, offer) ->
            when {
                active != null -> {
                    val (session, remaining) = active
                    if (session.status == FocusSessionStatus.RUNNING && remaining.isZero) completeOnScreen(session)
                    FocusUiState.Active(
                        isPaused = session.status == FocusSessionStatus.PAUSED,
                        remaining = remaining,
                        planned = session.planned,
                        target = session.taskId?.let { info(FocusTarget(it, session.subtaskId), allTasks) },
                    )
                }
                summary != null -> FocusUiState.Ended(summary, summary.target?.let { info(it, allTasks) })
                else -> FocusUiState.Setup(
                    target = target?.let { info(it, allTasks) },
                    minutes = minutes,
                    durations = durations,
                    options = allTasks.filter { it.status == TaskStatus.ACTIVE }.map { it.asOption() },
                    offerPreciseAlerts = offer,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusUiState.Loading)

    fun selectMinutes(value: Int) {
        // A length picked by hand wins over one still being picked for the target.
        lengthForTarget?.cancel()
        savedStateHandle[KEY_MINUTES] = value
    }

    fun selectTarget(target: FocusTarget?) = chooseTarget(target)

    /**
     * Picking what to focus on also picks a fitting length: the shortest offered one that covers
     * the task's estimate (a step's share of it, as on Home). Without an estimate, or with no task,
     * the default from Settings applies. Tapping a length afterwards still overrides it.
     */
    private fun chooseTarget(target: FocusTarget?) {
        selectedTarget.value = target
        lengthForTarget?.cancel()
        lengthForTarget = viewModelScope.launch {
            val task = target?.let { t -> tasks.observeTasks().first().firstOrNull { it.id == t.taskId } }
            val estimate = task?.let { RecommendationEngine.effectiveEstimate(it) }
            savedStateHandle[KEY_MINUTES] = estimate?.let { FocusDurations.forEstimate(it, preferences.focusDurations.first()) }
        }
    }

    fun start() {
        val target = selectedTarget.value
        viewModelScope.launch { manager.start(target, minutes.first()) }
    }

    fun pause() = launch { manager.pause() }

    fun resume() = launch { manager.resume() }

    fun finish() = launch { manager.finish() }

    fun cancel() = launch { manager.cancel() }

    /** When the Focus screen comes back: catches up on anything that happened meanwhile. */
    fun refresh() {
        canScheduleExact.value = alarms.canScheduleExact()
        launch { manager.recover(userPresent = false) }
    }

    /**
     * "Allow" on the on-time alerts card. Returns true when Android's permission screen should be
     * opened to finish it.
     */
    fun acceptPreciseAlerts(): Boolean {
        launch {
            preferences.setPreciseEndAlerts(true)
            preferences.markPreciseAlertsOfferAnswered()
        }
        return !alarms.canScheduleExact()
    }

    fun declinePreciseAlerts() = launch { preferences.markPreciseAlertsOfferAnswered() }

    /** "Mark this step done?" after a session. Checks off the step, or the task when it has no steps. */
    fun markTargetDone() {
        val target = (uiState.value as? FocusUiState.Ended)?.summary?.target ?: return
        // Remembered so closing the summary right away moves on, even before the database answers.
        justMarkedDone = target
        viewModelScope.launch {
            if (target.subtaskId != null) {
                val task = tasks.observeTasks().first().firstOrNull { it.id == target.taskId }
                val wasLastOpenStep = task != null && task.status != TaskStatus.DONE &&
                    task.subtasks.filter { !it.isDone }.map { it.id } == listOf(target.subtaskId)
                tasks.setStepDone(target.subtaskId, true)
                // As on the task screen: ask, never complete the task automatically.
                if (wasLastOpenStep) completeTaskOffer.value = target.taskId
            } else {
                tasks.complete(target.taskId, alsoCompleteOpenSteps = false)
            }
        }
    }

    /** "Mark the whole task done?" after the last step was checked off. */
    fun completeWholeTask() {
        val taskId = completeTaskOffer.value ?: return
        completeTaskOffer.value = null
        viewModelScope.launch { tasks.complete(taskId, alsoCompleteOpenSteps = false) }
    }

    fun declineWholeTask() {
        completeTaskOffer.value = null
    }

    /**
     * Back to set-up for another session. An unfinished target stays selected. A finished step
     * moves on to the task's next open step; after the last step or a whole task, nothing is selected.
     */
    fun closeSummary() {
        completeTaskOffer.value = null
        val target = manager.summary.value?.target
        if (target == null) {
            manager.dismissSummary()
            return
        }
        viewModelScope.launch {
            val task = tasks.observeTasks().first().firstOrNull { it.id == target.taskId }
            val finished = target == justMarkedDone || target.isDoneIn(task)
            if (finished) {
                val next = task?.takeIf { it.status == TaskStatus.ACTIVE }
                    ?.subtasks?.firstOrNull { !it.isDone && it.id != target.subtaskId }
                chooseTarget(next?.let { FocusTarget(task.id, it.id) })
            } else {
                selectedTarget.value = target
            }
            justMarkedDone = null
            // Only now, so set-up never shows the finished target for a moment.
            manager.dismissSummary()
        }
    }

    private fun FocusTarget.isDoneIn(task: Task?): Boolean = when {
        task == null -> true
        subtaskId != null -> task.subtasks.firstOrNull { it.id == subtaskId }?.isDone ?: true
        else -> task.status == TaskStatus.DONE
    }

    fun markNotificationPermissionAsked() = launch { preferences.markNotificationPermissionAsked() }

    private fun completeOnScreen(session: FocusSession) {
        if (completingSessionId == session.id) return
        completingSessionId = session.id
        launch { manager.recover(userPresent = true) }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private fun info(target: FocusTarget, allTasks: List<Task>): TargetInfo? {
        val task = allTasks.firstOrNull { it.id == target.taskId } ?: return null
        val step = task.subtasks.firstOrNull { it.id == target.subtaskId }
        return TargetInfo(
            target = target,
            title = step?.title ?: task.title,
            partOf = if (step != null) task.title else null,
            step = step?.let { task.stepPosition(it) },
            isDone = step?.isDone ?: (task.status == TaskStatus.DONE),
        )
    }

    private fun Task.asOption(): TargetInfo {
        val step = subtasks.firstOrNull { !it.isDone }
        return TargetInfo(
            target = FocusTarget(id, step?.id),
            title = step?.title ?: title,
            partOf = if (step != null) title else null,
            step = step?.let { stepPosition(it) },
            isDone = false,
        )
    }

    private fun Task.stepPosition(step: Subtask) = StepPosition(subtasks.indexOf(step) + 1, subtasks.size)

    private companion object {
        const val KEY_MINUTES = "minutes"
        const val TICK_MILLIS = 1_000L
    }
}
