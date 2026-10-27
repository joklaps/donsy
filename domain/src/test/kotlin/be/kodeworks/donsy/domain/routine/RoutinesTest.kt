// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.domain.routine

import be.kodeworks.donsy.domain.model.Importance
import be.kodeworks.donsy.domain.model.RepeatDays
import be.kodeworks.donsy.domain.model.Task
import be.kodeworks.donsy.domain.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class RoutinesTest {

    private val zone = ZoneId.of("Europe/Brussels")
    private val wednesday = LocalDate.of(2026, 10, 21)
    private var nextId = 1L

    private fun at(date: LocalDate, hour: Int, minute: Int = 0): Instant = ZonedDateTime.of(date.atTime(hour, minute), zone).toInstant()

    private fun routine(
        days: RepeatDays = RepeatDays.EVERY_DAY,
        time: LocalTime = LocalTime.of(8, 0),
        doneOn: LocalDate? = null,
        status: TaskStatus = TaskStatus.ACTIVE,
        updatedAt: Instant = at(wednesday.minusDays(7), 12),
    ) = Task(
        id = nextId++, title = "Take my pill", notes = "", status = status, importance = Importance.NORMAL,
        dueDate = null, dueTime = time, repeatDays = days, routineDoneOn = doneOn,
        estimateMinutes = null, effort = null, notNowUntil = null, lastWorkedAt = null, sourceBrainDumpId = null,
        createdAt = updatedAt, updatedAt = updatedAt, completedAt = null, subtasks = emptyList(),
    )

    @Test
    fun theNextReminderIsTodayIfItsTimeIsStillAhead() {
        val pill = routine()
        assertEquals(at(wednesday, 8), Routines.nextReminder(listOf(pill), at(wednesday, 7, 30), zone))
        assertEquals(at(wednesday.plusDays(1), 8), Routines.nextReminder(listOf(pill), at(wednesday, 8), zone))
    }

    @Test
    fun aRoutineDoneTodayIsNotRemindedAgainUntilItsNextDay() {
        val pill = routine(doneOn = wednesday)
        assertEquals(at(wednesday.plusDays(1), 8), Routines.nextReminder(listOf(pill), at(wednesday, 6), zone))
    }

    @Test
    fun onlyItsOwnDaysCount() {
        val schoolbag = routine(days = RepeatDays.WEEKDAYS, time = LocalTime.of(19, 0))
        val friday = wednesday.plusDays(2)
        // Friday evening: the next one is Monday, not the weekend.
        assertEquals(at(friday.plusDays(3), 19), Routines.nextReminder(listOf(schoolbag), at(friday, 20), zone))
    }

    @Test
    fun theEarliestOfAllRoutinesComesFirstAndPausedOnesNeverRemind() {
        val pill = routine(time = LocalTime.of(8, 0))
        val bag = routine(time = LocalTime.of(19, 0))
        val paused = routine(time = LocalTime.of(9, 0), status = TaskStatus.PAUSED)
        assertEquals(at(wednesday, 19), Routines.nextReminder(listOf(bag, pill, paused), at(wednesday, 12), zone))
        assertNull(Routines.nextReminder(listOf(paused), at(wednesday, 12), zone))
    }

    @Test
    fun theTimeStaysEightOClockAcrossTheChangeToWinterTime() {
        // Summer time ends on Sunday 25 October 2026 in Brussels.
        val pill = routine(days = RepeatDays.of(setOf(DayOfWeek.MONDAY))!!)
        val monday = LocalDate.of(2026, 10, 26)
        assertEquals(Instant.parse("2026-10-26T07:00:00Z"), Routines.nextReminder(listOf(pill), at(wednesday, 12), zone))
        assertEquals(at(monday, 8), Routines.nextReminder(listOf(pill), at(wednesday, 12), zone))
    }

    @Test
    fun remindersDueAreThoseWhoseTimeFellInTheWindow() {
        val pill = routine(time = LocalTime.of(8, 0))
        val bag = routine(time = LocalTime.of(19, 0))
        val due = Routines.remindersDue(listOf(pill, bag), after = at(wednesday, 7, 59), now = at(wednesday, 8, 0), zone = zone)
        assertEquals(listOf(pill), due)
        assertTrue(Routines.remindersDue(listOf(pill), after = at(wednesday, 8), now = at(wednesday, 9), zone = zone).isEmpty())
    }

    @Test
    fun aReminderJustBeforeMidnightThatArrivesLateStillCounts() {
        val late = routine(time = LocalTime.of(23, 59))
        val due = Routines.remindersDue(listOf(late), after = at(wednesday, 23, 58), now = at(wednesday.plusDays(1), 0, 1), zone = zone)
        assertEquals(listOf(late), due)
    }

    @Test
    fun noReminderForATimeThatPassedBeforeTheRoutineWasMadeOrWhenItIsDone() {
        val madeLate = routine(time = LocalTime.of(8, 0), updatedAt = at(wednesday, 8, 30))
        val done = routine(time = LocalTime.of(8, 0), doneOn = wednesday)
        assertTrue(Routines.remindersDue(listOf(madeLate, done), after = at(wednesday, 7), now = at(wednesday, 9), zone = zone).isEmpty())
    }

    @Test
    fun openTodayIsTodaysRoutinesThatAreNotDoneYet() {
        val pill = routine()
        val done = routine(doneOn = wednesday)
        val doneYesterday = routine(doneOn = wednesday.minusDays(1))
        val weekend = routine(days = RepeatDays.of(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))!!)
        assertEquals(listOf(pill, doneYesterday), Routines.openToday(listOf(pill, done, doneYesterday, weekend), wednesday))
        assertFalse(Routines.isDoneOn(doneYesterday, wednesday))
    }
}
