// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.feature.home

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import be.kodeworks.donsy.core.focus.FocusTarget
import be.kodeworks.donsy.domain.model.Effort
import be.kodeworks.donsy.domain.model.EnergyLevel
import be.kodeworks.donsy.domain.model.Importance
import be.kodeworks.donsy.domain.model.TaskDraft
import be.kodeworks.donsy.domain.recommendation.Reason
import be.kodeworks.donsy.domain.recommendation.Starter
import be.kodeworks.donsy.feature.tasks.components.StepPosition
import be.kodeworks.donsy.testing.FocusTestRig
import be.kodeworks.donsy.testing.MainDispatcherRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Duration
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val rigScope = CoroutineScope(SupervisorJob())
    private val rig by lazy { FocusTestRig(rigScope, tempFolder.root) }
    private val time get() = rig.time // 2026-10-21 12:00 in Brussels
    private val tasks get() = rig.tasks
    private val brainDump get() = rig.brainDump

    // Lazy: viewModelScope must be created after MainDispatcherRule has installed the test dispatcher.
    private val viewModel by lazy { HomeViewModel(SavedStateHandle(), tasks, brainDump, time, rig.manager, rig.energy) }

    @After
    fun tearDown() {
        rig.close()
        rigScope.cancel()
    }

    private suspend inline fun <reified T : HomeContent> content(crossinline predicate: (T) -> Boolean = { true }): T =
        viewModel.uiState.first { val c = it.content; c is T && predicate(c) }.content as T

    @Test
    fun firstRunWelcomes() = runTest {
        content<HomeContent.Welcome>()
    }

    @Test
    fun suggestsTheFirstOpenStepWithItsReasons() = runTest {
        val id = tasks.create(TaskDraft("Biology test", importance = Importance.HIGH))!!
        tasks.setStepDone(tasks.addStep(id, "Find the topics")!!, true)
        tasks.addStep(id, "Make flashcards")

        val suggestion = content<HomeContent.Suggestion>()
        assertEquals(id, suggestion.taskId)
        assertEquals("Make flashcards", suggestion.stepTitle)
        assertEquals(StepPosition(number = 2, count = 2), suggestion.stepPosition)
        assertEquals(listOf(Reason.Important, Reason.WorkedOnRecently), suggestion.reasons)
    }

    @Test
    fun showAnotherCyclesThroughAndCanStartOver() = runTest {
        val first = tasks.create(TaskDraft("First", importance = Importance.HIGH))!!
        val second = tasks.create(TaskDraft("Second"))!!
        assertEquals(first, content<HomeContent.Suggestion>().taskId)

        viewModel.showAnother()
        assertEquals(second, content<HomeContent.Suggestion> { it.taskId != first }.taskId)

        viewModel.showAnother()
        val nothing = content<HomeContent.NothingToSuggest>()
        assertEquals(true, nothing.allSkipped)
        assertEquals(listOf(Starter.ADD_SMALL_TASK, Starter.PICK_ANY_TASK), nothing.starters)

        viewModel.startOver()
        assertEquals(first, content<HomeContent.Suggestion>().taskId)
    }

    @Test
    fun notNowHidesTheTaskUntilTomorrowAndCanBeUndone() = runTest {
        val id = tasks.create(TaskDraft("Laundry"))!!
        content<HomeContent.Suggestion>()

        viewModel.events.test {
            viewModel.notNow()
            assertEquals(HomeEvent.SetAside(id), awaitItem())
        }
        assertEquals(Instant.parse("2026-10-21T22:00:00Z"), tasks.observeTask(id).first()!!.notNowUntil)
        assertEquals(false, content<HomeContent.NothingToSuggest>().allSkipped)

        viewModel.undoNotNow(id)
        assertEquals(id, content<HomeContent.Suggestion>().taskId)
        assertNull(tasks.observeTask(id).first()!!.notNowUntil)
    }

    @Test
    fun notNowExpiresWhenTheScreenComesBackTheNextDay() = runTest {
        val id = tasks.create(TaskDraft("Laundry"))!!
        content<HomeContent.Suggestion>()
        viewModel.notNow()
        content<HomeContent.NothingToSuggest>()

        time.advanceBy(Duration.ofDays(1))
        viewModel.refresh()
        assertEquals(id, content<HomeContent.Suggestion>().taskId)
    }

    @Test
    fun availableTimeChangesTheSuggestionAndTogglesOff() = runTest {
        tasks.create(TaskDraft("Long", estimateMinutes = 60))
        val short = tasks.create(TaskDraft("Short", estimateMinutes = 5))!!

        viewModel.toggleAvailableMinutes(15)
        val suggestion = content<HomeContent.Suggestion> { it.taskId == short }
        assertEquals(listOf(Reason.FitsAvailableTime(15)), suggestion.reasons)
        assertEquals(15, viewModel.uiState.value.availableMinutes)

        viewModel.toggleAvailableMinutes(15)
        assertNull(viewModel.uiState.first { it.availableMinutes == null }.availableMinutes)
    }

    @Test
    fun anEnergyCheckInChangesTheSuggestionAndTogglesOff() = runTest {
        tasks.create(TaskDraft("Big project", effort = Effort.HIGH))
        val light = tasks.create(TaskDraft("Water the plants", effort = Effort.LOW))!!

        viewModel.toggleEnergy(EnergyLevel.LOW)
        val suggestion = content<HomeContent.Suggestion> { it.taskId == light }
        assertEquals(listOf(Reason.MatchesEnergy(EnergyLevel.LOW)), suggestion.reasons)
        assertEquals(EnergyLevel.LOW, viewModel.uiState.value.energy)

        viewModel.toggleEnergy(EnergyLevel.LOW)
        viewModel.uiState.first { it.energy == null }
        assertNull(rig.energy.observeLatest().first())
    }

    @Test
    fun choosingAnotherEnergyLevelReplacesTheCheckIn() = runTest {
        tasks.create(TaskDraft("Laundry"))
        viewModel.toggleEnergy(EnergyLevel.LOW)
        viewModel.uiState.first { it.energy == EnergyLevel.LOW }

        viewModel.toggleEnergy(EnergyLevel.HIGH)
        viewModel.uiState.first { it.energy == EnergyLevel.HIGH }
    }

    @Test
    fun anEnergyCheckInWearsOffAfterFourHours() = runTest {
        tasks.create(TaskDraft("Laundry"))
        rig.energy.checkIn(EnergyLevel.MEDIUM)
        viewModel.uiState.first { it.energy == EnergyLevel.MEDIUM }

        time.advanceBy(Duration.ofHours(4))
        viewModel.refresh()
        viewModel.uiState.first { it.energy == null }
    }

    @Test
    fun startHandsTheSuggestedStepToFocus() = runTest {
        val id = tasks.create(TaskDraft("Essay"))!!
        val step = tasks.addStep(id, "Outline")!!
        content<HomeContent.Suggestion> { it.stepId == step }

        viewModel.startFocus()

        assertEquals(FocusTarget(id, step), rig.manager.pendingTarget.value)
    }

    @Test
    fun withOnlyThoughtsItSuggestsSortingTheBrainDump() = runTest {
        brainDump.capture("Something on my mind")
        assertEquals(listOf(Starter.SORT_BRAIN_DUMP, Starter.ADD_SMALL_TASK), content<HomeContent.NothingToSuggest>().starters)
    }
}
