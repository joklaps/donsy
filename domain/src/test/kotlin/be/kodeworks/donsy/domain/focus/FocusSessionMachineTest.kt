// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.domain.focus

import be.kodeworks.donsy.domain.model.FocusSessionStatus
import be.kodeworks.donsy.domain.time.FakeTimeSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Duration.ofMinutes
import java.time.Duration.ofSeconds

class FocusSessionMachineTest {

    private val time = FakeTimeSource()
    private val machine = FocusSessionMachine

    private fun started(minutes: Long = 15) = machine.start(taskId = 7, subtaskId = 70, planned = ofMinutes(minutes), time = time)

    @Test
    fun startRunsWithTheFullTimeLeft() {
        val session = started()
        assertEquals(FocusSessionStatus.RUNNING, session.status)
        assertEquals(7L, session.taskId)
        assertEquals(70L, session.subtaskId)
        assertEquals(ofMinutes(15), machine.remaining(session, time))
    }

    @Test
    fun timeCountsDownWhileRunning() {
        val session = started()
        time.advanceBy(ofMinutes(4).plusSeconds(30))
        assertEquals(ofMinutes(10).plusSeconds(30), machine.remaining(session, time))
    }

    @Test
    fun pausingStopsTheClockAndResumingContinuesIt() {
        var session = started()
        time.advanceBy(ofMinutes(5))
        session = machine.pause(session, time)
        assertEquals(FocusSessionStatus.PAUSED, session.status)
        assertEquals(time.now, session.pausedAt)

        time.advanceBy(ofMinutes(30)) // a long break doesn't eat the session
        assertEquals(ofMinutes(10), machine.remaining(session, time))

        session = machine.resume(session, time)
        assertNull(session.pausedAt)
        time.advanceBy(ofMinutes(2))
        assertEquals(ofMinutes(8), machine.remaining(session, time))
    }

    @Test
    fun changingTheWallClockHasNoEffect() {
        val session = started()
        time.advanceBy(ofMinutes(3))
        time.setWallClock(time.now.plus(Duration.ofHours(5))) // e.g. time zone or manual change
        assertEquals(ofMinutes(12), machine.remaining(session, time))
        time.setWallClock(time.now.minus(Duration.ofDays(1)))
        assertEquals(ofMinutes(12), machine.remaining(session, time))
    }

    @Test
    fun afterARebootWallTimeIsUsed() {
        val session = started()
        time.advanceBy(ofMinutes(3))
        time.reboot(downtime = ofMinutes(2)) // monotonic clock restarts, wall clock carries on
        assertEquals(ofMinutes(10), machine.remaining(session, time))
    }

    @Test
    fun afterARebootAClockSetBackNeverAddsTime() {
        val session = started()
        time.reboot()
        time.setWallClock(session.startedAt.minus(Duration.ofHours(1)))
        assertEquals(ofMinutes(15), machine.remaining(session, time))
    }

    @Test
    fun resumingAfterARebootStartsAFreshMeasurement() {
        var session = started()
        time.advanceBy(ofMinutes(5))
        session = machine.pause(session, time)
        time.reboot()
        session = machine.resume(session, time)
        assertEquals(time.bootCount, session.bootCount)
        time.advanceBy(ofMinutes(1))
        assertEquals(ofMinutes(9), machine.remaining(session, time))
    }

    @Test
    fun finishEarlyKeepsTheTimeSpent() {
        var session = started()
        time.advanceBy(ofMinutes(6))
        session = machine.finish(session, time)
        assertEquals(FocusSessionStatus.ENDED_EARLY, session.status)
        assertEquals(ofMinutes(6), session.accumulatedRun)
        assertEquals(time.now, session.endedAt)
    }

    @Test
    fun finishingWhenTheTimeIsUpCompletes() {
        var session = started(minutes = 5)
        time.advanceBy(ofMinutes(7))
        session = machine.finish(session, time)
        assertEquals(FocusSessionStatus.COMPLETED, session.status)
        assertEquals(ofMinutes(5), session.accumulatedRun)
    }

    @Test
    fun cancelEndsWithoutCompleting() {
        var session = started()
        time.advanceBy(ofSeconds(20))
        session = machine.cancel(session, time)
        assertEquals(FocusSessionStatus.CANCELLED, session.status)
        assertEquals(machine.cancel(session, time), session) // ended sessions don't change
    }

    @Test
    fun recoveryCompletesASessionThatRanOutWhileAway_atItsPlannedEnd() {
        val session = started(minutes = 10)
        val plannedEnd = session.startedAt.plus(ofMinutes(10))
        time.advanceBy(ofMinutes(45))

        val (recovered, note) = machine.recover(session, time)

        assertEquals(RecoveryNote.FINISHED_WHILE_AWAY, note)
        assertEquals(FocusSessionStatus.COMPLETED, recovered.status)
        assertEquals(plannedEnd, recovered.endedAt)
        assertEquals(ofMinutes(10), recovered.accumulatedRun)
    }

    @Test
    fun recoveryLeavesARunningSessionWithTimeLeftAlone() {
        val session = started()
        time.advanceBy(ofMinutes(14))
        assertEquals(session to null, machine.recover(session, time))
    }

    @Test
    fun recoveryWrapsUpALongPause_butNotAShortOne() {
        var session = started()
        time.advanceBy(ofMinutes(4))
        session = machine.pause(session, time)
        val pausedAt = time.now

        time.advanceBy(Duration.ofHours(11))
        assertEquals(session to null, machine.recover(session, time))

        time.advanceBy(Duration.ofHours(2))
        val (recovered, note) = machine.recover(session, time)
        assertEquals(RecoveryNote.ENDED_AFTER_LONG_PAUSE, note)
        assertEquals(FocusSessionStatus.ENDED_EARLY, recovered.status)
        assertEquals(pausedAt, recovered.endedAt)
        assertEquals(ofMinutes(4), recovered.accumulatedRun)
    }

    @Test
    fun aRebootWhileRunningCanStillFinishWhileAway() {
        val session = started(minutes = 5)
        time.advanceBy(ofMinutes(2))
        time.reboot(downtime = ofMinutes(10))
        assertEquals(RecoveryNote.FINISHED_WHILE_AWAY, machine.recover(session, time).second)
    }
}
