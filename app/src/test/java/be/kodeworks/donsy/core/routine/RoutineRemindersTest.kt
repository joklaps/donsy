// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.core.routine

import be.kodeworks.donsy.data.repository.RoomTaskRepository
import be.kodeworks.donsy.domain.model.RepeatDays
import be.kodeworks.donsy.domain.model.TaskDraft
import be.kodeworks.donsy.domain.time.FakeTimeSource
import be.kodeworks.donsy.testing.FakeRoutineAlarmScheduler
import be.kodeworks.donsy.testing.FakeRoutineNotifier
import be.kodeworks.donsy.testing.inMemoryDatabase
import be.kodeworks.donsy.testing.testPreferences
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
class RoutineRemindersTest {

    private val database = inMemoryDatabase()
    // Wednesday 21 October 2026, 7:00 in Brussels.
    private val time = FakeTimeSource(now = Instant.parse("2026-10-21T05:00:00Z"))
    private val tasks = RoomTaskRepository(database, time)
    private val alarms = FakeRoutineAlarmScheduler()
    private val notifier = FakeRoutineNotifier()
    private val reminders = RoutineReminders(tasks, testPreferences(), time, alarms, notifier)

    private val wednesdayAt8 = Instant.parse("2026-10-21T06:00:00Z")
    private val thursdayAt8 = Instant.parse("2026-10-22T06:00:00Z")

    @After
    fun tearDown() = database.close()

    private suspend fun pill() =
        tasks.create(TaskDraft("Take my pill", dueTime = LocalTime.of(8, 0), repeatDays = RepeatDays.EVERY_DAY))!!

    @Test
    fun theAlarmIsSetForTheNextReminderAndItShowsWhenItFires() = runTest {
        val pill = pill()
        reminders.sync()
        assertEquals(wednesdayAt8, alarms.scheduled)
        assertTrue(notifier.shown.isEmpty())

        time.now = wednesdayAt8
        reminders.sync()
        assertEquals(listOf(pill), notifier.shown)
        assertEquals(thursdayAt8, alarms.scheduled)

        // Syncing again (any task change does) doesn't repeat it.
        reminders.sync()
        assertEquals(listOf(pill), notifier.shown)
    }

    @Test
    fun theFirstCheckEverOnlyStartsTheClock() = runTest {
        pill()
        time.now = wednesdayAt8.plusSeconds(60)
        reminders.sync()
        assertTrue(notifier.shown.isEmpty())
    }

    @Test
    fun doneFromTheNotificationClearsItAndSkipsToTomorrow() = runTest {
        val pill = pill()
        reminders.sync()
        time.now = wednesdayAt8
        reminders.sync()

        reminders.markDone(pill)
        assertTrue(notifier.showing.isEmpty())
        assertEquals(thursdayAt8, alarms.scheduled)
    }

    @Test
    fun aReminderMissedForMoreThanAnHourIsNotShownLate() = runTest {
        pill()
        reminders.sync()
        // The phone was off from 7:00 until 9:30.
        time.now = wednesdayAt8.plus(Duration.ofMinutes(90))
        reminders.sync()
        assertTrue(notifier.shown.isEmpty())
    }

    @Test
    fun aPausedRoutineSetsNoAlarm() = runTest {
        val pill = pill()
        tasks.pause(pill)
        reminders.sync()
        assertNull(alarms.scheduled)
    }
}
