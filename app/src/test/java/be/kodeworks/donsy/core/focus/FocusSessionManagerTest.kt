// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.core.focus

import be.kodeworks.donsy.domain.focus.RecoveryNote
import be.kodeworks.donsy.domain.model.FocusSessionStatus
import be.kodeworks.donsy.domain.model.TaskDraft
import be.kodeworks.donsy.domain.model.TaskStatus
import be.kodeworks.donsy.testing.FakeFocusAlarmScheduler
import be.kodeworks.donsy.testing.FakeFocusNotifier
import be.kodeworks.donsy.testing.FocusTestRig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Duration.ofHours
import java.time.Duration.ofMinutes

@RunWith(RobolectricTestRunner::class)
class FocusSessionManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob())
    private val rig by lazy { FocusTestRig(scope, tempFolder.root) }
    private val manager get() = rig.manager

    @After
    fun tearDown() {
        rig.close()
        scope.cancel()
    }

    private suspend fun taskWithStep(): FocusTarget {
        val taskId = rig.tasks.create(TaskDraft("Essay"))!!
        return FocusTarget(taskId, rig.tasks.addStep(taskId, "Outline")!!)
    }

    @Test
    fun startStoresTheSessionAndSetsUpAlarmAndNotification() = runTest {
        val target = taskWithStep()
        manager.start(target, minutes = 15)

        val session = rig.sessions.getActive()!!
        assertEquals(FocusSessionStatus.RUNNING, session.status)
        assertEquals(target.subtaskId, session.subtaskId)
        assertEquals(rig.time.elapsedRealtimeMillis + ofMinutes(15).toMillis() to false, rig.alarms.scheduled)
        assertEquals(FakeFocusNotifier.Ongoing.Running("Outline", rig.time.now.plus(ofMinutes(15))), rig.notifier.ongoing)
    }

    @Test
    fun onlyOneSessionAtATime() = runTest {
        manager.start(null, minutes = 5)
        val first = rig.sessions.getActive()!!.id
        manager.start(taskWithStep(), minutes = 25)
        assertEquals(first, rig.sessions.getActive()!!.id)
    }

    @Test
    fun preciseAlertsAreUsedOnlyWhenChosenAndAllowed() = runTest {
        rig.preferences.setPreciseEndAlerts(true)
        manager.start(null, minutes = 5)
        assertEquals(false, rig.alarms.scheduled!!.second) // not allowed by Android

        manager.cancel()
        rig.alarms.exactAllowed = true
        manager.start(null, minutes = 5)
        assertEquals(true, rig.alarms.scheduled!!.second)
    }

    @Test
    fun exactAlarmsAreUsedWithoutAskingWhereNoPermissionIsNeeded() = runTest {
        val oldAndroid = FocusTestRig(scope, tempFolder.newFolder(), FakeFocusAlarmScheduler(exactAllowed = true, exactNeedsPermission = false))
        oldAndroid.manager.start(null, minutes = 5)
        assertEquals(true, oldAndroid.alarms.scheduled!!.second)
        oldAndroid.close()
    }

    @Test
    fun pauseCancelsTheAlarmAndShowsTimeLeft_resumeBringsItBack() = runTest {
        manager.start(null, minutes = 10)
        rig.time.advanceBy(ofMinutes(3).plusSeconds(20))

        manager.pause()
        assertNull(rig.alarms.scheduled)
        assertEquals(FakeFocusNotifier.Ongoing.Paused(null, minutesLeft = 7), rig.notifier.ongoing)

        manager.resume()
        assertNotNull(rig.alarms.scheduled)
        assertEquals(FocusSessionStatus.RUNNING, rig.sessions.getActive()!!.status)
    }

    @Test
    fun finishingEarlyCountsAsWorkButNeverCompletesTheTask() = runTest {
        val target = taskWithStep()
        manager.start(target, minutes = 25)
        rig.time.advanceBy(ofMinutes(12))

        manager.finish()

        assertNull(rig.sessions.getActive())
        assertNull(rig.notifier.ongoing)
        val summary = manager.summary.value!!
        assertEquals(FocusSessionStatus.ENDED_EARLY, summary.status)
        assertEquals(12, summary.focusedMinutes)
        assertNull(summary.note)
        val task = rig.tasks.observeTask(target.taskId).first()!!
        assertEquals(TaskStatus.ACTIVE, task.status)
        assertEquals(false, task.subtasks.single().isDone)
        assertEquals(rig.time.now, task.lastWorkedAt)
    }

    @Test
    fun cancellingLeavesNoSummaryAndNoWorkRecorded() = runTest {
        val target = taskWithStep()
        manager.start(target, minutes = 25)
        rig.time.advanceBy(ofMinutes(5))

        manager.cancel()

        assertNull(manager.summary.value)
        assertNull(rig.tasks.observeTask(target.taskId).first()!!.lastWorkedAt)
    }

    @Test
    fun recoveryCompletesASessionThatRanOutWhileAway_andAnnouncesIt() = runTest {
        manager.start(taskWithStep(), minutes = 5)
        rig.time.advanceBy(ofMinutes(30))

        manager.recover(userPresent = false)

        val summary = manager.summary.value!!
        assertEquals(FocusSessionStatus.COMPLETED, summary.status)
        assertEquals(RecoveryNote.FINISHED_WHILE_AWAY, summary.note)
        assertEquals(5, summary.focusedMinutes)
        assertEquals(1, rig.notifier.completedCount)
        assertNull(rig.alarms.scheduled)
    }

    @Test
    fun whenTheTimerRunsOutOnScreenThereIsNoAwayNote() = runTest {
        manager.start(null, minutes = 5)
        rig.time.advanceBy(ofMinutes(5))
        manager.recover(userPresent = true)
        assertNull(manager.summary.value!!.note)
    }

    @Test
    fun recoveryRestoresAlarmAndNotificationOfARunningSession_forExampleAfterAReboot() = runTest {
        manager.start(null, minutes = 15)
        rig.time.advanceBy(ofMinutes(5))
        rig.time.reboot(downtime = ofMinutes(1))
        rig.alarms.cancel()
        rig.notifier.cancelOngoing()

        manager.recover(userPresent = false)

        assertEquals(rig.time.elapsedRealtimeMillis + ofMinutes(9).toMillis() to false, rig.alarms.scheduled)
        assertNotNull(rig.notifier.ongoing)
    }

    @Test
    fun aLongPauseIsWrappedUpOnRecovery() = runTest {
        manager.start(null, minutes = 25)
        rig.time.advanceBy(ofMinutes(8))
        manager.pause()
        rig.time.advanceBy(ofHours(13))

        manager.recover(userPresent = false)

        val summary = manager.summary.value!!
        assertEquals(FocusSessionStatus.ENDED_EARLY, summary.status)
        assertEquals(RecoveryNote.ENDED_AFTER_LONG_PAUSE, summary.note)
        assertEquals(8, summary.focusedMinutes)
        assertEquals(0, rig.notifier.completedCount)
    }

    @Test
    fun minutesAreRoundedForPeople() {
        assertEquals(1, FocusSessionManager.roundedMinutes(java.time.Duration.ofSeconds(10)))
        assertEquals(0, FocusSessionManager.roundedMinutes(java.time.Duration.ZERO))
        assertEquals(12, FocusSessionManager.roundedMinutes(ofMinutes(12).plusSeconds(29)))
        assertEquals(13, FocusSessionManager.roundedUpMinutes(ofMinutes(12).plusSeconds(1)))
    }
}
