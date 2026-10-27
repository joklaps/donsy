// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.feature.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import be.kodeworks.donsy.core.focus.FocusSessionManager
import be.kodeworks.donsy.core.focus.FocusTarget
import be.kodeworks.donsy.domain.energy.EnergyCheckIns
import be.kodeworks.donsy.domain.model.EnergyCheckIn
import be.kodeworks.donsy.domain.model.EnergyLevel
import be.kodeworks.donsy.domain.recommendation.Reason
import be.kodeworks.donsy.domain.recommendation.Recommendation
import be.kodeworks.donsy.domain.recommendation.RecommendationContext
import be.kodeworks.donsy.domain.recommendation.RecommendationEngine
import be.kodeworks.donsy.domain.recommendation.Starter
import be.kodeworks.donsy.domain.repository.BrainDumpRepository
import be.kodeworks.donsy.domain.repository.EnergyRepository
import be.kodeworks.donsy.domain.repository.TaskRepository
import be.kodeworks.donsy.domain.time.TimeSource
import be.kodeworks.donsy.feature.tasks.components.StepPosition
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
import javax.inject.Inject

data class HomeUiState(
    val content: HomeContent = HomeContent.Loading,
    val availableMinutes: Int? = null,
    /** The user's energy from a recent check-in, if any. */
    val energy: EnergyLevel? = null,
)

sealed interface HomeContent {
    data object Loading : HomeContent

    /** First run: nothing captured and no tasks yet. */
    data object Welcome : HomeContent

    data class Suggestion(
        val taskId: Long,
        val taskTitle: String,
        /** The first open step, when the task has steps. */
        val stepId: Long?,
        val stepTitle: String?,
        /** Where the step sits in its task; null when the whole task is suggested. */
        val stepPosition: StepPosition?,
        val reasons: List<Reason>,
    ) : HomeContent

    data class NothingToSuggest(val starters: List<Starter>, val allSkipped: Boolean) : HomeContent
}

sealed interface HomeEvent {
    /** "Not now" was applied; can be undone. */
    data class SetAside(val taskId: Long) : HomeEvent
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val tasks: TaskRepository,
    brainDump: BrainDumpRepository,
    private val timeSource: TimeSource,
    private val focusSessions: FocusSessionManager,
    private val energyCheckIns: EnergyRepository,
) : ViewModel() {

    private val availableMinutes = savedStateHandle.getStateFlow<Int?>(KEY_AVAILABLE_MINUTES, null)
    private val skipped = MutableStateFlow<Set<Long>>(emptySet())

    /** Bumped when the screen resumes, so "now" (and "Not now" expiry) is re-evaluated. */
    private val refreshTick = MutableStateFlow(0)

    /** The latest check-in while it still counts; re-evaluated on [refresh]. */
    private val currentCheckIn: Flow<EnergyCheckIn?> =
        combine(energyCheckIns.observeLatest(), refreshTick) { latest, _ ->
            latest?.takeIf { EnergyCheckIns.current(it, timeSource.now()) != null }
        }

    private var latestCheckIn: EnergyCheckIn? = null

    val uiState: StateFlow<HomeUiState> = combine(
        tasks.observeTasks(),
        brainDump.observeInbox(),
        availableMinutes,
        skipped,
        currentCheckIn,
    ) { allTasks, inbox, minutes, skippedIds, checkIn ->
        latestCheckIn = checkIn
        val energy = checkIn?.level
        val content = if (allTasks.isEmpty() && inbox.isEmpty()) {
            HomeContent.Welcome
        } else {
            val context = RecommendationContext(
                availableMinutes = minutes,
                energy = energy,
                skippedTaskIds = skippedIds,
                brainDumpCount = inbox.size,
            )
            when (val result = RecommendationEngine.recommend(allTasks, context, timeSource.now(), timeSource.zone())) {
                is Recommendation.Suggest -> HomeContent.Suggestion(
                    taskId = result.task.id,
                    taskTitle = result.task.title,
                    stepId = result.step?.id,
                    stepTitle = result.step?.title,
                    stepPosition = result.step?.let { step ->
                        StepPosition(result.task.subtasks.indexOf(step) + 1, result.task.subtasks.size)
                    },
                    reasons = result.reasons,
                )
                is Recommendation.Nothing -> HomeContent.NothingToSuggest(result.starters, result.allSkipped)
            }
        }
        HomeUiState(content = content, availableMinutes = minutes, energy = energy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private val eventChannel = Channel<HomeEvent>(Channel.BUFFERED)
    val events: Flow<HomeEvent> = eventChannel.receiveAsFlow()

    private val currentSuggestion: HomeContent.Suggestion?
        get() = uiState.value.content as? HomeContent.Suggestion

    /** "Start": hands the suggested step to the Focus screen. */
    fun startFocus() {
        val suggestion = currentSuggestion ?: return
        focusSessions.prepare(FocusTarget(suggestion.taskId, suggestion.stepId))
    }

    /** Choosing the selected amount again clears it. */
    fun toggleAvailableMinutes(minutes: Int) {
        savedStateHandle[KEY_AVAILABLE_MINUTES] = if (availableMinutes.value == minutes) null else minutes
    }

    /** Checks in with [level]; choosing the current level again takes the check-in back. */
    fun toggleEnergy(level: EnergyLevel) {
        val current = latestCheckIn
        viewModelScope.launch {
            if (current?.level == level) energyCheckIns.delete(current.id) else energyCheckIns.checkIn(level)
        }
    }

    fun showAnother() {
        val suggestion = currentSuggestion ?: return
        skipped.update { it + suggestion.taskId }
    }

    /** Brings back everything passed over with "Show another". */
    fun startOver() {
        skipped.value = emptySet()
    }

    fun notNow() {
        val suggestion = currentSuggestion ?: return
        viewModelScope.launch {
            tasks.setNotNowUntil(suggestion.taskId, RecommendationEngine.notNowUntil(timeSource.now(), timeSource.zone()))
            eventChannel.send(HomeEvent.SetAside(suggestion.taskId))
        }
    }

    fun undoNotNow(taskId: Long) {
        viewModelScope.launch { tasks.setNotNowUntil(taskId, null) }
    }

    /** Undo for a task deleted on its detail screen after opening it from Home. */
    fun restoreTask(taskId: Long) {
        viewModelScope.launch { tasks.restore(taskId) }
    }

    fun refresh() = refreshTick.update { it + 1 }

    private companion object {
        const val KEY_AVAILABLE_MINUTES = "availableMinutes"
    }
}
