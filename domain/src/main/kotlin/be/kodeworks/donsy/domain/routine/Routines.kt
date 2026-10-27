// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.domain.routine

import be.kodeworks.donsy.domain.model.Task
import be.kodeworks.donsy.domain.model.TaskStatus
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Rules for routines: tasks that repeat on some weekdays at a set time (see docs/spec.md,
 * "Routines"). A routine is never "done" for good; it is done for one day ([Task.routineDoneOn]).
 * Days that pass without it being done simply drop away: nothing piles up.
 */
object Routines {

    /** How far back a reminder is still shown when Donsy was not running at its time (e.g. phone off). */
    val CATCH_UP_LIMIT: Duration = Duration.ofHours(1)

    fun occursOn(task: Task, date: LocalDate): Boolean = task.repeatDays?.contains(date.dayOfWeek) == true

    fun isDoneOn(task: Task, date: LocalDate): Boolean = task.routineDoneOn == date

    /** Only active routines remind; a paused one stays quiet until it is resumed. */
    private fun Task.reminds(): Boolean = repeatDays != null && dueTime != null && status == TaskStatus.ACTIVE

    /** The routine's reminder on [date], or null when it has none that day. */
    private fun Task.reminderOn(date: LocalDate, zone: ZoneId): Instant? {
        val time = dueTime ?: return null
        if (!reminds() || !occursOn(this, date) || isDoneOn(this, date)) return null
        return date.atTime(time).atZone(zone).toInstant()
    }

    /** The first reminder strictly after [now], over all [tasks]; null when there is none. */
    fun nextReminder(tasks: List<Task>, now: Instant, zone: ZoneId): Instant? {
        val today = now.atZone(zone).toLocalDate()
        return tasks.mapNotNull { task ->
            // A week ahead always reaches every repeat day, plus today's later time.
            (0L..7L).firstNotNullOfOrNull { offset ->
                task.reminderOn(today.plusDays(offset), zone)?.takeIf { it.isAfter(now) }
            }
        }.minOrNull()
    }

    /**
     * Routines whose reminder time fell in ([after], [now]]. Yesterday counts too, for a reminder
     * just before midnight that arrives a little late. A routine created or edited after its time
     * doesn't remind for that time.
     */
    fun remindersDue(tasks: List<Task>, after: Instant, now: Instant, zone: ZoneId): List<Task> {
        val today = now.atZone(zone).toLocalDate()
        return tasks.filter { task ->
            listOf(today.minusDays(1), today).any { date ->
                val at = task.reminderOn(date, zone)
                at != null && at.isAfter(after) && !at.isAfter(now) && task.updatedAt.isBefore(at)
            }
        }
    }

    /** Routines whose reminder may stay on screen: today is one of their days and they are not done yet. */
    fun openToday(tasks: List<Task>, today: LocalDate): List<Task> =
        tasks.filter { it.reminds() && occursOn(it, today) && !isDoneOn(it, today) }
}
