// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.core.routine

import be.kodeworks.donsy.data.preferences.UserPreferencesRepository
import be.kodeworks.donsy.domain.model.Task
import be.kodeworks.donsy.domain.repository.TaskRepository
import be.kodeworks.donsy.domain.routine.Routines
import be.kodeworks.donsy.domain.time.TimeSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps routine reminders in step with the routines. One [sync] does everything: show what came
 * due since the last check, clear what no longer applies and set the alarm for the next one. It
 * runs whenever tasks change, when the alarm fires and after reboots and clock changes, so there is
 * no separate bookkeeping per routine.
 */
@Singleton
class RoutineReminders @Inject constructor(
    private val tasks: TaskRepository,
    private val preferences: UserPreferencesRepository,
    private val time: TimeSource,
    private val alarms: RoutineAlarmScheduler,
    private val notifier: RoutineNotifier,
) {
    private val mutex = Mutex()

    /** Runs for as long as the app process lives. */
    suspend fun keepInSync() {
        tasks.observeTasks().collect { sync(it) }
    }

    suspend fun sync(current: List<Task>? = null) = mutex.withLock {
        val all = current ?: tasks.observeTasks().first()
        val routines = all.filter { it.isRoutine }
        val now = time.now()
        val zone = time.zone()

        val checkedUntil = preferences.routineRemindersCheckedUntil.first()
        // The first check ever only starts the clock; a missed reminder is shown up to an hour late.
        if (checkedUntil != null) {
            val after = maxOf(checkedUntil, now.minus(Routines.CATCH_UP_LIMIT))
            Routines.remindersDue(routines, after, now, zone).forEach(notifier::show)
        }
        preferences.setRoutineRemindersCheckedUntil(now)

        notifier.retainOnly(Routines.openToday(routines, now.atZone(zone).toLocalDate()).mapTo(mutableSetOf()) { it.id })

        val next = Routines.nextReminder(routines, now, zone)
        if (next == null) alarms.cancel() else alarms.schedule(next)
    }

    /** The notification's "Done" action. */
    suspend fun markDone(taskId: Long) {
        tasks.setRoutineDone(taskId, done = true)
        sync()
    }
}
