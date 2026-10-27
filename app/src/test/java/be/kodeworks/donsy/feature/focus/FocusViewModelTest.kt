// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.feature.focus

import androidx.lifecycle.SavedStateHandle
import be.kodeworks.donsy.core.focus.FocusTarget
import be.kodeworks.donsy.domain.model.FocusSessionStatus
import be.kodeworks.donsy.domain.model.TaskDraft
import be.kodeworks.donsy.domain.model.TaskStatus
import be.kodeworks.donsy.feature.tasks.components.StepPosition
import be.kodeworks.donsy.testing.FakeFocusAlarmScheduler
import be.kodeworks.donsy.testing.FocusTestRig
import be.kodeworks.donsy.testing.MainDispatcherRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Duration.ofMinutes

@RunWith(RobolectricTestRunner::class)
class FocusViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob())
    private val rig by lazy { FocusTestRig(scope, tempFolder.root) }

    // Lazy: viewModelScope must be created after MainDispatcherRule has installed the test dispatcher.
    private val viewModel by lazy { FocusViewModel(SavedStateHandle(), rig.manager, rig.tasks, rig.preferences, rig.time, rig.alarms) }

    @After
    fun tearDown() {
        rig.close()
        scope.cancel()
    }

    private suspend inline fun <reified T : FocusUiState> state(crossinline predicate: (T) -> Boolean = { true }): T =
        viewModel.uiState.first { it is T && predicate(it) } as T

    @Test
    fun setupDefaultsTo15MinutesAndOffersActiveTasksByTheirNextStep() = runTest {
        val id = rig.tasks.create(TaskDraft("Essay"))!!
        rig.tasks.setStepDone(rig.tasks.addStep(id, "Outline")!!, true)
        val draft = rig.tasks.addStep(id, "Draft")!!

        val setup = state<FocusUiState.Setup> { it.options.isNotEmpty() }
        assertEquals(15, setup.minutes)
        assertEquals(listOf(5, 10, 15, 25), setup.durations)
        assertEquals(null, setup.target)
        with(setup.options.single()) {
            assertEquals(FocusTarget(id, draft), target)
            assertEquals("Draft", title)
            assertEquals("Essay", partOf)
            assertEquals(StepPosition(number = 2, count = 2), step)
        }
    }

    @Test
    fun lengthsAndThePreselectedOneComeFromSettings() = runTest {
        rig.preferences.setFocusDurationOffered(45, offered = true)
        rig.preferences.setFocusDefaultMinutes(45)

        val setup = state<FocusUiState.Setup> { it.minutes == 45 }
        assertEquals(listOf(5, 10, 15, 25, 45), setup.durations)

        viewModel.start()
        assertEquals(ofMinutes(45), state<FocusUiState.Active>().planned)
    }

    @Test
    fun aLengthPickedOnScreenWinsOverTheDefault() = runTest {
        rig.preferences.setFocusDefaultMinutes(25)
        state<FocusUiState.Setup> { it.minutes == 25 }

        viewModel.selectMinutes(5)
        state<FocusUiState.Setup> { it.minutes == 5 }
        rig.preferences.setFocusDefaultMinutes(10)
        viewModel.start()

        assertEquals(ofMinutes(5), state<FocusUiState.Active>().planned)
    }

    @Test
    fun choosingATaskPicksTheShortestLengthThatCoversItsEstimate() = runTest {
        val id = rig.tasks.create(TaskDraft("Essay", estimateMinutes = 30))!!
        state<FocusUiState.Setup> { it.options.isNotEmpty() }

        // 30 minutes: longer than every default length (5, 10, 15, 25), so the longest.
        viewModel.selectTarget(FocusTarget(id))
        state<FocusUiState.Setup> { it.minutes == 25 }

        // Picking a length by hand still works.
        viewModel.selectMinutes(10)
        state<FocusUiState.Setup> { it.minutes == 10 }

        // No task: back to the default.
        viewModel.selectTarget(null)
        state<FocusUiState.Setup> { it.minutes == 15 }
    }

    @Test
    fun aStepGetsItsShareOfTheTaskEstimate() = runTest {
        val id = rig.tasks.create(TaskDraft("Room", estimateMinutes = 30))!!
        val first = rig.tasks.addStep(id, "Desk")!!
        rig.tasks.addSteps(id, listOf("Floor", "Bed", "Clothes"))

        // 30 minutes over 4 open steps = 8 minutes, so 10.
        rig.manager.prepare(FocusTarget(id, first))
        state<FocusUiState.Setup> { it.target != null && it.minutes == 10 }
    }

    @Test
    fun aTargetFromHomeIsPickedUp() = runTest {
        val id = rig.tasks.create(TaskDraft("Laundry"))!!
        rig.manager.prepare(FocusTarget(id))

        assertEquals("Laundry", state<FocusUiState.Setup> { it.target != null }.target!!.title)
        assertEquals(null, rig.manager.pendingTarget.value)
    }

    @Test
    fun startPauseResumeAndFinish() = runTest {
        val id = rig.tasks.create(TaskDraft("Guitar"))!!
        viewModel.selectTarget(FocusTarget(id))
        viewModel.selectMinutes(10)
        state<FocusUiState.Setup> { it.minutes == 10 && it.target != null }

        viewModel.start()
        val running = state<FocusUiState.Active>()
        assertEquals(ofMinutes(10), running.planned)
        assertEquals("Guitar", running.target!!.title)

        rig.time.advanceBy(ofMinutes(4))
        viewModel.pause()
        val paused = state<FocusUiState.Active> { it.isPaused }
        assertEquals(ofMinutes(6), paused.remaining)

        viewModel.resume()
        state<FocusUiState.Active> { !it.isPaused }
        viewModel.finish()
        val ended = state<FocusUiState.Ended>()
        assertEquals(FocusSessionStatus.ENDED_EARLY, ended.summary.status)
        assertEquals(4, ended.summary.focusedMinutes)
    }

    @Test
    fun checkingOffTheStepMovesSetUpToTheNextStep() = runTest {
        val id = rig.tasks.create(TaskDraft("Essay"))!!
        val step = rig.tasks.addStep(id, "Outline")!!
        val next = rig.tasks.addStep(id, "Draft")!!
        viewModel.selectTarget(FocusTarget(id, step))
        viewModel.start()
        state<FocusUiState.Active>()
        rig.time.advanceBy(ofMinutes(15))

        // The on-screen timer reaching zero completes the session.
        val ended = state<FocusUiState.Ended>()
        assertEquals(FocusSessionStatus.COMPLETED, ended.summary.status)
        assertEquals(null, ended.summary.note)

        viewModel.markTargetDone()
        assertTrue(state<FocusUiState.Ended> { it.target?.isDone == true }.target!!.isDone)

        viewModel.closeSummary()
        assertEquals(FocusTarget(id, next), state<FocusUiState.Setup>().target!!.target)
    }

    @Test
    fun notYetKeepsTheSameTargetForAnotherSession() = runTest {
        val id = rig.tasks.create(TaskDraft("Essay"))!!
        val step = rig.tasks.addStep(id, "Outline")!!
        viewModel.selectTarget(FocusTarget(id, step))
        viewModel.start()
        state<FocusUiState.Active>()
        viewModel.finish()
        state<FocusUiState.Ended>()

        viewModel.closeSummary()
        assertEquals(FocusTarget(id, step), state<FocusUiState.Setup>().target!!.target)
    }

    @Test
    fun checkingOffTheLastStepAsksToFinishTheWholeTask() = runTest {
        val id = rig.tasks.create(TaskDraft("Essay"))!!
        val step = rig.tasks.addStep(id, "Outline")!!
        viewModel.selectTarget(FocusTarget(id, step))
        viewModel.start()
        state<FocusUiState.Active>()
        viewModel.finish()
        state<FocusUiState.Ended>()

        viewModel.markTargetDone()
        viewModel.offerToCompleteTask.first { it }
        // Never automatic: the task is still open until the user says so.
        assertEquals(TaskStatus.ACTIVE, rig.tasks.observeTask(id).first()!!.status)

        viewModel.completeWholeTask()
        rig.tasks.observeTask(id).first { it?.status == TaskStatus.DONE }
        assertEquals(false, viewModel.offerToCompleteTask.value)
    }

    @Test
    fun notYetLeavesTheTaskOpen() = runTest {
        val id = rig.tasks.create(TaskDraft("Essay"))!!
        val step = rig.tasks.addStep(id, "Outline")!!
        viewModel.selectTarget(FocusTarget(id, step))
        viewModel.start()
        state<FocusUiState.Active>()
        viewModel.finish()
        state<FocusUiState.Ended>()

        viewModel.markTargetDone()
        viewModel.offerToCompleteTask.first { it }
        viewModel.declineWholeTask()

        assertEquals(false, viewModel.offerToCompleteTask.value)
        assertEquals(TaskStatus.ACTIVE, rig.tasks.observeTask(id).first()!!.status)
    }

    @Test
    fun noQuestionWhileOtherStepsAreOpen() = runTest {
        val id = rig.tasks.create(TaskDraft("Essay"))!!
        val step = rig.tasks.addStep(id, "Outline")!!
        rig.tasks.addStep(id, "Draft")
        viewModel.selectTarget(FocusTarget(id, step))
        viewModel.start()
        state<FocusUiState.Active>()
        viewModel.finish()
        state<FocusUiState.Ended>()

        viewModel.markTargetDone()
        state<FocusUiState.Ended> { it.target?.isDone == true }
        assertEquals(false, viewModel.offerToCompleteTask.value)
    }

    @Test
    fun startFromHomeReplacesAnOldSummary() = runTest {
        val id = rig.tasks.create(TaskDraft("Essay"))!!
        val step = rig.tasks.addStep(id, "Outline")!!
        viewModel.selectTarget(FocusTarget(id, step))
        viewModel.start()
        state<FocusUiState.Active>()
        viewModel.finish()
        state<FocusUiState.Ended>()
        viewModel.markTargetDone()
        viewModel.offerToCompleteTask.first { it }

        // The step is unchecked elsewhere and Home's Start hands it over again.
        rig.tasks.setStepDone(step, false)
        rig.manager.prepare(FocusTarget(id, step))

        assertEquals(FocusTarget(id, step), state<FocusUiState.Setup> { it.target != null }.target!!.target)
        assertEquals(false, viewModel.offerToCompleteTask.value)
    }

    @Test
    fun theSummaryFollowsTheStepsStoredState() = runTest {
        val id = rig.tasks.create(TaskDraft("Essay"))!!
        val step = rig.tasks.addStep(id, "Outline")!!
        rig.tasks.addStep(id, "Draft")
        viewModel.selectTarget(FocusTarget(id, step))
        viewModel.start()
        state<FocusUiState.Active>()
        viewModel.finish()
        state<FocusUiState.Ended>()

        viewModel.markTargetDone()
        state<FocusUiState.Ended> { it.target?.isDone == true }
        rig.tasks.setStepDone(step, false)
        state<FocusUiState.Ended> { it.target?.isDone == false }
    }

    @Test
    fun afterTheLastStepNothingIsSelected() = runTest {
        val id = rig.tasks.create(TaskDraft("Essay"))!!
        val step = rig.tasks.addStep(id, "Outline")!!
        viewModel.selectTarget(FocusTarget(id, step))
        viewModel.start()
        state<FocusUiState.Active>()
        viewModel.finish()
        state<FocusUiState.Ended>()

        viewModel.markTargetDone()
        viewModel.closeSummary()
        assertEquals(null, state<FocusUiState.Setup>().target)
    }

    @Test
    fun onTimeAlertsAreOfferedOnceUntilAnswered() = runTest {
        assertTrue(state<FocusUiState.Setup>().offerPreciseAlerts)

        assertTrue(viewModel.acceptPreciseAlerts()) // permission still needed: open Android's screen
        state<FocusUiState.Setup> { !it.offerPreciseAlerts }
        assertTrue(rig.preferences.preciseEndAlerts.first())
    }

    @Test
    fun declinedOnTimeAlertsAreNotOfferedAgain() = runTest {
        state<FocusUiState.Setup> { it.offerPreciseAlerts }
        viewModel.declinePreciseAlerts()
        state<FocusUiState.Setup> { !it.offerPreciseAlerts }
        assertEquals(false, rig.preferences.preciseEndAlerts.first())
    }

    @Test
    fun noOfferWhereExactAlarmsNeedNoPermission() = runTest {
        val rigWithoutPermission = FocusTestRig(scope, tempFolder.newFolder(), alarms = FakeFocusAlarmScheduler(exactAllowed = true, exactNeedsPermission = false))
        val vm = FocusViewModel(SavedStateHandle(), rigWithoutPermission.manager, rigWithoutPermission.tasks, rigWithoutPermission.preferences, rigWithoutPermission.time, rigWithoutPermission.alarms)
        val setup = vm.uiState.first { it is FocusUiState.Setup } as FocusUiState.Setup
        assertEquals(false, setup.offerPreciseAlerts)
        rigWithoutPermission.close()
    }

    @Test
    fun aTaskWithoutStepsIsMarkedDoneAsAWhole() = runTest {
        val id = rig.tasks.create(TaskDraft("Call grandma"))!!
        viewModel.selectTarget(FocusTarget(id))
        viewModel.start()
        state<FocusUiState.Active>()
        viewModel.finish()
        state<FocusUiState.Ended>()

        viewModel.markTargetDone()
        state<FocusUiState.Ended> { it.target?.isDone == true }

        // A finished task is not offered again.
        viewModel.closeSummary()
        assertEquals(null, state<FocusUiState.Setup>().target)
    }
}
